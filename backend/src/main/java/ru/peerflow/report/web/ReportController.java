package ru.peerflow.report.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ru.peerflow.report.service.AttachmentNotFoundException;
import ru.peerflow.report.service.ReportService;
import ru.peerflow.report.web.dto.ReportResponse;
import ru.peerflow.report.web.dto.SpendRequest;

/**
 * Отчёты лидера, файлы и траты бюджета ({@code BR-LIFE-7},
 * {@code BR-BUDGET-4}).
 *
 * <p>Всё закрыто токеном и членством ({@code BR-PRIV-1}): отчёты — контент
 * сообщества. Публиковать отчёт и тратить бюджет может только лидер задачи;
 * читать — любой действующий член ({@code BR-LIFE-9}).
 *
 * <p>Отчёт принимается как {@code multipart/form-data}: текст и файл приходят
 * одним запросом, чтобы «отчёт с текстом и файлом» не распадался на два
 * вызова, где второй может не дойти. Формат отчёта при этом не появляется —
 * текст становится обычным сообщением под задачей ({@code BR-LIFE-7}).
 */
@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Состояние отчёта задачи: файлы, траты и остаток бюджета.
     *
     * @param proposalId     задача
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return отчёт задачи глазами клиента
     */
    @GetMapping(
            path = "/proposals/{proposalId}/report",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportResponse report(@PathVariable UUID proposalId, Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return reportService.report(memberId, proposalId);
    }

    /**
     * Публикует отчёт лидера ({@code BR-LIFE-7}).
     *
     * @param proposalId     задача
     * @param text           текст отчёта; может не прилагаться, если есть файл
     * @param file           файл отчёта; может не прилагаться, если есть текст
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return {@code 201 Created} с состоянием отчёта задачи
     * @throws IOException если файл не удалось сохранить
     */
    @PostMapping(
            path = "/proposals/{proposalId}/reports",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportResponse> post(
            @PathVariable UUID proposalId,
            // Текст читаем как обычное поле формы, а не как часть с типом:
            // клиенты отправляют его без content-type, и разбор через
            // конвертер давал бы «нет подходящего конвертера» вместо отчёта.
            @RequestParam(value = "text", required = false) String text,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) throws IOException {
        UUID memberId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.post(memberId, proposalId, text, file));
    }

    /**
     * Записывает трату бюджета задачи ({@code BR-BUDGET-4}).
     *
     * @param proposalId     задача
     * @param request        сумма и заметка
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return {@code 201 Created} с состоянием отчёта задачи
     */
    @PostMapping(
            path = "/proposals/{proposalId}/expenses",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportResponse> spend(
            @PathVariable UUID proposalId,
            @Valid @RequestBody SpendRequest request,
            Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.spend(memberId, proposalId, request.amount(), request.note()));
    }

    /**
     * Скачивает файл отчёта ({@code BR-LIFE-7}).
     *
     * <p>Отдаём как {@code attachment}: браузер не должен открывать файл на
     * месте, иначе загруженный {@code .html} исполнялся бы в контексте сайта.
     *
     * @param attachmentId   вложение
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return файл с именем, которое дал человек
     * @throws IOException если файл не читается
     */
    @GetMapping(path = "/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable UUID attachmentId,
                                             Authentication authentication) throws IOException {
        UUID memberId = UUID.fromString(authentication.getName());
        ReportService.AttachmentFile found = reportService.file(memberId, attachmentId);
        Resource resource = new FileSystemResource(found.path());
        if (!resource.exists()) {
            // Файл могли убрать с диска при переносе каталога: описание в базе
            // ещё есть, а скачать нечего. Для клиента это «не найдено».
            throw new AttachmentNotFoundException(attachmentId);
        }
        // BR-PRIV-1: файл отчёта — не публичный ресурс, кэшировать его нельзя.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.attachment().getContentType()))
                .contentLength(Files.size(found.path()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(found.attachment().getFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }
}

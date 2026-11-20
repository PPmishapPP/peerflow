package ru.peerflow.report.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ru.peerflow.config.StorageProperties;

/**
 * Сохранение файлов отчётов на диск сервера ({@code BR-LIFE-7}).
 *
 * <p>В БД попадает только относительный путь внутри {@code peerflow.storage.root}
 * — так каталог можно перенести, не меняя схему. Файлы отчётов лежат отдельно
 * от аватаров: у них другие допустимые типы и другой срок жизни.
 *
 * <p>Проверяем размер и расширение. Расширение не заменяет антивирус, но
 * отсекает очевидно посторонние файлы; разбор содержимого в MVP не делаем
 * (YAGNI). Тип для ответа клиенту берём из загруженного файла, а если он не
 * указан — считаем потоком байтов.
 */
@Service
public class ReportStorage {

    /** Больше этого файл отчёта не принимаем: отчёт — документ, а не архив. */
    static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    /**
     * Разрешённые расширения.
     *
     * <p>Список намеренно узкий: документы, таблицы, презентации, картинки и
     * простой текст. Исполняемые файлы и архивы не принимаем — их нечем
     * проверить, а в отчёте они не нужны.
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "txt", "md", "csv",
            "png", "jpg", "jpeg", "webp",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "odt", "ods", "odp");

    /** Подкаталог каталога вложений, куда складываем файлы отчётов. */
    private static final String REPORTS_DIR = "reports";

    private final StorageProperties properties;

    public ReportStorage(StorageProperties properties) {
        this.properties = properties;
    }

    /**
     * Сохраняет файл отчёта и возвращает путь относительно корня вложений.
     *
     * @param file загруженный файл
     * @return путь вида {@code reports/<uuid>.pdf}
     * @throws IllegalArgumentException если файл пуст, слишком большой или
     *                                  имеет недопустимый тип
     * @throws IOException              если не удалось записать файл
     */
    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("файл отчёта пуст");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("файл больше допустимых "
                    + MAX_ATTACHMENT_BYTES / (1024 * 1024) + " МБ");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "такой тип файла не принимается: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        String fileName = UUID.randomUUID() + "." + extension;
        Path directory = properties.rootPath().resolve(REPORTS_DIR);
        Files.createDirectories(directory);

        Path target = directory.resolve(fileName).normalize();
        // Страховка от выхода за пределы каталога вложений: имя мы генерируем
        // сами, но путь всё равно проверяем.
        if (!target.startsWith(properties.rootPath())) {
            throw new IllegalArgumentException("недопустимый путь файла");
        }

        try (InputStream source = file.getInputStream()) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return REPORTS_DIR + "/" + fileName;
    }

    /**
     * Удаляет сохранённый файл. Ошибки игнорируются: файла может уже не быть,
     * и это не повод ломать ответ.
     *
     * @param relativePath путь, возвращённый {@link #store(MultipartFile)}
     */
    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(properties.rootPath().resolve(relativePath).normalize());
        } catch (IOException ignored) {
            // Файл — не критичные данные: запись в базе важнее.
        }
    }

    /**
     * Файл по относительному пути — для скачивания.
     *
     * @param relativePath путь из описания вложения
     * @return путь на диске
     */
    public Path resolve(String relativePath) {
        Path target = properties.rootPath().resolve(relativePath).normalize();
        if (!target.startsWith(properties.rootPath())) {
            throw new IllegalArgumentException("недопустимый путь файла");
        }
        return target;
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

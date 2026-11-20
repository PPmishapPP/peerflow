package ru.peerflow.community.web;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ru.peerflow.community.service.JoinRequestService;
import ru.peerflow.community.web.dto.JoinRequestResponse;
import ru.peerflow.community.web.dto.JoinVoteRequest;
import ru.peerflow.community.web.dto.SubmitJoinRequestRequest;

/**
 * Заявки на вступление и голосование ({@code BR-JOIN-4}, {@code BR-JOIN-5}).
 *
 * <p>Всё закрыто токеном ({@code BR-PRIV-1}): заявки содержат рассказ о себе
 * и фото, это контент сообщества. Подать заявку может любой зарегистрированный
 * аккаунт, смотреть список и голосовать — только действующий член
 * ({@code BR-VOTE-1}).
 *
 * <p>Подача принимает {@code multipart/form-data} — JSON-часть {@code request}
 * и необязательный файл {@code avatar}, как и регистрация.
 */
@RestController
@RequestMapping("/api/join-requests")
public class JoinRequestController {

    private final JoinRequestService joinRequestService;

    public JoinRequestController(JoinRequestService joinRequestService) {
        this.joinRequestService = joinRequestService;
    }

    /**
     * Подаёт заявку на вступление.
     *
     * @param request        «о себе»
     * @param avatar         фотография; может не прилагаться
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return {@code 201 Created} с заявкой
     */
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JoinRequestResponse> submit(
            @Valid @RequestPart("request") SubmitJoinRequestRequest request,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar,
            Authentication authentication) throws IOException {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(joinRequestService.submit(userId, request, avatar));
    }

    /**
     * Список заявок на рассмотрение.
     *
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return заявки в состоянии {@code PENDING}
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<JoinRequestResponse> pending(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return joinRequestService.pendingFor(userId);
    }

    /**
     * Последняя заявка самого человека — в любом состоянии.
     *
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return {@code 200} с заявкой или {@code 204}, если человек её не подавал
     */
    @GetMapping(path = "/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JoinRequestResponse> mine(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return joinRequestService.ownRequest(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Голосует по заявке.
     *
     * @param requestId      заявка
     * @param request        «за» или «против»
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return заявка после голосования
     */
    @PostMapping(
            path = "/{requestId}/votes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JoinRequestResponse vote(@PathVariable UUID requestId,
                                    @Valid @RequestBody JoinVoteRequest request,
                                    Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return joinRequestService.vote(userId, requestId, request.accept());
    }
}

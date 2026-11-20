package ru.peerflow.legitimacy.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.peerflow.legitimacy.service.LegitimacyService;
import ru.peerflow.legitimacy.web.dto.LegitimacyBoardResponse;

/**
 * Легитимность и кандидаты по задаче ({@code BR-LEGIT-2}–{@code BR-LEGIT-5},
 * {@code BR-LEADER-1}–{@code BR-LEADER-4}).
 *
 * <p>Всё закрыто токеном и членством ({@code BR-PRIV-1}): кто кого
 * поддерживает — это контент сообщества, а не публичные данные.
 *
 * <p>Почему ответ всегда один и тот же тип: экран показывает кандидатов,
 * лидера и «кому я передал доверие» одновременно. Если бы каждое действие
 * возвращало свою форму, клиент собирал бы состояние из трёх источников и
 * показывал бы лидера, которого уже нет.
 */
@RestController
@RequestMapping("/api/proposals/{proposalId}/legitimacy")
public class LegitimacyController {

    private final LegitimacyService legitimacyService;

    public LegitimacyController(LegitimacyService legitimacyService) {
        this.legitimacyService = legitimacyService;
    }

    /**
     * Кандидаты, лидер и текущая передача доверия.
     *
     * @param proposalId     задача
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return состояние легитимности задачи глазами клиента
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public LegitimacyBoardResponse board(@PathVariable UUID proposalId,
                                         Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return legitimacyService.board(memberId, proposalId);
    }

    /**
     * Передаёт свою легитимность кандидату ({@code BR-LEGIT-2}).
     *
     * @param proposalId     задача
     * @param candidateId    кандидат
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return состояние легитимности задачи после передачи
     */
    @PostMapping(
            path = "/{candidateId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LegitimacyBoardResponse> delegate(
            @PathVariable UUID proposalId,
            @PathVariable UUID candidateId,
            Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.OK)
                .body(legitimacyService.delegate(memberId, proposalId, candidateId));
    }

    /**
     * Забирает свою легитимность себе ({@code BR-LEGIT-5}).
     *
     * <p>DELETE без тела: отзывается ровно то, что моё, и указывать, у кого
     * именно, не нужно. Иначе клиенту пришлось бы запоминать, кому он передал
     * доверие, и промах по устаревшим данным отзывал бы не то.
     *
     * @param proposalId     задача
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return состояние легитимности задачи после отзыва
     */
    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public LegitimacyBoardResponse revoke(@PathVariable UUID proposalId,
                                          Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return legitimacyService.revoke(memberId, proposalId);
    }

    /**
     * Выдвигает себя кандидатом ({@code BR-LEADER-1}).
     *
     * @param proposalId     задача
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return состояние легитимности задачи после выдвижения
     */
    @PostMapping(
            path = "/candidates",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LegitimacyBoardResponse> nominate(
            @PathVariable UUID proposalId,
            Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(legitimacyService.nominate(memberId, proposalId));
    }
}

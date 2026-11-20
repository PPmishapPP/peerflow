package ru.peerflow.discussion.web;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.peerflow.discussion.service.DiscussionService;
import ru.peerflow.discussion.service.MessageSort;
import ru.peerflow.discussion.web.dto.PostMessageRequest;
import ru.peerflow.discussion.web.dto.ReactionRequest;
import ru.peerflow.discussion.web.dto.ThreadResponse;

/**
 * Дискуссия под предложением ({@code BR-DISC-1}–{@code BR-DISC-6},
 * {@code BR-VOTE-6}).
 *
 * <p>Всё закрыто токеном и членством ({@code BR-PRIV-1}): обсуждение — контент
 * сообщества. Писать и реагировать может только действующий член
 * ({@code BR-VOTE-1}).
 *
 * <p>Ответы на сообщения возвращаются вложенными в том же ответе: дерево
 * собирает сервер, и клиент показывает его как есть ({@code BR-DISC-6}).
 * Вместе с деревом приходит число скрытых метками «спам» сообщений — иначе
 * человек видел бы пустоту и не знал, что обсуждение было
 * ({@code BR-SPAM-1}).
 */
@RestController
@RequestMapping("/api")
public class DiscussionController {

    private final DiscussionService discussionService;

    public DiscussionController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    /**
     * Ветка обсуждения предложения.
     *
     * @param proposalId     предложение
     * @param sort           сортировка: {@code POPULAR} или {@code NEW};
     *                       по умолчанию популярность ({@code BR-DISC-4},
     *                       {@code BR-DISC-5})
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return корневые сообщения с ответами и число скрытых
     */
    @GetMapping(
            path = "/proposals/{proposalId}/messages",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ThreadResponse thread(@PathVariable UUID proposalId,
                                 @RequestParam(name = "sort", required = false) String sort,
                                 Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        // Разбор значения до обращения к базе: неизвестная сортировка должна
        // давать 400, а не список в другом порядке ({@code BR-DISC-4}).
        return discussionService.thread(memberId, proposalId, MessageSort.parse(sort));
    }

    /**
     * Пишет сообщение или ответ на сообщение ({@code BR-DISC-1},
     * {@code BR-DISC-3}).
     *
     * @param proposalId     предложение
     * @param request        текст и, возможно, родительское сообщение
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return {@code 201 Created} с веткой после добавления
     */
    @PostMapping(
            path = "/proposals/{proposalId}/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ThreadResponse> post(
            @PathVariable UUID proposalId,
            @Valid @RequestBody PostMessageRequest request,
            Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(discussionService.post(memberId, proposalId, request));
    }

    /**
     * Ставит или меняет реакцию на сообщение ({@code BR-DISC-2},
     * {@code BR-VOTE-6}).
     *
     * @param messageId      сообщение
     * @param request        вид реакции
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return ветка обсуждения после реакции
     */
    @PostMapping(
            path = "/messages/{messageId}/reactions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ThreadResponse react(@PathVariable UUID messageId,
                                @Valid @RequestBody ReactionRequest request,
                                Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return discussionService.react(memberId, messageId, request.kind());
    }
}

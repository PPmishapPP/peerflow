package ru.peerflow.proposal.web;

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
import org.springframework.web.bind.annotation.RestController;

import ru.peerflow.proposal.service.ClosureService;
import ru.peerflow.proposal.web.dto.ClosureStateResponse;
import ru.peerflow.proposal.web.dto.ClosureVoteRequest;

/**
 * Закрытие задачи: запуск процедуры и голосование «выполнено / не выполнено»
 * ({@code BR-LIFE-8}, {@code BR-LIFE-9}, {@code BR-VOTE-5},
 * {@code BR-OVER-9}).
 *
 * <p>Всё закрыто токеном и членством ({@code BR-PRIV-1}). Запускает процедуру
 * только лидер задачи, голосует — любой действующий член: выполнение работы
 * оценивает сообщество, а не исполнитель.
 *
 * <p>Ответ у всех методов один и тот же тип намеренно: экран показывает
 * счётчики, свой ответ и итог одновременно. Разные формы ответа заставили бы
 * клиент собирать состояние из трёх источников и показывать голос, которого
 * уже нет.
 */
@RestController
@RequestMapping("/api/proposals/{proposalId}/closure")
public class ClosureController {

    private final ClosureService closureService;

    public ClosureController(ClosureService closureService) {
        this.closureService = closureService;
    }

    /**
     * Состояние голосования по выполнению.
     *
     * @param proposalId     задача
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return счётчики, срок и свой ответ
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ClosureStateResponse state(@PathVariable UUID proposalId,
                                      Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return closureService.state(memberId, proposalId);
    }

    /**
     * Лидер запускает процедуру закрытия ({@code BR-LIFE-8}).
     *
     * @param proposalId     задача
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return {@code 201 Created} с состоянием голосования
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClosureStateResponse> open(@PathVariable UUID proposalId,
                                                     Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(closureService.open(memberId, proposalId));
    }

    /**
     * Голосует «выполнено / не выполнено» или меняет ответ ({@code BR-VOTE-5}).
     *
     * @param proposalId     задача
     * @param request        «выполнено» или нет
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return состояние голосования после голоса
     */
    @PostMapping(
            path = "/votes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ClosureStateResponse vote(@PathVariable UUID proposalId,
                                     @Valid @RequestBody ClosureVoteRequest request,
                                     Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return closureService.vote(memberId, proposalId, request.completed());
    }
}

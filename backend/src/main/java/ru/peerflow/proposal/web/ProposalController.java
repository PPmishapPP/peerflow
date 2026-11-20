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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.service.ProposalSort;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.proposal.web.dto.DecisionVoteRequest;
import ru.peerflow.proposal.web.dto.ProposalListResponse;
import ru.peerflow.proposal.web.dto.ProposalResponse;

/**
 * Предложения сообщества ({@code BR-OVER-2}, {@code BR-LIFE-1},
 * {@code BR-RANK-5}, {@code BR-VOTE-4}).
 *
 * <p>Всё закрыто токеном и членством ({@code BR-PRIV-1}): предложения — контент
 * сообщества. Внести предложение и голосовать может только действующий член
 * ({@code BR-VOTE-1}).
 */
@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    /**
     * Список предложений двумя секциями: «В работе» и «На обсуждении»
     * ({@code BR-RANK-5}).
     *
     * <p>Секции отдаём явно: их состав и порядок — правило сообщества, а не
     * оформление экрана. Клиент не должен восстанавливать разбиение сам, иначе
     * оно разъедется между платформами.
     *
     * @param sort           сортировка: {@code POPULAR}, {@code BUDGET}, {@code NEW};
     *                       по умолчанию популярность ({@code BR-RANK-4})
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return предложения двумя секциями
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ProposalListResponse list(
            @RequestParam(name = "sort", required = false) String sort,
            Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return proposalService.list(memberId, ProposalSort.parse(sort));
    }

    /**
     * Одно предложение с голосами.
     *
     * @param proposalId     предложение
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return предложение глазами клиента
     */
    @GetMapping(path = "/{proposalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProposalResponse get(@PathVariable UUID proposalId, Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return proposalService.get(memberId, proposalId);
    }

    /**
     * Вносит предложение ({@code BR-OVER-2}, {@code BR-LIFE-1}).
     *
     * @param request        тема, описание и обязательный бюджет
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return {@code 201 Created} с предложением
     */
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProposalResponse> create(
            @Valid @RequestBody CreateProposalRequest request,
            Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(proposalService.create(memberId, request));
    }

    /**
     * Голосует «делаем / не делаем» или меняет голос
     * ({@code BR-VOTE-4}, {@code BR-VOTE-13}).
     *
     * @param proposalId     предложение
     * @param request        «делаем» или «не делаем»
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return предложение после голосования
     */
    @PostMapping(
            path = "/{proposalId}/votes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ProposalResponse vote(@PathVariable UUID proposalId,
                                 @Valid @RequestBody DecisionVoteRequest request,
                                 Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return proposalService.vote(memberId, proposalId, request.inFavor());
    }
}

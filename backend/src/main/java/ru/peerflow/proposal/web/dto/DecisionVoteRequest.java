package ru.peerflow.proposal.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Голос «делаем / не делаем» ({@code BR-VOTE-4}).
 *
 * <p>{@code Boolean}, а не {@code boolean}: отсутствие поля — ошибка клиента,
 * а не молчаливое «против» (как и в голосе по заявке на вступление).
 *
 * @param inFavor {@code true} — «делаем», {@code false} — «не делаем»
 */
public record DecisionVoteRequest(

        @NotNull(message = "укажите, делаем или не делаем")
        Boolean inFavor
) {
}

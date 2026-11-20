package ru.peerflow.community.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Голос члена сообщества по заявке на вступление ({@code BR-VOTE-8}).
 *
 * <p>{@code Boolean}, а не {@code boolean}: отсутствие поля в запросе —
 * это ошибка клиента, а не молчаливое «против».
 *
 * @param accept {@code true} — «за приём», {@code false} — «против»
 */
public record JoinVoteRequest(

        @NotNull(message = "укажите, за или против вы голосуете")
        Boolean accept
) {
}

package ru.peerflow.proposal.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Ответ на вопрос «выполнена ли задача» ({@code BR-VOTE-5}, {@code BR-OVER-9}).
 *
 * <p>{@code Boolean}, а не {@code boolean}: отсутствие поля — ошибка клиента, а
 * не молчаливое «не выполнено». Молчаливый отказ был бы опаснее согласия:
 * задача осталась бы в работе из-за потерянного поля.
 *
 * @param completed {@code true} — «выполнено», {@code false} — «не выполнено»
 */
public record ClosureVoteRequest(

        @NotNull(message = "укажите, выполнена ли задача")
        Boolean completed
) {
}

package ru.peerflow.legitimacy.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Выдвижение себя кандидатом в лидеры ({@code BR-LEADER-1},
 * {@code BR-BUDGET-8}).
 *
 * <p>{@code Long}, а не {@code long}: кандидат без цены не отвечает на вопрос,
 * ради которого его выдвигают, — за сколько он выполнит задачу. Отсутствие
 * поля — ошибка клиента, а не молчаливый ноль.
 *
 * @param price цена: за сколько человек выполнит задачу; больше нуля
 */
public record NominateRequest(

        @NotNull(message = "назовите цену: за сколько вы выполните задачу")
        @Positive(message = "цена должна быть больше нуля")
        Long price
) {
}

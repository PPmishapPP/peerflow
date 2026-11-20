package ru.peerflow.report.web.dto;

import jakarta.validation.constraints.Positive;

/**
 * Трата бюджета задачи ({@code BR-BUDGET-4}).
 *
 * @param amount сколько потрачено; должно быть больше нуля
 * @param note   на что; может отсутствовать
 */
public record SpendRequest(

        @Positive(message = "трата должна быть положительной")
        long amount,

        String note
) {
}

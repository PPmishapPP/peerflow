package ru.peerflow.report.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Трата бюджета задачи ({@code BR-BUDGET-4}).
 *
 * @param id         идентификатор записи
 * @param memberId   кто потратил
 * @param memberName имя
 * @param amount     сколько
 * @param note       на что ушло; может отсутствовать
 * @param createdAt  когда записано
 */
public record BudgetExpenseResponse(
        UUID id,
        UUID memberId,
        String memberName,
        long amount,
        String note,
        Instant createdAt
) {
}

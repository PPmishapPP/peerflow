package ru.peerflow.report.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Отчёт задачи: файлы и траты бюджета ({@code BR-LIFE-7}, {@code BR-BUDGET-4},
 * {@code BR-BUDGET-5}).
 *
 * <p>Отдельного «отчёта» как объекта нет ({@code BR-LIFE-7}): ответ собирает
 * то, что уже есть — приложенные файлы и журнал трат. Текст отчёта живёт
 * обычным сообщением в дискуссии ({@code BR-DISC-1}), поэтому дублировать его
 * здесь не нужно.
 *
 * <p>Остаток бюджета считает сервер: формула «бюджет минус траты» — правило, и
 * повторять её на клиенте значило бы однажды получить разные остатки на двух
 * экранах.
 *
 * @param proposalId задача
 * @param files      приложенные файлы, свежие сверху
 * @param expenses   журнал трат, свежие сверху
 * @param budget     бюджет задачи — цена действующего лидера ({@code BR-LIFE-6}),
 *                   а пока лидера нет — утверждённый потолок ({@code BR-BUDGET-10})
 * @param spent      сколько уже потрачено действующим лидером ({@code BR-BUDGET-7})
 * @param remaining  остаток бюджета задачи; не бывает отрицательным
 */
public record ReportResponse(
        UUID proposalId,
        List<AttachmentResponse> files,
        List<BudgetExpenseResponse> expenses,
        long budget,
        long spent,
        long remaining
) {
}

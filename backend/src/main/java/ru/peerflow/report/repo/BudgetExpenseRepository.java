package ru.peerflow.report.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.legitimacy.domain.LeaderMandate;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.report.domain.BudgetExpense;

/**
 * Журнал трат бюджета задачи ({@code BR-BUDGET-4}).
 *
 * <p>Остаток бюджета считается суммой по задаче: держать отдельный счётчик
 * значило бы согласовывать два источника правды, а сумма всегда объяснима.
 */
public interface BudgetExpenseRepository extends JpaRepository<BudgetExpense, UUID> {

    /**
     * Траты задачи, свежие сверху.
     *
     * @param proposal задача
     * @return записи, возможно пустой список
     */
    @Query("""
            select expense
            from BudgetExpense expense
            join fetch expense.member
            where expense.proposal = :proposal
            order by expense.createdAt desc, expense.id desc
            """)
    List<BudgetExpense> findAllForProposal(Proposal proposal);

    /**
     * Сумма, потраченная в рамках одного мандата ({@code BR-BUDGET-7}).
     *
     * <p>Именно по мандату, а не по задаче: после смены лидера у задачи новая
     * цена и новая попытка, и остаток считается заново. Траты прошлого лидера
     * остаются в журнале как история.
     *
     * @param mandate мандат
     * @return потрачено; {@code 0}, если трат не было
     */
    @Query("""
            select coalesce(sum(expense.amount), 0)
            from BudgetExpense expense
            where expense.mandate = :mandate
            """)
    long totalSpent(LeaderMandate mandate);
}

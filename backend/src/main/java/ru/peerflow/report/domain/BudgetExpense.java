package ru.peerflow.report.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.proxy.HibernateProxy;

import ru.peerflow.community.domain.User;
import ru.peerflow.legitimacy.domain.LeaderMandate;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Запись о трате бюджета задачи ({@code BR-BUDGET-4}).
 *
 * <p>Бюджет в MVP виртуальный ({@code BR-BUDGET-1}), но учёт обязателен:
 * лидер «тратит» бюджет задачи, и эти траты записываются. Журнал, а не
 * счётчик в предложении: сообществу нужно видеть, на что ушли деньги, иначе
 * остаток перестал бы что-либо объяснять.
 *
 * <p>Отчётного формата у трат нет ({@code BR-BUDGET-5}): запись — это сумма и
 * необязательная заметка, а подробности лидер пишет сообщением под задачей,
 * которое и обсуждает сообщество.
 *
 * <p>Проверка «не больше остатка» живёт в сервисе: сумма остальных трат — это
 * данные вне агрегата, и сущность не может их знать.
 */
@Entity
@Table(name = "budget_expense")
public class BudgetExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false, updatable = false)
    private Proposal proposal;

    /**
     * Мандат, в рамках которого сделана трата ({@code BR-BUDGET-7}).
     *
     * <p>Трата принадлежит попытке выполнить задачу, а не задаче целиком.
     * Поэтому новый лидер после смены мандата начинает счёт заново, а траты
     * прошлого остаются в журнале как история: деньги действительно были
     * потрачены, и скрывать их нельзя.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mandate_id", nullable = false, updatable = false)
    private LeaderMandate mandate;

    /** Кто распорядился бюджетом — лидер задачи ({@code BR-LEGIT-4}). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private User member;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    /** Заметка «на что ушло»; необязательна. */
    @Size(max = 500)
    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected BudgetExpense() {
    }

    private BudgetExpense(Proposal proposal, LeaderMandate mandate, User member, long amount,
                          String note, Instant now) {
        this.proposal = proposal;
        this.mandate = mandate;
        this.member = member;
        this.amount = amount;
        this.note = note;
        this.createdAt = now;
    }

    /**
     * Записывает трату бюджета ({@code BR-BUDGET-4}).
     *
     * @param proposal задача
     * @param mandate  мандат, в рамках которого тратят
     * @param member   кто тратит
     * @param amount   сколько; должно быть больше нуля
     * @param note     заметка «на что»; может быть пустой
     * @param now      момент траты
     * @return запись журнала
     * @throws IllegalArgumentException если сумма не положительна
     */
    public static BudgetExpense of(Proposal proposal, LeaderMandate mandate, User member,
                                   long amount, String note, Instant now) {
        Objects.requireNonNull(proposal, "задача обязательна");
        Objects.requireNonNull(mandate, "мандат обязателен");
        Objects.requireNonNull(member, "кто тратит — обязательно");
        Objects.requireNonNull(now, "момент траты обязателен");
        if (amount <= 0) {
            throw new IllegalArgumentException("трата должна быть положительной");
        }
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        return new BudgetExpense(proposal, mandate, member, amount, trimmed, now);
    }

    public UUID getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    /**
     * Мандат, в рамках которого сделана трата ({@code BR-BUDGET-7}).
     *
     * @return мандат лидера
     */
    public LeaderMandate getMandate() {
        return mandate;
    }

    public User getMember() {
        return member;
    }

    public long getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> objectEffectiveClass = o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisEffectiveClass != objectEffectiveClass) {
            return false;
        }
        BudgetExpense other = (BudgetExpense) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "BudgetExpense{id=" + id + ", amount=" + amount + "}";
    }
}

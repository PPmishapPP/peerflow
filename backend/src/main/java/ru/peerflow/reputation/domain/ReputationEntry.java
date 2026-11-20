package ru.peerflow.reputation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.proxy.HibernateProxy;

import ru.peerflow.community.domain.User;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Запись о начислении репутации за закрытую задачу ({@code BR-REP-1},
 * {@code BR-REP-3}).
 *
 * <p>Репутация — журнал, а не счётчик: её назначение — витрина достижений
 * ({@code BR-REP-3}), и человек вправе видеть, за какую задачу он получил
 * очки. Счётчик пришлось бы пересчитывать, и объяснить число было бы нечем.
 *
 * <p>Единственное рабочее применение — тай-брейкер при выборе лидера
 * ({@code BR-REP-4}). Поэтому значение хранится целым числом: дробная
 * репутация не давала бы детерминированного сравнения.
 *
 * <p>Запись неизменяема: отозвать репутацию нельзя, иначе витрина достижений
 * стала бы предметом торга.
 */
@Entity
@Table(name = "reputation_ledger")
public class ReputationEntry {

    /** За что начислена репутация. */
    public enum Reason {

        /** Задача закрыта сообществом ({@code BR-REP-1}). */
        TASK_CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private User member;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false, updatable = false)
    private Proposal proposal;

    @Column(name = "points", nullable = false, updatable = false)
    private long points;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 32)
    private Reason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ReputationEntry() {
    }

    private ReputationEntry(User member, Proposal proposal, long points,
                            Reason reason, Instant now) {
        this.member = member;
        this.proposal = proposal;
        this.points = points;
        this.reason = reason;
        this.createdAt = now;
    }

    /**
     * Начисляет репутацию за закрытую задачу ({@code BR-REP-1}).
     *
     * <p>Начисление только положительное: репутация — достижение, а не штрафная
     * шкала.
     *
     * @param member   кому начисляют
     * @param proposal за какую задачу
     * @param points   сколько очков; должно быть больше нуля
     * @param now      момент начисления
     * @return запись журнала
     * @throws IllegalArgumentException если очков не положительно
     */
    public static ReputationEntry forClosedTask(User member, Proposal proposal,
                                                long points, Instant now) {
        Objects.requireNonNull(member, "член сообщества обязателен");
        Objects.requireNonNull(proposal, "задача обязательна");
        Objects.requireNonNull(now, "момент начисления обязателен");
        if (points <= 0) {
            throw new IllegalArgumentException("репутация начисляется только положительная");
        }
        return new ReputationEntry(member, proposal, points, Reason.TASK_CLOSED, now);
    }

    public UUID getId() {
        return id;
    }

    public User getMember() {
        return member;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public long getPoints() {
        return points;
    }

    public Reason getReason() {
        return reason;
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
        ReputationEntry other = (ReputationEntry) o;
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
        return "ReputationEntry{id=" + id + ", points=" + points + ", reason=" + reason + "}";
    }
}

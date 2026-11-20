package ru.peerflow.legitimacy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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
 * Исполнительный мандат лидера по задаче ({@code BR-LEGIT-4},
 * {@code BR-LEADER-5}, {@code BR-LIFE-6}).
 *
 * <p>Мандат, а не поле «лидер» в предложении: лидеров у задачи может быть
 * несколько подряд, и каждый раз важно, с какой поддержкой он начал и почему
 * закончил. Без снимка поддержки на момент выборов нельзя ответить на вопрос
 * «потерял ли он больше 30%» — сравнивать было бы не с чем
 * ({@code BR-LEADER-5}).
 *
 * <p>Порог удержания сравнивается через {@link BigDecimal}: {@code 0.7 * 10}
 * на {@code double} даёт {@code 6.999999999999999}, и «ровно 70%» превратились
 * бы в потерю мандата. Это ровно та ошибка, которую бизнес-правило запрещает.
 */
@Entity
@Table(name = "leader_mandate")
public class LeaderMandate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false, updatable = false)
    private Proposal proposal;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_id", nullable = false, updatable = false)
    private User leader;

    /**
     * Сколько легитимности лидер получил на выборах
     * ({@code BR-LEADER-3}, {@code BR-LEADER-5}).
     */
    @Column(name = "initial_support", nullable = false, updatable = false)
    private long initialSupport;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    /** Когда мандат закончился; {@code null} — мандат действует. */
    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 32)
    private MandateEndReason endReason;

    protected LeaderMandate() {
    }

    private LeaderMandate(Proposal proposal, User leader, long initialSupport, Instant now) {
        this.proposal = proposal;
        this.leader = leader;
        this.initialSupport = initialSupport;
        this.startedAt = now;
    }

    /**
     * Выдаёт мандат победителю выборов ({@code BR-LEGIT-4}).
     *
     * @param proposal       задача
     * @param leader         кандидат с наибольшей поддержкой
     * @param initialSupport сколько легитимности он собрал
     * @param now            момент выборов
     * @return действующий мандат
     * @throws IllegalArgumentException если поддержка отрицательна
     */
    public static LeaderMandate grant(Proposal proposal, User leader,
                                      long initialSupport, Instant now) {
        Objects.requireNonNull(proposal, "задача обязательна");
        Objects.requireNonNull(leader, "лидер обязателен");
        Objects.requireNonNull(now, "момент выборов обязателен");
        if (initialSupport < 0) {
            throw new IllegalArgumentException("поддержка не может быть отрицательной");
        }
        return new LeaderMandate(proposal, leader, initialSupport, now);
    }

    /**
     * Действует ли мандат.
     *
     * @return {@code true}, пока мандат не завершён
     */
    public boolean isActive() {
        return endedAt == null;
    }

    /**
     * Держится ли мандат при текущей поддержке ({@code BR-LEADER-5},
     * {@code BR-LIMIT-1}).
     *
     * <p>Условие — «осталось не меньше {@code retainRatio} от изначальной
     * поддержки». Ровно 70% мандат сохраняют: правило говорит «потерял больше
     * 30%», а не «потерял 30%».
     *
     * @param currentSupport сколько легитимности у лидера сейчас
     * @param retainRatio    доля удержания, например {@code 0.70}
     * @return {@code true}, если мандат ещё действителен
     */
    public boolean isRetainedBy(long currentSupport, double retainRatio) {
        return threshold(retainRatio).isRetainedBy(currentSupport);
    }

    /**
     * Сколько легитимности нужно сохранить, чтобы мандат удержался.
     *
     * @param retainRatio доля удержания
     * @return минимальная поддержка
     */
    public long requiredSupport(double retainRatio) {
        return threshold(retainRatio).requiredSupport();
    }

    private MandateThreshold threshold(double retainRatio) {
        return new MandateThreshold(initialSupport, retainRatio);
    }

    /**
     * Завершает мандат ({@code BR-LEADER-5}, {@code BR-LIFE-9}).
     *
     * @param reason почему мандат закончился
     * @param now    момент завершения
     * @throws IllegalStateException если мандат уже завершён
     */
    public void end(MandateEndReason reason, Instant now) {
        Objects.requireNonNull(reason, "причина завершения обязательна");
        Objects.requireNonNull(now, "момент завершения обязателен");
        if (!isActive()) {
            throw new IllegalStateException("мандат уже завершён: " + endReason);
        }
        this.endedAt = now;
        this.endReason = reason;
    }

    /**
     * Причина завершения мандата.
     *
     * @return причина; {@code null} — мандат ещё действует
     */
    public Optional<MandateEndReason> endedReason() {
        return Optional.ofNullable(endReason);
    }

    public UUID getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public User getLeader() {
        return leader;
    }

    public long getInitialSupport() {
        return initialSupport;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public MandateEndReason getEndReason() {
        return endReason;
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
        LeaderMandate other = (LeaderMandate) o;
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
        return "LeaderMandate{id=" + id + ", initialSupport=" + initialSupport
                + ", endReason=" + endReason + "}";
    }
}

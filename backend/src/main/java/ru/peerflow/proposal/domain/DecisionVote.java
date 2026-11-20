package ru.peerflow.proposal.domain;

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

import org.hibernate.proxy.HibernateProxy;

import ru.peerflow.community.domain.User;

/**
 * Голос члена сообщества «делаем / не делаем» ({@code BR-VOTE-4}).
 *
 * <p>Право голоса есть у каждого действующего члена, оно фиксированное — один
 * человек, один голос ({@code BR-VOTE-1}, {@code BR-VOTE-2}). Поэтому пара
 * «предложение + голосующий» уникальна в базе, а не только проверяется в коде.
 *
 * <p>В отличие от голоса по заявке на вступление, этот голос можно поменять,
 * пока голосование не закрыто ({@code BR-VOTE-13}): человек вправе передумать
 * после обсуждения. Меняется направление, а не добавляется вторая строка —
 * иначе «один человек — один голос» ломалось бы.
 */
@Entity
@Table(name = "proposal_decision_vote")
public class DecisionVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_id", nullable = false)
    private User voter;

    /** {@code true} — «делаем», {@code false} — «не делаем». */
    @Column(name = "in_favor", nullable = false)
    private boolean inFavor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Когда голос последний раз меняли ({@code BR-VOTE-13}). */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DecisionVote() {
    }

    DecisionVote(Proposal proposal, User voter, boolean inFavor) {
        this.proposal = Objects.requireNonNull(proposal, "предложение обязательно");
        this.voter = Objects.requireNonNull(voter, "голосующий обязателен");
        this.inFavor = inFavor;
    }

    /**
     * Меняет направление голоса ({@code BR-VOTE-13}).
     *
     * <p>Проверку «голосование ещё открыто» делает {@link Proposal}: голос не
     * должен уметь менять себя в обход предложения.
     *
     * @param inFavor {@code true} — «делаем», {@code false} — «не делаем»
     */
    void changeTo(boolean inFavor) {
        if (this.inFavor != inFavor) {
            this.inFavor = inFavor;
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Проверяет, что голос подан этим человеком.
     *
     * @param candidate проверяемый аккаунт
     * @return {@code true}, если голос принадлежит ему
     */
    public boolean isCastBy(User candidate) {
        return voter.equals(candidate);
    }

    public UUID getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public User getVoter() {
        return voter;
    }

    public boolean isInFavor() {
        return inFavor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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
        DecisionVote other = (DecisionVote) o;
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
        return "DecisionVote{id=" + id + ", inFavor=" + inFavor + "}";
    }
}

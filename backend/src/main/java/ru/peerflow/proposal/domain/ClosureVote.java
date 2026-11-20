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
 * Голос члена сообщества «выполнено / не выполнено» ({@code BR-VOTE-5},
 * {@code BR-OVER-9}).
 *
 * <p>Отдельный тип от {@link DecisionVote}, потому что вопрос другой: там
 * «делать ли», здесь «сделано ли». Один и тот же человек вправе сказать «за»
 * до работы и «не выполнено» после — это не противоречие, а разные решения.
 *
 * <p>Голос можно поменять, пока голосование не закрыто: человек может
 * посмотреть новый отчёт лидера и передумать. Один человек — один голос
 * ({@code BR-VOTE-2}), поэтому менять можно только направление.
 */
@Entity
@Table(name = "closure_vote")
public class ClosureVote {

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

    /** {@code true} — «выполнено», {@code false} — «не выполнено». */
    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ClosureVote() {
    }

    ClosureVote(Proposal proposal, User voter, boolean completed) {
        this.proposal = Objects.requireNonNull(proposal, "задача обязательна");
        this.voter = Objects.requireNonNull(voter, "голосующий обязателен");
        this.completed = completed;
    }

    /**
     * Меняет ответ, пока голосование не закрыто.
     *
     * <p>Проверку «голосование ещё идёт» делает {@link Proposal}: голос не
     * должен уметь менять себя в обход задачи.
     *
     * @param completed {@code true} — «выполнено»
     */
    void changeTo(boolean completed) {
        if (this.completed != completed) {
            this.completed = completed;
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

    public boolean isCompleted() {
        return completed;
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
        ClosureVote other = (ClosureVote) o;
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
        return "ClosureVote{id=" + id + ", completed=" + completed + "}";
    }
}

package ru.peerflow.community.domain;

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

/**
 * Голос члена сообщества по заявке на вступление ({@code BR-VOTE-8}).
 *
 * <p>Право голоса есть у каждого действующего члена ({@code BR-VOTE-1}), оно
 * фиксированное — один человек, один голос ({@code BR-VOTE-2}). Поэтому пара
 * «заявка + член» уникальна в базе, а не только проверяется в коде.
 *
 * <p>Голос неизменяем: переголосовать нельзя — иначе итог голосования можно
 * было бы переписывать и «догонять» порог в последний момент.
 */
@Entity
@Table(name = "join_vote")
public class JoinVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "join_request_id", nullable = false)
    private JoinRequest joinRequest;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    /** {@code true} — «за приём», {@code false} — «против». */
    @Column(name = "is_accept", nullable = false)
    private boolean accept;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected JoinVote() {
    }

    JoinVote(JoinRequest joinRequest, User member, boolean accept) {
        this.joinRequest = Objects.requireNonNull(joinRequest, "заявка обязательна");
        this.member = Objects.requireNonNull(member, "голосующий обязателен");
        this.accept = accept;
    }

    /**
     * Проверяет, что голос подан этим членом сообщества.
     *
     * @param candidate проверяемый аккаунт
     * @return {@code true}, если голос принадлежит ему
     */
    public boolean isCastBy(User candidate) {
        return member.equals(candidate);
    }

    public UUID getId() {
        return id;
    }

    public JoinRequest getJoinRequest() {
        return joinRequest;
    }

    public User getMember() {
        return member;
    }

    public boolean isAccept() {
        return accept;
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
        JoinVote other = (JoinVote) o;
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
        return "JoinVote{id=" + id + ", accept=" + accept + "}";
    }
}

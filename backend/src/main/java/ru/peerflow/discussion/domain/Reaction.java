package ru.peerflow.discussion.domain;

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

/**
 * Реакция члена сообщества на сообщение ({@code BR-DISC-2}, {@code BR-VOTE-6}).
 *
 * <p>Право реакции есть у каждого действующего члена и оно фиксированное —
 * один человек, одна реакция ({@code BR-VOTE-2}). Поэтому пара «сообщение +
 * член» уникальна в базе, а не только проверяется в коде: иначе быстрые
 * повторные нажатия на клиенте создали бы дубли и раздули счётчики.
 *
 * <p>В отличие от голоса по решению, реакцию можно поменять: человек вправе
 * передумать после ответа оппонента. Меняется вид реакции, а не добавляется
 * вторая строка.
 */
@Entity
@Table(name = "message_reaction")
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 8)
    private ReactionKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Когда реакцию последний раз меняли. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Reaction() {
    }

    Reaction(Message message, User member, ReactionKind kind) {
        this.message = Objects.requireNonNull(message, "сообщение обязательно");
        this.member = Objects.requireNonNull(member, "реагирующий обязателен");
        this.kind = Objects.requireNonNull(kind, "вид реакции обязателен");
    }

    /**
     * Меняет вид реакции.
     *
     * <p>Проверку «это член сообщества предложения» делает {@link Message}:
     * реакция не должна уметь менять себя в обход сообщения.
     *
     * @param kind новый вид реакции
     */
    void changeTo(ReactionKind kind) {
        Objects.requireNonNull(kind, "вид реакции обязателен");
        if (this.kind != kind) {
            this.kind = kind;
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Проверяет, что реакцию поставил этот человек.
     *
     * @param candidate проверяемый аккаунт
     * @return {@code true}, если реакция его
     */
    public boolean isMadeBy(User candidate) {
        return member.equals(candidate);
    }

    public UUID getId() {
        return id;
    }

    public Message getMessage() {
        return message;
    }

    public User getMember() {
        return member;
    }

    public ReactionKind getKind() {
        return kind;
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
        Reaction other = (Reaction) o;
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
        return "Reaction{id=" + id + ", kind=" + kind + "}";
    }
}

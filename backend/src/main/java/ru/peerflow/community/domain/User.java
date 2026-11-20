package ru.peerflow.community.domain;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;

/**
 * Аккаунт человека в системе.
 *
 * <p>Вход — по логину и паролю ({@code BR-JOIN-1}), поэтому логин уникален,
 * а пароль хранится только в виде хэша. Первый зарегистрировавшийся сразу
 * становится членом сообщества ({@code BR-JOIN-3}), следующих принимает
 * сообщество ({@code BR-JOIN-4}, {@code BR-JOIN-5}) — до этого аккаунт
 * существует без сообщества ({@link #getCommunity()} пуст).
 *
 * <p>Удаление — только пометкой статуса: строка остаётся в базе, чтобы не
 * рвать связи с историей сообщества ({@code BR-PRIV-2}).
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 64)
    @Column(name = "login", nullable = false, length = 64)
    private String login;

    @NotBlank
    @Size(max = 100)
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @NotBlank
    @Size(max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "about", columnDefinition = "text")
    private String about;

    @Size(max = 255)
    @Column(name = "avatar_path", length = 255)
    private String avatarPath;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    /** Момент вступления в сообщество; {@code null} — ещё не принят. */
    @Column(name = "joined_at")
    private Instant joinedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id")
    private Community community;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    private User(String login, String passwordHash, String displayName) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    /**
     * Создаёт аккаунт. Он ещё не сохранён и не состоит в сообществе.
     *
     * @param login        уникальный логин для входа
     * @param passwordHash хэш пароля (не пароль в открытом виде)
     * @param displayName  отображаемое имя
     * @return новый аккаунт
     */
    public static User register(String login, String passwordHash, String displayName) {
        return new User(login, passwordHash, displayName);
    }

    /**
     * Делает аккаунт членом сообщества ({@code BR-JOIN-3}, {@code BR-JOIN-4}).
     *
     * <p>Человек не может состоять в нескольких сообществах ({@code BR-JOIN-2}),
     * поэтому повторное вступление отклоняется.
     *
     * @param community сообщество, в которое принимают
     * @throws IllegalStateException если аккаунт уже состоит в сообществе
     */
    public void join(Community community) {
        if (this.community != null) {
            throw new IllegalStateException(
                    "аккаунт уже состоит в сообществе: " + this.community.getId());
        }
        this.community = Objects.requireNonNull(community, "сообщество обязательно");
        this.joinedAt = Instant.now();
    }

    /**
     * Проверяет, что аккаунт — действующий член сообщества: его приняли и он
     * не удалён. Именно это условие даёт право голоса ({@code BR-VOTE-1}) и
     * доступ к контенту сообщества ({@code BR-PRIV-1}).
     *
     * @return {@code true}, если аккаунт принят в сообщество и активен
     */
    public boolean isActiveMember() {
        return status == UserStatus.ACTIVE && community != null;
    }

    /**
     * Помечает аккаунт удалённым. Физического удаления нет ({@code BR-PRIV-2}).
     */
    public void markDeleted() {
        this.status = UserStatus.DELETED;
    }

    public UUID getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Community getCommunity() {
        return community;
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
        User other = (User) o;
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
        return "User{id=" + id + ", login='" + login + "', status=" + status + "}";
    }
}

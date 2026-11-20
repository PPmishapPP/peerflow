package ru.peerflow.community.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;

/**
 * Сообщество «Маяк».
 *
 * <p>Один сервер обслуживает одно сообщество, и человек не может состоять
 * в нескольких ({@code BR-JOIN-2}), поэтому в MVP эта таблица содержит ровно
 * одну строку. Отдельная сущность всё равно нужна: принадлежность аккаунта и
 * заявки на вступление ссылаются на сообщество, а не на «единственность
 * сервера» — так модель не придётся ломать, если сообществ станет больше.
 */
@Entity
@Table(name = "community")
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Community() {
    }

    private Community(String name) {
        this.name = name;
    }

    /**
     * Создаёт сообщество с заданным названием.
     *
     * @param name название сообщества
     * @return новое (ещё не сохранённое) сообщество
     */
    public static Community create(String name) {
        return new Community(name);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
        Community other = (Community) o;
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
        return "Community{id=" + id + ", name='" + name + "'}";
    }
}

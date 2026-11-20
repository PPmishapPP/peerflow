package ru.peerflow.community.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.domain.UserStatus;

/**
 * Доступ к аккаунтам.
 *
 * <p>Логин уникален ({@link #findByLogin(String)}), а подсчёт членов сообщества
 * нужен порогу приёма: «за» должно быть не меньше 30% принятых членов
 * ({@code BR-JOIN-5}, {@code BR-LIMIT-3}).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Ищет аккаунт по логину — основа входа ({@code BR-JOIN-1}).
     *
     * @param login логин
     * @return аккаунт, если такой логин зарегистрирован
     */
    Optional<User> findByLogin(String login);

    /**
     * Проверяет, занят ли логин.
     *
     * @param login логин
     * @return {@code true}, если логин уже зарегистрирован
     */
    boolean existsByLogin(String login);

    /**
     * Считает действующих членов сообщества.
     *
     * <p>Удалённые аккаунты не считаются ({@code BR-PRIV-2}) — они больше не
     * голосуют, поэтому не должны раздувать базу для порога приёма.
     *
     * @param community сообщество
     * @return количество активных членов
     */
    long countByCommunityAndStatus(Community community, UserStatus status);
}

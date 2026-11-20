package ru.peerflow.auth.service;

import java.util.UUID;

/**
 * Аккаунт из токена не найден в базе.
 *
 * <p>Токен может быть валидным по подписи, но аккаунт уже помечен удалённым
 * ({@code BR-PRIV-2}). Такой запрос не должен проходить: доступ к контенту
 * сообщества требует действующего членства ({@code BR-PRIV-1}).
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID userId) {
        super("аккаунт не найден: " + userId);
    }
}

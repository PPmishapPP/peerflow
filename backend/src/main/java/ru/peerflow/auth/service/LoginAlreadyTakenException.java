package ru.peerflow.auth.service;

/**
 * Логин уже занят другим аккаунтом ({@code BR-JOIN-1}).
 *
 * <p>Вход по логину требует его уникальности, поэтому повторная регистрация
 * того же логина невозможна. Наружу это уходит как {@code 409 Conflict}:
 * клиент может подсказать человеку выбрать другой логин.
 */
public class LoginAlreadyTakenException extends RuntimeException {

    public LoginAlreadyTakenException(String login) {
        super("логин уже занят: " + login);
    }
}

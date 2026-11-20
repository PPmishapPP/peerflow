package ru.peerflow.auth.service;

/**
 * Логин или пароль не подошли ({@code BR-JOIN-1}).
 *
 * <p>Сообщение намеренно не уточняет, что именно неверно: иначе по ответам
 * сервера можно было бы перебрать существующие логины.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("неверный логин или пароль");
    }
}

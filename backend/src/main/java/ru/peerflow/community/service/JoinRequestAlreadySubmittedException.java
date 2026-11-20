package ru.peerflow.community.service;

/**
 * Заявка на вступление уже подана и рассматривается.
 *
 * <p>Второй раз подавать незачем: одна заявка в работе на человека
 * ({@code BR-JOIN-4}). Повторная подача — не ошибка ввода, а конфликт
 * состояния, поэтому {@code 409}.
 */
public class JoinRequestAlreadySubmittedException extends RuntimeException {

    public JoinRequestAlreadySubmittedException() {
        super("заявка уже подана и рассматривается");
    }
}

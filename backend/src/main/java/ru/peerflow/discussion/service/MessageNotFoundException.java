package ru.peerflow.discussion.service;

import java.util.UUID;

/**
 * Сообщение не найдено либо лежит в другом сообществе ({@code BR-PRIV-1}).
 *
 * <p>Один тип на оба случая осознанно: по разнице кодов клиент не должен
 * узнавать о существовании сообщений в чужом сообществе.
 */
public class MessageNotFoundException extends RuntimeException {

    /**
     * Создаёт исключение для ненайденного сообщения.
     *
     * @param id идентификатор, по которому искали
     */
    public MessageNotFoundException(UUID id) {
        super("сообщение не найдено: " + id);
    }
}

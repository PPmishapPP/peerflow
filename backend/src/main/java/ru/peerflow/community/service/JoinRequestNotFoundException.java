package ru.peerflow.community.service;

import java.util.UUID;

/**
 * Заявка на вступление не найдена.
 *
 * <p>Член сообщества мог открыть список, а заявку уже рассмотрели в другом
 * окне. Это не ошибка данных, а устаревший экран — клиенту достаточно
 * обновить список.
 */
public class JoinRequestNotFoundException extends RuntimeException {

    public JoinRequestNotFoundException(UUID requestId) {
        super("заявка на вступление не найдена: " + requestId);
    }
}

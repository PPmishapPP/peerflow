package ru.peerflow.report.service;

import java.util.UUID;

/**
 * Файл отчёта не найден либо принадлежит другому сообществу
 * ({@code BR-PRIV-1}).
 *
 * <p>Один тип на оба случая осознанно — как и у предложений: по разнице кодов
 * нельзя узнать, что за файлы есть в чужом сообществе.
 */
public class AttachmentNotFoundException extends RuntimeException {

    /**
     * Создаёт исключение для ненайденного вложения.
     *
     * @param id идентификатор, по которому искали
     */
    public AttachmentNotFoundException(UUID id) {
        super("вложение не найдено: " + id);
    }
}

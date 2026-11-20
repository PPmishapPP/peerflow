package ru.peerflow.proposal.service;

import java.util.UUID;

/**
 * Предложение не найдено либо лежит в другом сообществе ({@code BR-PRIV-1}).
 *
 * <p>Один тип на оба случая осознанно: клиент не должен узнавать о
 * существовании чужих предложений по разнице между {@code 403} и {@code 404}.
 */
public class ProposalNotFoundException extends RuntimeException {

    /**
     * Создаёт исключение для ненайденного предложения.
     *
     * @param id идентификатор, по которому искали
     */
    public ProposalNotFoundException(UUID id) {
        super("предложение не найдено: " + id);
    }
}

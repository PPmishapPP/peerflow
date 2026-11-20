package ru.peerflow.legitimacy.service;

import java.util.UUID;

/**
 * Кандидат не найден среди членов сообщества ({@code BR-PRIV-1}).
 *
 * <p>Отдельно от {@link ru.peerflow.auth.service.AccountNotFoundException}:
 * тот означает «твоего аккаунта больше нет, войди заново» ({@code 401}), а
 * здесь проблема не в том, кто спрашивает, а в том, кого он назвал. Поэтому
 * {@code 404} — и чужой аккаунт неотличим от несуществующего.
 */
public class CandidateNotFoundException extends RuntimeException {

    /**
     * Создаёт исключение для кандидата, которого нет в сообществе.
     *
     * @param id идентификатор, по которому искали
     */
    public CandidateNotFoundException(UUID id) {
        super("кандидат не найден: " + id);
    }
}

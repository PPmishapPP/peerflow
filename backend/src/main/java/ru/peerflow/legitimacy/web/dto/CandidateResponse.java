package ru.peerflow.legitimacy.web.dto;

import java.util.UUID;

/**
 * Кандидат глазами клиента ({@code BR-LEADER-1}–{@code BR-LEADER-4}).
 *
 * <p>Показатели приходят посчитанными: порядок кандидатов и тай-брейкеры —
 * правило сообщества, и клиент не должен воспроизводить его сам
 * ({@code BR-LEADER-4}).
 *
 * @param memberId    аккаунт кандидата
 * @param displayName имя для человека
 * @param support     сколько легитимности передано ему сейчас
 * @param reputation  репутация — второй тай-брейкер ({@code BR-REP-4})
 * @param me          кандидат ли это текущий пользователь
 */
public record CandidateResponse(
        UUID memberId,
        String displayName,
        long support,
        long reputation,
        boolean me
) {
}

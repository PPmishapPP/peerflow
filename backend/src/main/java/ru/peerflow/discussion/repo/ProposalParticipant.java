package ru.peerflow.discussion.repo;

import java.util.UUID;

/**
 * Кто участвовал в обсуждении предложения ({@code BR-RANK-2}).
 *
 * <p>Пары «предложение + автор сообщения» нужны, чтобы посчитать уникальных
 * участников для всего списка одним запросом. Считать то же самое, обходя
 * сообщения каждого предложения, значило бы получить N+1 — ровно ту проблему,
 * от которой избавлялись в запросе предложений.
 *
 * @param proposalId предложение
 * @param authorId   автор сообщения
 */
public record ProposalParticipant(UUID proposalId, UUID authorId) {
}

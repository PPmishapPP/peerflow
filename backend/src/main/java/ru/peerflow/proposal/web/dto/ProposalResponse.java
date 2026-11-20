package ru.peerflow.proposal.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.peerflow.proposal.domain.ProposalStage;

/**
 * Предложение в том виде, в каком его видит клиент ({@code BR-RANK-2},
 * {@code BR-RANK-4}, {@code BR-VOTE-4}).
 *
 * <p>Почему отдаём посчитанные значения, а не сырые данные:
 * <ul>
 *   <li>{@code popularity} — метрика сортировки; если бы клиент считал её сам,
 *       правила разъехались бы между платформами ({@code BR-RANK-2});</li>
 *   <li>{@code votingDeadline} и {@code votingClosed} — срок голосования
 *       ({@code BR-VOTE-9}); клиенту нужен дедлайн, чтобы показать таймер, а
 *       не выдумывать его из даты внесения;</li>
 *   <li>{@code myVote} — выбор человека, чтобы кнопки были в правильном
 *       состоянии.</li>
 * </ul>
 *
 * @param id             идентификатор
 * @param title          тема
 * @param description    описание
 * @param authorId       автор
 * @param authorName     имя автора
 * @param budget         бюджет в рублях ({@code BR-BUDGET-2})
 * @param stage          стадия предложения
 * @param votesFor       сколько голосов «делаем»
 * @param votesAgainst   сколько голосов «не делаем»
 * @param participants   число уникальных участников ({@code BR-RANK-2})
 * @param popularity     метрика популярности ({@code BR-RANK-2})
 * @param createdAt      когда внесено
 * @param decidedAt      когда принято решение; {@code null}, пока идёт обсуждение
 * @param votingDeadline когда голосование закроется по сроку ({@code BR-VOTE-9})
 * @param votingClosed   закрыто ли голосование на момент ответа
 * @param votingStatus   почему голосование закрыто или что ждём — текст для человека
 * @param myVote         голос текущего пользователя; {@code null} — не голосовал
 */
public record ProposalResponse(
        UUID id,
        String title,
        String description,
        UUID authorId,
        String authorName,
        long budget,
        ProposalStage stage,
        long votesFor,
        long votesAgainst,
        long participants,
        long popularity,
        Instant createdAt,
        Instant decidedAt,
        Instant votingDeadline,
        boolean votingClosed,
        String votingStatus,
        MyVote myVote
) {
}

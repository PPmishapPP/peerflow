package ru.peerflow.discussion.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ru.peerflow.discussion.domain.ReactionKind;

/**
 * Сообщение дискуссии в том виде, в каком его видит клиент
 * ({@code BR-DISC-1}–{@code BR-DISC-3}, {@code BR-SPAM-1}).
 *
 * <p>Почему ответы вложены, а не приходят плоским списком с {@code parentId}:
 * порядок и вложенность внутри ветки — правило сообщества
 * ({@code BR-DISC-6}), и клиент не должен восстанавливать дерево сам. Иначе
 * правило ранжирования пришлось бы повторять на каждой платформе.
 *
 * <p>Счётчики реакций считает сервер: «плюсы поднимают, минусы опускают» — одна
 * формула на всех ({@code BR-DISC-4}).
 *
 * @param id          идентификатор сообщения
 * @param authorId    автор
 * @param authorName  имя автора
 * @param body        текст аргумента
 * @param createdAt   когда написано
 * @param plus        сколько меток «плюс»
 * @param minus       сколько меток «минус»
 * @param spam        сколько меток «спам»
 * @param myReaction  реакция текущего пользователя; {@code null} — не реагировал
 * @param replies     ответы в ветке, в порядке, заданном сервером
 */
public record MessageResponse(
        UUID id,
        UUID authorId,
        String authorName,
        String body,
        Instant createdAt,
        long plus,
        long minus,
        long spam,
        ReactionKind myReaction,
        List<MessageResponse> replies
) {
}

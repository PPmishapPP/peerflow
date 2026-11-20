package ru.peerflow.discussion.web.dto;

import java.util.List;

/**
 * Ветка обсуждения целиком ({@code BR-DISC-1}–{@code BR-DISC-6},
 * {@code BR-SPAM-1}).
 *
 * <p>Почему не просто список сообщений: клиенту нужно знать, что часть
 * обсуждения скрыта метками «спам». Иначе человек видел бы пустоту там, где
 * кто-то писал, и не понимал бы, куда делось сообщение. Скрытие — не удаление
 * ({@code BR-SPAM-1}), и об этом честно сообщается числом.
 *
 * @param messages показанные корневые сообщения с ответами
 * @param hidden   сколько сообщений предложения скрыто метками «спам»
 */
public record ThreadResponse(
        List<MessageResponse> messages,
        long hidden
) {
}

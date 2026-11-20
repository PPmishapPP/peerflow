package ru.peerflow.discussion.web.dto;

import jakarta.validation.constraints.NotNull;

import ru.peerflow.discussion.domain.ReactionKind;

/**
 * Реакция на сообщение ({@code BR-DISC-2}).
 *
 * <p>{@code @NotNull}: отсутствие реакции — ошибка клиента, а не молчаливый
 * выбор «плюс». Иначе опечатка в теле запроса поднимала бы сообщение вместо
 * того, чтобы быть замеченной.
 *
 * @param kind {@code PLUS}, {@code MINUS} или {@code SPAM}
 */
public record ReactionRequest(

        @NotNull(message = "реакция обязательна")
        ReactionKind kind
) {
}

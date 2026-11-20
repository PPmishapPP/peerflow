package ru.peerflow.discussion.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Новое сообщение дискуссии ({@code BR-DISC-1}).
 *
 * <p>{@code parentId} необязателен: без него получается корневое сообщение,
 * с ним — ответ в ветку ({@code BR-DISC-3}).
 *
 * @param body     текст аргумента
 * @param parentId сообщение, на которое отвечают; {@code null} — не ответ
 */
public record PostMessageRequest(

        @NotBlank(message = "сообщение не может быть пустым")
        @Size(max = 5000, message = "сообщение не длиннее 5000 символов")
        String body,

        String parentId
) {
}

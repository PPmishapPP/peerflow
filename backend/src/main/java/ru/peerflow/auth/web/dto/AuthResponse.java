package ru.peerflow.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Ответ на успешную регистрацию или вход ({@code BR-JOIN-1}).
 *
 * <p>Клиенту нужно немного: токен для дальнейших запросов, срок его жизни и
 * кто вошёл. Пароль и хэш не возвращаются никогда.
 *
 * @param token       значение для заголовка {@code Authorization: Bearer …}
 * @param expiresAt   момент, после которого токен недействителен
 * @param userId      идентификатор аккаунта
 * @param login       логин
 * @param displayName отображаемое имя
 * @param member      является ли аккаунт действующим членом сообщества
 *                    ({@code BR-JOIN-3}, {@code BR-JOIN-4})
 * @param communityId сообщество аккаунта; {@code null}, пока его не приняли
 */
public record AuthResponse(
        String token,
        Instant expiresAt,
        UUID userId,
        String login,
        String displayName,
        boolean member,
        UUID communityId
) {
}

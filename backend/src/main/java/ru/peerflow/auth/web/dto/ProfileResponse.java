package ru.peerflow.auth.web.dto;

import java.util.UUID;

/**
 * Профиль текущего пользователя.
 *
 * <p>Возвращается по токену: клиенту нужно знать, принят ли он в сообщество,
 * чтобы показать либо контент, либо экран ожидания ({@code BR-JOIN-4},
 * {@code BR-PRIV-1}). Пароль и его хэш не отдаются никогда.
 *
 * @param id          идентификатор аккаунта
 * @param login       логин
 * @param displayName отображаемое имя
 * @param about       рассказ о себе
 * @param avatarPath  путь к аватару внутри каталога вложений; может быть {@code null}
 * @param member      является ли аккаунт действующим членом сообщества
 * @param communityId сообщество аккаунта; {@code null}, пока не принят
 */
public record ProfileResponse(
        UUID id,
        String login,
        String displayName,
        String about,
        String avatarPath,
        boolean member,
        UUID communityId
) {
}

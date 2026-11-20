package ru.peerflow.community.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.peerflow.community.domain.JoinRequestStatus;

/**
 * Заявка на вступление в том виде, в каком её видит клиент.
 *
 * <p>Кроме содержимого заявки отдаём прогресс голосования: сколько уже «за»,
 * сколько всего голосов подано и какой порог нужно набрать. Без этих чисел
 * экран голосования не смог бы объяснить, что происходит ({@code BR-JOIN-5},
 * {@code BR-LIMIT-3}).
 *
 * @param id             идентификатор заявки
 * @param applicantId    идентификатор заявителя
 * @param applicantName  отображаемое имя заявителя
 * @param about          «о себе» из заявки
 * @param avatarPath     путь к фото из заявки
 * @param status         состояние заявки
 * @param createdAt      когда подана
 * @param decidedAt      когда принято решение; {@code null}, пока рассматривается
 * @param accepts        сколько голосов «за приём»
 * @param votes          сколько всего голосов подано
 * @param required       сколько голосов «за» нужно для приёма
 * @param votedByMe      голосовал ли уже текущий пользователь
 */
public record JoinRequestResponse(
        UUID id,
        UUID applicantId,
        String applicantName,
        String about,
        String avatarPath,
        JoinRequestStatus status,
        Instant createdAt,
        Instant decidedAt,
        long accepts,
        long votes,
        long required,
        boolean votedByMe
) {
}

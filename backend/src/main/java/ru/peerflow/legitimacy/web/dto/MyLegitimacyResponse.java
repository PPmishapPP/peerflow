package ru.peerflow.legitimacy.web.dto;

import java.util.List;

/**
 * Легитимность и репутация текущего человека ({@code BR-LEGIT-3},
 * {@code BR-REP-2}).
 *
 * <p>Репутация отдаётся вместе с передачами, а не отдельным запросом: профиль
 * показывает их рядом (рядом с аватаром — репутация, ниже — кому передано
 * доверие), и два запроса дали бы два момента времени на одном экране.
 *
 * @param reputation  накопленная репутация ({@code BR-REP-2}, {@code BR-REP-3})
 * @param delegations передачи доверия по активным задачам; задачи, где доверие
 *                    осталось у себя, тоже здесь
 */
public record MyLegitimacyResponse(
        long reputation,
        List<DelegationResponse> delegations
) {
}

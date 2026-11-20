package ru.peerflow.legitimacy.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Легитимность по одной задаче глазами клиента ({@code BR-LEGIT-2},
 * {@code BR-LEGIT-3}, {@code BR-LEADER-3}).
 *
 * <p>Почему одним ответом: экран задачи показывает кандидатов, лидера и «кому
 * я передал доверие» одновременно. Три запроса дали бы три момента времени, и
 * человек увидел бы лидера, которого уже нет, рядом с поддержкой, которой уже
 * нет.
 *
 * @param proposalId   задача
 * @param candidates   кандидаты в порядке силы — от сильнейшего
 * @param leader       действующий лидер; {@code null} — лидер ещё не выбран
 * @param myDelegation кому текущий пользователь передал легитимность;
 *                     {@code null} — доверие у себя ({@code BR-LEGIT-5})
 * @param total        сколько единиц легитимности передано кандидатам всего
 * @param members      сколько действующих членов в сообществе — знаменатель
 *                     для шкалы доверия
 */
public record LegitimacyBoardResponse(
        UUID proposalId,
        List<CandidateResponse> candidates,
        LeaderResponse leader,
        UUID myDelegation,
        long total,
        long members
) {
}

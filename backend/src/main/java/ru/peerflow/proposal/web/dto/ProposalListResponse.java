package ru.peerflow.proposal.web.dto;

import java.util.List;

/**
 * Список предложений двумя секциями ({@code BR-RANK-5}).
 *
 * <p>Секции отдаём явно, а не плоским списком: «В работе» и «На обсуждении» —
 * это правило сообщества, а не оформление экрана. Клиенту остаётся только
 * нарисовать две секции, не восстанавливая их самостоятельно по стадии.
 *
 * @param inWork     решение принято, есть бюджет
 * @param discussing идут обсуждение и голосование
 */
public record ProposalListResponse(
        List<ProposalResponse> inWork,
        List<ProposalResponse> discussing
) {
}

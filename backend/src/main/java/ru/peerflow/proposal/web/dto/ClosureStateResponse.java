package ru.peerflow.proposal.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.peerflow.proposal.domain.ProposalStage;

/**
 * Голосование «выполнено / не выполнено» глазами клиента ({@code BR-LIFE-8},
 * {@code BR-LIFE-9}, {@code BR-VOTE-5}, {@code BR-OVER-9}).
 *
 * <p>Отдаём и счётчики, и дедлайн, и свой ответ: экран закрытия показывает
 * всё сразу, и три запроса дали бы три момента времени, где счётчик не
 * совпадал бы с голосом.
 *
 * <p>{@code reputationAwarded} заполняется после закрытия: человеку нужно
 * видеть, что легитимность лидера не пропала, а стала репутацией
 * ({@code BR-REP-1}).
 *
 * @param proposalId       задача
 * @param stage            стадия задачи после проверки
 * @param open             идёт ли голосование сейчас
 * @param votesFor         сколько голосов «выполнено»
 * @param votesAgainst     сколько голосов «не выполнено»
 * @param members          сколько действующих членов — знаменатель для шкалы
 * @param deadline         когда голосование закроется по сроку;
 *                         {@code null} — процедура не запущена
 * @param status           что происходит — текст для человека
 * @param myVote           ответ текущего пользователя; {@code null} — не отвечал
 * @param leaderId         лидер, который запустил процедуру; {@code null} — лидера нет
 * @param leaderName       имя лидера
 * @param reputationAwarded сколько репутации начислено лидеру за задачу;
 *                         {@code 0} — задача ещё не закрыта
 */
public record ClosureStateResponse(
        UUID proposalId,
        ProposalStage stage,
        boolean open,
        long votesFor,
        long votesAgainst,
        long members,
        Instant deadline,
        String status,
        MyClosureVote myVote,
        UUID leaderId,
        String leaderName,
        long reputationAwarded
) {
}

package ru.peerflow.legitimacy.domain;

import ru.peerflow.proposal.domain.Proposal;

/**
 * Причина завершения мандата лидера ({@code BR-LEADER-5},
 * {@code BR-LEADER-7}).
 *
 * <p>Причина хранится, а не выводится из данных: «мандат кончился» и «задачу
 * закрыли» — разные события, и человеку важно, сменился ли лидер из-за
 * отозванного доверия или потому что работа закончена.
 */
public enum MandateEndReason {

    /** Лидер потерял поддержку ниже порога удержания ({@code BR-LEADER-5}). */
    MANDATE_LOST,

    /** Задачу закрыли решением сообщества ({@code BR-LIFE-9}). */
    TASK_CLOSED
}

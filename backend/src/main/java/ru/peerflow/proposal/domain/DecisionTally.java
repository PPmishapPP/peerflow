package ru.peerflow.proposal.domain;

/**
 * Сводка голосов «делаем / не делаем» ({@code BR-VOTE-4}).
 *
 * <p>Отдельный тип, а не пара чисел: по ней считается и решение, и метрика
 * популярности ({@code BR-RANK-2}), и обе не должны собирать голоса вручную —
 * иначе «за» и «против» однажды посчитают по-разному в двух местах.
 *
 * @param votesFor     сколько голосов «делаем»
 * @param votesAgainst сколько голосов «не делаем»
 */
public record DecisionTally(long votesFor, long votesAgainst) {

    /**
     * Всего подано голосов.
     *
     * @return сумма голосов «за» и «против»
     */
    public long total() {
        return votesFor + votesAgainst;
    }

    /**
     * Перевес на стороне «делаем» ({@code BR-VOTE-10}).
     *
     * <p>Равенство голосов решением не считается: задача не принимается.
     *
     * @return {@code true}, если голосов «за» строго больше
     */
    public boolean inFavorWins() {
        return votesFor > votesAgainst;
    }
}

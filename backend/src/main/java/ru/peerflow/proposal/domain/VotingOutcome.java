package ru.peerflow.proposal.domain;

import java.time.Instant;

/**
 * Чем заканчивается голосование «делаем / не делаем»
 * ({@code BR-VOTE-9}–{@code BR-VOTE-12}).
 *
 * <p>Отдельный тип, а не три проверки в сервисе: правило закрытия должно быть
 * одним и тем же при чтении списка, при голосовании и при показе карточки.
 * Иначе голосование закрывалось бы по-разному в зависимости от того, какой
 * экран человек открыл.
 *
 * @param decision что делать с предложением
 * @param reason   почему — это видит человек в карточке
 */
public record VotingOutcome(Decision decision, String reason) {

    /**
     * Сколько голосов минимум нужно, чтобы решение можно было принять досрочно.
     *
     * <p>Зачем это отдельно от порога активности: в маленьком сообществе «30%
     * членов» — это один человек. Без этой границы первый же голос «за»
     * закрывал бы голосование досрочно, то есть ровно то, чего мы не хотим.
     * Поэтому досрочное закрытие требует хотя бы двух голосов: решение всегда
     * подтверждено кем-то ещё, а не одним человеком
     * ({@code BR-VOTE-11}).
     */
    private static final long MIN_VOTES_FOR_EARLY_CLOSE = 2;

    /** Решение по предложению. */
    public enum Decision {

        /** Голосование продолжается, трогать стадию нельзя. */
        KEEP_OPEN,

        /** Сообщество решило «делаем» — предложение переходит в работу. */
        ACCEPT,

        /** Сообщество решило «не делаем» — предложение отклоняется. */
        REJECT
    }

    /**
     * Голосование закрыто и решение принято.
     *
     * @return {@code true}, если стадию уже можно менять
     */
    public boolean isFinal() {
        return decision != Decision.KEEP_OPEN;
    }

    /**
     * Определяет, пора ли закрывать голосование и с каким итогом.
     *
     * <p>Порядок проверок важен:
     * <ol>
     *   <li>срок истёк — решает большинство проголосовавших
     *       ({@code BR-VOTE-10}); если не проголосовал никто, предложение
     *       отклоняется ({@code BR-VOTE-12});</li>
     *   <li>срок не истёк, но сообщество проявило активность и явный перевес —
     *       закрываем досрочно ({@code BR-VOTE-11});</li>
     *   <li>иначе голосование продолжается.</li>
     * </ol>
     *
     * @param tally    текущая сводка голосов
     * @param rules    срок и пороги досрочного закрытия
     * @param members  сколько действующих членов в сообществе — от него
     *                 считается требуемая активность
     * @param createdAt когда предложение внесено — от него считается срок
     * @param now      текущий момент
     * @return что делать с предложением
     */
    public static VotingOutcome evaluate(DecisionTally tally, VotingRules rules,
                                         long members, Instant createdAt, Instant now) {
        boolean expired = !now.isBefore(rules.deadlineFor(createdAt));

        if (expired) {
            if (tally.total() == 0) {
                return new VotingOutcome(Decision.REJECT,
                        "за время голосования не подано ни одного голоса");
            }
            if (tally.inFavorWins()) {
                return new VotingOutcome(Decision.ACCEPT,
                        "большинство проголосовавших — за выполнение");
            }
            return new VotingOutcome(Decision.REJECT,
                    "большинства за выполнение нет");
        }

        long needed = rules.requiredParticipants(members);
        if (tally.total() < Math.max(needed, MIN_VOTES_FOR_EARLY_CLOSE)) {
            return new VotingOutcome(Decision.KEEP_OPEN,
                    "ждём голоса сообщества");
        }
        if (hasOverwhelmingLead(tally.votesFor(), tally.votesAgainst(),
                rules.earlyCloseLead())) {
            return new VotingOutcome(Decision.ACCEPT,
                    "сообщество активно голосует за выполнение");
        }
        if (hasOverwhelmingLead(tally.votesAgainst(), tally.votesFor(),
                rules.earlyCloseLead())) {
            return new VotingOutcome(Decision.REJECT,
                    "сообщество активно голосует против");
        }
        return new VotingOutcome(Decision.KEEP_OPEN,
                "перевес пока не решающий, ждём окончания срока");
    }

    /**
     * Проверяет перевес «не меньше чем {@code lead}:1» в пользу
     * {@code leading}.
     *
     * <p>Сравнение в целых числах, а не делением: {@code 2/1} и {@code 3/2} не
     * должны различаться ошибкой округления. Ноль на противоположной стороне
     * перевес даёт — но этого мало: активность сообщества проверяется отдельно
     * выше ({@code MIN_VOTES_FOR_EARLY_CLOSE} и доля членов), иначе первое же
     * «за» при пустом «против» закрывало бы голосование.
     */
    private static boolean hasOverwhelmingLead(long leading, long other, int lead) {
        return leading > 0 && leading >= other * lead;
    }
}

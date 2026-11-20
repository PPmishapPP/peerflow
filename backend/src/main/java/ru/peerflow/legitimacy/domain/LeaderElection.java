package ru.peerflow.legitimacy.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Выбор лидера по задаче ({@code BR-LEGIT-4}, {@code BR-LEADER-3},
 * {@code BR-LEADER-4}).
 *
 * <p>Порядок сравнения — правило сообщества, поэтому оно живёт здесь одним
 * компаратором, а не в SQL и не в сервисе:
 * <ol>
 *   <li>больше легитимности — выше ({@code BR-LEADER-3});</li>
 *   <li>при равенстве — больше репутации ({@code BR-REP-4});</li>
 *   <li>при равенстве — раньше вступил в сообщество
 *       ({@code BR-LEADER-4}).</li>
 * </ol>
 *
 * <p>Последний тай-брейкер обязателен, иначе два кандидата с одинаковой
 * поддержкой «прыгали» бы между запросами, и лидер менялся бы от простого
 * обновления экрана.
 */
public final class LeaderElection {

    private LeaderElection() {
    }

    /**
     * Кандидат с его показателями на момент выборов.
     *
     * @param candidateId аккаунт
     * @param displayName имя для человека
     * @param support     сколько легитимности передано
     * @param reputation  накопленная репутация ({@code BR-REP-4})
     * @param joinedAt    когда вступил в сообщество
     */
    public record CandidateScore(
            UUID candidateId,
            String displayName,
            long support,
            long reputation,
            Instant joinedAt
    ) {
        public CandidateScore {
            Objects.requireNonNull(candidateId, "кандидат обязателен");
        }
    }

    /**
     * Сравнение кандидатов по правилам сообщества
     * ({@code BR-LEADER-3}, {@code BR-LEADER-4}, {@code BR-REP-4}).
     *
     * <p>Каждый слой записан отдельно и явно «по убыванию»: вложенный
     * {@code reversed()} над цепочкой легко прочитать наоборот и молча
     * перевернуть тай-брейкеры.
     */
    private static final Comparator<CandidateScore> BY_MANDATE = Comparator
            .comparingLong(CandidateScore::support).reversed()
            .thenComparing(Comparator.comparingLong(CandidateScore::reputation).reversed())
            // Раньше вступил — выше. Отсутствие даты считается «самым поздним»:
            // не вступивший не может обогнать вступившего.
            .thenComparing(score -> score.joinedAt() == null
                    ? Instant.MAX
                    : score.joinedAt())
            // Последний слой — идентификатор: порядок должен быть полным,
            // иначе результат зависел бы от порядка строк в базе.
            .thenComparing(score -> score.candidateId().toString());

    /**
     * Упорядочивает кандидатов от сильнейшего к слабейшему.
     *
     * @param scores кандидаты с показателями
     * @return список в порядке убывания силы
     */
    public static List<CandidateScore> rank(List<CandidateScore> scores) {
        List<CandidateScore> ordered = new ArrayList<>(scores);
        ordered.sort(BY_MANDATE);
        return List.copyOf(ordered);
    }

    /**
     * Выбирает лидера ({@code BR-LEGIT-4}, {@code BR-LEADER-3}).
     *
     * <p>Если никто не собрал ни одной единицы доверия, лидера нет: назначать
     * «кандидата по алфавиту» значило бы выдать мандат без поддержки
     * сообщества.
     *
     * @param scores кандидаты с показателями
     * @return победитель или пусто, если поддержки нет ни у кого
     */
    public static Optional<CandidateScore> elect(List<CandidateScore> scores) {
        return rank(scores).stream()
                .filter(score -> score.support() > 0)
                .findFirst();
    }
}

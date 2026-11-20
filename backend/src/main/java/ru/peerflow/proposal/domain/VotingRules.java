package ru.peerflow.proposal.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Настройки голосования «делаем / не делаем» ({@code BR-VOTE-9}–{@code BR-VOTE-13}).
 *
 * <p>Пороги приходят из конфигурации ({@code PeerflowThresholds}), но само
 * правило живёт в домене: решение «голосование закрыто» не должно зависеть от
 * того, кто его вызвал — сервис при чтении списка или сам обработчик голоса.
 *
 * @param window          сколько идёт голосование ({@code BR-VOTE-9})
 * @param earlyCloseRatio какую долю членов нужно охватить, чтобы закрыть
 *                        голосование досрочно ({@code BR-VOTE-11})
 * @param earlyCloseLead  какой перевес считается достаточным для досрочного
 *                        закрытия: значение {@code 2} означает «2:1»
 *                        ({@code BR-VOTE-11})
 */
public record VotingRules(Duration window, double earlyCloseRatio, int earlyCloseLead) {

    /**
     * Проверяет, что настройки имеют смысл.
     *
     * @throws IllegalArgumentException если срок не положителен, доля вне
     *                                  диапазона {@code 0..1} или перевес
     *                                  меньше {@code 2} (при перевесе {@code 1}
     *                                  «досрочка» закрывала бы голосование при
     *                                  равенстве, то есть всегда)
     */
    public VotingRules {
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("срок голосования должен быть положительным");
        }
        if (earlyCloseRatio <= 0 || earlyCloseRatio > 1) {
            throw new IllegalArgumentException("доля для досрочного закрытия — от 0 до 1");
        }
        if (earlyCloseLead < 2) {
            throw new IllegalArgumentException("перевес для досрочного закрытия — не меньше 2:1");
        }
    }

    /**
     * Сколько членов должно проголосовать, чтобы голосование можно было
     * закрыть досрочно ({@code BR-VOTE-11}).
     *
     * <p>Считаем через {@link BigDecimal} и округление вверх по той же причине,
     * что и порог приёма: {@code 0.3 * 10} на {@code double} даёт
     * {@code 2.9999999999999996}, и «30% из десяти» стали бы двумя голосами
     * вместо трёх. Минимум один: пустое сообщество не голосует.
     *
     * @param members число действующих членов сообщества
     * @return минимальное число проголосовавших
     */
    public long requiredParticipants(long members) {
        if (members <= 0) {
            return 1;
        }
        long required = BigDecimal.valueOf(earlyCloseRatio)
                .multiply(BigDecimal.valueOf(members))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
        return Math.max(1, required);
    }

    /**
     * Момент, после которого голосование закрыто в любом случае.
     *
     * @param createdAt когда предложение внесено
     * @return дедлайн голосования
     */
    public Instant deadlineFor(Instant createdAt) {
        return createdAt.plus(window);
    }
}

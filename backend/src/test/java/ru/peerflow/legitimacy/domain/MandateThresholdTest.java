package ru.peerflow.legitimacy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Порог удержания мандата ({@code BR-LEADER-5}, {@code BR-LIMIT-1}).
 *
 * <p>Правило звучит как «потерял <em>больше</em> 30%», а не «потерял 30%».
 * Поэтому ровно 70% мандат сохраняют, и это надо проверить отдельно: на
 * {@code double} произведение {@code 0.7 * 10} даёт
 * {@code 6.999999999999999}, и наивное сравнение отправляло бы лидера в
 * отставку на пустом месте.
 *
 * <p>Тест без базы и Spring: это арифметика правила, а не работа с данными.
 * Сквозной путь «отзыв → мандат утрачен → переголосование» проверяется в
 * {@link ru.peerflow.legitimacy.LeaderElectionTest}.
 */
class MandateThresholdTest {

    @Test
    @DisplayName("Ровно 70% — мандат сохранён (BR-LEADER-5)")
    void exactlySeventyPercentRetains() {
        MandateThreshold threshold = new MandateThreshold(10, 0.70);

        assertThat(threshold.isRetainedBy(7))
                .as("потеряно ровно 30% — это ещё не «больше 30%»")
                .isTrue();
        assertThat(threshold.requiredSupport()).isEqualTo(7);
    }

    @Test
    @DisplayName("Меньше 70% — мандат утрачен (BR-LEADER-5)")
    void belowSeventyPercentEndsMandate() {
        MandateThreshold threshold = new MandateThreshold(10, 0.70);

        assertThat(threshold.isRetainedBy(6))
                .as("потеряно 40% — это больше 30%")
                .isFalse();
    }

    @Test
    @DisplayName("Ничего не потеряно — мандат держится (BR-LEADER-5)")
    void fullSupportRetains() {
        MandateThreshold threshold = new MandateThreshold(10, 0.70);

        assertThat(threshold.isRetainedBy(10)).isTrue();
        assertThat(threshold.isRetainedBy(11))
                .as("поддержка могла вырасти — это не повод снимать мандат")
                .isTrue();
    }

    @Test
    @DisplayName("Нулевая поддержка — мандат утрачен (BR-LEADER-5)")
    void zeroSupportEndsMandate() {
        MandateThreshold threshold = new MandateThreshold(10, 0.70);

        assertThat(threshold.isRetainedBy(0)).isFalse();
    }

    @Test
    @DisplayName("Дробный порог округляется вверх: нужно 7, а не 6 (BR-LIMIT-1)")
    void fractionalThresholdRoundsUp() {
        // 0.70 * 9 = 6.3: шесть единиц — меньше порога, мандат не держится.
        MandateThreshold threshold = new MandateThreshold(9, 0.70);

        assertThat(threshold.requiredSupport()).isEqualTo(7);
        assertThat(threshold.isRetainedBy(6)).isFalse();
        assertThat(threshold.isRetainedBy(7)).isTrue();
    }

    @Test
    @DisplayName("Мандат без поддержки на выборах держится только на нуле (BR-LEGIT-4)")
    void zeroInitialSupportAlwaysRetains() {
        MandateThreshold threshold = new MandateThreshold(0, 0.70);

        assertThat(threshold.isRetainedBy(0)).isTrue();
    }

    @Test
    @DisplayName("Доля удержания вне диапазона — ошибка настройки (BR-LIMIT-1)")
    void rejectsInvalidRatio() {
        assertThatThrownBy(() -> new MandateThreshold(10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MandateThreshold(10, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MandateThreshold(-1, 0.70))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

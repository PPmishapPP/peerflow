package ru.peerflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Тесты порогов MVP: значения по умолчанию, загрузка из конфигурации и
 * проверка допустимых границ.
 *
 * <p>Пороги относятся к {@code BR-LIMIT-1}, {@code BR-LIMIT-2},
 * {@code BR-LIMIT-3}: они обязаны совпадать с бизнес-описанием и не должны
 * быть «магическими числами» внутри логики.
 */
class PeerflowThresholdsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(ThresholdsTestConfiguration.class);

    @Test
    @DisplayName("Без конфигурации действуют значения по умолчанию из бизнес-описания")
    void appliesDefaultsFromBusinessRules() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            PeerflowThresholds thresholds = context.getBean(PeerflowThresholds.class);

            // BR-LIMIT-1: остаток ниже 70% — мандат утрачен.
            assertThat(thresholds.legitimacyRetainRatio()).isEqualTo(0.70);
            // BR-LIMIT-2: 3 метки «спам» скрывают сообщение.
            assertThat(thresholds.spamHideMarks()).isEqualTo(3);
            // BR-LIMIT-3: приём — минимум 30% голосов «за».
            assertThat(thresholds.joinAcceptRatio()).isEqualTo(0.30);
        });
    }

    @Test
    @DisplayName("Значения из конфигурации переопределяют значения по умолчанию")
    void bindsValuesFromConfiguration() {
        runner.withPropertyValues(
                        "peerflow.thresholds.legitimacy-retain-ratio=0.55",
                        "peerflow.thresholds.spam-hide-marks=5",
                        "peerflow.thresholds.join-accept-ratio=0.5")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    PeerflowThresholds thresholds = context.getBean(PeerflowThresholds.class);

                    assertThat(thresholds.legitimacyRetainRatio()).isEqualTo(0.55);
                    assertThat(thresholds.spamHideMarks()).isEqualTo(5);
                    assertThat(thresholds.joinAcceptRatio()).isEqualTo(0.5);
                });
    }

    @Test
    @DisplayName("Доля больше 1 отклоняется при старте")
    void rejectsRatioAboveOne() {
        runner.withPropertyValues("peerflow.thresholds.join-accept-ratio=1.5")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Отрицательная доля отклоняется при старте")
    void rejectsNegativeRatio() {
        runner.withPropertyValues("peerflow.thresholds.legitimacy-retain-ratio=-0.1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Ноль меток для скрытия спама отклоняется при старте")
    void rejectsZeroSpamMarks() {
        runner.withPropertyValues("peerflow.thresholds.spam-hide-marks=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(PeerflowThresholds.class)
    static class ThresholdsTestConfiguration {
    }
}

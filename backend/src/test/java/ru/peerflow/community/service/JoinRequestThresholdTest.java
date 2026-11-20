package ru.peerflow.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Арифметика порога приёма ({@code BR-JOIN-5}, {@code BR-LIMIT-3}).
 *
 * <p>Порог — «минимум 30% голосов за» от числа действующих членов. Долю
 * округляем вверх: недобрать голоса нельзя, иначе при семи членах двух голосов
 * (28.6%) хватило бы, хотя это меньше заявленных 30%. Расчёт на {@code double}
 * здесь опасен: {@code 0.3 * 10 = 2.9999999999999996}, и округление вверх дало
 * бы лишний голос.
 */
class JoinRequestThresholdTest {

    @Test
    @DisplayName("30% от десяти членов — три голоса, а не четыре")
    void computesExactThresholdForTenMembers() {
        assertThat(JoinRequestService.requiredAccepts(10, 0.30))
                .as("0.3 * 10 на double даёт 2.9999999999999996 — не должно стать четырьмя")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Дробная доля округляется вверх, чтобы 30% не превращались в 28.6%")
    void roundsFractionalShareUp() {
        assertThat(JoinRequestService.requiredAccepts(7, 0.30))
                .as("2.1 голоса — нужен третий голос")
                .isEqualTo(3);
        assertThat(JoinRequestService.requiredAccepts(4, 0.30))
                .as("1.2 голоса — нужен второй голос")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Целая доля не округляется вверх зря")
    void keepsWholeShareAsIs() {
        assertThat(JoinRequestService.requiredAccepts(3, 0.30)).isEqualTo(1);
        assertThat(JoinRequestService.requiredAccepts(10, 0.50)).isEqualTo(5);
        assertThat(JoinRequestService.requiredAccepts(10, 0.70)).isEqualTo(7);
    }

    @Test
    @DisplayName("Даже пустое сообщество требует хотя бы одного голоса")
    void requiresAtLeastOneVote() {
        assertThat(JoinRequestService.requiredAccepts(0, 0.30))
                .as("иначе сообщество без членов принимало бы всех молча")
                .isEqualTo(1);
        assertThat(JoinRequestService.requiredAccepts(1, 0.30)).isEqualTo(1);
    }
}

package ru.peerflow.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.peerflow.proposal.domain.DecisionTally;
import ru.peerflow.proposal.domain.VotingOutcome;
import ru.peerflow.proposal.domain.VotingRules;

/**
 * Правила закрытия голосования «делаем / не делаем»
 * ({@code BR-VOTE-9}–{@code BR-VOTE-12}, {@code BR-LIMIT-5},
 * {@code BR-LIMIT-6}).
 *
 * <p>Здесь проверяется главное обещание механики: первый голос «за» ничего не
 * решает, но и решение не застревает из-за отсутствия кворума. Обе цели
 * достигаются разными средствами — сроком и досрочным закрытием, — и именно
 * это проверяется на границах.
 */
class VotingOutcomeTest {

    private static final Instant CREATED = Instant.parse("2026-11-20T10:00:00Z");

    private static final VotingRules RULES =
            new VotingRules(Duration.ofHours(48), 0.30, 2);

    @Test
    @DisplayName("Один голос «за» не закрывает голосование — нужен срок или активность (BR-VOTE-11)")
    void singleVoteDoesNotDecide() {
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(1, 0), RULES, 10, CREATED, CREATED.plus(Duration.ofMinutes(5)));

        assertThat(outcome.decision())
                .as("первый голос не должен решать: у сообщества ещё есть время")
                .isEqualTo(VotingOutcome.Decision.KEEP_OPEN);
    }

    @Test
    @DisplayName("Один голос не решает даже в сообществе из одного человека (BR-VOTE-11)")
    void singleVoteDoesNotDecideInTinyCommunity() {
        // 30% от одного — один. Без нижней границы в два голоса решение
        // принимал бы один человек.
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(1, 0), RULES, 1, CREATED, CREATED.plus(Duration.ofMinutes(5)));

        assertThat(outcome.decision()).isEqualTo(VotingOutcome.Decision.KEEP_OPEN);
    }

    @Test
    @DisplayName("Досрочное закрытие: 30% членов и перевес 2:1 (BR-VOTE-11)")
    void closesEarlyWhenCommunityIsActiveAndDecisionIsClear() {
        // 10 членов → нужно 3 проголосовавших; 3:1 — перевес не меньше 2:1.
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(3, 1), RULES, 10, CREATED, CREATED.plus(Duration.ofHours(1)));

        assertThat(outcome.decision()).isEqualTo(VotingOutcome.Decision.ACCEPT);
    }

    @Test
    @DisplayName("Досрочка срабатывает ровно на перевесе 2:1 (граница BR-VOTE-11)")
    void closesEarlyOnExactLead() {
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(2, 1), RULES, 3, CREATED, CREATED.plus(Duration.ofHours(1)));

        assertThat(outcome.decision()).isEqualTo(VotingOutcome.Decision.ACCEPT);
    }

    @Test
    @DisplayName("Досрочка не срабатывает при перевесе меньше 2:1 (BR-VOTE-11)")
    void staysOpenWhenLeadIsWeak() {
        // 3:2 — перевес 1.5:1, недостаточно.
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(3, 2), RULES, 10, CREATED, CREATED.plus(Duration.ofHours(1)));

        assertThat(outcome.decision()).isEqualTo(VotingOutcome.Decision.KEEP_OPEN);
    }

    @Test
    @DisplayName("Досрочка не срабатывает без нужной активности, даже при явном перевесе (BR-VOTE-11)")
    void staysOpenWithoutEnoughParticipants() {
        // 10 членов, 30% → трое. Голосов 2:0 — перевес есть, активности нет.
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(2, 0), RULES, 10, CREATED, CREATED.plus(Duration.ofHours(1)));

        assertThat(outcome.decision()).isEqualTo(VotingOutcome.Decision.KEEP_OPEN);
    }

    @Test
    @DisplayName("Досрочка работает и против: сообщество активно против (BR-VOTE-11)")
    void closesEarlyWhenCommunityRejects() {
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(1, 3), RULES, 10, CREATED, CREATED.plus(Duration.ofHours(1)));

        assertThat(outcome.decision()).isEqualTo(VotingOutcome.Decision.REJECT);
    }

    @Test
    @DisplayName("По истечении срока решает большинство проголосовавших, кворум не нужен (BR-VOTE-10)")
    void decidesBySimpleMajorityAfterDeadline() {
        Instant afterDeadline = CREATED.plus(Duration.ofHours(49));

        // Двое голосовавших при десяти членах — всё равно решение.
        VotingOutcome accepted = VotingOutcome.evaluate(
                new DecisionTally(2, 1), RULES, 10, CREATED, afterDeadline);
        VotingOutcome rejected = VotingOutcome.evaluate(
                new DecisionTally(1, 2), RULES, 10, CREATED, afterDeadline);

        assertThat(accepted.decision()).isEqualTo(VotingOutcome.Decision.ACCEPT);
        assertThat(rejected.decision()).isEqualTo(VotingOutcome.Decision.REJECT);
    }

    @Test
    @DisplayName("Равенство голосов по истечении срока — задача не принимается (BR-VOTE-10)")
    void tieRejectsAfterDeadline() {
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(2, 2), RULES, 10, CREATED, CREATED.plus(Duration.ofHours(49)));

        assertThat(outcome.decision()).isEqualTo(VotingOutcome.Decision.REJECT);
    }

    @Test
    @DisplayName("Ни одного голоса к дедлайну — предложение отклоняется (BR-VOTE-12)")
    void noVotesRejectsAfterDeadline() {
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(0, 0), RULES, 10, CREATED, CREATED.plus(Duration.ofHours(49)));

        assertThat(outcome.decision()).isEqualTo(VotingOutcome.Decision.REJECT);
        assertThat(outcome.reason()).contains("не подано ни одного голоса");
    }

    @Test
    @DisplayName("Ровно на дедлайне голосование уже закрыто (граница BR-VOTE-9)")
    void deadlineBoundaryIsInclusive() {
        Instant deadline = CREATED.plus(Duration.ofHours(48));

        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(1, 0), RULES, 10, CREATED, deadline);

        assertThat(outcome.decision())
                .as("в момент дедлайна срок считается истёкшим")
                .isEqualTo(VotingOutcome.Decision.ACCEPT);
    }

    @Test
    @DisplayName("За секунду до дедлайна голосование ещё открыто (граница BR-VOTE-9)")
    void justBeforeDeadlineIsStillOpen() {
        VotingOutcome outcome = VotingOutcome.evaluate(
                new DecisionTally(1, 0), RULES, 10, CREATED,
                CREATED.plus(Duration.ofHours(48)).minusSeconds(1));

        assertThat(outcome.decision()).isEqualTo(VotingOutcome.Decision.KEEP_OPEN);
    }

    @Test
    @DisplayName("30% из десяти — три проголосовавших, а не два (BR-LIMIT-6)")
    void participantThresholdRoundsUp() {
        assertThat(RULES.requiredParticipants(10))
                .as("0.3 * 10 на double даёт 2.9999999999999996 — не должно стать двумя")
                .isEqualTo(3);
        assertThat(RULES.requiredParticipants(7)).isEqualTo(3);
        assertThat(RULES.requiredParticipants(4)).isEqualTo(2);
        assertThat(RULES.requiredParticipants(0))
                .as("пустое сообщество не закрывает голосование досрочно без голосов")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Срок голосования не может быть неположительным (BR-LIMIT-5)")
    void rejectsNonPositiveWindow() {
        assertThatThrownBy(() -> new VotingRules(Duration.ZERO, 0.30, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VotingRules(Duration.ofHours(-1), 0.30, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Перевес меньше 2:1 не годится для досрочного закрытия (BR-VOTE-11)")
    void rejectsLeadBelowTwo() {
        assertThatThrownBy(() -> new VotingRules(Duration.ofHours(48), 0.30, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package ru.peerflow.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.MembershipRequiredException;
import ru.peerflow.proposal.domain.ProposalStage;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.proposal.web.dto.ProposalResponse;
import ru.peerflow.support.PostgresTestBase;

/**
 * Голосование «делаем / не делаем» ({@code BR-VOTE-2}, {@code BR-VOTE-4},
 * {@code BR-VOTE-9}–{@code BR-VOTE-13}, {@code BR-LIFE-4}, {@code BR-RISK-1}).
 *
 * <p>Здесь проверяется то, что нельзя проверить в чистом домене: переходы
 * стадии в базе, идемпотентность повторных голосов и закрытие голосования по
 * времени. Часы подменены фиксированными, поэтому «ждать 48 часов» не нужно —
 * тест сдвигает время сам.
 *
 * <p>Сообщество — десять человек, и это важно: при малом сообществе порог
 * досрочного закрытия (30%) — это один-два голоса, и тесты «голосование ещё
 * открыто» стали бы проверять не то, что задумано.
 */
@SpringBootTest
@Import(DecisionVoteTest.MovableClockConfiguration.class)
class DecisionVoteTest extends PostgresTestBase {

    /** Начало отсчёта: от этого момента считается срок голосования. */
    private static final Instant START = Instant.parse("2026-11-20T09:00:00Z");

    private static final int MEMBERS = 10;

    /**
     * Часы, которые двигает тест.
     *
     * <p>Подменяем бин {@code clock} через {@code @Primary}-конфигурацию, а не
     * {@code Instant.now()} в сервисе: иначе пришлось бы ждать 48 реальных
     * часов, чтобы проверить закрытие голосования ({@code BR-VOTE-9}).
     */
    private static final MovableClock MOVABLE = new MovableClock(START);

    @TestConfiguration
    static class MovableClockConfiguration {

        @Bean
        @Primary
        Clock movableClock() {
            return MOVABLE;
        }
    }

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    private Community community;
    private User anna;
    private User boris;
    private User vera;

    @BeforeEach
    void setUp() {
        MOVABLE.set(START);
        cleanDatabase();
        community = communityRepository.save(Community.create("Маяк"));
        anna = member("anna", "Анна");
        boris = member("boris", "Борис");
        vera = member("vera", "Вера");
        for (int i = 3; i < MEMBERS; i++) {
            member("member" + i, "Участник " + i);
        }
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Голос «за» сохраняется и виден в счётчиках (BR-VOTE-4)")
    void storesVoteFor() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);

        ProposalResponse after = vote(boris, proposal.id(), true);

        assertThat(after.votesFor()).isEqualTo(1);
        assertThat(after.votesAgainst()).isZero();
        assertThat(after.myVote()).isNotNull();
        assertThat(after.myVote().inFavor()).isTrue();
    }

    @Test
    @DisplayName("Первый голос «за» не закрывает голосование даже при малом сообществе (BR-VOTE-11)")
    void singleVoteDoesNotDecide() {
        // Сообщество из одного человека: 30% — это один голос. Без нижней
        // границы «минимум два голоса» первый же голос решил бы всё.
        cleanDatabase();
        community = communityRepository.save(Community.create("Малый"));
        anna = member("anna", "Анна");

        ProposalResponse proposal = create(anna, "Купить воду", 2000);
        ProposalResponse after = vote(anna, proposal.id(), true);

        assertThat(after.votesFor()).isEqualTo(1);
        assertThat(after.stage())
                .as("решение не должно приниматься голосом одного человека")
                .isEqualTo(ProposalStage.DISCUSSION);
        assertThat(after.votingClosed()).isFalse();
    }

    @Test
    @DisplayName("Повторный голос не создаёт дубль: один человек — один голос (BR-VOTE-2)")
    void repeatedVoteDoesNotCreateDuplicate() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);

        vote(boris, proposal.id(), true);
        vote(boris, proposal.id(), true);

        ProposalResponse after = proposalService.get(anna.getId(), proposal.id());

        assertThat(after.votesFor())
                .as("двойное нажатие не должно удваивать голос")
                .isEqualTo(1);
        assertThat(proposalRepository.findByIdWithVotes(proposal.id()).orElseThrow().getVotes())
                .hasSize(1);
    }

    @Test
    @DisplayName("Смена голоса работает, пока голосование открыто (BR-VOTE-13)")
    void changesVoteWhileOpen() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);

        vote(boris, proposal.id(), true);
        ProposalResponse afterChange = vote(boris, proposal.id(), false);

        assertThat(afterChange.votesFor()).isZero();
        assertThat(afterChange.votesAgainst()).isEqualTo(1);
        assertThat(afterChange.myVote()).isNotNull();
        assertThat(afterChange.myVote().inFavor()).isFalse();
    }

    @Test
    @DisplayName("Досрочное закрытие: 30% членов и перевес 2:1 переводит в работу (BR-LIFE-4, BR-VOTE-11)")
    void acceptingProposalMovesToWork() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);

        // 10 членов → 30% это 3 голоса. 3:1 — перевес больше 2:1.
        vote(boris, proposal.id(), true);
        vote(vera, proposal.id(), true);
        ProposalResponse after = vote(anna, proposal.id(), true);

        assertThat(after.votesFor()).isEqualTo(3);
        assertThat(after.stage())
                .as("30% участия и явный перевес — решение принимается досрочно")
                .isEqualTo(ProposalStage.IN_WORK);
        assertThat(after.decidedAt()).isNotNull();
        assertThat(after.votingClosed()).isTrue();
    }

    @Test
    @DisplayName("Отрицательное большинство отклоняет предложение (BR-LIFE-4)")
    void rejectingProposalMovesToRejected() {
        ProposalResponse proposal = create(anna, "Купить диско-шар", 30000);

        vote(boris, proposal.id(), false);
        vote(vera, proposal.id(), false);
        ProposalResponse after = vote(anna, proposal.id(), false);

        assertThat(after.votesAgainst()).isEqualTo(3);
        assertThat(after.stage()).isEqualTo(ProposalStage.REJECTED);
    }

    @Test
    @DisplayName("После истечения срока голосовать нельзя (BR-VOTE-9)")
    void rejectsVoteAfterDeadline() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);

        MOVABLE.advance(Duration.ofHours(49));

        assertThatThrownBy(() -> vote(boris, proposal.id(), true))
                .as("голосование закрылось по сроку, голос не принимается")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("закрыто");
    }

    @Test
    @DisplayName("По истечении срока решение принимается без кворума (BR-VOTE-10, BR-RISK-1)")
    void decidesAfterDeadlineWithoutQuorum() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);
        vote(boris, proposal.id(), true);

        MOVABLE.advance(Duration.ofHours(49));

        ProposalResponse after = proposalService.get(anna.getId(), proposal.id());

        assertThat(after.stage())
                .as("один голос «за» при десяти членах — решение принято по сроку")
                .isEqualTo(ProposalStage.IN_WORK);
        assertThat(after.votesFor()).isEqualTo(1);
    }

    @Test
    @DisplayName("Равенство голосов по истечении срока — не делаем (BR-VOTE-10)")
    void tieRejectsAfterDeadline() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);
        vote(boris, proposal.id(), true);
        vote(vera, proposal.id(), false);

        MOVABLE.advance(Duration.ofHours(49));

        ProposalResponse after = proposalService.get(anna.getId(), proposal.id());

        assertThat(after.votesFor()).isEqualTo(1);
        assertThat(after.votesAgainst()).isEqualTo(1);
        assertThat(after.stage())
                .as("при равенстве задача не принимается")
                .isEqualTo(ProposalStage.REJECTED);
    }

    @Test
    @DisplayName("Ни одного голоса к дедлайну — предложение отклоняется (BR-VOTE-12)")
    void rejectsProposalWithNoVotesAfterDeadline() {
        ProposalResponse proposal = create(anna, "Купить диско-шар", 30000);

        MOVABLE.advance(Duration.ofHours(49));

        ProposalResponse after = proposalService.get(anna.getId(), proposal.id());

        assertThat(after.stage())
                .as("никому не интересно — предложение отклонено")
                .isEqualTo(ProposalStage.REJECTED);
        assertThat(after.votesFor()).isZero();
        assertThat(after.votesAgainst()).isZero();
    }

    @Test
    @DisplayName("Список закрывает истёкшие голосования, не дожидаясь открытия карточки (BR-VOTE-9)")
    void listClosesExpiredVotes() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);
        vote(boris, proposal.id(), true);

        MOVABLE.advance(Duration.ofHours(49));

        assertThat(proposalService.list(anna.getId(),
                ru.peerflow.proposal.service.ProposalSort.NEW).inWork())
                .extracting(ProposalResponse::id)
                .contains(proposal.id());
        assertThat(proposalService.list(anna.getId(),
                ru.peerflow.proposal.service.ProposalSort.NEW).discussing())
                .isEmpty();
    }

    @Test
    @DisplayName("Досрочка не срабатывает без нужной активности, даже при явном перевесе (BR-VOTE-11)")
    void staysOpenWithoutEnoughParticipants() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);

        // Двое «за» из десяти — 20%, меньше 30%: ждём.
        vote(anna, proposal.id(), true);
        ProposalResponse after = vote(boris, proposal.id(), true);

        assertThat(after.stage()).isEqualTo(ProposalStage.DISCUSSION);
        assertThat(after.votingStatus()).contains("ждём");
    }

    @Test
    @DisplayName("Голосовать может только член сообщества (BR-VOTE-1)")
    void rejectsVoteFromNonMember() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);
        User outsider = userRepository.save(User.register("petr", "hash", "Пётр"));

        assertThatThrownBy(() -> proposalService.vote(outsider.getId(), proposal.id(), true))
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("Голос по закрытому предложению не принимается (BR-VOTE-13)")
    void rejectsVoteOnDecidedProposal() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);
        vote(boris, proposal.id(), true);
        vote(vera, proposal.id(), true);
        vote(anna, proposal.id(), true);

        User extra = member("extra", "Лишний");
        assertThatThrownBy(() -> proposalService.vote(extra.getId(), proposal.id(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("закрыто");
    }

    @Test
    @DisplayName("Голос за пределами своего сообщества не проходит (BR-PRIV-1)")
    void rejectsVoteInOtherCommunity() {
        ProposalResponse proposal = create(anna, "Закупить воду", 2000);

        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        assertThatThrownBy(() -> proposalService.vote(stranger.getId(), proposal.id(), true))
                .isInstanceOf(ru.peerflow.proposal.service.ProposalNotFoundException.class);
    }

    private ProposalResponse create(User author, String title, long budget) {
        return proposalService.create(
                author.getId(),
                new CreateProposalRequest(title, "Описание", budget));
    }

    private ProposalResponse vote(User member, java.util.UUID proposalId, boolean inFavor) {
        return proposalService.vote(member.getId(), proposalId, inFavor);
    }

    private User member(String login, String displayName) {
        User user = User.register(login, "hash", displayName);
        user.join(community);
        return userRepository.save(user);
    }

    private void cleanDatabase() {
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }

    /** Часы, которые можно сдвинуть из теста. */
    static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        void advance(Duration duration) {
            this.now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

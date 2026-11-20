package ru.peerflow.legitimacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.legitimacy.domain.LeaderMandate;
import ru.peerflow.legitimacy.domain.MandateEndReason;
import ru.peerflow.legitimacy.repo.CandidateRepository;
import ru.peerflow.legitimacy.repo.LeaderMandateRepository;
import ru.peerflow.legitimacy.repo.LegitimacyEventRepository;
import ru.peerflow.legitimacy.service.LegitimacyService;
import ru.peerflow.legitimacy.web.dto.LegitimacyBoardResponse;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.domain.ProposalStage;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.proposal.web.dto.ProposalResponse;
import ru.peerflow.reputation.domain.ReputationEntry;
import ru.peerflow.reputation.repo.ReputationEntryRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Выбор лидера и утрата мандата ({@code BR-LEGIT-4}, {@code BR-LEADER-3}–
 * {@code BR-LEADER-7}, {@code BR-LIFE-5}, {@code BR-LIFE-10}).
 *
 * <p>Тай-брейкеры проверяются по одному: поддержка, затем репутация, затем дата
 * вступления. Именно в этом порядке они записаны в бизнес-описании
 * ({@code BR-LEADER-4}), и перепутанный порядок дал бы «победителя», которого
 * сообщество не выбирало.
 *
 * <p>Порог удержания здесь проверяется сквозным путём: отзыв доверия реально
 * возвращает задачу на переголосование. Сама арифметика порога (в том числе
 * «ровно 70% держат мандат») — в
 * {@link ru.peerflow.legitimacy.domain.MandateThresholdTest}: это правило
 * домена, и дублировать его границы на уровне сервиса незачем.
 */
@SpringBootTest
class LeaderElectionTest extends PostgresTestBase {

    /** Сообщество из десяти человек: 30% порога досрочки — ровно 3 голоса. */
    private static final int MEMBERS = 10;

    /** Голосов, которых достаточно для досрочного решения в таком составе. */
    private static final int VOTES_TO_ACCEPT = 3;

    /** Цена кандидата: чуть ниже потолка 12000 — бюджет берётся из неё. */
    private static final long BUDGET = 10000L;

    @Autowired
    private LegitimacyService legitimacyService;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private LegitimacyEventRepository eventRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private LeaderMandateRepository mandateRepository;

    @Autowired
    private ReputationEntryRepository reputationRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Community community;
    private final List<User> members = new ArrayList<>();
    private UUID proposalId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        members.clear();
        community = communityRepository.save(Community.create("Маяк"));
        for (int i = 0; i < MEMBERS; i++) {
            members.add(member("member" + i, "Член " + i));
        }
        proposalId = proposalService.create(members.get(0).getId(),
                new CreateProposalRequest("Аренда помещения", "Описание", 12000L)).id();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("После «делаем» лидером становится кандидат с наибольшей поддержкой (BR-LIFE-5)")
    void acceptingProposalElectsLeader() {
        User boris = members.get(1);
        User veronika = members.get(2);
        nominate(boris);
        nominate(veronika);
        delegate(members.get(3), boris);
        delegate(members.get(4), boris);
        delegate(members.get(5), veronika);

        acceptProposal();

        LegitimacyBoardResponse board = board();
        assertThat(board.leader()).isNotNull();
        assertThat(board.leader().memberId()).isEqualTo(boris.getId());
        assertThat(board.leader().support()).isEqualTo(2);
        assertThat(board.leader().active()).isTrue();
    }

    @Test
    @DisplayName("Кандидат без поддержки лидером не становится (BR-LEGIT-4)")
    void candidateWithoutSupportIsNotElected() {
        nominate(members.get(1));

        acceptProposal();

        assertThat(stage()).isEqualTo(ProposalStage.IN_WORK);
        assertThat(board().leader())
                .as("мандат без доверия сообщества — это назначение, а не выборы")
                .isNull();
    }

    @Test
    @DisplayName("Тай-брейкер 1: больше легитимности важнее репутации (BR-LEADER-4)")
    void supportBeatsReputation() {
        User boris = members.get(1);
        User veronika = members.get(2);
        nominate(boris);
        nominate(veronika);
        addReputation(veronika, 50);
        delegate(members.get(3), boris);
        delegate(members.get(4), boris);
        delegate(members.get(5), veronika);

        acceptProposal();

        assertThat(board().leader().memberId())
                .as("репутация — второй тай-брейкер, а не первый")
                .isEqualTo(boris.getId());
    }

    @Test
    @DisplayName("Тай-брейкер 2: при равной легитимности решает репутация (BR-REP-4)")
    void reputationBreaksTieOnEqualSupport() {
        User boris = members.get(1);
        User veronika = members.get(2);
        nominate(boris);
        nominate(veronika);
        delegate(members.get(3), boris);
        delegate(members.get(4), veronika);
        addReputation(veronika, 12);

        acceptProposal();

        assertThat(board().leader().memberId()).isEqualTo(veronika.getId());
    }

    @Test
    @DisplayName("Тай-брейкер 3: при равной репутации решает дата вступления (BR-LEADER-4)")
    void joinedAtBreaksTieOnEqualReputation() {
        User early = members.get(1);
        User late = members.get(2);
        // Даты проставляем явно: иначе оба «вступили сейчас», и порядок зависел
        // бы от миллисекунд, а не от правила.
        setJoinedAt(early, Instant.parse("2025-01-01T00:00:00Z"));
        setJoinedAt(late, Instant.parse("2026-01-01T00:00:00Z"));
        nominate(early);
        nominate(late);
        delegate(members.get(3), early);
        delegate(members.get(4), late);

        acceptProposal();

        assertThat(board().leader().memberId())
                .as("раньше вступил — при прочих равных становится лидером")
                .isEqualTo(early.getId());
    }

    @Test
    @DisplayName("Падение ниже 70% прекращает мандат (BR-LEADER-5, BR-LIMIT-1)")
    void losingMoreThanThirtyPercentEndsMandate() {
        User boris = members.get(1);
        nominate(boris);
        delegateOthers(boris, 1);
        acceptProposal();
        assertThat(board().leader().support()).isEqualTo(9);

        // 70% от 9 = 6.3, значит нужно 7. Осталось 7 — держимся.
        revoke(members.get(2));
        revoke(members.get(3));
        assertThat(board().leader().support()).isEqualTo(7);
        assertThat(board().leader().active()).isTrue();

        // Осталось 6 — это ниже порога.
        revoke(members.get(4));

        LeaderMandate mandate = mandateRepository.findAllForProposal(
                proposalRepository.findById(proposalId).orElseThrow()).get(0);
        assertThat(mandate.isActive()).isFalse();
        assertThat(mandate.getEndReason()).isEqualTo(MandateEndReason.MANDATE_LOST);
    }

    @Test
    @DisplayName("Мандат сообщает, сколько поддержки нужно сохранить (BR-LEADER-5)")
    void leaderShowsRetentionTarget() {
        User boris = members.get(1);
        nominate(boris);
        delegateOthers(boris, 1);

        acceptProposal();

        LegitimacyBoardResponse board = board();
        assertThat(board.leader().initialSupport()).isEqualTo(9);
        assertThat(board.leader().requiredSupport())
                .as("70% от 9 округляется вверх до 7")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("Утрата мандата возвращает задачу на переголосование (BR-LEADER-7, BR-LIFE-10)")
    void mandateLossReturnsTaskToRevote() {
        User boris = members.get(1);
        nominate(boris);
        delegateOthers(boris, 1);
        acceptProposal();
        assertThat(stage()).isEqualTo(ProposalStage.IN_WORK);

        for (int i = 2; i < MEMBERS; i++) {
            revoke(members.get(i));
        }

        board();

        assertThat(stage())
                .as("сообщество заново решает, продолжать ли задачу")
                .isEqualTo(ProposalStage.DISCUSSION);
    }

    @Test
    @DisplayName("При переголосовании прежние голоса не переносятся (BR-LEADER-7)")
    void revoteStartsFromEmptyVotes() {
        User boris = members.get(1);
        nominate(boris);
        delegateOthers(boris, 1);
        acceptProposal();

        for (int i = 2; i < MEMBERS; i++) {
            revoke(members.get(i));
        }
        board();

        ProposalResponse proposal = proposalService.get(members.get(0).getId(), proposalId);
        assertThat(proposal.votesFor())
                .as("голоса прошлого круга не должны решать новый вопрос")
                .isZero();
        assertThat(proposal.votesAgainst()).isZero();
    }

    @Test
    @DisplayName("После переголосования выбирается лидер по новой поддержке (BR-LEGIT-4)")
    void revoteElectsLeaderFromNewSupport() {
        User boris = members.get(1);
        User veronika = members.get(2);
        nominate(boris);
        nominate(veronika);
        delegateOthers(boris, 1);
        acceptProposal();
        assertThat(board().leader().memberId()).isEqualTo(boris.getId());

        // Отзываем всё: мандат утрачен, задача вернулась к обсуждению.
        for (int i = 2; i < MEMBERS; i++) {
            revoke(members.get(i));
        }
        board();
        assertThat(stage()).isEqualTo(ProposalStage.DISCUSSION);

        // Второй круг: поддержка уже у другого кандидата.
        delegateOthers(veronika, 2);
        acceptProposal();

        LegitimacyBoardResponse board = board();
        assertThat(board.leader().memberId()).isEqualTo(veronika.getId());
        assertThat(board.leader().support()).isEqualTo(9);
        assertThat(board.leader().active()).isTrue();
    }

    @Test
    @DisplayName("Утрата мандата объясняется причиной (BR-LEADER-6)")
    void mandateLossHasReason() {
        User boris = members.get(1);
        nominate(boris);
        delegateOthers(boris, 1);
        acceptProposal();

        for (int i = 2; i < MEMBERS; i++) {
            revoke(members.get(i));
        }

        LegitimacyBoardResponse board = board();
        assertThat(board.leader()).isNotNull();
        assertThat(board.leader().memberId()).isEqualTo(boris.getId());
        assertThat(board.leader().active()).isFalse();
        assertThat(board.leader().endReason()).isEqualTo(MandateEndReason.MANDATE_LOST);
    }

    @Test
    @DisplayName("Выдвигаться можно только пока идёт обсуждение (BR-LEADER-1)")
    void cannotNominateAfterDecision() {
        nominate(members.get(1));
        delegate(members.get(2), members.get(1));
        acceptProposal();

        assertThatThrownBy(() -> legitimacyService.nominate(
                members.get(3).getId(), proposalId, BUDGET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("голосование по предложению закрыто");
    }

    @Test
    @DisplayName("Повторное выдвижение не создаёт второго кандидата (BR-LEADER-1)")
    void nominationIsIdempotent() {
        nominate(members.get(1));
        nominate(members.get(1));

        assertThat(candidateRepository.findAllForProposal(
                proposalRepository.findById(proposalId).orElseThrow())).hasSize(1);
    }

    @Test
    @DisplayName("Бюджет задачи — цена победившего кандидата, а не потолок (BR-LIFE-6, BR-BUDGET-8)")
    void taskBudgetComesFromWinnerPrice() {
        User boris = members.get(1);
        nominate(boris);
        delegate(members.get(2), boris);
        delegate(members.get(3), boris);
        acceptProposal();

        Proposal proposal = proposalRepository.findById(proposalId).orElseThrow();
        assertThat(proposal.getBudget())
                .as("потолок не меняется: его утвердило сообщество")
                .isEqualTo(12000L);
        assertThat(proposal.getTaskBudget())
                .as("лидер работает за свою цену внутри потолка")
                .isEqualTo(BUDGET);
    }

    @Test
    @DisplayName("Кандидат не может назвать цену выше потолка (BR-BUDGET-8)")
    void candidatePriceCannotExceedCeiling() {
        assertThatThrownBy(() -> legitimacyService.nominate(
                members.get(1).getId(), proposalId, 12001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("больше потолка");
    }

    @Test
    @DisplayName("Кандидат без цены не выдвигается (BR-BUDGET-8)")
    void candidateMustNamePrice() {
        assertThatThrownBy(() -> legitimacyService.nominate(
                members.get(1).getId(), proposalId, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("цену больше нуля");
    }

    @Test
    @DisplayName("Передумать о цене можно, пока идёт обсуждение (BR-BUDGET-8)")
    void candidateCanChangePriceBeforeDecision() {
        User boris = members.get(1);
        nominate(boris);
        legitimacyService.nominate(boris.getId(), proposalId, 4000L);

        assertThat(candidateRepository.findForProposalAndMember(
                proposalRepository.findById(proposalId).orElseThrow(), boris.getId())
                .orElseThrow().getPrice()).isEqualTo(4000L);
    }

    @Test
    @DisplayName("Утрата мандата сбрасывает бюджет задачи, новый лидер приносит свой (BR-BUDGET-7)")
    void mandateLossResetsTaskBudget() {
        User boris = members.get(1);
        nominate(boris);
        delegateOthers(boris, 1);
        acceptProposal();

        assertThat(proposalRepository.findById(proposalId).orElseThrow().getTaskBudget())
                .isEqualTo(BUDGET);

        // Отзываем доверие почти у всех: остаётся меньше 70% — мандат утрачен.
        for (int i = 2; i < MEMBERS; i++) {
            revoke(members.get(i));
        }

        assertThat(proposalRepository.findById(proposalId).orElseThrow().getTaskBudget())
                .as("бюджет принадлежал прежнему лидеру и сброшен")
                .isNull();
    }

    /**
     * Голосует «делаем» столькими голосами, сколько нужно для досрочного
     * решения в сообществе из десяти человек.
     */
    private void acceptProposal() {
        for (int i = 0; i < VOTES_TO_ACCEPT; i++) {
            proposalService.vote(members.get(i).getId(), proposalId, true);
        }
    }

    private void nominate(User user) {
        legitimacyService.nominate(user.getId(), proposalId, BUDGET);
    }

    private void delegate(User from, User to) {
        legitimacyService.delegate(from.getId(), proposalId, to.getId());
    }

    /**
     * Передаёт доверие кандидату от всех, кроме него самого: себе передавать
     * нечего ({@code BR-LEGIT-2}).
     */
    private void delegateOthers(User candidate, int candidateIndex) {
        for (int i = 0; i < MEMBERS; i++) {
            if (i != candidateIndex) {
                delegate(members.get(i), candidate);
            }
        }
    }

    private void revoke(User from) {
        legitimacyService.revoke(from.getId(), proposalId);
    }

    private LegitimacyBoardResponse board() {
        return legitimacyService.board(members.get(0).getId(), proposalId);
    }

    private ProposalStage stage() {
        return proposalRepository.findById(proposalId).orElseThrow().getStage();
    }

    private void addReputation(User member, long points) {
        reputationRepository.save(ReputationEntry.forClosedTask(
                member,
                proposalRepository.findById(proposalId).orElseThrow(),
                points,
                Instant.now()));
    }

    private void setJoinedAt(User member, Instant joinedAt) {
        jdbcTemplate.update("update app_user set joined_at = ? where id = ?",
                java.sql.Timestamp.from(joinedAt), member.getId());
    }

    private User member(String login, String displayName) {
        User user = User.register(login, "hash", displayName);
        user.join(community);
        return userRepository.save(user);
    }

    private void cleanDatabase() {
        reputationRepository.deleteAll();
        mandateRepository.deleteAll();
        candidateRepository.deleteAll();
        eventRepository.deleteAll();
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

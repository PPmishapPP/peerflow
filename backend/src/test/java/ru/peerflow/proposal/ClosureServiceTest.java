package ru.peerflow.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
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
import ru.peerflow.legitimacy.service.NotLeaderException;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.domain.ProposalStage;
import ru.peerflow.proposal.repo.ClosureVoteRepository;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ClosureService;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.ClosureStateResponse;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.report.repo.AttachmentRepository;
import ru.peerflow.report.repo.BudgetExpenseRepository;
import ru.peerflow.reputation.repo.ReputationEntryRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Закрытие задачи и переход легитимности в репутацию ({@code BR-LIFE-8},
 * {@code BR-LIFE-9}, {@code BR-VOTE-5}, {@code BR-OVER-9}, {@code BR-REP-1}).
 *
 * <p>Сообщество из трёх человек: 30% — это один голос, но досрочка требует
 * минимум двух ({@code BR-VOTE-11}), поэтому «два голоса за» — ровно та
 * граница, на которой решение принимается.
 */
@SpringBootTest
class ClosureServiceTest extends PostgresTestBase {

    private static final int MEMBERS = 3;

    /** Цена лидера: чуть ниже потолка — бюджет задачи берётся из неё. */
    private static final long BUDGET = 10000L;

    @Autowired
    private ClosureService closureService;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private LegitimacyService legitimacyService;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ClosureVoteRepository closureVoteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private LegitimacyEventRepository eventRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private LeaderMandateRepository mandateRepository;

    @Autowired
    private ReputationEntryRepository reputationRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private BudgetExpenseRepository expenseRepository;

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
                new CreateProposalRequest("Печать листовок", "Описание", 12000L)).id();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Лидер запускает закрытие: голосование открыто (BR-LIFE-8)")
    void leaderOpensClosure() {
        User leader = electLeader();

        ClosureStateResponse state = closureService.open(leader.getId(), proposalId);

        assertThat(state.open()).isTrue();
        assertThat(state.deadline()).isNotNull();
        assertThat(state.leaderId()).isEqualTo(leader.getId());
        assertThat(state.status()).contains("ждём голоса");
    }

    @Test
    @DisplayName("Не лидер не может запустить закрытие (BR-LIFE-8)")
    void nonLeaderCannotOpenClosure() {
        electLeader();

        assertThatThrownBy(() -> closureService.open(members.get(0).getId(), proposalId))
                .isInstanceOf(NotLeaderException.class);
        assertThat(proposal().isClosureOpen()).isFalse();
    }

    @Test
    @DisplayName("Повторный запуск закрытия запрещён (BR-LIFE-8)")
    void closureCannotBeOpenedTwice() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);

        assertThatThrownBy(() -> closureService.open(leader.getId(), proposalId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже запущена");
    }

    @Test
    @DisplayName("Голос «выполнено» учитывается и возвращается человеку (BR-VOTE-5)")
    void closureVoteIsCounted() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);

        ClosureStateResponse state = closureService.vote(
                members.get(0).getId(), proposalId, true);

        assertThat(state.votesFor()).isEqualTo(1);
        assertThat(state.votesAgainst()).isZero();
        assertThat(state.myVote()).isNotNull();
        assertThat(state.myVote().completed()).isTrue();
    }

    @Test
    @DisplayName("Свой ответ можно поменять, пока голосование не закрыто (BR-VOTE-13)")
    void closureVoteCanBeChanged() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);

        closureService.vote(members.get(0).getId(), proposalId, false);
        ClosureStateResponse state = closureService.vote(
                members.get(0).getId(), proposalId, true);

        assertThat(state.votesFor()).isEqualTo(1);
        assertThat(state.votesAgainst())
                .as("передумать — это заменить ответ, а не добавить второй")
                .isZero();
        assertThat(closureVoteRepository.findAllForProposal(proposal())).hasSize(1);
    }

    @Test
    @DisplayName("Голос без запущенной процедуры запрещён (BR-LIFE-8)")
    void cannotVoteWithoutOpenClosure() {
        electLeader();

        assertThatThrownBy(() -> closureService.vote(members.get(0).getId(), proposalId, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не идёт");
    }

    @Test
    @DisplayName("Два голоса «выполнено» закрывают задачу (BR-LIFE-9, BR-OVER-9)")
    void twoVotesCloseTask() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);

        closureService.vote(members.get(0).getId(), proposalId, true);
        ClosureStateResponse state = closureService.vote(
                members.get(1).getId(), proposalId, true);

        assertThat(state.stage()).isEqualTo(ProposalStage.CLOSED);
        assertThat(state.open()).isFalse();
        assertThat(state.status()).contains("задача закрыта");
    }

    @Test
    @DisplayName("При закрытии легитимность лидера переходит в репутацию (BR-REP-1)")
    void legitimacyBecomesReputationOnClose() {
        User leader = electLeader();
        // Двое из трёх передали доверие лидеру — ровно столько, сколько он
        // получил при выборах (BR-LEADER-3).
        long support = legitimacyService.standing(proposal()).supportersOf(leader.getId());
        assertThat(support).isEqualTo(2);

        closureService.open(leader.getId(), proposalId);
        closureService.vote(members.get(0).getId(), proposalId, true);
        ClosureStateResponse state = closureService.vote(
                members.get(1).getId(), proposalId, true);

        assertThat(state.reputationAwarded())
                .as("оставшаяся легитимность — это и есть начисление")
                .isEqualTo(support);
        assertThat(reputationRepository.sumForMember(leader.getId())).isEqualTo(support);
    }

    @Test
    @DisplayName("После закрытия мандат завершён с причиной TASK_CLOSED (BR-LIFE-9)")
    void closeEndsMandate() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);
        closureService.vote(members.get(0).getId(), proposalId, true);
        closureService.vote(members.get(1).getId(), proposalId, true);

        LeaderMandate mandate = mandateRepository.findAllForProposal(proposal()).get(0);
        assertThat(mandate.isActive()).isFalse();
        assertThat(mandate.getEndReason()).isEqualTo(MandateEndReason.TASK_CLOSED);
        assertThat(mandate.getLeader().getId()).isEqualTo(leader.getId());
    }

    @Test
    @DisplayName("Повторное закрытие невозможно: репутация не удваивается (BR-REP-1)")
    void cannotCloseTwice() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);
        closureService.vote(members.get(0).getId(), proposalId, true);
        closureService.vote(members.get(1).getId(), proposalId, true);
        long awarded = reputationRepository.sumForMember(leader.getId());

        assertThatThrownBy(() -> closureService.open(leader.getId(), proposalId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(closureService.state(leader.getId(), proposalId).reputationAwarded())
                .isEqualTo(awarded);
        assertThat(reputationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("«Не выполнено» оставляет задачу в работе и очищает голоса (BR-OVER-9)")
    void rejectedClosureKeepsTaskInWork() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);

        closureService.vote(members.get(0).getId(), proposalId, false);
        ClosureStateResponse state = closureService.vote(
                members.get(1).getId(), proposalId, false);

        assertThat(state.stage()).isEqualTo(ProposalStage.IN_WORK);
        assertThat(state.open()).isFalse();
        assertThat(state.votesFor())
                .as("следующая попытка начинается с чистого листа")
                .isZero();
        assertThat(state.votesAgainst()).isZero();
        assertThat(reputationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("После «не выполнено» лидер может запустить процедуру заново (BR-LIFE-8)")
    void closureCanBeRetried() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);
        closureService.vote(members.get(0).getId(), proposalId, false);
        closureService.vote(members.get(1).getId(), proposalId, false);

        ClosureStateResponse state = closureService.open(leader.getId(), proposalId);

        assertThat(state.open()).isTrue();
        assertThat(state.votesFor()).isZero();
    }

    @Test
    @DisplayName("По истечении срока задача закрывается большинством (BR-VOTE-10, BR-LIFE-9)")
    void expiredClosureIsClosedByMajority() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);
        // Один голос «за»: для решения этого мало (досрочке нужны два голоса,
        // BR-VOTE-11), но по истечении срока большинство проголосовавших
        // решает (BR-VOTE-10).
        closureService.vote(members.get(0).getId(), proposalId, true);

        // Сдвигаем начало голосования в прошлое, чтобы истёк срок.
        jdbcTemplate.update("update proposal set closure_opened_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(3))),
                proposalId);

        ClosureStateResponse state = closureService.state(members.get(2).getId(), proposalId);

        assertThat(state.stage()).isEqualTo(ProposalStage.CLOSED);
    }

    @Test
    @DisplayName("Голосование закрыто для тех, кто ещё не член сообщества (BR-PRIV-1)")
    void nonMemberCannotVote() {
        User leader = electLeader();
        closureService.open(leader.getId(), proposalId);
        User waiting = User.register("waiting", "hash", "Ожидающий");
        userRepository.save(waiting);

        assertThatThrownBy(() -> closureService.vote(waiting.getId(), proposalId, true))
                .isInstanceOf(ru.peerflow.community.service.MembershipRequiredException.class);
    }

    /**
     * Проводит задачу в работу и выбирает лидером третьего члена: два голоса
     * «за» из трёх дают досрочное решение, поддержка двух — мандат.
     */
    private User electLeader() {
        User leader = members.get(2);
        legitimacyService.nominate(leader.getId(), proposalId, BUDGET);
        legitimacyService.delegate(members.get(0).getId(), proposalId, leader.getId());
        legitimacyService.delegate(members.get(1).getId(), proposalId, leader.getId());
        proposalService.vote(members.get(0).getId(), proposalId, true);
        proposalService.vote(members.get(1).getId(), proposalId, true);
        return leader;
    }

    private Proposal proposal() {
        return proposalRepository.findById(proposalId).orElseThrow();
    }

    private User member(String login, String displayName) {
        User user = User.register(login, "hash", displayName);
        user.join(community);
        return userRepository.save(user);
    }

    private void cleanDatabase() {
        expenseRepository.deleteAll();
        attachmentRepository.deleteAll();
        closureVoteRepository.deleteAll();
        reputationRepository.deleteAll();
        mandateRepository.deleteAll();
        candidateRepository.deleteAll();
        eventRepository.deleteAll();
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

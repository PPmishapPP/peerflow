package ru.peerflow.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ru.peerflow.auth.service.TokenService;
import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.discussion.service.DiscussionService;
import ru.peerflow.discussion.web.dto.PostMessageRequest;
import ru.peerflow.legitimacy.repo.CandidateRepository;
import ru.peerflow.legitimacy.repo.LeaderMandateRepository;
import ru.peerflow.legitimacy.repo.LegitimacyEventRepository;
import ru.peerflow.proposal.repo.ClosureVoteRepository;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.report.repo.AttachmentRepository;
import ru.peerflow.report.repo.BudgetExpenseRepository;
import ru.peerflow.reputation.repo.ReputationEntryRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Поток событий {@code /api/events} ({@code BR-DISC-1}, {@code BR-VOTE-4},
 * {@code BR-LIFE-7}, {@code BR-PRIV-1}).
 *
 * <p>Проверяется контракт с клиентом: поток открыт только члену сообщества,
 * первое событие приходит сразу, а события несут вид изменения и задачу, чтобы
 * клиент знал, что перезапросить. Содержимое задачи в события не кладётся —
 * поэтому утечки данных другого сообщества здесь быть не может, но сама
 * подписка на чужое сообщество должна быть невозможна.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventStreamTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private DiscussionService discussionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ClosureVoteRepository closureVoteRepository;

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

    private Community community;
    private final List<User> members = new ArrayList<>();
    private UUID proposalId;

    /** Открытые в тесте потоки; закрываем их, чтобы подписки не копились. */
    private final List<MvcResult> streams = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanDatabase();
        members.clear();
        streams.clear();
        community = communityRepository.save(Community.create("Маяк"));
        for (int i = 0; i < 2; i++) {
            members.add(member("member" + i, "Член " + i));
        }
        proposalId = proposalService.create(members.get(0).getId(),
                new CreateProposalRequest("Печать листовок", "Описание", 12000L)).id();
    }

    @AfterEach
    void tearDown() {
        for (MvcResult result : streams) {
            result.getRequest().getAsyncContext().complete();
        }
        cleanDatabase();
    }

    @Test
    @DisplayName("Поток открыт, первое событие — ready (BR-DISC-1)")
    void streamIsOpenedWithReadyEvent() throws Exception {
        MvcResult result = subscribe(members.get(0));

        assertThat(result.getResponse().getContentAsString())
                .as("клиент должен узнать, что поток действительно открыт")
                .contains("event:ready");
    }

    @Test
    @DisplayName("Новое сообщение приходит подписчику (BR-DISC-1)")
    void postedMessageIsDelivered() throws Exception {
        MvcResult result = subscribe(members.get(1));

        discussionService.post(
                members.get(0).getId(),
                proposalId,
                new PostMessageRequest("Готово, листовки напечатаны", null));

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:MESSAGE_POSTED");
        assertThat(body)
                .as("клиенту нужен идентификатор задачи, чтобы знать, что перезапросить")
                .contains(proposalId.toString());
    }

    @Test
    @DisplayName("Голос меняет счётчики и приходит событием (BR-VOTE-4)")
    void voteChangeIsDelivered() throws Exception {
        MvcResult result = subscribe(members.get(1));

        proposalService.vote(members.get(0).getId(), proposalId, true);

        assertThat(result.getResponse().getContentAsString())
                .contains("event:VOTE_CHANGED")
                .contains(proposalId.toString());
    }

    @Test
    @DisplayName("Чужое сообщество не получает событий (BR-PRIV-1)")
    void eventsDoNotLeakBetweenCommunities() throws Exception {
        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        MvcResult result = subscribe(stranger);
        proposalService.vote(members.get(0).getId(), proposalId, true);

        assertThat(result.getResponse().getContentAsString())
                .as("в потоке чужого сообщества не должно быть ни одного события задачи")
                .doesNotContain("VOTE_CHANGED")
                .doesNotContain(proposalId.toString());
        assertThat(publisher.subscriberCount(community)).isZero();
        assertThat(publisher.subscriberCount(other)).isEqualTo(1);
    }

    @Test
    @DisplayName("Не член сообщества не может подписаться: 403 (BR-PRIV-1)")
    void nonMemberCannotSubscribe() throws Exception {
        User pending = User.register("pending", "hash", "Ожидающий");
        userRepository.save(pending);

        mockMvc.perform(get("/api/events")
                        .header("Authorization", bearer(pending)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Без токена поток недоступен (BR-PRIV-1)")
    void subscriptionRequiresToken() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Отписка убирает поток из рассылки")
    void completedStreamIsRemoved() throws Exception {
        MvcResult result = subscribe(members.get(0));
        assertThat(publisher.subscriberCount(community)).isEqualTo(1);

        result.getRequest().getAsyncContext().complete();
        streams.clear();

        assertThat(publisher.subscriberCount(community)).isZero();
    }

    private MvcResult subscribe(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/events")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        streams.add(result);
        return result;
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.issue(user).value();
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
        messageRepository.deleteAll();
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

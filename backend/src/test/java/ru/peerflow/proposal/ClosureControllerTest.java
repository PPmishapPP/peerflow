package ru.peerflow.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import ru.peerflow.auth.service.TokenService;
import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.legitimacy.repo.CandidateRepository;
import ru.peerflow.legitimacy.repo.LeaderMandateRepository;
import ru.peerflow.legitimacy.repo.LegitimacyEventRepository;
import ru.peerflow.legitimacy.service.LegitimacyService;
import ru.peerflow.proposal.repo.ClosureVoteRepository;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.report.repo.AttachmentRepository;
import ru.peerflow.report.repo.BudgetExpenseRepository;
import ru.peerflow.reputation.repo.ReputationEntryRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Сквозные тесты закрытия задачи через HTTP ({@code BR-LIFE-8},
 * {@code BR-LIFE-9}, {@code BR-VOTE-5}, {@code BR-PRIV-1}).
 *
 * <p>Проверяется контракт, который видит клиент. Правила голосования, срока и
 * перехода в репутацию проверены в {@link ClosureServiceTest} — дублировать их
 * через HTTP незачем.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClosureControllerTest extends PostgresTestBase {

    /** Цена лидера: чуть ниже потолка — бюджет задачи берётся из неё. */
    private static final long BUDGET = 10000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private LegitimacyService legitimacyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private ProposalRepository proposalRepository;

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

    @BeforeEach
    void setUp() {
        cleanDatabase();
        members.clear();
        community = communityRepository.save(Community.create("Маяк"));
        for (int i = 0; i < 3; i++) {
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
    @DisplayName("Лидер запускает закрытие: 201 и открытое голосование (BR-LIFE-8)")
    void leaderOpensClosure() throws Exception {
        User leader = electLeader();

        JsonNode state = json(mockMvc.perform(
                        post("/api/proposals/{id}/closure", proposalId)
                                .header("Authorization", bearer(leader)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse());

        assertThat(state.path("open").asBoolean()).isTrue();
        assertThat(state.path("stage").asText()).isEqualTo("IN_WORK");
        assertThat(state.path("leaderId").asText()).isEqualTo(leader.getId().toString());
    }

    @Test
    @DisplayName("Не лидер не может запустить закрытие: 403 (BR-LIFE-8)")
    void nonLeaderCannotOpenClosure() throws Exception {
        electLeader();

        mockMvc.perform(post("/api/proposals/{id}/closure", proposalId)
                        .header("Authorization", bearer(members.get(0))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Голос «выполнено»: счётчик и свой ответ в ответе (BR-VOTE-5)")
    void closureVoteIsReturned() throws Exception {
        User leader = electLeader();
        mockMvc.perform(post("/api/proposals/{id}/closure", proposalId)
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isCreated());

        JsonNode state = json(mockMvc.perform(
                        post("/api/proposals/{id}/closure/votes", proposalId)
                                .header("Authorization", bearer(members.get(0)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"completed": true}
                                        """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());

        assertThat(state.path("votesFor").asLong()).isEqualTo(1);
        assertThat(state.path("myVote").path("completed").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Голос без поля completed — 400 (BR-VOTE-5)")
    void voteWithoutValueIsBadRequest() throws Exception {
        User leader = electLeader();
        mockMvc.perform(post("/api/proposals/{id}/closure", proposalId)
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/proposals/{id}/closure/votes", proposalId)
                        .header("Authorization", bearer(members.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Решение сообщества закрывает задачу и показывает репутацию (BR-REP-1)")
    void votesCloseTaskAndAwardReputation() throws Exception {
        User leader = electLeader();
        mockMvc.perform(post("/api/proposals/{id}/closure", proposalId)
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isCreated());

        vote(members.get(0), true);
        JsonNode state = vote(members.get(1), true);

        assertThat(state.path("stage").asText()).isEqualTo("CLOSED");
        assertThat(state.path("reputationAwarded").asLong())
                .as("легитимность лидера стала репутацией")
                .isEqualTo(2);
        assertThat(state.path("status").asText()).contains("задача закрыта");
    }

    @Test
    @DisplayName("Повторный запуск закрытия — 409 (BR-LIFE-8)")
    void openingTwiceIsConflict() throws Exception {
        User leader = electLeader();
        mockMvc.perform(post("/api/proposals/{id}/closure", proposalId)
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/proposals/{id}/closure", proposalId)
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Чужое сообщество не видит голосование: 404 (BR-PRIV-1)")
    void closureOfOtherCommunityIsHidden() throws Exception {
        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        mockMvc.perform(get("/api/proposals/{id}/closure", proposalId)
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Без токена голосование недоступно (BR-PRIV-1)")
    void closureRequiresToken() throws Exception {
        mockMvc.perform(get("/api/proposals/{id}/closure", proposalId))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode vote(User voter, boolean completed) throws Exception {
        return json(mockMvc.perform(
                        post("/api/proposals/{id}/closure/votes", proposalId)
                                .header("Authorization", bearer(voter))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"completed\": " + completed + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());
    }

    private User electLeader() {
        User leader = members.get(2);
        legitimacyService.nominate(leader.getId(), proposalId, BUDGET);
        legitimacyService.delegate(members.get(0).getId(), proposalId, leader.getId());
        legitimacyService.delegate(members.get(1).getId(), proposalId, leader.getId());
        proposalService.vote(members.get(0).getId(), proposalId, true);
        proposalService.vote(members.get(1).getId(), proposalId, true);
        return leader;
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsByteArray());
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
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

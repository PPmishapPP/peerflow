package ru.peerflow.legitimacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ru.peerflow.auth.service.TokenService;
import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.legitimacy.repo.CandidateRepository;
import ru.peerflow.legitimacy.repo.LeaderMandateRepository;
import ru.peerflow.legitimacy.repo.LegitimacyEventRepository;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.reputation.repo.ReputationEntryRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Сквозные тесты легитимности через HTTP ({@code BR-LEGIT-2}–{@code BR-LEGIT-5},
 * {@code BR-LEADER-1}, {@code BR-PRIV-1}).
 *
 * <p>Проверяется то, что видит клиент: коды ответа, форма тела и закрытость
 * точек. Выбор лидера и порог удержания здесь не дублируются — они проверены в
 * {@link LeaderElectionTest} на домене, где это дешевле и точнее.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LegitimacyControllerTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private LegitimacyEventRepository eventRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private LeaderMandateRepository mandateRepository;

    @Autowired
    private ReputationEntryRepository reputationRepository;

    private Community community;
    private User anna;
    private User boris;
    private UUID proposalId;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        community = communityRepository.save(Community.create("Маяк"));
        anna = member("anna", "Анна");
        boris = member("boris", "Борис");
        proposalId = createProposal();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Доска легитимности: пустые кандидаты и нулевая передача (BR-LEGIT-2)")
    void emptyBoard() throws Exception {
        JsonNode board = json(mockMvc.perform(get("/api/proposals/{id}/legitimacy", proposalId)
                        .header("Authorization", bearer(anna)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());

        assertThat(board.path("candidates")).isEmpty();
        assertThat(board.path("total").asLong()).isZero();
        assertThat(board.path("members").asLong()).isEqualTo(2);
        assertThat(board.path("leader").isNull()).isTrue();
        assertThat(board.path("myDelegation").isNull()).isTrue();
    }

    @Test
    @DisplayName("Выдвижение: 201 и кандидат в ответе (BR-LEADER-1)")
    void nominateAddsCandidate() throws Exception {
        JsonNode board = json(mockMvc.perform(
                        post("/api/proposals/{id}/legitimacy/candidates", proposalId)
                                .header("Authorization", bearer(boris))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\": 9000}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse());

        assertThat(board.path("candidates")).hasSize(1);
        assertThat(board.path("candidates").get(0).path("memberId").asText())
                .isEqualTo(boris.getId().toString());
        assertThat(board.path("candidates").get(0).path("price").asLong())
                .as("цена кандидата видна до голосования (BR-BUDGET-9)")
                .isEqualTo(9000L);
        assertThat(board.path("candidates").get(0).path("me").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Выдвижение без цены — 400 (BR-BUDGET-8)")
    void nominateRequiresPrice() throws Exception {
        mockMvc.perform(post("/api/proposals/{id}/legitimacy/candidates", proposalId)
                        .header("Authorization", bearer(boris))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Цена выше потолка — 400 (BR-BUDGET-8)")
    void nominateRejectsPriceAboveCeiling() throws Exception {
        mockMvc.perform(post("/api/proposals/{id}/legitimacy/candidates", proposalId)
                        .header("Authorization", bearer(boris))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 12001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Передача легитимности: 200 и myDelegation в ответе (BR-LEGIT-2)")
    void delegateSetsMyDelegation() throws Exception {
        nominate(boris);

        JsonNode board = json(mockMvc.perform(
                        post("/api/proposals/{id}/legitimacy/{candidate}",
                                proposalId, boris.getId())
                                .header("Authorization", bearer(anna)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());

        assertThat(board.path("myDelegation").asText()).isEqualTo(boris.getId().toString());
        assertThat(board.path("total").asLong()).isEqualTo(1);
        assertThat(board.path("candidates").get(0).path("support").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("Отзыв легитимности: 200 и пустая передача (BR-LEGIT-5)")
    void revokeClearsDelegation() throws Exception {
        nominate(boris);
        delegate(anna, boris);

        JsonNode board = json(mockMvc.perform(
                        delete("/api/proposals/{id}/legitimacy", proposalId)
                                .header("Authorization", bearer(anna)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());

        assertThat(board.path("myDelegation").isNull()).isTrue();
        assertThat(board.path("total").asLong()).isZero();
        assertThat(board.path("candidates").get(0).path("support").asLong()).isZero();
    }

    @Test
    @DisplayName("Передать легитимность себе — 400 (BR-LEGIT-2)")
    void cannotDelegateToSelf() throws Exception {
        mockMvc.perform(post("/api/proposals/{id}/legitimacy/{candidate}",
                        proposalId, anna.getId())
                        .header("Authorization", bearer(anna)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Чужое сообщество не видно: 404 (BR-PRIV-1)")
    void otherCommunityIsHidden() throws Exception {
        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        mockMvc.perform(get("/api/proposals/{id}/legitimacy", proposalId)
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/proposals/{id}/legitimacy/{candidate}",
                        proposalId, boris.getId())
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Без токена доска недоступна (BR-PRIV-1)")
    void boardRequiresToken() throws Exception {
        mockMvc.perform(get("/api/proposals/{id}/legitimacy", proposalId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Профиль: передачи доверия и репутация (BR-LEGIT-3, BR-REP-2)")
    void myLegitimacyShowsDelegationsAndReputation() throws Exception {
        nominate(boris);
        delegate(anna, boris);

        JsonNode profile = json(mockMvc.perform(get("/api/me/legitimacy")
                        .header("Authorization", bearer(anna)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());

        assertThat(profile.path("reputation").asLong()).isZero();
        assertThat(profile.path("delegations")).hasSize(1);
        JsonNode row = profile.path("delegations").get(0);
        assertThat(row.path("proposalId").asText()).isEqualTo(proposalId.toString());
        assertThat(row.path("proposalTitle").asText()).isEqualTo("Аренда помещения");
        assertThat(row.path("candidateId").asText()).isEqualTo(boris.getId().toString());
        assertThat(row.path("candidateName").asText()).isEqualTo("Борис");
    }

    @Test
    @DisplayName("Профиль: задача без переданной легитимности тоже видна (BR-LEGIT-5)")
    void myLegitimacyShowsProposalsWithOwnLegitimacy() throws Exception {
        JsonNode profile = json(mockMvc.perform(get("/api/me/legitimacy")
                        .header("Authorization", bearer(anna)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());

        assertThat(profile.path("delegations")).hasSize(1);
        JsonNode row = profile.path("delegations").get(0);
        assertThat(row.path("proposalId").asText()).isEqualTo(proposalId.toString());
        assertThat(row.path("candidateId").isNull())
                .as("доверие у себя — это состояние, а не отсутствие строки")
                .isTrue();
        assertThat(row.path("candidateName").isNull()).isTrue();
    }

    @Test
    @DisplayName("Профиль: легитимность разных задач не смешивается (BR-LEGIT-3)")
    void myLegitimacyKeepsProposalsSeparate() throws Exception {
        UUID otherProposal = anotherProposal();
        nominate(boris);
        delegate(anna, boris);
        mockMvc.perform(post("/api/proposals/{id}/legitimacy/{candidate}",
                        otherProposal, anna.getId())
                        .header("Authorization", bearer(boris)))
                .andExpect(status().isOk());

        JsonNode profile = json(mockMvc.perform(get("/api/me/legitimacy")
                        .header("Authorization", bearer(anna)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse());

        Map<String, String> byProposal = new HashMap<>();
        profile.path("delegations").forEach(row -> byProposal.put(
                row.path("proposalId").asText(),
                row.path("candidateId").isNull() ? "" : row.path("candidateId").asText()));
        assertThat(byProposal.get(proposalId.toString())).isEqualTo(boris.getId().toString());
        assertThat(byProposal.get(otherProposal.toString()))
                .as("по другой задаче доверие осталось у себя")
                .isEmpty();
    }

    @Test
    @DisplayName("Профиль легитимности закрыт без токена (BR-PRIV-1)")
    void myLegitimacyRequiresToken() throws Exception {
        mockMvc.perform(get("/api/me/legitimacy"))
                .andExpect(status().isUnauthorized());
    }

    private UUID anotherProposal() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/proposals")
                        .header("Authorization", bearer(boris))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Второй проект", "description": "Другое", "budget": 5000}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        return UUID.fromString(json(response).path("id").asText());
    }

    private void nominate(User user) throws Exception {
        mockMvc.perform(post("/api/proposals/{id}/legitimacy/candidates", proposalId)
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 10000}"))
                .andExpect(status().isCreated());
    }

    private void delegate(User from, User to) throws Exception {
        mockMvc.perform(post("/api/proposals/{id}/legitimacy/{candidate}",
                        proposalId, to.getId())
                        .header("Authorization", bearer(from)))
                .andExpect(status().isOk());
    }

    private UUID createProposal() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/proposals")
                        .header("Authorization", bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Аренда помещения", "description": "Описание", "budget": 12000}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        return UUID.fromString(json(response).path("id").asText());
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
        reputationRepository.deleteAll();
        mandateRepository.deleteAll();
        candidateRepository.deleteAll();
        eventRepository.deleteAll();
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

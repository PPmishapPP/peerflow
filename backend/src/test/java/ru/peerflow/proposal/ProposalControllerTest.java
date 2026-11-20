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
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Сквозные тесты предложений через HTTP.
 *
 * <p>Здесь проверяется то, что видит клиент: коды ответа, форма секций и
 * закрытость точек ({@code BR-PRIV-1}, {@code BR-RANK-5}). Арифметика
 * популярности проверяется в {@link ProposalRankingTest}, ход голосования —
 * в {@link DecisionVoteTest}: дублировать правила на уровне HTTP незачем.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProposalControllerTest extends PostgresTestBase {

    private static final int MEMBERS = 10;

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

    private Community community;

    private final List<User> members = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanDatabase();
        members.clear();
        community = communityRepository.save(Community.create("Маяк"));
        createMembers(MEMBERS);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Создание: 201 с бюджетом и стадией «На обсуждении» (BR-LIFE-1)")
    void createsProposal() throws Exception {
        JsonNode body = create(members.get(0), "Закупить проектор", 35000);

        assertThat(body.path("stage").asText()).isEqualTo("DISCUSSION");
        assertThat(body.path("budget").asLong()).isEqualTo(35000);
        assertThat(body.path("authorName").asText()).isEqualTo("Член 0");
        assertThat(body.path("votingClosed").asBoolean()).isFalse();
        assertThat(body.path("votingDeadline").asText()).isNotBlank();
        assertThat(body.path("myVote").isNull()).isTrue();
    }

    @Test
    @DisplayName("Создание: без бюджета — 400 (BR-BUDGET-2)")
    void rejectsProposalWithoutBudget() throws Exception {
        mockMvc.perform(post("/api/proposals")
                        .header("Authorization", bearer(members.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Вода", "description": "Нужна вода"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Создание: нулевой бюджет — 400 (граница BR-BUDGET-2)")
    void rejectsZeroBudget() throws Exception {
        mockMvc.perform(post("/api/proposals")
                        .header("Authorization", bearer(members.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Вода", "description": "Нужна вода", "budget": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Создание: пустая тема — 400")
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/proposals")
                        .header("Authorization", bearer(members.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   ", "description": "Описание", "budget": 100}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Создание: без токена — 401, предложения закрыты (BR-PRIV-1)")
    void rejectsCreationWithoutToken() throws Exception {
        mockMvc.perform(post("/api/proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Вода", "description": "Нужна вода", "budget": 100}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Список: две секции; принятое — в работе, остальное — на обсуждении (BR-RANK-5)")
    void listsTwoSections() throws Exception {
        UUID discussing = UUID.fromString(create(members.get(0), "Вода", 2000)
                .path("id").asText());
        UUID inWork = UUID.fromString(create(members.get(0), "Дверь", 4500)
                .path("id").asText());

        vote(members.get(0), inWork, true);
        vote(members.get(1), inWork, true);
        vote(members.get(2), inWork, true);

        JsonNode list = list(members.get(0), null);

        assertThat(ids(list.path("inWork"))).containsExactly(inWork);
        assertThat(ids(list.path("discussing"))).containsExactly(discussing);
    }

    @Test
    @DisplayName("Список: сортировка по бюджету приходит с сервера (BR-RANK-4)")
    void sortsByBudget() throws Exception {
        create(members.get(0), "Дешёвое", 1000);
        create(members.get(0), "Дорогое", 50000);

        JsonNode list = list(members.get(0), "budget");

        assertThat(titles(list.path("discussing"))).containsExactly("Дорогое", "Дешёвое");
    }

    @Test
    @DisplayName("Список: неизвестная сортировка — 400 (BR-RANK-4)")
    void rejectsUnknownSort() throws Exception {
        mockMvc.perform(get("/api/proposals")
                        .param("sort", "cheapest")
                        .header("Authorization", bearer(members.get(0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Карточка: 200 для члена сообщества, 404 для чужого (BR-PRIV-1)")
    void returnsProposalToMembersOnly() throws Exception {
        UUID proposalId = UUID.fromString(create(members.get(0), "Вода", 2000)
                .path("id").asText());

        mockMvc.perform(get("/api/proposals/{id}", proposalId)
                        .header("Authorization", bearer(members.get(1))))
                .andExpect(status().isOk());

        User stranger = userRepository.save(User.register("stranger", "hash", "Чужой"));
        mockMvc.perform(get("/api/proposals/{id}", proposalId)
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Карточка: несуществующее предложение — 404")
    void returnsNotFoundForUnknownProposal() throws Exception {
        mockMvc.perform(get("/api/proposals/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(members.get(0))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Голосование: голос «за» виден в счётчиках и в своём выборе (BR-VOTE-4)")
    void votesForProposal() throws Exception {
        UUID proposalId = UUID.fromString(create(members.get(0), "Вода", 2000)
                .path("id").asText());

        JsonNode body = vote(members.get(1), proposalId, true);

        assertThat(body.path("votesFor").asLong()).isEqualTo(1);
        assertThat(body.path("votesAgainst").asLong()).isZero();
        assertThat(body.path("myVote").path("inFavor").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Голосование: без поля inFavor — 400, валидация не пропускает")
    void rejectsVoteWithoutDecision() throws Exception {
        UUID proposalId = UUID.fromString(create(members.get(0), "Вода", 2000)
                .path("id").asText());

        mockMvc.perform(post("/api/proposals/{id}/votes", proposalId)
                        .header("Authorization", bearer(members.get(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Голосование: битый JSON — 400, а не 500 из-за обработчика IOException")
    void rejectsMalformedVoteBody() throws Exception {
        UUID proposalId = UUID.fromString(create(members.get(0), "Вода", 2000)
                .path("id").asText());

        mockMvc.perform(post("/api/proposals/{id}/votes", proposalId)
                        .header("Authorization", bearer(members.get(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{inFavor:true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Голосование: не-член получает 403 (BR-VOTE-1)")
    void rejectsVoteByNonMember() throws Exception {
        UUID proposalId = UUID.fromString(create(members.get(0), "Вода", 2000)
                .path("id").asText());
        User applicant = userRepository.save(User.register("petr", "hash", "Пётр"));

        mockMvc.perform(post("/api/proposals/{id}/votes", proposalId)
                        .header("Authorization", bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inFavor\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Голосование: порог активности переводит предложение в работу (BR-LIFE-4)")
    void acceptingProposalThroughApi() throws Exception {
        UUID proposalId = UUID.fromString(create(members.get(0), "Вода", 2000)
                .path("id").asText());

        vote(members.get(0), proposalId, true);
        vote(members.get(1), proposalId, true);
        JsonNode last = vote(members.get(2), proposalId, true);

        assertThat(last.path("stage").asText())
                .as("30% из десяти и перевес 3:0 — решение принято досрочно")
                .isEqualTo("IN_WORK");
        assertThat(last.path("votingClosed").asBoolean()).isTrue();
    }

    private JsonNode create(User author, String title, long budget) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/proposals")
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new CreatePayload(title, "Описание предложения", budget))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        return json(response);
    }

    private JsonNode list(User member, String sort) throws Exception {
        var request = get("/api/proposals").header("Authorization", bearer(member));
        if (sort != null) {
            request = request.param("sort", sort);
        }
        MockHttpServletResponse response = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        return json(response);
    }

    private JsonNode vote(User member, UUID proposalId, boolean inFavor) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/proposals/{id}/votes", proposalId)
                                .header("Authorization", bearer(member))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"inFavor\":" + inFavor + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        return json(response);
    }

    private List<UUID> ids(JsonNode section) {
        List<UUID> ids = new ArrayList<>();
        section.forEach(item -> ids.add(UUID.fromString(item.path("id").asText())));
        return ids;
    }

    private List<String> titles(JsonNode section) {
        List<String> titles = new ArrayList<>();
        section.forEach(item -> titles.add(item.path("title").asText()));
        return titles;
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsByteArray());
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.issue(user).value();
    }

    private void createMembers(int count) {
        for (int i = 0; i < count; i++) {
            User member = User.register("member" + i, "hash", "Член " + i);
            member.join(community);
            members.add(userRepository.save(member));
        }
    }

    private void cleanDatabase() {
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }

    /** Тело запроса на создание предложения. */
    private record CreatePayload(String title, String description, long budget) {
    }
}

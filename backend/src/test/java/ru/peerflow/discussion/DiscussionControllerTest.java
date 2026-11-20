package ru.peerflow.discussion;

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
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Сквозные тесты дискуссии через HTTP ({@code BR-DISC-1}–{@code BR-DISC-3},
 * {@code BR-PRIV-1}).
 *
 * <p>Здесь проверяется то, что видит клиент: коды ответа, вложенность ответов
 * и закрытость точек. Правила ранжирования и скрытия спама проверяются в
 * {@link MessageRankingTest}: дублировать их на уровне HTTP незачем.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiscussionControllerTest extends PostgresTestBase {

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
    private MessageRepository messageRepository;

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
    @DisplayName("Сообщение: 201 и оно в ветке (BR-DISC-1)")
    void postsMessage() throws Exception {
        JsonNode thread = postMessage(anna, "Предлагаю аренду", null);

        assertThat(thread).hasSize(1);
        assertThat(thread.get(0).path("body").asText()).isEqualTo("Предлагаю аренду");
        assertThat(thread.get(0).path("authorName").asText()).isEqualTo("Анна");
    }

    @Test
    @DisplayName("Ответ приходит вложенным в своё сообщение (BR-DISC-3)")
    void postsReply() throws Exception {
        UUID rootId = UUID.fromString(postMessage(anna, "Аренда", null)
                .get(0).path("id").asText());

        JsonNode thread = postMessage(boris, "А покупка?", rootId);

        assertThat(thread.get(0).path("replies")).hasSize(1);
        assertThat(thread.get(0).path("replies").get(0).path("body").asText())
                .isEqualTo("А покупка?");
    }

    @Test
    @DisplayName("Пустое сообщение — 400 (BR-DISC-1)")
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/proposals/{id}/messages", proposalId)
                        .header("Authorization", bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Неизвестная сортировка — 400 (BR-DISC-4)")
    void rejectsUnknownSort() throws Exception {
        mockMvc.perform(get("/api/proposals/{id}/messages", proposalId)
                        .param("sort", "oldest")
                        .header("Authorization", bearer(anna)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Без токена обсуждение закрыто (BR-PRIV-1)")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/proposals/{id}/messages", proposalId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Не-член сообщества получает 403 (BR-PRIV-1)")
    void rejectsNonMember() throws Exception {
        User outsider = userRepository.save(User.register("petr", "hash", "Пётр"));

        mockMvc.perform(get("/api/proposals/{id}/messages", proposalId)
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Реакция на сообщение: счётчик в ответе (BR-DISC-2)")
    void reactsToMessage() throws Exception {
        UUID messageId = UUID.fromString(postMessage(anna, "Аренда", null)
                .get(0).path("id").asText());

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/messages/{id}/reactions", messageId)
                                .header("Authorization", bearer(boris))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"kind": "PLUS"}
                                        """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        JsonNode thread = json(response).path("messages");
        assertThat(thread.get(0).path("plus").asLong()).isEqualTo(1);
        assertThat(thread.get(0).path("myReaction").asText()).isEqualTo("PLUS");
    }

    @Test
    @DisplayName("Неизвестная реакция — 400, а не молчаливый «плюс» (BR-DISC-2)")
    void rejectsUnknownReaction() throws Exception {
        UUID messageId = UUID.fromString(postMessage(anna, "Аренда", null)
                .get(0).path("id").asText());

        mockMvc.perform(post("/api/messages/{id}/reactions", messageId)
                        .header("Authorization", bearer(boris))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind": "LIKE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Реакция на несуществующее сообщение — 404")
    void reactionToUnknownMessage() throws Exception {
        mockMvc.perform(post("/api/messages/{id}/reactions", UUID.randomUUID())
                        .header("Authorization", bearer(boris))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind": "PLUS"}
                                """))
                .andExpect(status().isNotFound());
    }

    private JsonNode postMessage(User author, String body, UUID parentId) throws Exception {
        String payload = parentId == null
                ? "{\"body\":" + jsonString(body) + "}"
                : "{\"body\":" + jsonString(body) + ",\"parentId\":\"" + parentId + "\"}";
        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/proposals/{id}/messages", proposalId)
                                .header("Authorization", bearer(author))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        return json(response).path("messages");
    }

    private UUID createProposal() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/proposals")
                        .header("Authorization", bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Аренда", "description": "Описание", "budget": 12000}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        return UUID.fromString(json(response).path("id").asText());
    }

    private String jsonString(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
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
        messageRepository.deleteAll();
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

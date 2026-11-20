package ru.peerflow.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import ru.peerflow.auth.service.TokenService;
import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.JoinRequestRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Сквозные тесты заявок на вступление через HTTP.
 *
 * <p>Здесь проверяется то, что видит клиент: {@code multipart} с фото, коды
 * ответа и закрытость точек ({@code BR-PRIV-1}). Логика порога проверяется
 * в {@link JoinRequestServiceTest} — дублировать арифметику на уровне HTTP
 * смысла нет, достаточно убедиться, что она доступна по сети.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JoinRequestControllerTest extends PostgresTestBase {

    private static final String ABOUT = "Пришёл помогать с общими делами";

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
    private JoinRequestRepository joinRequestRepository;

    private Community community;

    private final List<User> members = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanDatabase();
        members.clear();
        community = communityRepository.save(Community.create("Маяк"));
        createMembers(10);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Подача: 201 с заявкой, «о себе» и фото сохранены (BR-JOIN-4)")
    void submitsRequestWithAvatar() throws Exception {
        User applicant = createApplicant("petr", "Пётр");

        JsonNode body = submit(applicant, avatar("photo.png"));

        assertThat(body.path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("about").asText()).isEqualTo(ABOUT);
        assertThat(body.path("applicantName").asText()).isEqualTo("Пётр");
        assertThat(body.path("avatarPath").asText())
                .as("фото заявки сохранено и доступно клиенту")
                .startsWith("avatars/");
        assertThat(body.path("required").asLong())
                .as("клиент сразу видит, сколько голосов нужно")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Подача: без файла тоже принимается")
    void submitsRequestWithoutAvatar() throws Exception {
        User applicant = createApplicant("petr", "Пётр");

        JsonNode body = submit(applicant, null);

        assertThat(body.path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("avatarPath").isNull()).isTrue();
    }

    @Test
    @DisplayName("Подача: повторная заявка — 409, а не вторая строка (BR-JOIN-4)")
    void rejectsSecondSubmission() throws Exception {
        User applicant = createApplicant("petr", "Пётр");
        submit(applicant, null);

        mockMvc.perform(submitBuilder(applicant, null))
                .andExpect(status().isConflict());

        assertThat(joinRequestRepository.count())
                .as("вторая заявка не создана")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Подача: член сообщества заявку подать не может — 409 (BR-JOIN-2)")
    void rejectsSubmissionByMember() throws Exception {
        mockMvc.perform(submitBuilder(members.get(0), null))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Подача: без токена — 401, заявки закрыты (BR-PRIV-1)")
    void rejectsSubmissionWithoutToken() throws Exception {
        mockMvc.perform(multipart("/api/join-requests")
                        .file(jsonPart())
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Голосование: не член сообщества получает 403 (BR-VOTE-1)")
    void rejectsVoteByNonMember() throws Exception {
        User applicant = createApplicant("petr", "Пётр");
        UUID requestId = UUID.fromString(submit(applicant, null).path("id").asText());

        mockMvc.perform(post("/api/join-requests/{id}/votes", requestId)
                        .header("Authorization", bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Голосование: повторный голос — 409 (BR-VOTE-2)")
    void rejectsDuplicateVote() throws Exception {
        User applicant = createApplicant("petr", "Пётр");
        UUID requestId = UUID.fromString(submit(applicant, null).path("id").asText());

        vote(members.get(0), requestId, true);

        mockMvc.perform(post("/api/join-requests/{id}/votes", requestId)
                        .header("Authorization", bearer(members.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Голосование: без поля accept — 400, валидация не пропускает")
    void rejectsVoteWithoutDecision() throws Exception {
        User applicant = createApplicant("petr", "Пётр");
        UUID requestId = UUID.fromString(submit(applicant, null).path("id").asText());

        mockMvc.perform(post("/api/join-requests/{id}/votes", requestId)
                        .header("Authorization", bearer(members.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Голосование: битый JSON — 400, а не 500 из-за обработчика IOException")
    void rejectsMalformedVoteBody() throws Exception {
        User applicant = createApplicant("petr", "Пётр");
        UUID requestId = UUID.fromString(submit(applicant, null).path("id").asText());

        mockMvc.perform(post("/api/join-requests/{id}/votes", requestId)
                        .header("Authorization", bearer(members.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{accept:true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Голосование: несуществующая заявка — 404")
    void returnsNotFoundForUnknownRequest() throws Exception {
        mockMvc.perform(post("/api/join-requests/{id}/votes", UUID.randomUUID())
                        .header("Authorization", bearer(members.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Список заявок: член видит заявку, не-член получает 403 (BR-PRIV-1)")
    void listsRequestsForMembersOnly() throws Exception {
        User applicant = createApplicant("petr", "Пётр");
        submit(applicant, null);

        mockMvc.perform(get("/api/join-requests")
                        .header("Authorization", bearer(members.get(0))))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode list = json(result.getResponse());
                    assertThat(list.isArray()).isTrue();
                    assertThat(list).hasSize(1);
                    assertThat(list.path(0).path("applicantName").asText())
                            .isEqualTo("Пётр");
                });

        mockMvc.perform(get("/api/join-requests")
                        .header("Authorization", bearer(applicant)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Своя заявка: 204, пока её нет, и 200 после подачи")
    void returnsOwnRequestOrNoContent() throws Exception {
        User applicant = createApplicant("petr", "Пётр");

        mockMvc.perform(get("/api/join-requests/mine")
                        .header("Authorization", bearer(applicant)))
                .andExpect(status().isNoContent());

        submit(applicant, null);

        mockMvc.perform(get("/api/join-requests/mine")
                        .header("Authorization", bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        json(result.getResponse()).path("status").asText())
                        .isEqualTo("PENDING"));
    }

    @Test
    @DisplayName("Порог: третье «за» при десяти членах принимает человека (BR-JOIN-5)")
    void acceptsApplicantThroughApi() throws Exception {
        User applicant = createApplicant("petr", "Пётр");
        UUID requestId = UUID.fromString(submit(applicant, null).path("id").asText());

        vote(members.get(0), requestId, true);
        vote(members.get(1), requestId, true);
        JsonNode last = vote(members.get(2), requestId, true);

        assertThat(last.path("status").asText())
                .as("порог набран — заявка решена в момент голосования")
                .isEqualTo("ACCEPTED");
        assertThat(userRepository.findById(applicant.getId()).orElseThrow().isActiveMember())
                .as("принятый получает доступ к контенту сообщества")
                .isTrue();
    }

    private JsonNode submit(User applicant, MockMultipartFile avatar) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(submitBuilder(applicant, avatar))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        return json(response);
    }

    private RequestBuilder submitBuilder(User applicant, MockMultipartFile avatar)
            throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/join-requests")
                .file(jsonPart());
        if (avatar != null) {
            builder.file(avatar);
        }
        return builder
                .header("Authorization", bearer(applicant))
                .with(request -> {
                    request.setMethod("POST");
                    return request;
                });
    }

    private JsonNode vote(User member, UUID requestId, boolean accept) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/join-requests/{id}/votes", requestId)
                                .header("Authorization", bearer(member))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"accept\":" + accept + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        return json(response);
    }

    private MockMultipartFile jsonPart() throws Exception {
        return new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(new SubmitPayload(ABOUT)));
    }

    private MockMultipartFile avatar(String fileName) {
        return new MockMultipartFile(
                "avatar", fileName, "image/png", new byte[]{1, 2, 3, 4});
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

    private User createApplicant(String login, String displayName) {
        return userRepository.save(User.register(login, "hash", displayName));
    }

    private void cleanDatabase() {
        joinRequestRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }

    /** Тело JSON-части {@code request}: только текстовое поле. */
    private record SubmitPayload(String about) {
    }
}

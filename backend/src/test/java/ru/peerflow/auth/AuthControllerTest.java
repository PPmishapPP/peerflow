package ru.peerflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import ru.peerflow.auth.web.dto.LoginRequest;
import ru.peerflow.auth.web.dto.RegisterRequest;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Сквозные тесты регистрации и входа через HTTP.
 *
 * <p>Проверяем то, что видит клиент: коды ответа, тело и закрытость профиля.
 * Первый зарегистрировавшийся становится членом сообщества ({@code BR-JOIN-3}),
 * следующие — нет ({@code BR-JOIN-4}); вход принимает только верную пару
 * логин/пароль ({@code BR-JOIN-1}); профиль без токена недоступен
 * ({@code BR-PRIV-1}); пароль в базе хранится хэшем.
 *
 * <p>Тело ответа разбирается из байтов, а не из строки: так проверки не зависят
 * от кодировки, которую {@code MockHttpServletResponse} подставляет по
 * умолчанию, и кириллица сравнивается честно.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends PostgresTestBase {

    private static final String PASSWORD = "strong-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Регистрация: 201, токен выдан, первый аккаунт — член сообщества (BR-JOIN-3)")
    void registersFirstUserAsMember() throws Exception {
        JsonNode body = register("ivan", "Иван", "Рассказ о себе");

        assertThat(body.path("token").asText())
                .as("клиент получает токен доступа (BR-JOIN-1)")
                .isNotBlank();
        assertThat(body.path("member").asBoolean())
                .as("первый зарегистрировавшийся вступает автоматически (BR-JOIN-3)")
                .isTrue();
        assertThat(body.path("communityId").isNull())
                .as("сообщество создано и привязано к аккаунту")
                .isFalse();
        assertThat(body.path("expiresAt").asText())
                .as("клиент знает срок жизни токена")
                .isNotBlank();
        assertThat(body.path("displayName").asText()).isEqualTo("Иван");
    }

    @Test
    @DisplayName("Регистрация: второй аккаунт ждёт приёма — member=false (BR-JOIN-4)")
    void registersSecondUserWithoutCommunity() throws Exception {
        register("ivan", "Иван", null);

        JsonNode body = register("petr", "Пётр", "Хочу в сообщество");

        assertThat(body.path("member").asBoolean())
                .as("приём в сообщество решается голосованием (BR-JOIN-5)")
                .isFalse();
        assertThat(body.path("communityId").isNull())
                .as("до приёма сообщества у аккаунта нет")
                .isTrue();
    }

    @Test
    @DisplayName("Регистрация: пароль хранится хэшем, а не открытым текстом (BR-JOIN-1)")
    void storesPasswordAsHash() throws Exception {
        register("ivan", "Иван", null);

        User user = userRepository.findByLogin("ivan").orElseThrow();

        assertThat(user.getPasswordHash())
                .as("в базе не должно быть исходного пароля")
                .isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash()))
                .as("хэш проверяется тем же кодировщиком")
                .isTrue();
        assertThat(user.getAbout()).isNull();
    }

    @Test
    @DisplayName("Регистрация: «о себе» сохраняется (BR-JOIN-4)")
    void storesAbout() throws Exception {
        register("ivan", "Иван", "Люблю чистые подъезды");

        assertThat(userRepository.findByLogin("ivan").orElseThrow().getAbout())
                .isEqualTo("Люблю чистые подъезды");
    }

    @Test
    @DisplayName("Регистрация: занятый логин отклоняется с 409 (BR-JOIN-1)")
    void rejectsDuplicateLogin() throws Exception {
        register("ivan", "Иван", null);

        MockHttpServletResponse response = mockMvc.perform(
                        registerRequest("ivan", "Иван Второй", null))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse();

        assertThat(json(response).path("title").asText()).isEqualTo("Логин уже занят");
        assertThat(userRepository.findByLogin("ivan").orElseThrow().getDisplayName())
                .as("первый аккаунт не перезаписывается")
                .isEqualTo("Иван");
    }

    @Test
    @DisplayName("Регистрация: слишком короткий пароль отклоняется с 400")
    void rejectsInvalidRegistration() throws Exception {
        RegisterRequest tooShortPassword =
                new RegisterRequest("ivan", "short", "Иван", null);

        mockMvc.perform(multipart("/api/auth/register")
                        .file(jsonPart(tooShortPassword)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.existsByLogin("ivan"))
                .as("аккаунт с невалидными данными не создаётся")
                .isFalse();
    }

    @Test
    @DisplayName("Вход: верные логин и пароль дают 200 и токен (BR-JOIN-1)")
    void logsInWithCorrectPassword() throws Exception {
        register("ivan", "Иван", null);

        MockHttpServletResponse response = mockMvc.perform(loginRequest("ivan", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        JsonNode body = json(response);
        assertThat(body.path("token").asText()).isNotBlank();
        assertThat(body.path("login").asText()).isEqualTo("ivan");
        assertThat(body.path("member").asBoolean())
                .as("вход у члена сообщества возвращает признак членства")
                .isTrue();
    }

    @Test
    @DisplayName("Вход: неверный пароль даёт 401 с общим ответом (BR-JOIN-1)")
    void rejectsWrongPassword() throws Exception {
        register("ivan", "Иван", null);

        MockHttpServletResponse response = mockMvc
                .perform(loginRequest("ivan", "someone-elses-password"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse();

        assertThat(json(response).path("title").asText()).isEqualTo("Вход не выполнен");
    }

    @Test
    @DisplayName("Вход: неизвестный логин даёт тот же ответ 401 (BR-JOIN-1)")
    void rejectsUnknownLogin() throws Exception {
        MockHttpServletResponse response = mockMvc
                .perform(loginRequest("nobody", "some-password-123"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse();

        assertThat(json(response).path("title").asText())
                .as("ответ не должен подсказывать, существует ли логин")
                .isEqualTo("Вход не выполнен");
    }

    @Test
    @DisplayName("Профиль без токена недоступен (BR-PRIV-1)")
    void profileRequiresToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Профиль по токену возвращает аккаунт вошедшего (BR-PRIV-1)")
    void profileReturnsCurrentUser() throws Exception {
        String token = register("ivan", "Иван", "Рассказ о себе")
                .path("token").asText();

        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        JsonNode body = json(response);
        assertThat(body.path("login").asText()).isEqualTo("ivan");
        assertThat(body.path("displayName").asText()).isEqualTo("Иван");
        assertThat(body.path("about").asText()).isEqualTo("Рассказ о себе");
        assertThat(body.path("member").asBoolean()).isTrue();
    }

    private void cleanDatabase() {
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }

    private JsonNode register(String login, String displayName, String about) throws Exception {
        MockHttpServletResponse response = mockMvc
                .perform(registerRequest(login, displayName, about))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        return json(response);
    }

    private RequestBuilder registerRequest(String login, String displayName, String about)
            throws Exception {
        RegisterRequest request = new RegisterRequest(login, PASSWORD, displayName, about);
        return multipart("/api/auth/register")
                .file(jsonPart(request))
                .file(new MockMultipartFile("avatar", "avatar.png", "image/png",
                        new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
    }

    private RequestBuilder loginRequest(String login, String password) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new LoginRequest(login, password)));
    }

    private MockMultipartFile jsonPart(RegisterRequest request) throws Exception {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsByteArray());
    }
}

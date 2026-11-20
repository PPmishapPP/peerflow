package ru.peerflow.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import ru.peerflow.auth.service.TokenService;
import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.config.StorageProperties;
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.legitimacy.repo.CandidateRepository;
import ru.peerflow.legitimacy.repo.LeaderMandateRepository;
import ru.peerflow.legitimacy.repo.LegitimacyEventRepository;
import ru.peerflow.legitimacy.service.LegitimacyService;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.report.repo.AttachmentRepository;
import ru.peerflow.report.repo.BudgetExpenseRepository;
import ru.peerflow.reputation.repo.ReputationEntryRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Сквозные тесты отчётов и вложений через HTTP ({@code BR-LIFE-7},
 * {@code BR-BUDGET-4}, {@code BR-PRIV-1}).
 *
 * <p>Проверяется контракт, который видит клиент: коды ответа, имя поля с
 * файлом, заголовок скачивания и закрытость точек. Правила «кто может
 * отчитываться» и «сколько можно потратить» проверены в
 * {@link ReportServiceTest} — дублировать их через HTTP незачем.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest extends PostgresTestBase {

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
    private MessageRepository messageRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private BudgetExpenseRepository expenseRepository;

    @Autowired
    private LegitimacyEventRepository eventRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private LeaderMandateRepository mandateRepository;

    @Autowired
    private ReputationEntryRepository reputationRepository;

    @Autowired
    private StorageProperties storageProperties;

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
    @DisplayName("Отчёт с файлом: 201 и файл в ответе (BR-LIFE-7)")
    void reportWithFileIsCreated() throws Exception {
        User leader = electLeader();

        JsonNode report = json(mockMvc.perform(multipart("/api/proposals/{id}/reports", proposalId)
                        .file(pdf())
                        .param("text", "Листовки напечатаны")
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse());

        assertThat(report.path("files")).hasSize(1);
        assertThat(report.path("files").get(0).path("fileName").asText())
                .isEqualTo("листовки.pdf");
        assertThat(report.path("files").get(0).path("authorName").asText()).isEqualTo("Член 2");
    }

    @Test
    @DisplayName("Отчёт с текстом: 201 и пустой список файлов (BR-LIFE-7)")
    void reportWithTextOnlyIsCreated() throws Exception {
        User leader = electLeader();

        JsonNode report = json(mockMvc.perform(multipart("/api/proposals/{id}/reports", proposalId)
                        .param("text", "Готово, листовки развезены")
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse());

        assertThat(report.path("files")).isEmpty();
        assertThat(messageRepository.findAllForProposal(proposal())).hasSize(1);
    }

    @Test
    @DisplayName("Пустой отчёт — 400 (BR-LIFE-7)")
    void emptyReportIsBadRequest() throws Exception {
        User leader = electLeader();

        mockMvc.perform(multipart("/api/proposals/{id}/reports", proposalId)
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Недопустимый тип файла — 400 (BR-LIFE-7)")
    void forbiddenFileTypeIsBadRequest() throws Exception {
        User leader = electLeader();
        MockMultipartFile exe = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/proposals/{id}/reports", proposalId)
                        .file(exe)
                        .param("text", "отчёт")
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Отчёт не лидера — 403 (BR-LIFE-6)")
    void reportFromNonLeaderIsForbidden() throws Exception {
        electLeader();

        mockMvc.perform(multipart("/api/proposals/{id}/reports", proposalId)
                        .param("text", "Отчёт от лица лидера")
                        .header("Authorization", bearer(members.get(0))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Файл скачивается как вложение с исходным именем (BR-LIFE-7)")
    void attachmentIsDownloaded() throws Exception {
        User leader = electLeader();
        JsonNode report = json(mockMvc.perform(multipart("/api/proposals/{id}/reports", proposalId)
                        .file(pdf())
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse());
        String url = report.path("files").get(0).path("url").asText();

        MockHttpServletResponse response = mockMvc.perform(get(url)
                        .header("Authorization", bearer(leader)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn()
                .getResponse();

        assertThat(response.getContentAsByteArray())
                .isEqualTo("%PDF-1.4 тест".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Трата бюджета: 201 и остаток в ответе (BR-BUDGET-4)")
    void expenseIsCreated() throws Exception {
        User leader = electLeader();

        JsonNode report = json(mockMvc.perform(post("/api/proposals/{id}/expenses", proposalId)
                        .header("Authorization", bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 2500, "note": "Печать"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse());

        assertThat(report.path("spent").asLong()).isEqualTo(2500L);
        assertThat(report.path("remaining").asLong())
                .as("бюджет задачи — цена лидера (10000), а не потолок")
                .isEqualTo(7500L);
    }

    @Test
    @DisplayName("Нулевая трата — 400 (BR-BUDGET-4)")
    void zeroExpenseIsBadRequest() throws Exception {
        User leader = electLeader();

        mockMvc.perform(post("/api/proposals/{id}/expenses", proposalId)
                        .header("Authorization", bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Чужое сообщество не видит отчёт: 404 (BR-PRIV-1)")
    void reportOfOtherCommunityIsHidden() throws Exception {
        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        mockMvc.perform(get("/api/proposals/{id}/report", proposalId)
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Без токена отчёт недоступен (BR-PRIV-1)")
    void reportRequiresToken() throws Exception {
        mockMvc.perform(get("/api/proposals/{id}/report", proposalId))
                .andExpect(status().isUnauthorized());
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

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "листовки.pdf", "application/pdf",
                "%PDF-1.4 тест".getBytes(StandardCharsets.UTF_8));
    }

    private ru.peerflow.proposal.domain.Proposal proposal() {
        return proposalRepository.findById(proposalId).orElseThrow();
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
        for (var attachment : attachmentRepository.findAll()) {
            Path path = storageProperties.rootPath().resolve(attachment.getPath());
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
                // Файла может уже не быть.
            }
        }
        expenseRepository.deleteAll();
        attachmentRepository.deleteAll();
        messageRepository.deleteAll();
        reputationRepository.deleteAll();
        mandateRepository.deleteAll();
        candidateRepository.deleteAll();
        eventRepository.deleteAll();
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

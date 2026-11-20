package ru.peerflow.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.MembershipRequiredException;
import ru.peerflow.config.StorageProperties;
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.legitimacy.repo.CandidateRepository;
import ru.peerflow.legitimacy.repo.LeaderMandateRepository;
import ru.peerflow.legitimacy.repo.LegitimacyEventRepository;
import ru.peerflow.legitimacy.service.LegitimacyService;
import ru.peerflow.legitimacy.service.NotLeaderException;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.domain.ProposalStage;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalNotFoundException;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.report.repo.AttachmentRepository;
import ru.peerflow.report.repo.BudgetExpenseRepository;
import ru.peerflow.report.service.AttachmentNotFoundException;
import ru.peerflow.report.service.ReportService;
import ru.peerflow.report.web.dto.BudgetExpenseResponse;
import ru.peerflow.report.web.dto.ReportResponse;
import ru.peerflow.reputation.repo.ReputationEntryRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Отчёты лидера, вложения и траты бюджета ({@code BR-LIFE-7},
 * {@code BR-BUDGET-4}, {@code BR-BUDGET-5}).
 *
 * <p>Проверяется то, ради чего эти правила существуют: отчёт публикует только
 * лидер, файл проверяется по типу и размеру, траты не могут превысить бюджет,
 * а чужое сообщество не видит ни отчётов, ни файлов.
 *
 * <p>Отдельного «отчёта» здесь нет намеренно: текст отчёта должен стать
 * обычным сообщением под задачей ({@code BR-LIFE-7}), и тест это фиксирует.
 */
@SpringBootTest
class ReportServiceTest extends PostgresTestBase {

    /** Трое в сообществе: досрочка наступает на двух голосах «за» из трёх. */
    private static final int MEMBERS = 3;

    /** Цена лидера: чуть ниже потолка — бюджет задачи берётся из неё. */
    private static final long BUDGET = 10000L;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private LegitimacyService legitimacyService;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private BudgetExpenseRepository expenseRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ProposalRepository proposalRepository;

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
    private StorageProperties storageProperties;

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
    @DisplayName("Отчёт с текстом становится сообщением под задачей (BR-LIFE-7)")
    void textReportBecomesMessage() throws IOException {
        User leader = electLeader();

        ReportResponse report = reportService.post(leader.getId(), proposalId,
                "Листовки напечатаны, 500 штук", null);

        assertThat(report.files()).isEmpty();
        assertThat(messageRepository.findAllForProposal(proposal())).hasSize(1);
        assertThat(messageRepository.findAllForProposal(proposal()).get(0).getBody())
                .isEqualTo("Листовки напечатаны, 500 штук");
    }

    @Test
    @DisplayName("Файл отчёта сохраняется на диск и описывается в ответе (BR-LIFE-7)")
    void fileReportIsStored() throws IOException {
        User leader = electLeader();

        ReportResponse report = reportService.post(leader.getId(), proposalId,
                null, pdf("листовки.pdf"));

        assertThat(report.files()).hasSize(1);
        assertThat(report.files().get(0).fileName()).isEqualTo("листовки.pdf");
        assertThat(report.files().get(0).contentType()).isEqualTo("application/pdf");
        assertThat(report.files().get(0).authorId()).isEqualTo(leader.getId());
        assertThat(report.files().get(0).url())
                .isEqualTo("/api/attachments/" + report.files().get(0).id());

        // Файл действительно лежит на диске: описание без файла — битая ссылка.
        assertThat(Files.exists(pathOf(report.files().get(0).id()))).isTrue();
    }

    @Test
    @DisplayName("Текст и файл в одном отчёте: сообщение и вложение (BR-LIFE-7)")
    void textAndFileInOneReport() throws IOException {
        User leader = electLeader();

        ReportResponse report = reportService.post(leader.getId(), proposalId,
                "Подробности в файле", pdf("отчёт.pdf"));

        assertThat(report.files()).hasSize(1);
        assertThat(messageRepository.findAllForProposal(proposal()))
                .as("текст отчёта — обычное сообщение, а не отдельная запись")
                .hasSize(1);
    }

    @Test
    @DisplayName("Пустой отчёт не принимается (BR-LIFE-7)")
    void emptyReportIsRejected() {
        User leader = electLeader();

        assertThatThrownBy(() -> reportService.post(leader.getId(), proposalId, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пустым");
    }

    @Test
    @DisplayName("Недопустимый тип файла не принимается (BR-LIFE-7)")
    void forbiddenFileTypeIsRejected() {
        User leader = electLeader();
        MultipartFile exe = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> reportService.post(leader.getId(), proposalId, null, exe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("тип файла");
        assertThat(attachmentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Слишком большой файл не принимается (BR-LIFE-7)")
    void tooLargeFileIsRejected() {
        User leader = electLeader();
        MultipartFile huge = new MockMultipartFile(
                "file", "отчёт.pdf", "application/pdf", new byte[11 * 1024 * 1024]);

        assertThatThrownBy(() -> reportService.post(leader.getId(), proposalId, null, huge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("МБ");
        assertThat(attachmentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Отчёт публикует только лидер задачи (BR-LIFE-6, BR-LIFE-7)")
    void onlyLeaderCanReport() {
        electLeader();
        User notLeader = members.get(0);

        assertThatThrownBy(() -> reportService.post(
                notLeader.getId(), proposalId, "Отчёт от лица лидера", null))
                .isInstanceOf(NotLeaderException.class);
    }

    @Test
    @DisplayName("Отчёт закрыт для того, кто ещё не член сообщества (BR-PRIV-1)")
    void nonMemberCannotReport() {
        User leader = electLeader();
        User waiting = User.register("waiting", "hash", "Ожидающий");
        userRepository.save(waiting);

        assertThatThrownBy(() -> reportService.post(waiting.getId(), proposalId, "текст", null))
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("Чужое сообщество не видит отчёт: 404 (BR-PRIV-1)")
    void otherCommunityCannotReadReport() throws IOException {
        User leader = electLeader();
        reportService.post(leader.getId(), proposalId, "текст", null);
        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        assertThatThrownBy(() -> reportService.report(stranger.getId(), proposalId))
                .isInstanceOf(ProposalNotFoundException.class);
    }

    @Test
    @DisplayName("Чужой файл отчёта недоступен (BR-PRIV-1)")
    void otherCommunityCannotDownloadAttachment() throws IOException {
        User leader = electLeader();
        ReportResponse report = reportService.post(leader.getId(), proposalId, null, pdf("файл.pdf"));
        UUID attachmentId = report.files().get(0).id();

        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        assertThatThrownBy(() -> reportService.file(stranger.getId(), attachmentId))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    @DisplayName("Трата уменьшает остаток бюджета (BR-BUDGET-4)")
    void spendReducesRemaining() {
        User leader = electLeader();

        ReportResponse report = reportService.spend(leader.getId(), proposalId, 2000L, "Печать");

        assertThat(report.budget())
                .as("бюджет задачи — цена лидера, а не потолок 12000")
                .isEqualTo(BUDGET);
        assertThat(report.spent()).isEqualTo(2000L);
        assertThat(report.remaining()).isEqualTo(8000L);
        assertThat(report.expenses()).hasSize(1);
        assertThat(report.expenses().get(0).amount()).isEqualTo(2000L);
        assertThat(report.expenses().get(0).note()).isEqualTo("Печать");
    }

    @Test
    @DisplayName("Трата больше остатка не записывается (BR-BUDGET-4)")
    void cannotSpendMoreThanRemaining() {
        User leader = electLeader();
        reportService.spend(leader.getId(), proposalId, 9000L, "Первая трата");

        assertThatThrownBy(() -> reportService.spend(
                leader.getId(), proposalId, 2000L, "Вторая трата"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("остатка бюджета");
        assertThat(expenseRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Новый лидер начинает счёт трат заново, прошлые остаются в журнале (BR-BUDGET-7)")
    void newLeaderStartsWithCleanSlate() {
        User first = electLeader();
        reportService.spend(first.getId(), proposalId, 6000L, "Печать листовок");

        // Отзываем доверие почти у всех: мандат утрачен, задача возвращается
        // на переголосование (BR-LIFE-10).
        legitimacyService.delegate(members.get(0).getId(), proposalId, first.getId());
        legitimacyService.revoke(members.get(1).getId(), proposalId);
        Proposal afterLoss = proposal();
        assertThat(afterLoss.getStage()).isEqualTo(ProposalStage.DISCUSSION);
        assertThat(afterLoss.getTaskBudget())
                .as("бюджет прежнего лидера сброшен")
                .isNull();

        // Новый лидер называет свою цену и побеждает.
        User next = members.get(0);
        legitimacyService.nominate(next.getId(), proposalId, 8000L);
        legitimacyService.delegate(members.get(1).getId(), proposalId, next.getId());
        proposalService.vote(members.get(1).getId(), proposalId, true);
        proposalService.vote(members.get(2).getId(), proposalId, true);

        ReportResponse report = reportService.report(next.getId(), proposalId);
        assertThat(report.budget())
                .as("бюджет — цена нового лидера")
                .isEqualTo(8000L);
        assertThat(report.spent())
                .as("счёт трат новой попытки пуст: чужие траты её не уменьшают")
                .isZero();
        assertThat(report.remaining()).isEqualTo(8000L);
        assertThat(report.expenses())
                .as("прошлые траты видны как история задачи")
                .hasSize(1)
                .first()
                .extracting(BudgetExpenseResponse::amount)
                .isEqualTo(6000L);
    }

    @Test
    @DisplayName("Без лидера виден потолок как ожидаемый бюджет (BR-BUDGET-2)")
    void showsCeilingWhileNoLeader() {
        // Задачу приняли «делаем», но кандидатов не было: лидера нет.
        proposalService.vote(members.get(0).getId(), proposalId, true);
        proposalService.vote(members.get(1).getId(), proposalId, true);

        ReportResponse report = reportService.report(members.get(0).getId(), proposalId);

        assertThat(report.budget())
                .as("пока исполнителя нет, сообщество видит утверждённый потолок")
                .isEqualTo(12000L);
        assertThat(report.spent()).isZero();
        assertThat(report.remaining()).isEqualTo(12000L);
    }

    @Test
    @DisplayName("Тратить бюджет может только лидер (BR-BUDGET-4)")
    void onlyLeaderCanSpend() {
        electLeader();

        assertThatThrownBy(() -> reportService.spend(
                members.get(0).getId(), proposalId, 1000L, "Чужая трата"))
                .isInstanceOf(NotLeaderException.class);
        assertThat(expenseRepository.findAll()).isEmpty();
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

    private Path pathOf(UUID attachmentId) {
        String relative = attachmentRepository.findById(attachmentId).orElseThrow().getPath();
        return storageProperties.rootPath().resolve(relative);
    }

    private MultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf",
                "%PDF-1.4 тест".getBytes(StandardCharsets.UTF_8));
    }

    private User member(String login, String displayName) {
        User user = User.register(login, "hash", displayName);
        user.join(community);
        return userRepository.save(user);
    }

    private void cleanDatabase() {
        // Файлы убираем вместе с описаниями: иначе следующий тест видел бы
        // вложения прошлого.
        for (var attachment : attachmentRepository.findAll()) {
            Path path = storageProperties.rootPath().resolve(attachment.getPath());
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Файла может уже не быть — это не повод ронять тест.
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

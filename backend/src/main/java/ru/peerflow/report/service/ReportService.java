package ru.peerflow.report.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.peerflow.auth.service.AccountNotFoundException;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.MembershipRequiredException;
import ru.peerflow.discussion.domain.Message;
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.legitimacy.domain.LeaderMandate;
import ru.peerflow.legitimacy.repo.LeaderMandateRepository;
import ru.peerflow.legitimacy.service.NotLeaderException;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.domain.ProposalStage;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalNotFoundException;
import ru.peerflow.realtime.EventPublisher;
import ru.peerflow.realtime.EventType;
import ru.peerflow.report.domain.Attachment;
import ru.peerflow.report.domain.BudgetExpense;
import ru.peerflow.report.repo.AttachmentRepository;
import ru.peerflow.report.repo.BudgetExpenseRepository;
import ru.peerflow.report.web.dto.AttachmentResponse;
import ru.peerflow.report.web.dto.BudgetExpenseResponse;
import ru.peerflow.report.web.dto.ReportResponse;

/**
 * Отчёты лидера и траты бюджета ({@code BR-LIFE-7}, {@code BR-BUDGET-4},
 * {@code BR-BUDGET-5}).
 *
 * <p>Отчётом считается любое сообщение или файл под задачей
 * ({@code BR-LIFE-7}): отдельного формата нет, поэтому сервис не заводит
 * собственного вида отчёта. Текст становится обычным сообщением и обсуждается
 * теми же правилами ({@code BR-OVER-7}), файл — записью о вложении. Один
 * запрос может нести и то, и другое: человек отправляет отчёт один раз, а не
 * «сначала текст, потом файл».
 *
 * <p>Публикует отчёты лидер задачи: это его исполнительный мандат
 * ({@code BR-LEGIT-4}, {@code BR-LIFE-6}). Проверка обязательна, иначе отчёт
 * «от лица лидера» мог бы написать любой член, и сообщество не отличило бы
 * исполнителя от комментатора. Читать отчёты может любой член сообщества —
 * они и нужны сообществу для решения «выполнено / нет» ({@code BR-LIFE-9}).
 *
 * <p>Траты бюджета тоже пишет лидер ({@code BR-BUDGET-4}). Сумма всех трат не
 * может превысить бюджет: в MVP деньги виртуальны ({@code BR-BUDGET-1}), но
 * учёт обязан сходиться, иначе «остаток» перестал бы быть числом.
 */
@Service
public class ReportService {

    private final AttachmentRepository attachmentRepository;
    private final BudgetExpenseRepository expenseRepository;
    private final MessageRepository messageRepository;
    private final ProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final LeaderMandateRepository mandateRepository;
    private final ReportStorage storage;
    private final EventPublisher events;
    private final Clock clock;

    public ReportService(AttachmentRepository attachmentRepository,
                         BudgetExpenseRepository expenseRepository,
                         MessageRepository messageRepository,
                         ProposalRepository proposalRepository,
                         UserRepository userRepository,
                         LeaderMandateRepository mandateRepository,
                         ReportStorage storage,
                         EventPublisher events,
                         Clock clock) {
        this.attachmentRepository = attachmentRepository;
        this.expenseRepository = expenseRepository;
        this.messageRepository = messageRepository;
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
        this.mandateRepository = mandateRepository;
        this.storage = storage;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Публикует отчёт: текст, файл или оба ({@code BR-LIFE-7}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @param text       текст отчёта; может быть пустым, если есть файл
     * @param file       приложенный файл; может отсутствовать
     * @return состояние отчёта задачи после публикации
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     * @throws NotLeaderException          если отчёт публикует не лидер
     * @throws IllegalArgumentException    если нет ни текста, ни файла либо
     *                                     файл не принят
     * @throws IOException                 если файл не удалось сохранить
     */
    @Transactional
    public ReportResponse post(UUID memberId, UUID proposalId, String text,
                               MultipartFile file) throws IOException {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        requireLeader(proposal, member);

        boolean hasText = text != null && !text.isBlank();
        boolean hasFile = file != null && !file.isEmpty();
        if (!hasText && !hasFile) {
            throw new IllegalArgumentException("отчёт не может быть пустым");
        }

        // Файл сохраняем до записи в базу: если диск недоступен, в базе не
        // останется вложения, которое нечем открыть.
        String path = hasFile ? storage.store(file) : null;

        if (hasText) {
            // Текст отчёта — обычное сообщение: под ним работает та же
            // дискуссия и те же реакции (BR-OVER-7, BR-DISC-1).
            messageRepository.save(
                    Message.create(proposal, member, null, text, clock.instant()));
        }

        if (hasFile) {
            attachmentRepository.save(Attachment.attach(
                    proposal,
                    member,
                    path,
                    originalName(file),
                    file.getContentType(),
                    file.getSize(),
                    clock.instant()));
        }

        // BR-LIFE-7: новый отчёт — «горячая» поверхность. Одно событие на
        // публикацию, даже если пришли и текст, и файл: для клиента это один
        // новый отчёт, а не два повода перерисовать экран.
        events.publishAfterCommit(
                proposal.getCommunity(), EventType.REPORT_POSTED, proposal.getId());

        return reportOf(proposal);
    }

    /**
     * Отчёты и траты задачи ({@code BR-LIFE-7}, {@code BR-BUDGET-4}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @return файлы, журнал трат и остаток
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     */
    @Transactional(readOnly = true)
    public ReportResponse report(UUID memberId, UUID proposalId) {
        User member = loadActiveMember(memberId);
        return reportOf(loadProposal(member, proposalId));
    }

    /**
     * Записывает трату бюджета ({@code BR-BUDGET-4}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @param amount     сколько потрачено; больше нуля
     * @param note       на что; может отсутствовать
     * @return состояние отчёта задачи после записи
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     * @throws NotLeaderException          если тратит не лидер
     * @throws IllegalArgumentException    если сумма не положительна или
     *                                     больше остатка
     */
    @Transactional
    public ReportResponse spend(UUID memberId, UUID proposalId, long amount, String note) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        LeaderMandate mandate = requireLeader(proposal, member);

        long budget = budgetOf(proposal);
        long spent = expenseRepository.totalSpent(mandate);
        long remaining = budget - spent;
        if (amount > remaining) {
            // BR-BUDGET-4: бюджет задачи — не бесконечный счёт. Трата сверх
            // остатка сделала бы «остаток» отрицательным и бессмысленным.
            throw new IllegalArgumentException(
                    "трата больше остатка бюджета: осталось " + remaining);
        }

        expenseRepository.save(
                BudgetExpense.of(proposal, mandate, member, amount, note, clock.instant()));
        return reportOf(proposal);
    }

    /**
     * Описание файла, если он доступен этому члену сообщества
     * ({@code BR-PRIV-1}).
     *
     * @param memberId     аккаунт из токена
     * @param attachmentId вложение
     * @return вложение и путь на диске
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws AttachmentNotFoundException если файла нет или он в чужом сообществе
     */
    @Transactional(readOnly = true)
    public AttachmentFile file(UUID memberId, UUID attachmentId) {
        User member = loadActiveMember(memberId);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(found -> Objects.equals(
                        found.getProposal().getCommunity(), member.getCommunity()))
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        return new AttachmentFile(attachment, storage.resolve(attachment.getPath()));
    }

    private ReportResponse reportOf(Proposal proposal) {
        List<AttachmentResponse> files = new ArrayList<>();
        for (Attachment attachment : attachmentRepository.findAllForProposal(proposal)) {
            files.add(new AttachmentResponse(
                    attachment.getId(),
                    attachment.getFileName(),
                    attachment.getContentType(),
                    attachment.getSizeBytes(),
                    attachment.getAuthor().getId(),
                    attachment.getAuthor().getDisplayName(),
                    attachment.getCreatedAt(),
                    "/api/attachments/" + attachment.getId()));
        }

        List<BudgetExpenseResponse> expenses = new ArrayList<>();
        for (BudgetExpense expense : expenseRepository.findAllForProposal(proposal)) {
            expenses.add(new BudgetExpenseResponse(
                    expense.getId(),
                    expense.getMember().getId(),
                    expense.getMember().getDisplayName(),
                    expense.getAmount(),
                    expense.getNote(),
                    expense.getCreatedAt()));
        }

        long spent = spentOf(proposal);
        long budget = budgetOf(proposal);
        return new ReportResponse(
                proposal.getId(),
                files,
                expenses,
                budget,
                spent,
                // BR-BUDGET-4: остаток не бывает отрицательным, даже если
                // исторические данные противоречат этому правилу.
                Math.max(0, budget - spent));
    }

    /**
     * Бюджет задачи: цена действующего лидера, а пока его нет — потолок
     * ({@code BR-LIFE-6}, {@code BR-BUDGET-2}).
     *
     * <p>Пока лидера нет, показываем потолок как ожидаемый бюджет: сообщество
     * уже утвердило, на какую сумму рассчитывает, и ноль выглядел бы так, будто
     * задача ничего не стоит. Тратить при этом некому — {@code requireLeader}
     * не пропустит трату, — но человек видит, ради какой суммы ищется
     * исполнитель.
     *
     * @param proposal задача
     * @return бюджет задачи или утверждённый потолок
     */
    private long budgetOf(Proposal proposal) {
        Long budget = proposal.getTaskBudget();
        return budget == null ? proposal.getBudget() : budget;
    }

    /**
     * Потрачено в рамках действующего мандата ({@code BR-BUDGET-7}).
     *
     * <p>Не по задаче целиком: после смены лидера начинается новая попытка, и
     * новый лидер не должен наследовать чужой счёт. Прошлые траты остаются в
     * журнале как история.
     *
     * @param proposal задача
     * @return потрачено; {@code 0}, если лидера нет или он ещё не тратил
     */
    private long spentOf(Proposal proposal) {
        return mandateRepository.findActive(proposal)
                .map(expenseRepository::totalSpent)
                .orElse(0L);
    }

    /**
     * Проверяет, что действие делает действующий лидер задачи
     * ({@code BR-LIFE-6}, {@code BR-LIFE-7}).
     *
     * <p>Отчёты и траты имеют смысл только у задачи в работе: у закрытой
     * задачи распоряжаться бюджетом уже некому.
     *
     * @return действующий мандат лидера — он нужен, чтобы записать трату к
     *         текущей попытке ({@code BR-BUDGET-7})
     */
    private LeaderMandate requireLeader(Proposal proposal, User member) {
        if (proposal.getStage() != ProposalStage.IN_WORK) {
            throw new IllegalStateException(
                    "отчёты и траты доступны только задаче в работе: " + proposal.getStage());
        }
        LeaderMandate mandate = mandateRepository.findActive(proposal)
                .orElseThrow(() -> new NotLeaderException(proposal.getId()));
        if (!Objects.equals(mandate.getLeader().getId(), member.getId())) {
            throw new NotLeaderException(proposal.getId());
        }
        return mandate;
    }

    private String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "отчёт";
        }
        // Имя от клиента — это данные, а не путь: отделяем от каталогов,
        // иначе строка вида «../../etc/passwd» попала бы в описание как есть.
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private Proposal loadProposal(User member, UUID proposalId) {
        Objects.requireNonNull(proposalId, "задача обязательна");
        return proposalRepository.findById(proposalId)
                .filter(proposal -> Objects.equals(proposal.getCommunity(), member.getCommunity()))
                .orElseThrow(() -> new ProposalNotFoundException(proposalId));
    }

    private User loadActiveMember(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));
        if (!user.isActiveMember()) {
            throw new MembershipRequiredException(
                    "действие доступно только действующему члену сообщества");
        }
        return user;
    }

    /**
     * Файл отчёта и его место на диске — для скачивания.
     *
     * @param attachment описание вложения
     * @param path       путь на диске
     */
    public record AttachmentFile(Attachment attachment, Path path) {
    }
}

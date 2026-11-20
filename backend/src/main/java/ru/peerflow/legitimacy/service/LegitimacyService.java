package ru.peerflow.legitimacy.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.peerflow.auth.service.AccountNotFoundException;
import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.domain.UserStatus;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.MembershipRequiredException;
import ru.peerflow.legitimacy.domain.Candidate;
import ru.peerflow.legitimacy.domain.LeaderElection;
import ru.peerflow.legitimacy.domain.LeaderMandate;
import ru.peerflow.legitimacy.domain.LegitimacyEvent;
import ru.peerflow.legitimacy.domain.LegitimacyStanding;
import ru.peerflow.legitimacy.domain.MandateEndReason;
import ru.peerflow.legitimacy.repo.CandidateRepository;
import ru.peerflow.legitimacy.repo.LeaderMandateRepository;
import ru.peerflow.legitimacy.repo.LegitimacyEventRepository;
import ru.peerflow.legitimacy.web.dto.CandidateResponse;
import ru.peerflow.legitimacy.web.dto.DelegationResponse;
import ru.peerflow.legitimacy.web.dto.LeaderResponse;
import ru.peerflow.legitimacy.web.dto.LegitimacyBoardResponse;
import ru.peerflow.legitimacy.web.dto.MyLegitimacyResponse;
import ru.peerflow.config.PeerflowThresholds;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.domain.ProposalStage;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalNotFoundException;
import ru.peerflow.reputation.service.ReputationService;

/**
 * Легитимность, кандидаты и мандат лидера ({@code BR-LEGIT-1}–
 * {@code BR-LEGIT-6}, {@code BR-LEADER-1}–{@code BR-LEADER-8}).
 *
 * <p>Сервис ничего не «пересчитывает назад»: он только дописывает событие в
 * журнал и сворачивает журнал в текущее состояние
 * ({@link LegitimacyStanding}). Поэтому отзыв никогда не стирает историю, а
 * вопрос «почему у лидера больше нет мандата» всегда имеет ответ.
 *
 * <p>Единица состояния — пара «задача + член» ({@code BR-LEGIT-3}). Один член
 * распоряжается одной единицей доверия ({@code BR-LEGIT-1}): повторная
 * передача не накапливается, а заменяет получателя.
 *
 * <p>Повторение того же действия событий не плодит: если доверие уже у этого
 * кандидата, журнал не растёт. Это важно для мобильного клиента, где двойное
 * нажатие — обычное дело.
 *
 * <p><strong>Когда выбирается лидер и когда кончается мандат.</strong> Ни
 * планировщика, ни фоновых задач в MVP нет, поэтому выбор лидера и проверка
 * порога удержания происходят при чтении карточки задачи
 * ({@link #board(UUID, UUID)}). Это то же решение, что и у закрытия
 * голосования: итог — детерминированная функция от данных, и пересчитать его
 * в момент, когда кто-то смотрит, достаточно. Пока карточку никто не открыл,
 * мандат формально ещё числится действующим — но и повлиять на это некому:
 * бюджет расходуется через те же экраны.
 */
@Service
public class LegitimacyService {

    private final LegitimacyEventRepository eventRepository;
    private final CandidateRepository candidateRepository;
    private final LeaderMandateRepository mandateRepository;
    private final ProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final ReputationService reputationService;
    private final PeerflowThresholds thresholds;
    private final java.time.Clock clock;

    public LegitimacyService(LegitimacyEventRepository eventRepository,
                             CandidateRepository candidateRepository,
                             LeaderMandateRepository mandateRepository,
                             ProposalRepository proposalRepository,
                             UserRepository userRepository,
                             ReputationService reputationService,
                             PeerflowThresholds thresholds,
                             java.time.Clock clock) {
        this.eventRepository = eventRepository;
        this.candidateRepository = candidateRepository;
        this.mandateRepository = mandateRepository;
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
        this.reputationService = reputationService;
        this.thresholds = thresholds;
        this.clock = clock;
    }

    /**
     * Передаёт легитимность кандидату ({@code BR-LEGIT-2}).
     *
     * <p>Передать можно только свою легитимность: {@code memberId} приходит из
     * токена, а не из тела запроса, поэтому распорядиться чужим доверием
     * нельзя.
     *
     * @param memberId    аккаунт из токена
     * @param proposalId  задача
     * @param candidateId кандидат
     * @return состояние легитимности задачи после передачи
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     */
    @Transactional
    public LegitimacyBoardResponse delegate(UUID memberId, UUID proposalId, UUID candidateId) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        User candidate = loadCandidate(member, candidateId);

        LegitimacyStanding standing = standing(proposal);
        if (!Objects.equals(standing.recipientOf(member.getId()), candidate.getId())) {
            eventRepository.save(
                    LegitimacyEvent.delegate(proposal, member, candidate, clock.instant()));
        }
        return refresh(proposal, member);
    }

    /**
     * Возвращает свою легитимность себе ({@code BR-LEGIT-5}).
     *
     * <p>Доступно в любой момент и без согласия кандидата: доверие — не
     * договор на срок. Если доверие и так у себя, ничего не происходит —
     * повторное нажатие не должно писать в журнал.
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @return состояние легитимности задачи после отзыва
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     */
    @Transactional
    public LegitimacyBoardResponse revoke(UUID memberId, UUID proposalId) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);

        LegitimacyStanding standing = standing(proposal);
        if (standing.isDelegated(member.getId())) {
            eventRepository.save(LegitimacyEvent.revoke(proposal, member, clock.instant()));
        }
        return refresh(proposal, member);
    }

    /**
     * Выдвигает себя кандидатом в лидеры ({@code BR-LEADER-1}).
     *
     * <p>Повторное выдвижение безопасно: кандидатура одна, второй строки не
     * появляется. Так двойное нажатие на клиенте не превращается в «двух
     * кандидатов» с тем же именем.
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @return состояние легитимности задачи после выдвижения
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     * @throws IllegalStateException       если выдвигаться уже поздно
     */
    @Transactional
    public LegitimacyBoardResponse nominate(UUID memberId, UUID proposalId) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);

        if (candidateRepository.findForProposalAndMember(proposal, member.getId()).isEmpty()) {
            candidateRepository.save(
                    Candidate.nominate(proposal, member, clock.instant()));
        }
        return refresh(proposal, member);
    }

    /**
     * Легитимность и лидер по задаче глазами клиента ({@code BR-LEGIT-3},
     * {@code BR-LEADER-3}, {@code BR-LEADER-5}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @return кандидаты, лидер и текущая передача доверия
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     */
    @Transactional
    public LegitimacyBoardResponse board(UUID memberId, UUID proposalId) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        return refresh(proposal, member);
    }

    /**
     * Кому текущий человек передал легитимность и какой у него репутация
     * ({@code BR-LEGIT-3}, {@code BR-REP-2}).
     *
     * <p>Показываем только задачи, по которым сообщество ещё не сказало «не
     * делаем» и которые не закрыты: передача доверия по отклонённой задаче
     * ничего не значит, и строка о ней в профиле только путала бы.
     *
     * <p>Задачи без переданной легитимности тоже попадают в список: «доверие у
     * себя» — это состояние, а не отсутствие данных ({@code BR-LEGIT-5}).
     *
     * @param memberId аккаунт из токена
     * @return репутация и передачи по активным задачам
     * @throws MembershipRequiredException если аккаунт не действующий член
     */
    @Transactional(readOnly = true)
    public MyLegitimacyResponse myLegitimacy(UUID memberId) {
        User member = loadActiveMember(memberId);
        List<Proposal> proposals = proposalRepository.findAllForCommunity(
                member.getCommunity(),
                List.of(ProposalStage.DISCUSSION, ProposalStage.IN_WORK));

        // Один запрос на журнал всех задач вместо одного на каждую: профиль
        // открывается часто, и N+1 здесь заметен.
        Map<UUID, List<LegitimacyEvent>> eventsByProposal = new HashMap<>();
        for (LegitimacyEvent event : eventRepository.findAllForProposals(proposals)) {
            eventsByProposal
                    .computeIfAbsent(event.getProposal().getId(), id -> new ArrayList<>())
                    .add(event);
        }

        // Имена получателей — одним запросом: иначе профиль с десятком задач
        // дёргал бы базу на каждую строку ради одного слова.
        Set<UUID> recipientIds = new HashSet<>();

        List<DelegationResponse> delegations = new ArrayList<>();
        Map<UUID, UUID> recipients = new LinkedHashMap<>();
        for (Proposal proposal : proposals) {
            LegitimacyStanding standing = LegitimacyStanding.of(
                    eventsByProposal.getOrDefault(proposal.getId(), List.of()));
            UUID recipientId = standing.recipientOf(member.getId());
            recipients.put(proposal.getId(), recipientId);
            if (recipientId != null) {
                recipientIds.add(recipientId);
            }
        }

        Map<UUID, String> names = namesOf(recipientIds);
        for (Proposal proposal : proposals) {
            UUID recipientId = recipients.get(proposal.getId());
            delegations.add(new DelegationResponse(
                    proposal.getId(),
                    proposal.getTitle(),
                    recipientId,
                    recipientId == null ? null : names.get(recipientId)));
        }

        return new MyLegitimacyResponse(
                reputationService.reputationOf(member.getId()),
                delegations);
    }

    /**
     * Текущее распределение легитимности по задаче
     * ({@code BR-LEGIT-3}).
     *
     * @param proposal задача
     * @return свёртка журнала
     */
    @Transactional(readOnly = true)
    public LegitimacyStanding standing(Proposal proposal) {
        List<LegitimacyEvent> events = eventRepository.findAllForProposal(proposal);
        return LegitimacyStanding.of(events);
    }

    /**
     * Текущее распределение легитимности глазами клиента.
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @return свёртка журнала
     */
    @Transactional(readOnly = true)
    public LegitimacyStanding standing(UUID memberId, UUID proposalId) {
        User member = loadActiveMember(memberId);
        return standing(loadProposal(member, proposalId));
    }

    /**
     * Сколько членов поддерживают кандидата сейчас.
     *
     * @param proposal    задача
     * @param candidateId кандидат
     * @return число переданных единиц доверия
     */
    @Transactional(readOnly = true)
    public long supporters(Proposal proposal, UUID candidateId) {
        return standing(proposal).supportersOf(candidateId);
    }

    /**
     * Проверяет мандат и, если лидер потерял поддержку, возвращает задачу на
     * переголосование ({@code BR-LEADER-5}, {@code BR-LEADER-7},
     * {@code BR-LIFE-10}).
     * <p>Возвращает задачу на переголосование именно здесь, а не при отзыве:
     * отозвать доверие может любой член и в любой момент, а решение «мандат
     * утрачен» — вопрос соотношения поддержки и порога. Считать его в момент
     * отзыва значило бы, что итог зависит от того, кто первым нажал кнопку.
     *
     * @param proposal задача
     * @return состояние задачи после проверки
     */
    @Transactional
    public Proposal reconcile(Proposal proposal) {
        Instant now = clock.instant();
        Optional<LeaderMandate> active = mandateRepository.findActive(proposal);

        if (proposal.getStage() != ProposalStage.IN_WORK) {
            // В обсуждении действующего мандата быть не может: лидер выбран
            // только решением сообщества ({@code BR-LEADER-3}).
            active.ifPresent(mandate ->
                    mandate.end(MandateEndReason.MANDATE_LOST, now));
            return proposal;
        }

        if (active.isPresent()) {
            LeaderMandate mandate = active.get();
            long support = standing(proposal).supportersOf(mandate.getLeader().getId());
            if (!mandate.isRetainedBy(support, thresholds.legitimacyRetainRatio())) {
                mandate.end(MandateEndReason.MANDATE_LOST, now);
                // BR-LEADER-7, BR-LIFE-10: сообщество заново решает,
                // продолжать ли задачу, и при «да» назначает нового лидера.
                proposal.reopenForRevote(now);
            }
            return proposal;
        }

        electLeader(proposal, now);
        return proposal;
    }

    /**
     * Назначает лидера по действующим кандидатам ({@code BR-LEGIT-4},
     * {@code BR-LEADER-3}).
     *
     * <p>Если никто не собрал поддержки, лидера нет: мандат без доверия
     * сообщества — это назначение, а не выборы.
     */
    private void electLeader(Proposal proposal, Instant now) {
        LegitimacyStanding standing = standing(proposal);
        List<Candidate> candidates = candidateRepository.findAllForProposal(proposal);
        if (candidates.isEmpty()) {
            return;
        }

        List<UUID> ids = candidates.stream().map(candidate -> candidate.getMember().getId()).toList();
        Map<UUID, Long> reputation = reputationService.reputationOf(ids);
        List<LeaderElection.CandidateScore> scores = candidates.stream()
                .map(candidate -> new LeaderElection.CandidateScore(
                        candidate.getMember().getId(),
                        candidate.getMember().getDisplayName(),
                        standing.supportersOf(candidate.getMember().getId()),
                        reputation.getOrDefault(candidate.getMember().getId(), 0L),
                        candidate.getMember().getJoinedAt()))
                .toList();

        LeaderElection.elect(scores).ifPresent(winner -> mandateRepository.save(
                LeaderMandate.grant(proposal, memberOf(proposal, winner.candidateId()),
                        winner.support(), now)));
    }

    /**
     * Пересобирает ответ и заодно применяет правила мандата.
     */
    private LegitimacyBoardResponse refresh(Proposal proposal, User viewer) {
        reconcile(proposal);
        return boardResponse(proposal, viewer);
    }

    private LegitimacyBoardResponse boardResponse(Proposal proposal, User viewer) {
        LegitimacyStanding standing = standing(proposal);
        List<Candidate> candidates = candidateRepository.findAllForProposal(proposal);

        List<UUID> ids = candidates.stream().map(candidate -> candidate.getMember().getId()).toList();
        Map<UUID, Long> reputation = reputationService.reputationOf(ids);

        List<CandidateResponse> responses = new ArrayList<>(candidates.stream()
                .map(candidate -> new CandidateResponse(
                        candidate.getMember().getId(),
                        candidate.getMember().getDisplayName(),
                        standing.supportersOf(candidate.getMember().getId()),
                        reputation.getOrDefault(candidate.getMember().getId(), 0L),
                        Objects.equals(candidate.getMember().getId(), viewer.getId())))
                .toList());

        // Порядок задаёт домен, а не SQL: тай-брейкеры — правило сообщества
        // ({@code BR-LEADER-4}), и клиент показывает список как есть.
        responses.sort(Comparator
                .comparingLong(CandidateResponse::support).reversed()
                .thenComparing(Comparator.comparingLong(CandidateResponse::reputation).reversed())
                .thenComparing(CandidateResponse::displayName));

        LeaderResponse leader = mandateRepository.findActive(proposal)
                .map(mandate -> leaderResponse(mandate, standing))
                .orElseGet(() -> lastEndedMandate(proposal));

        return new LegitimacyBoardResponse(
                proposal.getId(),
                responses,
                leader,
                standing.recipientOf(viewer.getId()),
                standing.totalDelegated(),
                countMembers(proposal.getCommunity()));
    }

    /**
     * Завершённый мандат — чтобы клиент мог объяснить, что лидер был и почему
     * его больше нет ({@code BR-LEADER-6}).
     */
    private LeaderResponse lastEndedMandate(Proposal proposal) {
        List<LeaderMandate> history = mandateRepository.findAllForProposal(proposal);
        if (history.isEmpty()) {
            return null;
        }
        LeaderMandate last = history.get(history.size() - 1);
        return leaderResponse(last, standing(proposal));
    }

    private LeaderResponse leaderResponse(LeaderMandate mandate, LegitimacyStanding standing) {
        long support = standing.supportersOf(mandate.getLeader().getId());
        return new LeaderResponse(
                mandate.getLeader().getId(),
                mandate.getLeader().getDisplayName(),
                support,
                mandate.getInitialSupport(),
                mandate.requiredSupport(thresholds.legitimacyRetainRatio()),
                mandate.getStartedAt(),
                mandate.isActive(),
                mandate.getEndReason());
    }

    private User memberOf(Proposal proposal, UUID memberId) {
        return userRepository.findById(memberId)
                .filter(found -> Objects.equals(found.getCommunity(), proposal.getCommunity()))
                .orElseThrow(() -> new CandidateNotFoundException(memberId));
    }

    /** Имена получателей доверия: одним запросом на весь профиль. */
    private Map<UUID, String> namesOf(Set<UUID> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(memberIds)) {
            names.put(user.getId(), user.getDisplayName());
        }
        return names;
    }

    private long countMembers(Community community) {
        return userRepository.countByCommunityAndStatus(community, UserStatus.ACTIVE);
    }

    private User loadCandidate(User member, UUID candidateId) {
        Objects.requireNonNull(candidateId, "кандидат обязателен");
        if (Objects.equals(candidateId, member.getId())) {
            // Не «кандидат не найден»: человек найден, но действие бессмысленно.
            // Так клиент получает понятную причину, а не намёк на опечатку.
            throw new IllegalArgumentException(
                    "передать легитимность себе нельзя: она уже своя");
        }
        return userRepository.findById(candidateId)
                // Кандидат из другого сообщества не должен даже подтверждать
                // своё существование ({@code BR-PRIV-1}).
                .filter(found -> Objects.equals(found.getCommunity(), member.getCommunity()))
                .orElseThrow(() -> new CandidateNotFoundException(candidateId));
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
}

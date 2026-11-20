package ru.peerflow.proposal.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import ru.peerflow.config.PeerflowThresholds;
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.discussion.repo.ProposalParticipant;
import ru.peerflow.legitimacy.service.LegitimacyService;
import ru.peerflow.proposal.domain.DecisionTally;
import ru.peerflow.proposal.domain.DecisionVote;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.domain.ProposalStage;
import ru.peerflow.proposal.domain.VotingOutcome;
import ru.peerflow.proposal.domain.VotingRules;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.proposal.web.dto.MyVote;
import ru.peerflow.proposal.web.dto.ProposalListResponse;
import ru.peerflow.proposal.web.dto.ProposalResponse;

/**
 * Предложения и голосование «делаем / не делаем»
 * ({@code BR-OVER-2}, {@code BR-LIFE-1}–{@code BR-LIFE-5}, {@code BR-VOTE-4},
 * {@code BR-VOTE-9}–{@code BR-VOTE-13}).
 *
 * <p>Что здесь, а что в домене: сами правила (бюджет обязателен, голос один,
 * когда закрывается голосование) живут в {@link Proposal} и
 * {@link VotingOutcome}. Сервис только загружает данные, подставляет число
 * членов сообщества и применяет вердикт. Поэтому правило закрытия одинаково
 * при чтении списка, при открытии карточки и при голосовании.
 *
 * <p>Почему голосование закрывается «при чтении», а не по расписанию: в MVP нет
 * фоновых задач и планировщика, а итог голосования — детерминированная функция
 * от времени и голосов. Достаточно пересчитать его в момент, когда кто-то
 * смотрит на предложение: результат будет тот же, что и при точном срабатывании
 * по часам, но не нужен отдельный процесс, который может упасть.
 */
@Service
public class ProposalService {

    /** Стадии, которые показываются в списке ({@code BR-RANK-5}). */
    private static final Set<ProposalStage> LISTED_STAGES =
            Set.of(ProposalStage.IN_WORK, ProposalStage.DISCUSSION);

    private final ProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final LegitimacyService legitimacyService;
    private final PeerflowThresholds thresholds;
    private final Clock clock;

    public ProposalService(ProposalRepository proposalRepository,
                           UserRepository userRepository,
                           MessageRepository messageRepository,
                           LegitimacyService legitimacyService,
                           PeerflowThresholds thresholds,
                           Clock clock) {
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.legitimacyService = legitimacyService;
        this.thresholds = thresholds;
        this.clock = clock;
    }

    /**
     * Вносит предложение ({@code BR-OVER-2}, {@code BR-LIFE-1}).
     *
     * @param authorId аккаунт из токена
     * @param request  тема, описание и обязательный бюджет
     * @return созданное предложение в стадии «На обсуждении»
     * @throws MembershipRequiredException если автор не действующий член
     * @throws IllegalArgumentException    если бюджет не положителен
     */
    @Transactional
    public ProposalResponse create(UUID authorId, CreateProposalRequest request) {
        User author = loadActiveMember(authorId);
        Proposal proposal = Proposal.create(
                author,
                author.getCommunity(),
                request.title().trim(),
                request.description().trim(),
                request.budget(),
                clock.instant());

        Proposal saved = proposalRepository.save(proposal);
        return response(saved, author, countMembers(author.getCommunity()), Set.of());
    }

    /**
     * Список предложений двумя секциями: «В работе» и «На обсуждении»
     * ({@code BR-RANK-5}), отсортированный выбранным способом
     * ({@code BR-RANK-4}).
     *
     * <p>Истёкшие голосования закрываются здесь же: иначе предложение
     * показывалось бы как «на обсуждении» ещё сутки после дедлайна, пока
     * кто-нибудь не откроет его карточку.
     *
     * @param memberId аккаунт из токена
     * @param sort     сортировка внутри секций
     * @return секции в порядке «В работе», «На обсуждении»
     * @throws MembershipRequiredException если аккаунт не действующий член
     */
    @Transactional
    public ProposalListResponse list(UUID memberId, ProposalSort sort) {
        User member = loadActiveMember(memberId);
        Community community = member.getCommunity();

        List<Proposal> proposals =
                new ArrayList<>(proposalRepository.findAllForCommunity(community, LISTED_STAGES));

        // Число членов одно на весь список: иначе на каждое предложение
        // выполнялся бы отдельный count — ровно тот N+1, от которого мы
        // избавлялись в запросе.
        long members = countMembers(community);
        closeExpired(proposals, members);
        // BR-LEADER-5, BR-LIFE-10: мандат проверяется при чтении — другого
        // момента в MVP нет. Без этого лидер без поддержки показывался бы
        // действующим, пока кто-нибудь не откроет карточку.
        reconcileMandates(proposals);

        // Авторы сообщений — тоже участники ({@code BR-RANK-2}), и берём их
        // одним запросом на весь список: обходить сообщения каждого
        // предложения — это тот же N+1, только по другой таблице.
        Map<UUID, Set<UUID>> messageAuthors = messageAuthors(proposals);

        Comparator<Proposal> order = comparatorFor(sort, messageAuthors);
        List<ProposalResponse> inWork = section(
                proposals, ProposalStage.IN_WORK, order, member, members, messageAuthors);
        List<ProposalResponse> discussing = section(
                proposals, ProposalStage.DISCUSSION, order, member, members, messageAuthors);

        return new ProposalListResponse(inWork, discussing);
    }

    /**
     * Одно предложение с голосами ({@code BR-PRIV-1}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId предложение
     * @return предложение глазами клиента
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если предложения нет в этом сообществе
     */
    @Transactional
    public ProposalResponse get(UUID memberId, UUID proposalId) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        long members = countMembers(member.getCommunity());
        closeIfExpired(proposal, members);
        // Лидер выбирается в тот момент, когда сообщество сказало «делаем»
        // (BR-LEADER-3), а мандат может быть утрачен позже (BR-LEADER-5).
        // Обе проверки — здесь, потому что других моментов в MVP нет.
        legitimacyService.reconcile(proposal);
        return response(proposal, member, members, messageAuthors(proposal));
    }

    /**
     * Голосует «делаем / не делаем» или меняет свой голос ({@code BR-VOTE-4},
     * {@code BR-VOTE-13}).
     *
     * <p>После голоса сразу проверяется, не пора ли закрыть голосование:
     * если человек стал последним, кого не хватало до досрочки, — предложение
     * переходит в работу или отклоняется в этом же запросе, а не «когда-нибудь
     * потом».
     *
     * @param memberId   аккаунт из токена
     * @param proposalId предложение
     * @param inFavor    {@code true} — «делаем»
     * @return предложение после голосования
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если предложения нет в этом сообществе
     * @throws IllegalStateException       если голосование уже закрыто
     */
    @Transactional
    public ProposalResponse vote(UUID memberId, UUID proposalId, boolean inFavor) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        long members = countMembers(member.getCommunity());

        // Сначала фиксируем то, что уже истекло: голос в закрытое голосование
        // не должен проходить только потому, что никто не открывал список.
        closeIfExpired(proposal, members);

        proposal.castVote(member, inFavor);
        closeIfExpired(proposal, members);
        legitimacyService.reconcile(proposal);

        return response(proposal, member, members, messageAuthors(proposal));
    }

    /**
     * Порядок сортировки внутри секций ({@code BR-RANK-4}).
     *
     * <p>При равенстве метрики порядок добивается по времени создания: иначе
     * два одинаковых по популярности предложения «прыгали» бы между запросами.
     */
    private Comparator<Proposal> comparatorFor(ProposalSort sort,
                                               Map<UUID, Set<UUID>> messageAuthors) {
        Comparator<Proposal> byNewest =
                Comparator.comparing(Proposal::getCreatedAt).reversed();
        return switch (sort) {
            case POPULAR -> Comparator
                    .comparingLong((Proposal proposal) ->
                            proposal.popularity(authorsOf(proposal, messageAuthors)))
                    .reversed()
                    .thenComparing(byNewest);
            case BUDGET -> Comparator
                    .comparingLong(Proposal::getBudget).reversed()
                    .thenComparing(byNewest);
            case NEW -> byNewest;
        };
    }

    private List<ProposalResponse> section(List<Proposal> proposals, ProposalStage stage,
                                           Comparator<Proposal> order, User viewer,
                                           long members,
                                           Map<UUID, Set<UUID>> messageAuthors) {
        return proposals.stream()
                .filter(proposal -> proposal.getStage() == stage)
                .sorted(order)
                .map(proposal -> response(
                        proposal, viewer, members, authorsOf(proposal, messageAuthors)))
                .toList();
    }

    /**
     * Авторы сообщений по предложениям — одним запросом ({@code BR-RANK-2}).
     *
     * <p>Возвращаем множество идентификаторов, а не число: уникальность
     * участников считает домен вместе с автором и голосовавшими, иначе одна и
     * та же формула жила бы в двух местах.
     */
    private Map<UUID, Set<UUID>> messageAuthors(List<Proposal> proposals) {
        if (proposals.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<UUID>> authors = new HashMap<>();
        for (ProposalParticipant participant : messageRepository.findParticipants(proposals)) {
            authors.computeIfAbsent(participant.proposalId(), id -> new HashSet<>())
                    .add(participant.authorId());
        }
        return authors;
    }

    private Set<UUID> messageAuthors(Proposal proposal) {
        return authorsOf(proposal, messageAuthors(List.of(proposal)));
    }

    private Set<UUID> authorsOf(Proposal proposal, Map<UUID, Set<UUID>> messageAuthors) {
        return messageAuthors.getOrDefault(proposal.getId(), Set.of());
    }

    /**
     * Закрывает голосования, у которых истёк срок, — для всего списка сразу.
     */
    private void closeExpired(List<Proposal> proposals, long members) {
        VotingRules rules = rules();
        Instant now = clock.instant();
        for (Proposal proposal : proposals) {
            if (proposal.getStage() != ProposalStage.DISCUSSION) {
                continue;
            }
            VotingOutcome outcome = VotingOutcome.evaluate(
                    proposal.tally(), rules, members, proposal.getVotingOpenedAt(), now);
            apply(proposal, outcome, now);
        }
    }

    private void closeIfExpired(Proposal proposal, long members) {
        if (proposal.getStage() != ProposalStage.DISCUSSION) {
            return;
        }
        Instant now = clock.instant();
        VotingOutcome outcome = VotingOutcome.evaluate(
                proposal.tally(), rules(), members, proposal.getVotingOpenedAt(), now);
        apply(proposal, outcome, now);
    }

    /**
     * Проверяет мандаты всех предложений списка
     * ({@code BR-LEADER-5}, {@code BR-LIFE-10}).
     *
     * <p>Ровно та же причина, что и у закрытия голосования «при чтении»: итог —
     * детерминированная функция от поддержки и порога, и держать для неё
     * отдельный процесс не нужно.
     */
    private void reconcileMandates(List<Proposal> proposals) {
        for (Proposal proposal : proposals) {
            legitimacyService.reconcile(proposal);
        }
    }

    private void apply(Proposal proposal, VotingOutcome outcome, Instant now) {
        if (!outcome.isFinal()) {
            return;
        }
        switch (outcome.decision()) {
            case ACCEPT -> {
                proposal.accept(now);
                // BR-LIFE-5, BR-LEADER-3: как только принято «делаем»,
                // лидером становится кандидат с наибольшей легитимностью.
                legitimacyService.reconcile(proposal);
            }
            case REJECT -> proposal.reject(now);
            case KEEP_OPEN -> {
                // Ничего не делаем: голосование продолжается.
            }
        }
    }

    private long countMembers(Community community) {
        return userRepository.countByCommunityAndStatus(community, UserStatus.ACTIVE);
    }

    private VotingRules rules() {
        return new VotingRules(
                thresholds.proposalVotingWindow(),
                thresholds.proposalEarlyCloseRatio(),
                thresholds.proposalEarlyCloseLead());
    }

    private ProposalResponse response(Proposal proposal, User viewer, long members,
                                      Collection<UUID> messageAuthors) {
        DecisionTally tally = proposal.tally();
        boolean open = proposal.getStage() == ProposalStage.DISCUSSION;

        // Итог считаем только для открытого голосования: у решённого
        // предложения стадия — уже состоявшееся решение, и пересчитывать его
        // заново значило бы показывать человеку другую причину, чем та, по
        // которой решение приняли.
        String votingStatus = open
                ? VotingOutcome.evaluate(tally, rules(), members,
                        proposal.getCreatedAt(), clock.instant()).reason()
                : closedReason(proposal.getStage());

        DecisionVote mine = proposal.voteOf(viewer);
        return new ProposalResponse(
                proposal.getId(),
                proposal.getTitle(),
                proposal.getDescription(),
                proposal.getAuthor().getId(),
                proposal.getAuthor().getDisplayName(),
                proposal.getBudget(),
                proposal.getStage(),
                tally.votesFor(),
                tally.votesAgainst(),
                proposal.participants(messageAuthors),
                proposal.popularity(messageAuthors),
                proposal.getCreatedAt(),
                proposal.getDecidedAt(),
                rules().deadlineFor(proposal.getCreatedAt()),
                !open,
                votingStatus,
                mine == null ? null : new MyVote(mine.isInFavor(), mine.getUpdatedAt()));
    }

    /**
     * Пояснение к уже закрытому голосованию.
     *
     * <p>Не пересчитываем итог заново: стадия — это состоявшееся решение, и
     * «почему» должно описывать его, а не текущую арифметику голосов.
     */
    private String closedReason(ProposalStage stage) {
        return switch (stage) {
            case IN_WORK -> "сообщество решило: делаем";
            case REJECTED -> "сообщество решило: не делаем";
            case CLOSED -> "задача закрыта";
            case DISCUSSION -> throw new IllegalStateException(
                    "обсуждение ещё идёт: " + stage);
        };
    }

    private Proposal loadProposal(User member, UUID proposalId) {
        Objects.requireNonNull(proposalId, "предложение обязательно");
        // Ищем только в своём сообществе: чужое предложение не должно даже
        // подтверждать своё существование ({@code BR-PRIV-1}).
        return proposalRepository.findByIdWithVotes(proposalId)
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

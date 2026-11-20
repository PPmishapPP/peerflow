package ru.peerflow.proposal.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import ru.peerflow.legitimacy.domain.LeaderMandate;
import ru.peerflow.legitimacy.domain.LegitimacyStanding;
import ru.peerflow.legitimacy.domain.MandateEndReason;
import ru.peerflow.legitimacy.repo.LeaderMandateRepository;
import ru.peerflow.legitimacy.service.LegitimacyService;
import ru.peerflow.legitimacy.service.NotLeaderException;
import ru.peerflow.proposal.domain.ClosureVote;
import ru.peerflow.proposal.domain.DecisionTally;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.domain.ProposalStage;
import ru.peerflow.proposal.domain.VotingOutcome;
import ru.peerflow.proposal.domain.VotingRules;
import ru.peerflow.proposal.repo.ClosureVoteRepository;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.web.dto.ClosureStateResponse;
import ru.peerflow.proposal.web.dto.MyClosureVote;
import ru.peerflow.realtime.EventPublisher;
import ru.peerflow.realtime.EventType;
import ru.peerflow.reputation.service.ReputationService;

/**
 * Закрытие задачи: лидер запускает процедуру, сообщество решает, выполнена ли
 * работа ({@code BR-LIFE-8}, {@code BR-LIFE-9}, {@code BR-OVER-9},
 * {@code BR-VOTE-5}).
 *
 * <p>Запускает только лидер: это его мандат ({@code BR-LIFE-6}), и «закрыть
 * задачу» — заявление исполнителя о том, что работа сделана. Решает
 * сообщество: лидер не может объявить свою работу выполненной сам
 * ({@code BR-OVER-9}).
 *
 * <p>Правила голосования те же, что у решения «делаем / не делаем»
 * ({@code BR-VOTE-9}–{@code BR-VOTE-13}): срок, досрочное закрытие и простое
 * большинство. Отдельные пороги означали бы, что сообщество голосует по двум
 * разным правилам об одном и том же, и человеку пришлось бы помнить, какое
 * правило сейчас действует.
 *
 * <p>Итог считается «при чтении»: держать для него отдельный процесс в MVP
 * незачем, а результат — детерминированная функция от голосов и времени.
 *
 * <p>При закрытии оставшаяся легитимность лидера переходит в репутацию
 * ({@code BR-REP-1}): доверие, которое сообщество ему оказало, превращается в
 * достижение, а не исчезает.
 */
@Service
public class ClosureService {

    private final ClosureVoteRepository closureVoteRepository;
    private final ProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final LeaderMandateRepository mandateRepository;
    private final LegitimacyService legitimacyService;
    private final ReputationService reputationService;
    private final EventPublisher events;
    private final PeerflowThresholds thresholds;
    private final Clock clock;

    public ClosureService(ClosureVoteRepository closureVoteRepository,
                          ProposalRepository proposalRepository,
                          UserRepository userRepository,
                          LeaderMandateRepository mandateRepository,
                          LegitimacyService legitimacyService,
                          ReputationService reputationService,
                          EventPublisher events,
                          PeerflowThresholds thresholds,
                          Clock clock) {
        this.closureVoteRepository = closureVoteRepository;
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
        this.mandateRepository = mandateRepository;
        this.legitimacyService = legitimacyService;
        this.reputationService = reputationService;
        this.events = events;
        this.thresholds = thresholds;
        this.clock = clock;
    }

    /**
     * Лидер запускает процедуру закрытия ({@code BR-LIFE-8}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @return состояние голосования после запуска
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     * @throws NotLeaderException          если процедуру запускает не лидер
     * @throws IllegalStateException       если задача не в работе или процедура
     *                                     уже запущена
     */
    @Transactional
    public ClosureStateResponse open(UUID memberId, UUID proposalId) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        // Сначала состояние задачи, потом мандат: у закрытой задачи мандата
        // уже нет, и ответ «нужен мандат» сбивал бы с толку — закрывать нечего
        // независимо от того, кто просит.
        if (proposal.getStage() != ProposalStage.IN_WORK) {
            throw new IllegalStateException(
                    "закрыть можно только задачу в работе: " + proposal.getStage());
        }
        requireLeader(proposal, member);
        proposal.openClosure(clock.instant());
        return stateOf(proposal, member, countMembers(proposal.getCommunity()));
    }

    /**
     * Голосует «выполнено / не выполнено» или меняет ответ
     * ({@code BR-VOTE-5}, {@code BR-VOTE-13}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @param completed  {@code true} — «выполнено»
     * @return состояние голосования после голоса
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     * @throws IllegalStateException       если процедура закрытия не идёт
     */
    @Transactional
    public ClosureStateResponse vote(UUID memberId, UUID proposalId, boolean completed) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        long members = countMembers(proposal.getCommunity());

        // Сначала фиксируем то, что уже истекло: голос в закрытое голосование
        // не должен проходить только потому, что никто не открывал экран.
        closeIfDecided(proposal, members);
        proposal.castClosureVote(member, completed);
        closeIfDecided(proposal, members);
        // BR-VOTE-5: счётчики «выполнено / нет» — горячая поверхность.
        // Событие после коммита: несохранённый голос клиенту не виден.
        events.publishAfterCommit(
                member.getCommunity(), EventType.VOTE_CHANGED, proposal.getId());

        return stateOf(proposal, member, members);
    }

    /**
     * Состояние голосования по выполнению ({@code BR-LIFE-9}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId задача
     * @return счётчики, срок и свой ответ
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если задачи нет в этом сообществе
     */
    @Transactional
    public ClosureStateResponse state(UUID memberId, UUID proposalId) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        long members = countMembers(proposal.getCommunity());
        closeIfDecided(proposal, members);
        return stateOf(proposal, member, members);
    }

    /**
     * Применяет итог голосования «выполнено / не выполнено», если оно уже
     * закрыто по сроку или досрочно ({@code BR-LIFE-9}).
     *
     * <p>Планировщика в MVP нет: итог — детерминированная функция от голосов и
     * времени, и его достаточно применить в тот момент, когда кто-то открыл
     * задачу. Иначе закрытая задача висела бы «в работе» до первого визита.
     *
     * @param proposal задача
     * @return задача после применения итога
     */
    @Transactional
    public Proposal reconcile(Proposal proposal) {
        closeIfDecided(proposal, countMembers(proposal.getCommunity()));
        return proposal;
    }

    /**
     * Закрывает задачу, если сообщество уже решило ({@code BR-LIFE-9},
     * {@code BR-REP-1}).
     */
    private void closeIfDecided(Proposal proposal, long members) {
        if (!proposal.isClosureOpen()) {
            return;
        }
        Instant now = clock.instant();
        VotingOutcome outcome = VotingOutcome.evaluate(
                proposal.closureTally(), rules(), members,
                proposal.getClosureOpenedAt(), now);
        if (!outcome.isFinal()) {
            return;
        }
        if (outcome.decision() == VotingOutcome.Decision.ACCEPT) {
            finish(proposal, now);
        } else {
            // «Не выполнено» — не отказ от задачи, а требование доработать:
            // задача остаётся в работе, а лидер может запустить процедуру
            // заново (BR-OVER-9).
            proposal.cancelClosure(now);
        }
    }

    /**
     * Закрывает задачу и переносит легитимность лидера в репутацию
     * ({@code BR-REP-1}).
     */
    private void finish(Proposal proposal, Instant now) {
        LeaderMandate mandate = mandateRepository.findActive(proposal).orElse(null);
        long points = 0;
        if (mandate != null) {
            LegitimacyStanding standing = legitimacyService.standing(proposal);
            points = standing.supportersOf(mandate.getLeader().getId());
            if (points > 0) {
                reputationService.awardForClosedTask(
                        proposal, mandate.getLeader(), points, now);
            }
            mandate.end(MandateEndReason.TASK_CLOSED, now);
        }
        proposal.close(now);
        // BR-LIFE-9: задача закрыта — это событие для списка и карточки.
        events.publishAfterCommit(
                proposal.getCommunity(), EventType.TASK_CLOSED, proposal.getId());
    }

    private ClosureStateResponse stateOf(Proposal proposal, User viewer, long members) {
        DecisionTally tally = proposal.closureTally();
        Optional<LeaderMandate> active = mandateRepository.findActive(proposal);

        // Лидера может уже не быть (мандат утрачен) — тогда показываем
        // последний мандат: человеку нужно знать, чья это была задача.
        Optional<LeaderMandate> shown = active.isPresent()
                ? active
                : lastMandate(proposal);

        boolean open = proposal.isClosureOpen();
        Instant deadline = open
                ? rules().deadlineFor(proposal.getClosureOpenedAt())
                : null;

        String status = statusOf(proposal, tally, open);
        MyClosureVote myVote = null;
        ClosureVote vote = proposal.closureVoteOf(viewer);
        if (vote != null) {
            myVote = new MyClosureVote(vote.isCompleted(), vote.getUpdatedAt());
        }

        long awarded = reputationService.awardedFor(proposal);

        return new ClosureStateResponse(
                proposal.getId(),
                proposal.getStage(),
                open,
                tally.votesFor(),
                tally.votesAgainst(),
                members,
                deadline,
                status,
                myVote,
                shown.map(mandate -> mandate.getLeader().getId()).orElse(null),
                shown.map(mandate -> mandate.getLeader().getDisplayName()).orElse(null),
                awarded);
    }

    private String statusOf(Proposal proposal, DecisionTally tally, boolean open) {
        if (proposal.getStage() == ProposalStage.CLOSED) {
            return "задача закрыта: сообщество признало работу выполненной";
        }
        if (!open) {
            return proposal.getStage() == ProposalStage.IN_WORK
                    ? "лидер ещё не запускал процедуру закрытия"
                    : "задача не в работе";
        }
        if (tally.total() == 0) {
            return "лидер запустил закрытие, ждём голоса сообщества";
        }
        return VotingOutcome.evaluate(tally, rules(),
                countMembers(proposal.getCommunity()),
                proposal.getClosureOpenedAt(), clock.instant()).reason();
    }

    private Optional<LeaderMandate> lastMandate(Proposal proposal) {
        List<LeaderMandate> history = mandateRepository.findAllForProposal(proposal);
        return history.isEmpty()
                ? Optional.empty()
                : Optional.of(history.get(history.size() - 1));
    }

    /**
     * Проверяет, что процедуру закрытия запускает действующий лидер задачи
     * ({@code BR-LIFE-6}, {@code BR-LIFE-8}).
     */
    private void requireLeader(Proposal proposal, User member) {
        LeaderMandate mandate = mandateRepository.findActive(proposal)
                .orElseThrow(() -> new NotLeaderException(proposal.getId()));
        if (!Objects.equals(mandate.getLeader().getId(), member.getId())) {
            throw new NotLeaderException(proposal.getId());
        }
    }

    private VotingRules rules() {
        return new VotingRules(
                thresholds.proposalVotingWindow(),
                thresholds.proposalEarlyCloseRatio(),
                thresholds.proposalEarlyCloseLead());
    }

    private long countMembers(Community community) {
        return userRepository.countByCommunityAndStatus(community, UserStatus.ACTIVE);
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

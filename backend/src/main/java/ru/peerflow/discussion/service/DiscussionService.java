package ru.peerflow.discussion.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.peerflow.auth.service.AccountNotFoundException;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.MembershipRequiredException;
import ru.peerflow.config.PeerflowThresholds;
import ru.peerflow.discussion.domain.Message;
import ru.peerflow.discussion.domain.MessageOrder;
import ru.peerflow.discussion.domain.ReactionKind;
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.discussion.web.dto.MessageResponse;
import ru.peerflow.discussion.web.dto.PostMessageRequest;
import ru.peerflow.discussion.web.dto.ThreadResponse;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalNotFoundException;

/**
 * Дискуссия под предложением: сообщения, ответы и реакции
 * ({@code BR-DISC-1}–{@code BR-DISC-3}, {@code BR-VOTE-6}).
 *
 * <p>Что здесь, а что в домене: правила «одна реакция на человека», «ответ
 * только в свою ветку», «текст не пустой» живут в {@link Message}; порядок
 * сообщений — в {@link MessageOrder}. Сервис только загружает данные,
 * проверяет доступ и собирает дерево ответов.
 *
 * <p>Дерево собирает сервер, а не клиент: вложенность и порядок внутри ветки —
 * правило сообщества ({@code BR-DISC-6}). Если бы клиент восстанавливал
 * дерево сам, порядок разъехался бы между платформами.
 *
 * <p>Скрытие спама — фильтр при чтении, а не удаление: метку можно снять, и
 * сообщение снова станет видимым ({@code BR-SPAM-1}). Вместе со скрытым
 * сообщением уходит и его ветка: отвечать на сообщение, которого не видно,
 * бессмысленно.
 */
@Service
public class DiscussionService {

    private final MessageRepository messageRepository;
    private final ProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final PeerflowThresholds thresholds;
    private final Clock clock;

    public DiscussionService(MessageRepository messageRepository,
                             ProposalRepository proposalRepository,
                             UserRepository userRepository,
                             PeerflowThresholds thresholds,
                             Clock clock) {
        this.messageRepository = messageRepository;
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
        this.thresholds = thresholds;
        this.clock = clock;
    }

    /**
     * Пишет сообщение или ответ на сообщение ({@code BR-DISC-1},
     * {@code BR-DISC-3}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId предложение, под которым идёт дискуссия
     * @param request    текст и, возможно, родительское сообщение
     * @return ветка обсуждения после добавления
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если предложения нет в этом сообществе
     * @throws MessageNotFoundException    если родителя нет в этом предложении
     */
    @Transactional
    public ThreadResponse post(UUID memberId, UUID proposalId,
                               PostMessageRequest request) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        Message parent = loadParent(proposal, request.parentId());

        Message message = Message.create(
                proposal, member, parent, request.body(), clock.instant());
        messageRepository.save(message);

        return threadFor(member, proposal, MessageSort.POPULAR);
    }

    /**
     * Ставит или меняет реакцию на сообщение ({@code BR-DISC-2},
     * {@code BR-VOTE-6}).
     *
     * <p>Одна реакция на человека: повторное нажатие меняет её вид. Поэтому
     * быстрые повторные нажатия на клиенте не создают дублей и не раздувают
     * счётчики.
     *
     * @param memberId  аккаунт из токена
     * @param messageId сообщение
     * @param kind      вид реакции
     * @return ветка обсуждения после реакции
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws MessageNotFoundException    если сообщения нет в этом сообществе
     */
    @Transactional
    public ThreadResponse react(UUID memberId, UUID messageId, ReactionKind kind) {
        User member = loadActiveMember(memberId);
        Message message = loadMessage(member, messageId);
        message.react(member, kind);
        return threadFor(member, message.getProposal(), MessageSort.POPULAR);
    }

    /**
     * Ветка обсуждения предложения ({@code BR-DISC-1}–{@code BR-DISC-5}).
     *
     * @param memberId   аккаунт из токена
     * @param proposalId предложение
     * @param sort       порядок сообщений на каждом уровне ветки
     * @return корневые сообщения с вложенными ответами
     * @throws MembershipRequiredException если аккаунт не действующий член
     * @throws ProposalNotFoundException   если предложения нет в этом сообществе
     */
    @Transactional(readOnly = true)
    public ThreadResponse thread(UUID memberId, UUID proposalId, MessageSort sort) {
        User member = loadActiveMember(memberId);
        Proposal proposal = loadProposal(member, proposalId);
        return threadFor(member, proposal, sort);
    }

    /**
     * Ветка обсуждения в «Популярном» порядке — для экранов, где выбора ещё
     * нет.
     *
     * @param memberId   аккаунт из токена
     * @param proposalId предложение
     * @return корневые сообщения с вложенными ответами
     */
    @Transactional(readOnly = true)
    public ThreadResponse thread(UUID memberId, UUID proposalId) {
        return thread(memberId, proposalId, MessageSort.POPULAR);
    }

    private ThreadResponse threadFor(User viewer, Proposal proposal, MessageSort sort) {
        List<Message> messages = messageRepository.findAllForProposal(proposal);
        int hideMarks = thresholds.spamHideMarks();
        Comparator<Message> order = sort == MessageSort.NEW
                ? MessageOrder.NEWEST
                : MessageOrder.POPULAR;

        Map<UUID, List<Message>> repliesByParent = new LinkedHashMap<>();
        List<Message> roots = new ArrayList<>();
        for (Message message : messages) {
            if (message.getParent() == null) {
                roots.add(message);
            } else {
                repliesByParent
                        .computeIfAbsent(message.getParent().getId(), id -> new ArrayList<>())
                        .add(message);
            }
        }

        roots.sort(order);
        List<MessageResponse> shown = roots.stream()
                // BR-SPAM-1: скрытое не показываем ни в корне, ни в ветке.
                .filter(root -> !root.isHidden(hideMarks))
                .map(root -> response(root, viewer, repliesByParent, order, hideMarks))
                .toList();

        // Скрытое считаем как разницу: скрытый корень уносит с собой всю ветку,
        // и «сколько скрыто» должно включать ответы, иначе человек решил бы,
        // что спрятано меньше, чем есть ({@code BR-SPAM-1}).
        long visible = shown.stream().mapToLong(DiscussionService::countWithReplies).sum();
        return new ThreadResponse(shown, messages.size() - visible);
    }

    private static long countWithReplies(MessageResponse message) {
        return 1 + message.replies().stream()
                .mapToLong(DiscussionService::countWithReplies)
                .sum();
    }

    private MessageResponse response(Message message, User viewer,
                                     Map<UUID, List<Message>> repliesByParent,
                                     Comparator<Message> order, int hideMarks) {
        List<Message> replies = new ArrayList<>(
                repliesByParent.getOrDefault(message.getId(), List.of()));
        replies.sort(order);

        return new MessageResponse(
                message.getId(),
                message.getAuthor().getId(),
                message.getAuthor().getDisplayName(),
                message.getBody(),
                message.getCreatedAt(),
                message.plusCount(),
                message.minusCount(),
                message.spamCount(),
                reactionOf(message, viewer),
                // BR-DISC-6: порядок и скрытие действуют и внутри веток,
                // вложенно — на каждом уровне одинаково.
                replies.stream()
                        .filter(reply -> !reply.isHidden(hideMarks))
                        .map(reply -> response(reply, viewer, repliesByParent, order, hideMarks))
                        .toList());
    }

    private ReactionKind reactionOf(Message message, User viewer) {
        var reaction = message.reactionOf(viewer);
        return reaction == null ? null : reaction.getKind();
    }

    private Message loadParent(Proposal proposal, String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(parentId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "некорректный идентификатор родительского сообщения");
        }
        // Родитель обязан быть в этом же предложении: иначе ветка уехала бы
        // в чужую задачу ({@code BR-DISC-3}).
        return messageRepository.findById(id)
                .filter(found -> Objects.equals(found.getProposal(), proposal))
                .orElseThrow(() -> new MessageNotFoundException(id));
    }

    private Message loadMessage(User member, UUID messageId) {
        Objects.requireNonNull(messageId, "сообщение обязательно");
        // Ищем только в своём сообществе: чужое сообщение не должно даже
        // подтверждать своё существование ({@code BR-PRIV-1}).
        return messageRepository.findById(messageId)
                .filter(found -> Objects.equals(
                        found.getProposal().getCommunity(), member.getCommunity()))
                .orElseThrow(() -> new MessageNotFoundException(messageId));
    }

    private Proposal loadProposal(User member, UUID proposalId) {
        Objects.requireNonNull(proposalId, "предложение обязательно");
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

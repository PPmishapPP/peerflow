package ru.peerflow.discussion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.MembershipRequiredException;
import ru.peerflow.discussion.domain.Message;
import ru.peerflow.discussion.domain.ReactionKind;
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.discussion.service.DiscussionService;
import ru.peerflow.discussion.service.MessageNotFoundException;
import ru.peerflow.discussion.web.dto.MessageResponse;
import ru.peerflow.discussion.web.dto.PostMessageRequest;
import ru.peerflow.discussion.web.dto.ThreadResponse;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalNotFoundException;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.proposal.web.dto.ProposalResponse;
import ru.peerflow.support.PostgresTestBase;

/**
 * Сообщения, ответы и реакции ({@code BR-DISC-1}–{@code BR-DISC-3},
 * {@code BR-VOTE-6}, {@code BR-PRIV-1}).
 *
 * <p>Ранжирование и скрытие спама проверяются отдельно — в
 * {@link MessageRankingTest}: это другое правило, и смешивать его с
 * «написать и ответить» значило бы, что падение одного теста не говорит, что
 * именно сломалось.
 *
 * <p>Сообщество намеренно небольшое: предложение остаётся «на обсуждении»,
 * пока его никто не поддержал, — иначе голосование закрылось бы досрочно и
 * предложение уехало бы в секцию «В работе».
 */
@SpringBootTest
class DiscussionServiceTest extends PostgresTestBase {

    @Autowired
    private DiscussionService discussionService;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private Community community;
    private User anna;
    private User boris;
    private User vera;
    private UUID proposalId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        community = communityRepository.save(Community.create("Маяк"));
        anna = member("anna", "Анна");
        boris = member("boris", "Борис");
        vera = member("vera", "Вера");
        proposalId = proposalService.create(anna.getId(),
                new CreateProposalRequest("Закупить проектор", "Описание", 35000L)).id();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Сообщение появляется в ветке обсуждения (BR-DISC-1)")
    void postsMessage() {
        List<MessageResponse> thread = post(anna, "Предлагаю взять в аренду");

        assertThat(thread).hasSize(1);
        MessageResponse message = thread.get(0);
        assertThat(message.body()).isEqualTo("Предлагаю взять в аренду");
        assertThat(message.authorName()).isEqualTo("Анна");
        assertThat(message.plus()).isZero();
        assertThat(message.minus()).isZero();
        assertThat(message.spam()).isZero();
        assertThat(message.myReaction()).isNull();
        assertThat(message.replies()).isEmpty();
    }

    @Test
    @DisplayName("Ответ вкладывается в своё сообщение (BR-DISC-3)")
    void repliesAreNested() {
        UUID rootId = post(anna, "Аренда").get(0).id();

        List<MessageResponse> thread = post(boris, "А почему не покупка?", rootId);

        assertThat(thread).hasSize(1);
        assertThat(thread.get(0).replies()).hasSize(1);
        assertThat(thread.get(0).replies().get(0).body()).isEqualTo("А почему не покупка?");
        assertThat(thread.get(0).replies().get(0).authorName()).isEqualTo("Борис");
    }

    @Test
    @DisplayName("Ветка второго уровня тоже вкладывается (BR-DISC-6)")
    void nestedRepliesKeepTheirLevel() {
        UUID rootId = post(anna, "Аренда").get(0).id();
        UUID firstReplyId = post(boris, "Покупка выгоднее", rootId).get(0).replies().get(0).id();

        List<MessageResponse> thread = post(vera, "Считали амортизацию?", firstReplyId);

        MessageResponse reply = thread.get(0).replies().get(0);
        assertThat(reply.replies()).hasSize(1);
        assertThat(reply.replies().get(0).body()).isEqualTo("Считали амортизацию?");
    }

    @Test
    @DisplayName("Пустое сообщение отклоняется (BR-DISC-1: это аргумент)")
    void rejectsBlankMessage() {
        assertThatThrownBy(() -> post(anna, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пустым");
    }

    @Test
    @DisplayName("Сообщение обрезается по краям")
    void trimsBody() {
        List<MessageResponse> thread = post(anna, "  Аренда  ");

        assertThat(thread.get(0).body()).isEqualTo("Аренда");
    }

    @Test
    @DisplayName("Реакция «плюс» считается в счётчиках (BR-DISC-2)")
    void reactionIsCounted() {
        UUID messageId = post(anna, "Аренда").get(0).id();

        List<MessageResponse> thread = react(boris, messageId, ReactionKind.PLUS);

        assertThat(thread.get(0).plus()).isEqualTo(1);
        assertThat(thread.get(0).minus()).isZero();
    }

    @Test
    @DisplayName("У человека одна реакция: повторное нажатие меняет её, а не добавляет (BR-DISC-2)")
    void reactionIsSinglePerMember() {
        UUID messageId = post(anna, "Аренда").get(0).id();

        react(boris, messageId, ReactionKind.PLUS);
        List<MessageResponse> thread = react(boris, messageId, ReactionKind.MINUS);

        assertThat(thread.get(0).plus())
                .as("старая реакция сменилась, а не осталась рядом")
                .isZero();
        assertThat(thread.get(0).minus()).isEqualTo(1);
    }

    @Test
    @DisplayName("Повторная та же реакция не создаёт дубль")
    void repeatedSameReactionIsIdempotent() {
        UUID messageId = post(anna, "Аренда").get(0).id();

        react(boris, messageId, ReactionKind.PLUS);
        react(boris, messageId, ReactionKind.PLUS);
        List<MessageResponse> thread = react(boris, messageId, ReactionKind.PLUS);

        assertThat(thread.get(0).plus()).isEqualTo(1);
        assertThat(reactionRows(messageId))
                .as("в базе одна строка реакции, а не три")
                .hasSize(1);
    }

    @Test
    @DisplayName("Своя реакция видна человеку, чужая — нет (BR-DISC-2)")
    void showsOwnReactionOnly() {
        UUID messageId = post(anna, "Аренда").get(0).id();
        react(boris, messageId, ReactionKind.SPAM);

        List<MessageResponse> seenByBoris =
                discussionService.thread(boris.getId(), proposalId).messages();
        List<MessageResponse> seenByVera =
                discussionService.thread(vera.getId(), proposalId).messages();

        assertThat(seenByBoris.get(0).myReaction()).isEqualTo(ReactionKind.SPAM);
        assertThat(seenByVera.get(0).myReaction())
                .as("чужая реакция не должна выглядеть своей")
                .isNull();
    }

    @Test
    @DisplayName("Реакции разных людей складываются (BR-DISC-2)")
    void reactionsOfDifferentMembersAddUp() {
        UUID messageId = post(anna, "Аренда").get(0).id();

        react(boris, messageId, ReactionKind.PLUS);
        List<MessageResponse> thread = react(vera, messageId, ReactionKind.PLUS);

        assertThat(thread.get(0).plus()).isEqualTo(2);
    }

    @Test
    @DisplayName("Писать может только член сообщества (BR-PRIV-1)")
    void rejectsMessageFromNonMember() {
        User outsider = userRepository.save(User.register("petr", "hash", "Пётр"));

        assertThatThrownBy(() -> post(outsider, "Я мимо проходил"))
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("Чужое предложение и его обсуждение не видны (BR-PRIV-1)")
    void hidesDiscussionOfOtherCommunity() {
        post(anna, "Аренда");

        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        assertThatThrownBy(() -> discussionService.thread(stranger.getId(), proposalId))
                .isInstanceOf(ProposalNotFoundException.class);
    }

    @Test
    @DisplayName("Ответ на сообщение из другого предложения отклоняется (BR-DISC-3)")
    void rejectsReplyToForeignMessage() {
        UUID otherProposal = proposalService.create(boris.getId(),
                new CreateProposalRequest("Другое", "Описание", 1000L)).id();
        UUID foreignMessageId = postIn(vera, otherProposal, "Чужое обсуждение")
                .messages().get(0).id();

        assertThatThrownBy(() -> post(anna, "Ответ не туда", foreignMessageId))
                .as("ветка не должна уезжать в чужую задачу")
                .isInstanceOf(MessageNotFoundException.class);
    }

    @Test
    @DisplayName("Реакция на несуществующее сообщение — 404-исключение")
    void rejectsReactionToUnknownMessage() {
        assertThatThrownBy(() -> react(anna, UUID.randomUUID(), ReactionKind.PLUS))
                .isInstanceOf(MessageNotFoundException.class);
    }

    @Test
    @DisplayName("Порядок в ветке — по времени: разговор читается сверху вниз")
    void ordersThreadByTime() {
        post(anna, "Первое");
        post(boris, "Второе");

        List<MessageResponse> thread =
                discussionService.thread(vera.getId(), proposalId).messages();

        assertThat(thread).extracting(MessageResponse::body)
                .containsExactly("Первое", "Второе");
    }

    @Test
    @DisplayName("Скрытое спамом считается отдельно, а не молча исчезает (BR-SPAM-1)")
    void reportsNumberOfHiddenMessages() {
        UUID messageId = post(anna, "Шум").get(0).id();
        react(boris, messageId, ReactionKind.SPAM);
        react(vera, messageId, ReactionKind.SPAM);

        // В сообществе три человека: третьей метки нет, но и двух мало —
        // проверяем, что при двух метках счётчик скрытых ещё нулевой.
        assertThat(discussionService.thread(anna.getId(), proposalId).hidden()).isZero();

        react(anna, messageId, ReactionKind.SPAM);

        var thread = discussionService.thread(anna.getId(), proposalId);
        assertThat(thread.messages()).isEmpty();
        assertThat(thread.hidden())
                .as("человек должен знать, что сообщение было и скрыто")
                .isEqualTo(1);
    }

    private List<MessageResponse> post(User author, String body) {
        return post(author, body, null);
    }

    private List<MessageResponse> post(User author, String body, UUID parentId) {
        return postIn(author, proposalId,
                body, parentId == null ? null : parentId.toString()).messages();
    }

    private ThreadResponse postIn(User author, UUID targetProposalId, String body) {
        return postIn(author, targetProposalId, body, null);
    }

    private ThreadResponse postIn(User author, UUID targetProposalId,
                                  String body, String parentId) {
        return discussionService.post(author.getId(), targetProposalId,
                new PostMessageRequest(body, parentId));
    }

    /**
     * Строки реакций сообщения — отдельным запросом.
     *
     * <p>Читаем в своей транзакции, а не через {@code getReactions()}: после
     * выхода из сервиса коллекция уже отсоединена, и её размер нельзя получить
     * без ленивой загрузки. Число строк — то, что действительно проверяем.
     */
    private List<UUID> reactionRows(UUID messageId) {
        return transactionTemplate.execute(status -> messageRepository.findById(messageId)
                .orElseThrow()
                .getReactions()
                .stream()
                .map(reaction -> reaction.getId())
                .toList());
    }

    private List<MessageResponse> react(User member, UUID messageId, ReactionKind kind) {
        return discussionService.react(member.getId(), messageId, kind).messages();
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

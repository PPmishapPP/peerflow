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
import ru.peerflow.discussion.domain.ReactionKind;
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.discussion.service.DiscussionService;
import ru.peerflow.discussion.service.MessageSort;
import ru.peerflow.discussion.web.dto.MessageResponse;
import ru.peerflow.discussion.web.dto.PostMessageRequest;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.support.PostgresTestBase;

/**
 * Порядок сообщений и скрытие спама ({@code BR-DISC-4}–{@code BR-DISC-6},
 * {@code BR-SPAM-1}, {@code BR-LIMIT-2}).
 *
 * <p>Главное, что здесь защищается, — две разные шкалы, которые легко
 * перепутать: «плюсы» поднимают сообщение, «минусы» опускают его только при
 * равенстве плюсов, а «спам» не участвует в порядке вовсе — он скрывает.
 *
 * <p>Сообщество из пяти человек: чтобы ставить метки «спам» от разных людей,
 * нужно несколько независимых аккаунтов, а голосование по предложению при
 * таком составе не должно закрыться само.
 */
@SpringBootTest
class MessageRankingTest extends PostgresTestBase {

    private static final int MEMBERS = 5;

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

    private Community community;
    private final java.util.List<User> members = new java.util.ArrayList<>();
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
                new CreateProposalRequest("Аренда помещения", "Описание", 12000L)).id();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("«Плюс» поднимает сообщение выше (BR-DISC-4)")
    void plusRaisesMessage() {
        UUID plain = postId(members.get(0), "Просто мнение");
        UUID supported = postId(members.get(1), "Поддержанное мнение");
        react(members.get(2), supported, ReactionKind.PLUS);
        react(members.get(3), supported, ReactionKind.PLUS);

        List<MessageResponse> thread = thread(MessageSort.POPULAR);

        assertThat(thread).extracting(MessageResponse::id)
                .as("поддержанное выше, хотя написано позже")
                .containsExactly(supported, plain);
    }

    @Test
    @DisplayName("«Минус» опускает сообщение при равенстве плюсов (BR-DISC-4)")
    void minusLowersOnEqualPlus() {
        UUID neutral = postId(members.get(0), "Нейтральное");
        UUID disliked = postId(members.get(1), "Спорное");
        react(members.get(2), disliked, ReactionKind.MINUS);
        react(members.get(3), disliked, ReactionKind.MINUS);

        List<MessageResponse> thread = thread(MessageSort.POPULAR);

        assertThat(thread).extracting(MessageResponse::id)
                .as("при равных плюсах выше то, у чего меньше минусов")
                .containsExactly(neutral, disliked);
    }

    @Test
    @DisplayName("Плюсы важнее минусов: 3 плюса и 2 минуса выше, чем 2 плюса (BR-DISC-4)")
    void plusOutweighsMinus() {
        UUID twoPlus = postId(members.get(0), "Два плюса");
        UUID threePlusTwoMinus = postId(members.get(1), "Три плюса");

        react(members.get(0), twoPlus, ReactionKind.PLUS);
        react(members.get(1), twoPlus, ReactionKind.PLUS);

        react(members.get(0), threePlusTwoMinus, ReactionKind.PLUS);
        react(members.get(1), threePlusTwoMinus, ReactionKind.PLUS);
        react(members.get(2), threePlusTwoMinus, ReactionKind.PLUS);
        react(members.get(3), threePlusTwoMinus, ReactionKind.MINUS);
        react(members.get(4), threePlusTwoMinus, ReactionKind.MINUS);

        List<MessageResponse> thread = thread(MessageSort.POPULAR);

        assertThat(thread).extracting(MessageResponse::id)
                .as("сначала сравниваются плюсы, минусы решают только при равенстве")
                .containsExactly(threePlusTwoMinus, twoPlus);
    }

    @Test
    @DisplayName("«Новые»: свежее сверху, даже если оно непопулярно (BR-DISC-5)")
    void newestSortPutsFreshFirst() {
        UUID old = postId(members.get(0), "Старое");
        UUID fresh = postId(members.get(1), "Новое");
        react(members.get(2), old, ReactionKind.PLUS);

        List<MessageResponse> thread = thread(MessageSort.NEW);

        assertThat(thread).extracting(MessageResponse::id)
                .as("в «Новых» популярность не влияет: свежее не тонет")
                .containsExactly(fresh, old);
    }

    @Test
    @DisplayName("Три метки «спам» скрывают сообщение (BR-SPAM-1, BR-LIMIT-2)")
    void threeSpamMarksHideMessage() {
        UUID messageId = postId(members.get(0), "Купите слона");

        react(members.get(1), messageId, ReactionKind.SPAM);
        react(members.get(2), messageId, ReactionKind.SPAM);
        List<MessageResponse> afterTwo = thread(MessageSort.NEW);
        assertThat(afterTwo)
                .as("две метки — ещё не спам, сообщение видно")
                .hasSize(1);
        assertThat(afterTwo.get(0).spam()).isEqualTo(2);

        react(members.get(3), messageId, ReactionKind.SPAM);
        List<MessageResponse> afterThree = thread(MessageSort.NEW);

        assertThat(afterThree)
                .as("третья метка скрывает сообщение")
                .isEmpty();
        assertThat(hiddenCount(MessageSort.NEW))
                .as("скрытое не исчезает бесследно: клиенту сообщается число")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Скрытый спам не удаляется: снятие метки возвращает сообщение (BR-SPAM-1)")
    void hiddenSpamIsNotDeleted() {
        UUID messageId = postId(members.get(0), "Шум");
        react(members.get(1), messageId, ReactionKind.SPAM);
        react(members.get(2), messageId, ReactionKind.SPAM);
        react(members.get(3), messageId, ReactionKind.SPAM);
        assertThat(thread(MessageSort.NEW)).isEmpty();

        // Один из поставивших «спам» передумал и поставил «плюс».
        List<MessageResponse> afterChange = react(members.get(1), messageId, ReactionKind.PLUS);

        assertThat(messageRepository.findById(messageId))
                .as("строка сообщения осталась в базе")
                .isPresent();
        assertThat(afterChange)
                .as("меток спама стало две — сообщение снова видно")
                .hasSize(1);
        assertThat(afterChange.get(0).plus()).isEqualTo(1);
        assertThat(afterChange.get(0).spam()).isEqualTo(2);
    }

    @Test
    @DisplayName("Скрытие действует и внутри ветки (BR-DISC-6)")
    void hiddenSpamInsideThread() {
        UUID rootId = postId(members.get(0), "Корень");
        UUID replyId = replyId(members.get(1), "Ответ-спам", rootId);

        react(members.get(2), replyId, ReactionKind.SPAM);
        react(members.get(3), replyId, ReactionKind.SPAM);
        react(members.get(4), replyId, ReactionKind.SPAM);

        List<MessageResponse> thread = thread(MessageSort.POPULAR);

        assertThat(thread).hasSize(1);
        assertThat(thread.get(0).replies())
                .as("скрытый ответ исчезает из ветки, корень остаётся")
                .isEmpty();
    }

    @Test
    @DisplayName("Порядок внутри ветки — по тому же правилу, что и в корне (BR-DISC-6)")
    void sortsRepliesInsideThread() {
        UUID rootId = postId(members.get(0), "Корень");
        UUID plainReply = replyId(members.get(1), "Обычный ответ", rootId);
        UUID supportedReply = replyId(members.get(2), "Поддержанный ответ", rootId);
        react(members.get(3), supportedReply, ReactionKind.PLUS);

        List<MessageResponse> thread = thread(MessageSort.POPULAR);

        assertThat(thread.get(0).replies()).extracting(MessageResponse::id)
                .as("внутри ветки популярное тоже выше")
                .containsExactly(supportedReply, plainReply);
    }

    @Test
    @DisplayName("Сортировка по умолчанию — «Популярные» (BR-DISC-4)")
    void defaultSortIsPopular() {
        assertThat(MessageSort.parse(null)).isEqualTo(MessageSort.POPULAR);
        assertThat(MessageSort.parse("  ")).isEqualTo(MessageSort.POPULAR);
        assertThat(MessageSort.parse("new")).isEqualTo(MessageSort.NEW);
        assertThat(MessageSort.parse("popular")).isEqualTo(MessageSort.POPULAR);
    }

    @Test
    @DisplayName("Неизвестная сортировка — ошибка клиента, а не молчаливая подмена (BR-DISC-4)")
    void rejectsUnknownSort() {
        assertThatThrownBy(() -> MessageSort.parse("oldest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("неизвестная сортировка сообщений");
    }

    private List<MessageResponse> thread(MessageSort sort) {
        return discussionService.thread(members.get(0).getId(), proposalId, sort).messages();
    }

    private long hiddenCount(MessageSort sort) {
        return discussionService.thread(members.get(0).getId(), proposalId, sort).hidden();
    }

    private List<MessageResponse> post(User author, String body) {
        return post(author, body, null);
    }

    private List<MessageResponse> post(User author, String body, UUID parentId) {
        return discussionService.post(author.getId(), proposalId,
                new PostMessageRequest(body, parentId == null ? null : parentId.toString()))
                .messages();
    }

    /**
     * Идентификатор только что написанного сообщения.
     *
     * <p>Ищем по тексту, а не берём первый элемент: порядок в ветке —
     * «Популярные», и только что написанное сообщение не обязано оказаться
     * первым. Текст в каждом тесте уникален.
     */
    private UUID postId(User author, String body) {
        post(author, body);
        return find(body);
    }

    private UUID replyId(User author, String body, UUID parentId) {
        post(author, body, parentId);
        return find(body);
    }

    private UUID find(String body) {
        return flatten(thread(MessageSort.NEW)).stream()
                .filter(message -> message.body().equals(body))
                .findFirst()
                .orElseThrow(() -> new AssertionError("сообщение не найдено: " + body))
                .id();
    }

    private List<MessageResponse> flatten(List<MessageResponse> thread) {
        List<MessageResponse> all = new java.util.ArrayList<>();
        for (MessageResponse message : thread) {
            all.add(message);
            all.addAll(flatten(message.replies()));
        }
        return all;
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

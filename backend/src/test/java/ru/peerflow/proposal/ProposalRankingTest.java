package ru.peerflow.proposal;

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
import ru.peerflow.discussion.repo.MessageRepository;
import ru.peerflow.discussion.service.DiscussionService;
import ru.peerflow.discussion.web.dto.PostMessageRequest;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.service.ProposalSort;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.proposal.web.dto.ProposalResponse;
import ru.peerflow.support.PostgresTestBase;

/**
 * Популярность и сортировки списка ({@code BR-RANK-1}–{@code BR-RANK-4}).
 *
 * <p>Главное, что здесь защищается, — формула популярности: «участники плюс
 * перевес голосов» ({@code BR-RANK-2}). Объём активности её не повышает
 * ({@code BR-RANK-3}): повторные голоса одних и тех же людей и громкие
 * непопулярные предложения не должны всплывать наверх.
 *
 * <p>Сообщество намеренно большое (десять человек): при малом сообществе
 * голосование закрывалось бы досрочно прямо в тесте, и предложение уезжало бы
 * в секцию «В работе», где сортировку уже не проверить.
 */
@SpringBootTest
class ProposalRankingTest extends PostgresTestBase {

    private static final int MEMBERS = 10;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private DiscussionService discussionService;

    private Community community;
    private User anna;
    private User boris;
    private User vera;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        community = communityRepository.save(Community.create("Маяк"));
        for (int i = 0; i < MEMBERS; i++) {
            User member = member("member" + i, "Участник " + i);
            if (i == 0) {
                anna = member;
            } else if (i == 1) {
                boris = member;
            } else if (i == 2) {
                vera = member;
            }
        }
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Популярность = участники + перевес голосов (BR-RANK-2)")
    void popularityIsParticipantsPlusVoteLead() {
        ProposalResponse proposal = create(anna, "Вода", 2000);
        vote(boris, proposal.id(), true);

        ProposalResponse afterVote = proposalService.get(anna.getId(), proposal.id());

        // Участники: автор и Борис = 2. Перевес: 1 − 0 = 1. Итого 3.
        assertThat(afterVote.participants()).isEqualTo(2);
        assertThat(afterVote.votesFor()).isEqualTo(1);
        assertThat(afterVote.popularity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Автор — участник, даже если сам не голосовал (BR-RANK-2)")
    void authorCountsAsParticipantWithoutVoting() {
        ProposalResponse proposal = create(anna, "Вода", 2000);

        ProposalResponse loaded = proposalService.get(anna.getId(), proposal.id());

        assertThat(loaded.participants()).isEqualTo(1);
        assertThat(loaded.popularity())
                .as("голосов нет, перевес 0 — популярность равна числу участников")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Повторные голоса одних и тех же не поднимают популярность (BR-RANK-3)")
    void repeatedVotesDoNotRaisePopularity() {
        ProposalResponse proposal = create(anna, "Вода", 2000);
        vote(boris, proposal.id(), true);
        long popularityOnce = proposalService.get(anna.getId(), proposal.id()).popularity();

        // Борис меняет голос туда-обратно и голосует ещё раз — «объём» растёт,
        // а людей и перевес те же.
        vote(boris, proposal.id(), false);
        vote(boris, proposal.id(), true);
        vote(boris, proposal.id(), true);

        ProposalResponse after = proposalService.get(anna.getId(), proposal.id());

        assertThat(after.participants()).isEqualTo(2);
        assertThat(after.popularity())
                .as("активность одного человека не должна раздувать метрику")
                .isEqualTo(popularityOnce);
    }

    @Test
    @DisplayName("Перевес «против» опускает популярность ниже числа участников (BR-RANK-2)")
    void negativeLeadLowersPopularity() {
        ProposalResponse proposal = create(anna, "Диско-шар", 30000);
        vote(boris, proposal.id(), false);

        ProposalResponse after = proposalService.get(anna.getId(), proposal.id());

        // Участники: 2, перевес: 0 − 1 = −1. Итого 1.
        assertThat(after.participants()).isEqualTo(2);
        assertThat(after.popularity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Сортировка по популярности: поддержанное выше шумного (BR-RANK-1, BR-RANK-3)")
    void popularSortPutsSupportedProposalFirst() {
        ProposalResponse real = create(anna, "Аренда помещения", 12000);
        ProposalResponse noise = create(vera, "Борьба с 5G", 3000);

        // Живое обсуждение: двое поддержали.
        vote(boris, real.id(), true);
        vote(vera, real.id(), true);

        // Шумное: голоса есть, но перевес отрицательный.
        vote(anna, noise.id(), false);
        vote(boris, noise.id(), false);

        List<ProposalResponse> discussing =
                proposalService.list(anna.getId(), ProposalSort.POPULAR).discussing();

        assertThat(discussing)
                .extracting(ProposalResponse::id)
                .as("поддержанное выше шумного, хотя у шумного тоже есть голоса")
                .containsExactly(real.id(), noise.id());
    }

    @Test
    @DisplayName("Сортировка по бюджету: больше бюджет — выше (BR-RANK-4)")
    void sortsByBudgetDescending() {
        create(anna, "Дешёвое", 1000);
        create(anna, "Дорогое", 50000);
        create(anna, "Среднее", 7000);

        assertThat(proposalService.list(anna.getId(), ProposalSort.BUDGET).discussing())
                .extracting(ProposalResponse::title)
                .containsExactly("Дорогое", "Среднее", "Дешёвое");
    }

    @Test
    @DisplayName("Сортировка по новизне: свежее сверху (BR-RANK-4)")
    void sortsByNewest() {
        createWithDistinctTime(anna, "Первое");
        createWithDistinctTime(anna, "Второе");
        createWithDistinctTime(anna, "Третье");

        assertThat(proposalService.list(anna.getId(), ProposalSort.NEW).discussing())
                .extracting(ProposalResponse::title)
                .containsExactly("Третье", "Второе", "Первое");
    }

    @Test
    @DisplayName("Сортировка по умолчанию — популярность (BR-RANK-4)")
    void defaultSortIsPopular() {
        assertThat(ProposalSort.parse(null)).isEqualTo(ProposalSort.POPULAR);
        assertThat(ProposalSort.parse("  ")).isEqualTo(ProposalSort.POPULAR);
        assertThat(ProposalSort.parse("budget")).isEqualTo(ProposalSort.BUDGET);
        assertThat(ProposalSort.parse("new")).isEqualTo(ProposalSort.NEW);
    }

    @Test
    @DisplayName("Неизвестная сортировка — ошибка клиента, а не молчаливая подмена (BR-RANK-4)")
    void rejectsUnknownSort() {
        assertThatThrownBy(() -> ProposalSort.parse("cheapest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("неизвестная сортировка");
    }

    @Test
    @DisplayName("Популярность в секции «В работе» считается по тем же правилам (BR-RANK-2)")
    void popularityWorksForProposalsInWork() {
        ProposalResponse accepted = create(anna, "Вода", 2000);
        vote(anna, accepted.id(), true);
        vote(boris, accepted.id(), true);
        vote(vera, accepted.id(), true);

        Proposal proposal = proposalRepository.findByIdWithVotes(accepted.id()).orElseThrow();

        assertThat(proposal.participants()).isEqualTo(3);
        assertThat(proposal.popularity()).isEqualTo(6);
    }

    @Test
    @DisplayName("Авторы сообщений — тоже участники (BR-RANK-2)")
    void messageAuthorsCountAsParticipants() {
        ProposalResponse proposal = create(anna, "Вода", 2000);
        UUID proposalId = proposal.id();

        discussionService.post(boris.getId(), proposalId,
                new PostMessageRequest("Поддерживаю", null));
        discussionService.post(vera.getId(), proposalId,
                new PostMessageRequest("И я", null));

        ProposalResponse loaded = proposalService.get(anna.getId(), proposalId);

        // Автор и двое писавших = 3; голосов нет, перевес 0.
        assertThat(loaded.participants()).isEqualTo(3);
        assertThat(loaded.popularity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Объём сообщений популярность не поднимает (BR-RANK-3)")
    void messageVolumeDoesNotRaisePopularity() {
        ProposalResponse proposal = create(anna, "Вода", 2000);
        UUID proposalId = proposal.id();

        // Борис пишет много и отвечает сам себе — «объёма» много, людей один.
        UUID rootId = discussionService.post(boris.getId(), proposalId,
                new PostMessageRequest("Первое", null)).messages().get(0).id();
        discussionService.post(boris.getId(), proposalId,
                new PostMessageRequest("Второе", rootId.toString()));
        discussionService.post(boris.getId(), proposalId,
                new PostMessageRequest("Третье", rootId.toString()));

        ProposalResponse after = proposalService.get(anna.getId(), proposalId);

        assertThat(after.participants())
                .as("участник считается один раз, сколько бы он ни написал")
                .isEqualTo(2);
        assertThat(after.popularity())
                .as("громкое обсуждение не поднимает предложение")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Сортировка по популярности учитывает авторов сообщений (BR-RANK-1)")
    void popularSortCountsMessageAuthors() {
        ProposalResponse quiet = create(anna, "Тихое", 2000);
        ProposalResponse discussed = create(vera, "Обсуждаемое", 2000);

        // У обсуждаемого два автора сообщений — участников больше.
        discussionService.post(anna.getId(), discussed.id(),
                new PostMessageRequest("Первый аргумент", null));
        discussionService.post(boris.getId(), discussed.id(),
                new PostMessageRequest("Второй аргумент", null));

        List<ProposalResponse> discussing =
                proposalService.list(anna.getId(), ProposalSort.POPULAR).discussing();

        assertThat(discussing).extracting(ProposalResponse::id)
                .as("живое обсуждение поднимает предложение выше тихого")
                .contains(quiet.id(), discussed.id())
                .startsWith(discussed.id());
    }

    private ProposalResponse create(User author, String title, long budget) {
        return proposalService.create(
                author.getId(),
                new CreateProposalRequest(title, "Описание предложения", budget));
    }

    /**
     * Создаёт предложение, гарантируя ему более позднее время создания.
     *
     * <p>Без паузы два предложения могут получить одинаковый
     * {@code created_at} (PostgreSQL хранит время с точностью до микросекунды),
     * и тогда «свежее сверху» нечем проверить: порядок стал бы произвольным.
     */
    private ProposalResponse createWithDistinctTime(User author, String title) {
        sleepBriefly();
        return create(author, title, 1000);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("тест прерван", e);
        }
    }

    private void vote(User member, java.util.UUID proposalId, boolean inFavor) {
        proposalService.vote(member.getId(), proposalId, inFavor);
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

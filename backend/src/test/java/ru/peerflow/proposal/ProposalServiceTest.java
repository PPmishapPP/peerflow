package ru.peerflow.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.peerflow.proposal.domain.ProposalStage;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalNotFoundException;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.service.ProposalSort;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.proposal.web.dto.ProposalListResponse;
import ru.peerflow.proposal.web.dto.ProposalResponse;
import ru.peerflow.support.PostgresTestBase;

/**
 * Внесение предложения и доступ к нему ({@code BR-LIFE-1},
 * {@code BR-BUDGET-2}, {@code BR-OVER-2}, {@code BR-RANK-5},
 * {@code BR-PRIV-1}).
 *
 * <p>Сортировки и метрика популярности проверяются отдельно — в
 * {@link ProposalRankingTest}: это другое правило, и смешивать его с
 * «создание и доступ» значило бы, что падение одного теста не говорит, что
 * именно сломалось.
 */
@SpringBootTest
class ProposalServiceTest extends PostgresTestBase {

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    private Community community;
    private User anna;
    private User boris;
    private User vera;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        community = communityRepository.save(Community.create("Маяк"));
        anna = member("anna", "Анна");
        boris = member("boris", "Борис");
        vera = member("vera", "Вера");
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Создание: предложение с бюджетом появляется «На обсуждении» (BR-LIFE-1)")
    void createsProposalInDiscussion() {
        ProposalResponse created = create(anna, "Закупить проектор", 35000);

        assertThat(created.stage()).isEqualTo(ProposalStage.DISCUSSION);
        assertThat(created.budget()).isEqualTo(35000);
        assertThat(created.votesFor()).isZero();
        assertThat(created.votesAgainst()).isZero();
        assertThat(created.authorName()).isEqualTo("Анна");
        assertThat(created.votingClosed()).isFalse();
        assertThat(created.myVote()).isNull();
        assertThat(created.votingDeadline())
                .as("клиент знает, когда закончится голосование (BR-VOTE-9)")
                .isNotNull();
    }

    @Test
    @DisplayName("Создание: тема и описание обрезаются по краям")
    void trimsTitleAndDescription() {
        ProposalResponse created = proposalService.create(anna.getId(),
                new CreateProposalRequest("  Вода  ", "  Нужна вода  ", 2000L));

        assertThat(created.title()).isEqualTo("Вода");
        assertThat(created.description()).isEqualTo("Нужна вода");
    }

    @Test
    @DisplayName("Создание: без бюджета предложение внести нельзя (BR-BUDGET-2)")
    void rejectsProposalWithoutBudget() {
        assertThatThrownBy(() -> create(anna, "Без бюджета", 0))
                .as("потолок бюджета обязателен и должен быть положительным")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("без потолка бюджета");
    }

    @Test
    @DisplayName("Создание: отрицательный бюджет отклоняется (граница BR-BUDGET-2)")
    void rejectsNegativeBudget() {
        assertThatThrownBy(() -> create(anna, "Минус", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Создание: нулевой бюджет не создаёт строку в базе (BR-BUDGET-2)")
    void failedCreationLeavesNoRow() {
        assertThatThrownBy(() -> create(anna, "Без бюджета", 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(proposalRepository.count())
                .as("отклонённое предложение не должно попадать в базу")
                .isZero();
    }

    @Test
    @DisplayName("Создание: вносить предложение может только член сообщества (BR-OVER-2)")
    void rejectsProposalFromNonMember() {
        User outsider = userRepository.save(User.register("petr", "hash", "Пётр"));

        assertThatThrownBy(() -> create(outsider, "Идея", 100))
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("Список: две секции, «В работе» и «На обсуждении» (BR-RANK-5)")
    void splitsIntoTwoSections() {
        ProposalResponse discussing = create(anna, "Вода", 2000);
        ProposalResponse inWork = create(boris, "Дверь", 4500);

        // Голосуем «за» так, чтобы вторая задача перешла в работу досрочно.
        vote(anna, inWork.id(), true);
        vote(boris, inWork.id(), true);

        ProposalListResponse sections = proposalService.list(anna.getId(), ProposalSort.NEW);

        assertThat(sections.inWork())
                .extracting(ProposalResponse::id)
                .containsExactly(inWork.id());
        assertThat(sections.discussing())
                .extracting(ProposalResponse::id)
                .containsExactly(discussing.id());
    }

    @Test
    @DisplayName("Список: пустые секции приходят пустыми списками, а не null")
    void emptySectionsAreEmptyLists() {
        ProposalListResponse sections = proposalService.list(anna.getId(), ProposalSort.POPULAR);

        assertThat(sections.inWork()).isNotNull().isEmpty();
        assertThat(sections.discussing()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Карточка: голос текущего пользователя виден (BR-VOTE-13)")
    void showsOwnVote() {
        ProposalResponse proposal = create(anna, "Вода", 2000);
        vote(boris, proposal.id(), true);

        ProposalResponse seenByBoris = proposalService.get(boris.getId(), proposal.id());
        ProposalResponse seenByVera = proposalService.get(vera.getId(), proposal.id());

        assertThat(seenByBoris.myVote()).isNotNull();
        assertThat(seenByBoris.myVote().inFavor()).isTrue();
        assertThat(seenByVera.myVote())
                .as("тот, кто не голосовал, не должен видеть чужой голос как свой")
                .isNull();
    }

    @Test
    @DisplayName("Чужое предложение не видно и не подтверждает существование (BR-PRIV-1)")
    void hidesProposalFromOtherCommunity() {
        ProposalResponse proposal = create(anna, "Вода", 2000);

        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        assertThatThrownBy(() -> proposalService.get(stranger.getId(), proposal.id()))
                .isInstanceOf(ProposalNotFoundException.class);
    }

    @Test
    @DisplayName("Список: доступен только члену сообщества (BR-PRIV-1)")
    void listRequiresMembership() {
        User outsider = userRepository.save(User.register("petr", "hash", "Пётр"));

        assertThatThrownBy(() -> proposalService.list(outsider.getId(), ProposalSort.POPULAR))
                .isInstanceOf(MembershipRequiredException.class);
    }

    private ProposalResponse create(User author, String title, long budget) {
        return proposalService.create(
                author.getId(),
                new CreateProposalRequest(title, "Описание предложения", budget));
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
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

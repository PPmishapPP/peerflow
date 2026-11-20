package ru.peerflow.legitimacy;

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
import org.springframework.jdbc.core.JdbcTemplate;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.MembershipRequiredException;
import ru.peerflow.legitimacy.domain.LegitimacyEvent;
import ru.peerflow.legitimacy.domain.LegitimacyStanding;
import ru.peerflow.legitimacy.domain.LegitimacyEventKind;
import ru.peerflow.legitimacy.repo.LegitimacyEventRepository;
import ru.peerflow.legitimacy.service.CandidateNotFoundException;
import ru.peerflow.legitimacy.service.LegitimacyService;
import ru.peerflow.proposal.repo.ProposalRepository;
import ru.peerflow.proposal.service.ProposalNotFoundException;
import ru.peerflow.proposal.service.ProposalService;
import ru.peerflow.proposal.web.dto.CreateProposalRequest;
import ru.peerflow.support.PostgresTestBase;

/**
 * Журнал легитимности: передача, отзыв и независимость по задачам
 * ({@code BR-LEGIT-2}, {@code BR-LEGIT-3}, {@code BR-LEGIT-5}).
 *
 * <p>Главное, что здесь защищается, — «одна единица доверия на человека на
 * задачу». Если бы передача накапливалась, один член мог бы поддержать всех
 * кандидатов сразу, и выбор лидера потерял бы смысл
 * ({@code BR-LEGIT-4}).
 *
 * <p>Отзыв проверяется отдельно от передачи: это не «отмена» прежнего события,
 * а новое. Журнал не переписывается, поэтому после отзыва в нём остаются оба
 * факта — и на них можно ответить, почему мандат утрачен
 * ({@code BR-LEADER-6}).
 */
@SpringBootTest
class LegitimacyServiceTest extends PostgresTestBase {

    @Autowired
    private LegitimacyService legitimacyService;

    @Autowired
    private LegitimacyEventRepository eventRepository;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Community community;
    private User anna;
    private User boris;
    private User veronika;
    private UUID proposalId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        community = communityRepository.save(Community.create("Маяк"));
        anna = member("anna", "Анна");
        boris = member("boris", "Борис");
        veronika = member("veronika", "Вероника");
        proposalId = proposalService.create(anna.getId(),
                new CreateProposalRequest("Аренда помещения", "Описание", 12000L)).id();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Передача: легитимность уходит кандидату (BR-LEGIT-2)")
    void delegateGivesLegitimacyToCandidate() {
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());

        LegitimacyStanding standing = standing();
        assertThat(standing.recipientOf(anna.getId())).isEqualTo(boris.getId());
        assertThat(standing.supportersOf(boris.getId())).isEqualTo(1);
        assertThat(standing.totalDelegated()).isEqualTo(1);
    }

    @Test
    @DisplayName("Передача записывается в журнал как DELEGATE (BR-LEGIT-2)")
    void delegateAppendsEvent() {
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());

        List<LegitimacyEvent> events = eventsOf(proposalId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getKind()).isEqualTo(LegitimacyEventKind.DELEGATE);
        assertThat(events.get(0).getMember().getId()).isEqualTo(anna.getId());
        assertThat(events.get(0).getCandidate().getId()).isEqualTo(boris.getId());
    }

    @Test
    @DisplayName("Одна единица доверия: повторная передача заменяет кандидата (BR-LEGIT-1)")
    void delegationReplacesRecipientInsteadOfAccumulating() {
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());
        legitimacyService.delegate(anna.getId(), proposalId, veronika.getId());

        LegitimacyStanding standing = standing();
        assertThat(standing.recipientOf(anna.getId())).isEqualTo(veronika.getId());
        assertThat(standing.supportersOf(boris.getId())).isZero();
        assertThat(standing.supportersOf(veronika.getId())).isEqualTo(1);
        assertThat(standing.totalDelegated())
                .as("человек распоряжается ровно одной единицей доверия")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Повторная передача тому же кандидату не плодит событий (BR-LEGIT-2)")
    void repeatedDelegationToSameCandidateIsIdempotent() {
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());

        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());

        assertThat(eventsOf(proposalId))
                .as("двойное нажатие на клиенте не должно раздувать журнал")
                .hasSize(1);
    }

    @Test
    @DisplayName("Отзыв возвращает легитимность себе (BR-LEGIT-5)")
    void revokeReturnsLegitimacy() {
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());

        legitimacyService.revoke(anna.getId(), proposalId);

        LegitimacyStanding standing = standing();
        assertThat(standing.recipientOf(anna.getId())).isNull();
        assertThat(standing.supportersOf(boris.getId())).isZero();
        assertThat(standing.totalDelegated()).isZero();
    }

    @Test
    @DisplayName("Отзыв не стирает историю: в журнале остаются оба события (BR-LEGIT-5)")
    void revokeAppendsInsteadOfRewriting() {
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());

        legitimacyService.revoke(anna.getId(), proposalId);

        assertThat(eventsOf(proposalId))
                .extracting(LegitimacyEvent::getKind)
                .containsExactly(LegitimacyEventKind.DELEGATE, LegitimacyEventKind.REVOKE);
    }

    @Test
    @DisplayName("Отзыв без передачи ничего не пишет в журнал (BR-LEGIT-5)")
    void revokeWithoutDelegationIsNoop() {
        legitimacyService.revoke(anna.getId(), proposalId);

        assertThat(eventsOf(proposalId)).isEmpty();
    }

    @Test
    @DisplayName("После отзыва можно снова передать тому же кандидату (BR-LEGIT-5)")
    void canDelegateAgainAfterRevoke() {
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());
        legitimacyService.revoke(anna.getId(), proposalId);
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());

        LegitimacyStanding standing = standing();
        assertThat(standing.recipientOf(anna.getId())).isEqualTo(boris.getId());
        assertThat(eventsOf(proposalId))
                .extracting(LegitimacyEvent::getKind)
                .containsExactly(
                        LegitimacyEventKind.DELEGATE,
                        LegitimacyEventKind.REVOKE,
                        LegitimacyEventKind.DELEGATE);
    }

    @Test
    @DisplayName("Легитимность по задачам независима (BR-LEGIT-3, BR-LEADER-8)")
    void legitimacyIsIndependentPerProposal() {
        UUID otherProposalId = proposalService.create(veronika.getId(),
                new CreateProposalRequest("Второй проект", "Другое", 5000L)).id();

        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());
        legitimacyService.delegate(anna.getId(), otherProposalId, veronika.getId());

        LegitimacyStanding first = legitimacyService.standing(anna.getId(), proposalId);
        LegitimacyStanding second = legitimacyService.standing(anna.getId(), otherProposalId);

        assertThat(first.recipientOf(anna.getId())).isEqualTo(boris.getId());
        assertThat(second.recipientOf(anna.getId())).isEqualTo(veronika.getId());
        assertThat(first.supportersOf(veronika.getId()))
                .as("поддержка по другой задаче не видна в этой")
                .isZero();
    }

    @Test
    @DisplayName("Отзыв по одной задаче не трогает другую (BR-LEGIT-3)")
    void revokeInOneProposalKeepsAnother() {
        UUID otherProposalId = proposalService.create(veronika.getId(),
                new CreateProposalRequest("Второй проект", "Другое", 5000L)).id();
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());
        legitimacyService.delegate(anna.getId(), otherProposalId, veronika.getId());

        legitimacyService.revoke(anna.getId(), proposalId);

        assertThat(legitimacyService.standing(anna.getId(), proposalId).totalDelegated()).isZero();
        assertThat(legitimacyService.standing(anna.getId(), otherProposalId)
                .recipientOf(anna.getId())).isEqualTo(veronika.getId());
    }

    @Test
    @DisplayName("Передать легитимность себе нельзя (BR-LEGIT-2)")
    void cannotDelegateToSelf() {
        assertThatThrownBy(() ->
                legitimacyService.delegate(anna.getId(), proposalId, anna.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("себе нельзя");
    }

    @Test
    @DisplayName("Кандидат из другого сообщества не найден (BR-PRIV-1)")
    void candidateFromAnotherCommunityIsNotFound() {
        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);

        assertThatThrownBy(() ->
                legitimacyService.delegate(anna.getId(), proposalId, stranger.getId()))
                .isInstanceOf(CandidateNotFoundException.class);
    }

    @Test
    @DisplayName("Задача другого сообщества не найдена (BR-PRIV-1)")
    void proposalFromAnotherCommunityIsNotFound() {
        Community other = communityRepository.save(Community.create("Другой"));
        User stranger = User.register("stranger", "hash", "Чужой");
        stranger.join(other);
        userRepository.save(stranger);
        UUID foreignProposal = proposalService.create(stranger.getId(),
                new CreateProposalRequest("Чужое", "Описание", 1000L)).id();

        assertThatThrownBy(() ->
                legitimacyService.delegate(anna.getId(), foreignProposal, boris.getId()))
                .isInstanceOf(ProposalNotFoundException.class);
    }

    @Test
    @DisplayName("Не член сообщества не может передавать легитимность (BR-VOTE-1)")
    void nonMemberCannotDelegate() {
        // Аккаунт зарегистрирован, но ещё не принят в сообщество.
        User pending = userRepository.save(User.register("pending", "hash", "Ожидающий"));

        assertThatThrownBy(() ->
                legitimacyService.delegate(pending.getId(), proposalId, boris.getId()))
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("Журнал только дописывается: UPDATE запрещён базой (BR-LEGIT-5)")
    void journalRejectsUpdate() {
        legitimacyService.delegate(anna.getId(), proposalId, boris.getId());
        LegitimacyEvent event = eventsOf(proposalId).get(0);

        // Правило продублировано триггером, поэтому проверяем именно базу:
        // иначе запрос мимо кода смог бы переписать историю доверия.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update legitimacy_event set kind = 'REVOKE' where id = ?", event.getId()))
                .hasMessageContaining("append-only");
    }

    private List<LegitimacyEvent> eventsOf(UUID proposalIdLocal) {
        return eventRepository.findAllForProposal(
                proposalRepository.findById(proposalIdLocal).orElseThrow());
    }

    /** Текущее состояние по задаче, без побочных эффектов выбора лидера. */
    private LegitimacyStanding standing() {
        return legitimacyService.standing(
                proposalRepository.findById(proposalId).orElseThrow());
    }

    private User member(String login, String displayName) {
        User user = User.register(login, "hash", displayName);
        user.join(community);
        return userRepository.save(user);
    }

    private void cleanDatabase() {
        eventRepository.deleteAll();
        proposalRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

package ru.peerflow.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.JoinRequestStatus;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.JoinRequestRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.JoinRequestAlreadySubmittedException;
import ru.peerflow.community.service.JoinRequestNotFoundException;
import ru.peerflow.community.service.JoinRequestService;
import ru.peerflow.community.service.MembershipRequiredException;
import ru.peerflow.community.web.dto.JoinRequestResponse;
import ru.peerflow.community.web.dto.SubmitJoinRequestRequest;
import ru.peerflow.support.PostgresTestBase;

/**
 * Приём новых участников: подача заявки и голосование ({@code BR-JOIN-4},
 * {@code BR-JOIN-5}, {@code BR-VOTE-8}).
 *
 * <p>Главное, что здесь проверяется, — арифметика порога: «минимум 30% голосов
 * за» ({@code BR-LIMIT-3}) считается от действующих членов сообщества, поэтому
 * при десяти членах трёх голосов «за» достаточно, а двух — нет, даже если
 * проголосовали только они. Заодно проверяются запреты: голосовать может
 * только член, один человек — один голос ({@code BR-VOTE-2}), и по решённой
 * заявке голосовать нельзя.
 *
 * <p>{@code @SpringBootTest} коммитит транзакции методов сервиса, поэтому база
 * чистится вручную до и после каждого теста.
 */
@SpringBootTest
class JoinRequestServiceTest extends PostgresTestBase {

    private static final String ABOUT = "Хочу вступить: готов помогать с субботниками";

    @Autowired
    private JoinRequestService joinRequestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private JoinRequestRepository joinRequestRepository;

    private Community community;

    private final List<User> members = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanDatabase();
        members.clear();
        community = communityRepository.save(Community.create("Маяк"));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Приём: 10 членов и 3 голоса «за» — порог 30% набран (BR-JOIN-5)")
    void acceptsApplicantWhenThresholdReached() throws Exception {
        createMembers(10);
        User applicant = createApplicant("petr", "Пётр");

        JoinRequestResponse request = submit(applicant);
        assertThat(request.status())
                .as("новая заявка ждёт рассмотрения")
                .isEqualTo(JoinRequestStatus.PENDING);
        assertThat(request.required())
                .as("30% от десяти членов — три голоса (BR-LIMIT-3)")
                .isEqualTo(3);

        vote(members.get(0), request.id(), true);
        vote(members.get(1), request.id(), true);
        JoinRequestResponse afterThird = vote(members.get(2), request.id(), true);

        assertThat(afterThird.accepts()).isEqualTo(3);
        assertThat(afterThird.status())
                .as("трёх голосов «за» достаточно, остальные голоса не нужны (BR-RISK-1)")
                .isEqualTo(JoinRequestStatus.ACCEPTED);
        assertThat(afterThird.decidedAt()).isNotNull();

        User accepted = userRepository.findById(applicant.getId()).orElseThrow();
        assertThat(accepted.isActiveMember())
                .as("принятый становится членом сообщества и получает доступ (BR-PRIV-1)")
                .isTrue();
        assertThat(accepted.getCommunity()).isEqualTo(community);
        assertThat(accepted.getAbout())
                .as("«о себе» переносится из заявки в профиль")
                .isEqualTo(ABOUT);
    }

    @Test
    @DisplayName("Приём: 10 членов и 2 голоса «за» — порог не набран, заявка ждёт (BR-JOIN-5)")
    void keepsRequestPendingBelowThreshold() throws Exception {
        createMembers(10);
        User applicant = createApplicant("petr", "Пётр");

        JoinRequestResponse request = submit(applicant);
        vote(members.get(0), request.id(), true);
        JoinRequestResponse afterSecond = vote(members.get(1), request.id(), true);

        assertThat(afterSecond.accepts()).isEqualTo(2);
        assertThat(afterSecond.status())
                .as("двух голосов «за» недостаточно — есть кому ещё голосовать")
                .isEqualTo(JoinRequestStatus.PENDING);
        assertThat(userRepository.findById(applicant.getId()).orElseThrow().isActiveMember())
                .as("до набора порога человек остаётся вне сообщества")
                .isFalse();
    }

    @Test
    @DisplayName("Отклонение: когда порог уже недостижим, заявка отклоняется сразу")
    void rejectsRequestWhenThresholdUnreachable() throws Exception {
        createMembers(10);
        User applicant = createApplicant("petr", "Пётр");

        JoinRequestResponse request = submit(applicant);
        vote(members.get(0), request.id(), true);

        JoinRequestResponse last = null;
        for (int i = 1; i < 9; i++) {
            last = vote(members.get(i), request.id(), false);
        }

        assertThat(last).isNotNull();
        assertThat(last.status())
                .as("оставшиеся голоса уже не дают порог — держать заявку незачем")
                .isEqualTo(JoinRequestStatus.REJECTED);
        assertThat(userRepository.findById(applicant.getId()).orElseThrow().isActiveMember())
                .isFalse();
    }

    @Test
    @DisplayName("Голос: повторный голос того же члена отклоняется (BR-VOTE-2)")
    void rejectsSecondVoteBySameMember() throws Exception {
        createMembers(10);
        User applicant = createApplicant("petr", "Пётр");
        JoinRequestResponse request = submit(applicant);
        UUID requestId = request.id();

        vote(members.get(0), requestId, true);

        assertThatThrownBy(() -> joinRequestService.vote(members.get(0).getId(), requestId, false))
                .as("один человек — один голос")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже учтён");
    }

    @Test
    @DisplayName("Голос: не член сообщества голосовать не может (BR-VOTE-1)")
    void rejectsVoteByNonMember() throws Exception {
        createMembers(3);
        User applicant = createApplicant("petr", "Пётр");
        UUID requestId = submit(applicant).id();

        assertThatThrownBy(() -> joinRequestService.vote(applicant.getId(), requestId, true))
                .as("право голоса есть только у принятых членов")
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("Голос: по решённой заявке голосовать нельзя")
    void rejectsVoteOnDecidedRequest() throws Exception {
        createMembers(10);
        User applicant = createApplicant("petr", "Пётр");
        UUID requestId = submit(applicant).id();

        vote(members.get(0), requestId, true);
        vote(members.get(1), requestId, true);
        vote(members.get(2), requestId, true);

        assertThatThrownBy(() -> joinRequestService.vote(members.get(3).getId(), requestId, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже рассмотрена");
    }

    @Test
    @DisplayName("Подача: вторая заявка при уже рассматриваемой отклоняется (BR-JOIN-4)")
    void rejectsDuplicatePendingRequest() throws Exception {
        User applicant = createApplicant("petr", "Пётр");
        submit(applicant);

        assertThatThrownBy(() -> submit(applicant))
                .as("одна заявка в работе на человека")
                .isInstanceOf(JoinRequestAlreadySubmittedException.class);
    }

    @Test
    @DisplayName("Подача: действующий член заявку подать не может (BR-JOIN-2)")
    void rejectsRequestFromMember() throws Exception {
        createMembers(1);
        User member = members.get(0);

        assertThatThrownBy(() -> submit(member))
                .as("аккаунт уже состоит в сообществе")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже состоит в сообществе");
    }

    @Test
    @DisplayName("Список заявок: доступен только члену сообщества (BR-PRIV-1)")
    void pendingListRequiresMembership() throws Exception {
        createMembers(1);
        User applicant = createApplicant("petr", "Пётр");
        submit(applicant);

        assertThat(joinRequestService.pendingFor(members.get(0).getId()))
                .as("член видит заявку в списке на рассмотрение")
                .hasSize(1)
                .first()
                .extracting(JoinRequestResponse::applicantName)
                .isEqualTo("Пётр");

        assertThatThrownBy(() -> joinRequestService.pendingFor(applicant.getId()))
                .as("до приёма заявки сообщества человеку не видны")
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("Своя заявка: отдаётся в любом состоянии, чужая — не видна")
    void returnsOwnRequestOnly() throws Exception {
        createMembers(1);
        User applicant = createApplicant("petr", "Пётр");

        assertThat(joinRequestService.ownRequest(applicant.getId()))
                .as("заявок ещё нет")
                .isEmpty();

        JoinRequestResponse submitted = submit(applicant);

        assertThat(joinRequestService.ownRequest(applicant.getId()))
                .get()
                .extracting(JoinRequestResponse::id)
                .isEqualTo(submitted.id());
    }

    @Test
    @DisplayName("Голос: заявка другого сообщества не видна и не голосуется (BR-PRIV-1)")
    void rejectsVoteForUnknownRequest() {
        createMembers(1);

        assertThatThrownBy(() -> joinRequestService.vote(members.get(0).getId(),
                UUID.randomUUID(), true))
                .isInstanceOf(JoinRequestNotFoundException.class);
    }

    @Test
    @DisplayName("После приёма порог не растёт: решённая заявка не «недобирает» голоса (BR-JOIN-5)")
    void requiredDoesNotGrowAfterAcceptance() throws Exception {
        createMembers(10);
        User applicant = createApplicant("petr", "Пётр");

        JoinRequestResponse request = submit(applicant);
        vote(members.get(0), request.id(), true);
        vote(members.get(1), request.id(), true);
        JoinRequestResponse accepted = vote(members.get(2), request.id(), true);

        assertThat(accepted.status()).isEqualTo(JoinRequestStatus.ACCEPTED);
        assertThat(accepted.required())
                .as("порог считается без принятого: иначе он вырос бы уже после решения")
                .isEqualTo(3);
        assertThat(accepted.accepts())
                .as("голосов «за» ровно столько, сколько показал порог")
                .isEqualTo(accepted.required());
    }

    @Test
    @DisplayName("Подача: порог для заявителя не зависит от числа проголосовавших (BR-JOIN-5)")
    void requiredIgnoresVoteCount() throws Exception {
        createMembers(10);
        User applicant = createApplicant("petr", "Пётр");

        JoinRequestResponse request = submit(applicant);
        vote(members.get(0), request.id(), true);
        JoinRequestResponse afterOne = vote(members.get(1), request.id(), false);

        assertThat(afterOne.required())
                .as("порог — доля сообщества, а не проголосовавших")
                .isEqualTo(3);
        assertThat(afterOne.votes()).isEqualTo(2);
    }

    private JoinRequestResponse submit(User applicant) throws Exception {
        return joinRequestService.submit(
                applicant.getId(), new SubmitJoinRequestRequest(ABOUT), null);
    }

    private JoinRequestResponse vote(User member, UUID requestId, boolean accept) {
        return joinRequestService.vote(member.getId(), requestId, accept);
    }

    private void createMembers(int count) {
        for (int i = 0; i < count; i++) {
            User member = User.register("member" + i, "hash", "Член " + i);
            member.join(community);
            members.add(userRepository.save(member));
        }
    }

    private User createApplicant(String login, String displayName) {
        return userRepository.save(User.register(login, "hash", displayName));
    }

    private void cleanDatabase() {
        joinRequestRepository.deleteAll();
        userRepository.deleteAll();
        communityRepository.deleteAll();
    }
}

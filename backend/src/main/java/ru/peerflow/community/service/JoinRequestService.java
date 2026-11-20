package ru.peerflow.community.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.peerflow.auth.service.AccountNotFoundException;
import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.JoinRequest;
import ru.peerflow.community.domain.JoinRequestStatus;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.domain.UserStatus;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.JoinRequestRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.web.dto.JoinRequestResponse;
import ru.peerflow.community.web.dto.SubmitJoinRequestRequest;
import ru.peerflow.config.PeerflowThresholds;

/**
 * Приём новых участников ({@code BR-JOIN-4}, {@code BR-JOIN-5}).
 *
 * <p>Поток такой: человек, зарегистрировавшийся после первого, подаёт заявку
 * с рассказом о себе и фото; действующие члены голосуют ({@code BR-VOTE-8});
 * как только голосов «за» набралось не меньше порога, человек становится
 * членом сообщества и получает доступ к контенту ({@code BR-PRIV-1}).
 *
 * <p>Порог считается от числа <b>действующих членов</b>, а не от числа
 * проголосовавших: «минимум 30% голосов за» ({@code BR-JOIN-5},
 * {@code BR-LIMIT-3}) — это доля сообщества, иначе двое активных могли бы
 * принять человека за всех. Кворум при этом не требуется
 * ({@code BR-RISK-1}): если «за» набралось достаточно — приём происходит, не
 * дожидаясь остальных.
 *
 * <p>Обратный случай тоже обрабатывается: если оставшиеся голоса уже не могут
 * дать порог, заявка отклоняется сразу, а не висит «в ожидании чуда».
 */
@Service
public class JoinRequestService {

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final AvatarStorage avatarStorage;
    private final PeerflowThresholds thresholds;

    public JoinRequestService(UserRepository userRepository,
                              CommunityRepository communityRepository,
                              JoinRequestRepository joinRequestRepository,
                              AvatarStorage avatarStorage,
                              PeerflowThresholds thresholds) {
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.avatarStorage = avatarStorage;
        this.thresholds = thresholds;
    }

    /**
     * Подаёт заявку на вступление ({@code BR-JOIN-4}).
     *
     * <p>Фото сохраняется до записи заявки: если заявку создать не удалось,
     * файл удаляется — иначе на диске остались бы снимки без владельца.
     *
     * @param userId  аккаунт из токена
     * @param request «о себе»
     * @param avatar  фотография; может отсутствовать
     * @return созданная заявка в состоянии {@code PENDING}
     * @throws AccountNotFoundException           если аккаунт не найден
     * @throws JoinRequestAlreadySubmittedException если заявка уже в работе
     * @throws IllegalStateException                если аккаунт уже в сообществе
     * @throws IllegalArgumentException             если фото не принято
     * @throws IOException                          если фото не удалось сохранить
     */
    @Transactional
    public JoinRequestResponse submit(UUID userId,
                                      SubmitJoinRequestRequest request,
                                      MultipartFile avatar) throws IOException {
        User applicant = loadUser(userId);
        Community community = loadCommunity();

        if (joinRequestRepository.existsByApplicantAndStatus(applicant, JoinRequestStatus.PENDING)) {
            throw new JoinRequestAlreadySubmittedException();
        }

        String avatarPath = null;
        if (avatar != null && !avatar.isEmpty()) {
            avatarPath = avatarStorage.store(avatar);
        }

        try {
            JoinRequest joinRequest = JoinRequest.submit(
                    applicant, community, request.about().trim(), avatarPath);
            JoinRequest saved = joinRequestRepository.save(joinRequest);
            return response(saved, applicant);
        } catch (DataIntegrityViolationException e) {
            // Параллельная подача: частичный уникальный индекс в базе не пустил
            // вторую заявку в работе — это тот же конфликт, что и проверка выше.
            avatarStorage.deleteQuietly(avatarPath);
            throw new JoinRequestAlreadySubmittedException();
        } catch (RuntimeException e) {
            avatarStorage.deleteQuietly(avatarPath);
            throw e;
        }
    }

    /**
     * Список заявок на рассмотрение для действующего члена сообщества.
     *
     * <p>Доступен только членам ({@code BR-PRIV-1}): заявки — такой же контент
     * сообщества, как и всё остальное.
     *
     * @param memberId аккаунт из токена
     * @return заявки в состоянии {@code PENDING}, свежие сверху
     * @throws MembershipRequiredException если аккаунт не действующий член
     */
    @Transactional(readOnly = true)
    public List<JoinRequestResponse> pendingFor(UUID memberId) {
        User member = loadActiveMember(memberId);
        return joinRequestRepository
                .findAllForCommunity(member.getCommunity(), JoinRequestStatus.PENDING)
                .stream()
                .map(joinRequest -> response(joinRequest, member))
                .toList();
    }

    /**
     * Отдаёт последнюю заявку самого человека — в любом состоянии.
     *
     * <p>Нужна клиенту, чтобы показать «ваша заявка рассматривается» или
     * «вас не приняли» вместо пустого экрана.
     *
     * @param userId аккаунт из токена
     * @return заявка, если человек её подавал
     */
    @Transactional(readOnly = true)
    public Optional<JoinRequestResponse> ownRequest(UUID userId) {
        User user = loadUser(userId);
        return joinRequestRepository.findFirstByApplicantOrderByCreatedAtDesc(user)
                .map(joinRequest -> response(joinRequest, user));
    }

    /**
     * Голосует по заявке и, если порог набран, принимает человека
     * ({@code BR-VOTE-8}, {@code BR-JOIN-5}).
     *
     * @param memberId  аккаунт из токена
     * @param requestId заявка
     * @param accept    {@code true} — «за приём»
     * @return заявка после голосования, с обновлённым состоянием
     * @throws MembershipRequiredException  если голосует не действующий член
     * @throws JoinRequestNotFoundException если заявки нет в сообществе члена
     * @throws IllegalStateException        если заявка уже решена или голос уже подан
     */
    @Transactional
    public JoinRequestResponse vote(UUID memberId, UUID requestId, boolean accept) {
        User member = loadActiveMember(memberId);
        Community community = member.getCommunity();

        // Заявку ищем только в своём сообществе: чужие заявки не должны даже
        // подтверждать своё существование ({@code BR-PRIV-1}).
        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .filter(found -> Objects.equals(found.getCommunity(), community))
                .orElseThrow(() -> new JoinRequestNotFoundException(requestId));

        joinRequest.vote(member, accept);

        long members = userRepository.countByCommunityAndStatus(community, UserStatus.ACTIVE);
        long required = requiredAccepts(members, thresholds.joinAcceptRatio());
        long accepts = joinRequest.accepts();

        if (accepts >= required) {
            promote(joinRequest);
        } else if (accepts + Math.max(0, members - joinRequest.voteCount()) < required) {
            // Даже если проголосуют все оставшиеся «за», порог не набрать.
            joinRequest.reject();
        }

        return response(joinRequest, member);
    }

    /**
     * Сколько голосов «за» нужно для приёма при заданном числе членов.
     *
     * <p>Считаем через {@link BigDecimal}: {@code double} даёт на некоторых
     * долях {@code 2.9999999999999996}, и округление вверх превратило бы три
     * голоса в четыре. Минимум один голос — иначе сообщество из одного человека
     * принимало бы всех молча.
     *
     * @param members число действующих членов
     * @param ratio   доля голосов «за» из настроек ({@code BR-LIMIT-3})
     * @return минимальное число голосов «за»
     */
    static long requiredAccepts(long members, double ratio) {
        if (members <= 0) {
            return 1;
        }
        long required = BigDecimal.valueOf(ratio)
                .multiply(BigDecimal.valueOf(members))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
        return Math.max(1, required);
    }

    /**
     * Принимает заявку и заводит человека в сообщество.
     *
     * <p>«О себе» и фото переносятся из заявки в профиль: человек вступал с ними,
     * и после приёма профиль должен показывать то же самое.
     */
    private void promote(JoinRequest joinRequest) {
        joinRequest.accept();
        User applicant = joinRequest.getApplicant();
        applicant.join(joinRequest.getCommunity());
        if (joinRequest.getAbout() != null) {
            applicant.setAbout(joinRequest.getAbout());
        }
        if (joinRequest.getAvatarPath() != null) {
            applicant.setAvatarPath(joinRequest.getAvatarPath());
        }
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));
    }

    private User loadActiveMember(UUID userId) {
        User user = loadUser(userId);
        if (!user.isActiveMember()) {
            throw new MembershipRequiredException(
                    "действие доступно только действующему члену сообщества");
        }
        return user;
    }

    private Community loadCommunity() {
        return communityRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "сообщество ещё не создано — некому рассматривать заявку"));
    }

    private JoinRequestResponse response(JoinRequest joinRequest, User viewer) {
        User applicant = joinRequest.getApplicant();
        return new JoinRequestResponse(
                joinRequest.getId(),
                applicant.getId(),
                applicant.getDisplayName(),
                joinRequest.getAbout(),
                joinRequest.getAvatarPath(),
                joinRequest.getStatus(),
                joinRequest.getCreatedAt(),
                joinRequest.getDecidedAt(),
                joinRequest.accepts(),
                joinRequest.voteCount(),
                requiredAcceptsFor(joinRequest.getCommunity()),
                joinRequest.hasVotedBy(viewer));
    }

    private long requiredAcceptsFor(Community community) {
        // Требование считаем от текущих членов сообщества, а не от числа
        // проголосовавших: порог — доля сообщества ({@code BR-JOIN-5}).
        long members = community == null
                ? 0
                : userRepository.countByCommunityAndStatus(community, UserStatus.ACTIVE);
        return requiredAccepts(members, thresholds.joinAcceptRatio());
    }
}

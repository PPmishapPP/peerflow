package ru.peerflow.community.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.JoinRequest;
import ru.peerflow.community.domain.JoinRequestStatus;
import ru.peerflow.community.domain.User;

/**
 * Доступ к заявкам на вступление ({@code BR-JOIN-4}).
 *
 * <p>Заявку находят либо по заявителю (проверка «уже подал?»), либо по
 * сообществу — тогда это список на рассмотрение.
 */
public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    /**
     * Ищет заявку заявителя в заданном состоянии.
     *
     * @param applicant заявитель
     * @param status    требуемое состояние
     * @return заявка, если она есть
     */
    Optional<JoinRequest> findFirstByApplicantAndStatus(User applicant, JoinRequestStatus status);

    /**
     * Возвращает последнюю заявку заявителя в любом состоянии.
     *
     * <p>Нужна экрану «моя заявка»: человек должен видеть, что она
     * рассматривается, принята или отклонена.
     *
     * @param applicant заявитель
     * @return последняя заявка, если человек её подавал
     */
    Optional<JoinRequest> findFirstByApplicantOrderByCreatedAtDesc(User applicant);

    /**
     * Проверяет, есть ли у заявителя заявка в заданном состоянии.
     *
     * @param applicant заявитель
     * @param status    требуемое состояние
     * @return {@code true}, если заявка есть
     */
    boolean existsByApplicantAndStatus(User applicant, JoinRequestStatus status);

    /**
     * Возвращает заявки сообщества в заданном состоянии, свежие сверху.
     *
     * <p>Заявитель подгружается сразу ({@code join fetch}): список заявок
     * показывается членам сообщества, и «ленивая» подгрузка на каждую строку
     * означала бы N+1 запрос.
     *
     * @param community сообщество
     * @param status    требуемое состояние
     * @return список заявок, возможно пустой
     */
    @Query("""
            select request
            from JoinRequest request
            join fetch request.applicant
            where request.community = :community and request.status = :status
            order by request.createdAt desc
            """)
    List<JoinRequest> findAllForCommunity(Community community, JoinRequestStatus status);
}

package ru.peerflow.legitimacy.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.legitimacy.domain.LeaderMandate;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Доступ к мандатам лидеров ({@code BR-LEGIT-4}, {@code BR-LEADER-5}).
 *
 * <p>Действующий мандат у задачи один — это гарантирует частичный уникальный
 * индекс в базе. История мандатов читается отдельно: она объясняет, почему
 * лидер сменился ({@code BR-LEADER-6}, {@code BR-LIFE-10}).
 */
public interface LeaderMandateRepository extends JpaRepository<LeaderMandate, UUID> {

    /**
     * Действующий мандат задачи.
     *
     * @param proposal задача
     * @return мандат, если задача кому-то поручена
     */
    @Query("""
            select mandate
            from LeaderMandate mandate
            join fetch mandate.leader
            where mandate.proposal = :proposal
              and mandate.endedAt is null
            """)
    Optional<LeaderMandate> findActive(Proposal proposal);

    /**
     * Действующие мандаты набора задач — одним запросом.
     *
     * @param proposals задачи
     * @return мандаты, у которых ещё нет даты завершения
     */
    @Query("""
            select mandate
            from LeaderMandate mandate
            join fetch mandate.leader
            join fetch mandate.proposal
            where mandate.proposal in :proposals
              and mandate.endedAt is null
            """)
    List<LeaderMandate> findActiveFor(Collection<Proposal> proposals);

    /**
     * Вся история мандатов задачи.
     *
     * @param proposal задача
     * @return мандаты в порядке выдачи
     */
    @Query("""
            select mandate
            from LeaderMandate mandate
            join fetch mandate.leader
            where mandate.proposal = :proposal
            order by mandate.startedAt, mandate.id
            """)
    List<LeaderMandate> findAllForProposal(Proposal proposal);
}

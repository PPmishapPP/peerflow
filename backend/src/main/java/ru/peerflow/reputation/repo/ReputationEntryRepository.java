package ru.peerflow.reputation.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.reputation.domain.ReputationEntry;

/**
 * Доступ к журналу репутации ({@code BR-REP-1}, {@code BR-REP-4}).
 *
 * <p>Сумма считается в базе: репутация — второй тай-брейкер при выборе лидера,
 * и она нужна на каждого кандидата. Тащить все записи в память, чтобы сложить
 * их, — тот же N+1, только по объёму.
 */
public interface ReputationEntryRepository extends JpaRepository<ReputationEntry, UUID> {

    /**
     * Сумма репутации человека.
     *
     * @param memberId аккаунт
     * @return очки; {@code 0}, если начислений не было
     */
    @Query("""
            select coalesce(sum(entry.points), 0)
            from ReputationEntry entry
            where entry.member.id = :memberId
            """)
    long sumForMember(UUID memberId);

    /**
     * Суммы репутации для набора людей — одним запросом
     * ({@code BR-REP-4}).
     *
     * @param memberIds аккаунты
     * @return пары «аккаунт + сумма»
     */
    @Query("""
            select entry.member.id, sum(entry.points)
            from ReputationEntry entry
            where entry.member.id in :memberIds
            group by entry.member.id
            """)
    List<Object[]> sumForMembers(Collection<UUID> memberIds);

    /**
     * Начисление за конкретную задачу.
     *
     * @param proposal задача
     * @return запись, если задачу уже закрывали
     */
    Optional<ReputationEntry> findByProposal(Proposal proposal);
}

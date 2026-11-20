package ru.peerflow.legitimacy.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.legitimacy.domain.Candidate;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Доступ к кандидатурам по задаче ({@code BR-LEADER-1}, {@code BR-LEADER-2}).
 *
 * <p>Читаем сразу с аккаунтами: имена кандидатов нужны в каждом ответе, и
 * «ленивая» подгрузка на кандидата дала бы N+1.
 */
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    /**
     * Все кандидатуры задачи.
     *
     * @param proposal задача
     * @return кандидаты в порядке выдвижения
     */
    @Query("""
            select candidate
            from Candidate candidate
            join fetch candidate.member
            where candidate.proposal = :proposal
            order by candidate.createdAt, candidate.id
            """)
    List<Candidate> findAllForProposal(Proposal proposal);

    /**
     * Кандидатура конкретного члена по задаче.
     *
     * @param proposal задача
     * @param memberId член сообщества
     * @return кандидатура, если человек выдвигался
     */
    @Query("""
            select candidate
            from Candidate candidate
            where candidate.proposal = :proposal
              and candidate.member.id = :memberId
            """)
    Optional<Candidate> findForProposalAndMember(Proposal proposal, UUID memberId);
}

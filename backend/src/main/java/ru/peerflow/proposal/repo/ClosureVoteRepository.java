package ru.peerflow.proposal.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.proposal.domain.ClosureVote;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Голоса «выполнено / не выполнено» ({@code BR-VOTE-5}).
 *
 * <p>Читаются вместе с голосующими: экран закрытия показывает, кто как
 * ответил, и «ленивая» подгрузка на каждую строку дала бы N+1.
 */
public interface ClosureVoteRepository extends JpaRepository<ClosureVote, UUID> {

    /**
     * Голоса по задаче.
     *
     * @param proposal задача
     * @return голоса, возможно пустой список
     */
    @Query("""
            select vote
            from ClosureVote vote
            join fetch vote.voter
            where vote.proposal = :proposal
            order by vote.createdAt, vote.id
            """)
    List<ClosureVote> findAllForProposal(Proposal proposal);
}

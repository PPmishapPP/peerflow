package ru.peerflow.proposal.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.community.domain.Community;
import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.proposal.domain.ProposalStage;

/**
 * Доступ к предложениям ({@code BR-OVER-2}, {@code BR-LIFE-1}).
 *
 * <p>Читаем сразу с автором и голосами: список и карточка всегда показывают
 * и автора, и счётчики ({@code BR-RANK-2}), а «ленивая» подгрузка на каждую
 * строку списка дала бы N+1 запрос. Голосующих внутри голосов тоже подгружаем
 * — без них нельзя посчитать уникальных участников.
 *
 * <p>Сортировка по популярности в SQL не выражается честно: популярность —
 * функция от числа голосов и уникальных участников, и её формула должна жить
 * в одном месте — в домене ({@link Proposal#popularity()}). Поэтому из базы
 * приходят все нужные предложения, а порядок задаёт сервис.
 */
public interface ProposalRepository extends JpaRepository<Proposal, UUID> {

    /**
     * Предложения сообщества в заданных стадиях.
     *
     * <p>Стадии передаются параметром, а не перечислением в тексте запроса:
     * так список стадий меняется в одном месте — в сервисе.
     *
     * @param community сообщество
     * @param stages    интересующие стадии
     * @return предложения, возможно пустой список
     */
    @Query("""
            select distinct proposal
            from Proposal proposal
            join fetch proposal.author
            left join fetch proposal.votes vote
            left join fetch vote.voter
            where proposal.community = :community and proposal.stage in :stages
            """)
    List<Proposal> findAllForCommunity(Community community, Collection<ProposalStage> stages);

    /**
     * Одно предложение с автором и голосами.
     *
     * @param id идентификатор предложения
     * @return предложение, если оно есть
     */
    @Query("""
            select proposal
            from Proposal proposal
            join fetch proposal.author
            left join fetch proposal.votes vote
            left join fetch vote.voter
            where proposal.id = :id
            """)
    Optional<Proposal> findByIdWithVotes(UUID id);
}

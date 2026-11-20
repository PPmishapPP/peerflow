package ru.peerflow.legitimacy.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.legitimacy.domain.LegitimacyEvent;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Доступ к журналу легитимности ({@code BR-LEGIT-2}, {@code BR-LEGIT-3},
 * {@code BR-LEGIT-5}).
 *
 * <p>Журнал только читается и дописывается: обновлять события нельзя, поэтому
 * репозиторий не даёт ни одного метода изменения. Порядок — по колонке
 * {@code seq}, а не по времени: два события в одну миллисекунду иначе
 * упорядочивались бы случайно, и «текущее состояние» зависело бы от везения.
 */
public interface LegitimacyEventRepository extends JpaRepository<LegitimacyEvent, UUID> {

    /**
     * Все события задачи в порядке добавления.
     *
     * <p>Участников подгружаем сразу: свёртка состояния читает и того, кто
     * доверил, и того, кому доверили, — «ленивая» подгрузка на каждое событие
     * дала бы N+1.
     *
     * @param proposal задача
     * @return события, возможно пустой список
     */
    @Query("""
            select event
            from LegitimacyEvent event
            join fetch event.member
            left join fetch event.candidate
            where event.proposal = :proposal
            order by event.seq
            """)
    List<LegitimacyEvent> findAllForProposal(Proposal proposal);

    /**
     * События набора задач — одним запросом.
     *
     * <p>Нужно списку предложений: проверять порог удержания у каждого
     * предложения отдельным запросом — это N+1 на самом частом экране.
     *
     * @param proposals задачи
     * @return события всех задач в порядке добавления
     */
    @Query("""
            select event
            from LegitimacyEvent event
            join fetch event.member
            left join fetch event.candidate
            where event.proposal in :proposals
            order by event.seq
            """)
    List<LegitimacyEvent> findAllForProposals(Collection<Proposal> proposals);
}

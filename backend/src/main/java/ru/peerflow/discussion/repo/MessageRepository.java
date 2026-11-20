package ru.peerflow.discussion.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.discussion.domain.Message;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Доступ к сообщениям дискуссии ({@code BR-DISC-1}, {@code BR-DISC-3}).
 *
 * <p>Ветку читаем сразу с автором и реакциями: экран показывает и автора, и
 * счётчики «плюс / минус / спам», а «ленивая» подгрузка на каждое сообщение
 * дала бы N+1. Реагирующих внутри реакций тоже подгружаем — без них нельзя
 * понять, какая реакция «моя».
 *
 * <p>Порядок задаёт сервис, а не SQL: правило ранжирования
 * ({@code BR-DISC-4}, {@code BR-DISC-5}) должно жить в одном месте — в домене,
 * и работать одинаково для корней и для веток.
 */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Все сообщения предложения с авторами и реакциями.
     *
     * @param proposal предложение
     * @return сообщения, возможно пустой список
     */
    @Query("""
            select distinct message
            from Message message
            join fetch message.author
            left join fetch message.reactions reaction
            left join fetch reaction.member
            where message.proposal = :proposal
            """)
    List<Message> findAllForProposal(Proposal proposal);

    /**
     * Пары «предложение + автор сообщения» для набора предложений
     * ({@code BR-RANK-2}).
     *
     * <p>Пары, а не distinct-счётчик: уникальность участников считает домен
     * вместе с автором и голосовавшими, иначе одна и та же формула была бы
     * размазана по двум местам.
     *
     * @param proposals предложения
     * @return пары «предложение + автор»
     */
    @Query("""
            select new ru.peerflow.discussion.repo.ProposalParticipant(
                message.proposal.id, message.author.id)
            from Message message
            where message.proposal in :proposals
            """)
    List<ProposalParticipant> findParticipants(Collection<Proposal> proposals);
}

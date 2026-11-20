package ru.peerflow.report.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.peerflow.proposal.domain.Proposal;
import ru.peerflow.report.domain.Attachment;

/**
 * Вложения отчётов ({@code BR-LIFE-7}).
 *
 * <p>Отчёты задачи читаются одним запросом вместе с автором: экран показывает
 * и файл, и кто его приложил, а «ленивая» подгрузка на каждую строку дала бы
 * N+1.
 */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Файлы задачи, свежие сверху.
     *
     * @param proposal задача
     * @return вложения, возможно пустой список
     */
    @Query("""
            select attachment
            from Attachment attachment
            join fetch attachment.author
            where attachment.proposal = :proposal
            order by attachment.createdAt desc, attachment.id desc
            """)
    List<Attachment> findAllForProposal(Proposal proposal);
}

package ru.peerflow.community.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.peerflow.community.domain.Community;

/**
 * Доступ к сообществу.
 *
 * <p>В MVP сообщество одно ({@code BR-JOIN-2}), поэтому «текущее сообщество» —
 * это единственная строка таблицы; {@link #findFirstByOrderByCreatedAtAsc()}
 * возвращает его без знания идентификатора.
 */
public interface CommunityRepository extends JpaRepository<Community, UUID> {

    /**
     * Возвращает сообщество сервера.
     *
     * <p>Если сообществ окажется несколько, «текущим» считается созданное
     * первым — это то сообщество, в которое попал первый зарегистрировавшийся
     * ({@code BR-JOIN-3}).
     *
     * @return сообщество, если оно уже создано
     */
    Optional<Community> findFirstByOrderByCreatedAtAsc();
}

package ru.peerflow.reputation.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.peerflow.reputation.repo.ReputationEntryRepository;

/**
 * Чтение репутации для витрины и тай-брейкера ({@code BR-REP-2},
 * {@code BR-REP-4}).
 *
 * <p>Начисление живёт в закрытии задачи, а здесь — только чтение: репутация
 * обязана быть доступна раньше, чем появится первое закрытие
 * ({@code BR-REP-4} — второй тай-брейкер при выборе лидера).
 *
 * <p>Набор сумм читается одним запросом: кандидатов у задачи может быть
 * несколько, и отдельный запрос на каждого — это N+1 на самом видном месте.
 */
@Service
public class ReputationService {

    private final ReputationEntryRepository repository;

    public ReputationService(ReputationEntryRepository repository) {
        this.repository = repository;
    }

    /**
     * Репутация одного человека.
     *
     * @param memberId аккаунт
     * @return очки; {@code 0}, если начислений не было
     */
    @Transactional(readOnly = true)
    public long reputationOf(UUID memberId) {
        Objects.requireNonNull(memberId, "аккаунт обязателен");
        return repository.sumForMember(memberId);
    }

    /**
     * Репутация набора людей — одним запросом ({@code BR-REP-4}).
     *
     * @param memberIds аккаунты
     * @return суммы по аккаунтам; отсутствующие считаются нулём
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> reputationOf(List<UUID> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> sums = new HashMap<>();
        for (Object[] row : repository.sumForMembers(memberIds)) {
            sums.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return sums;
    }
}

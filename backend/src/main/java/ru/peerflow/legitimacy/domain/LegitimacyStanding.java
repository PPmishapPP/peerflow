package ru.peerflow.legitimacy.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Текущее распределение легитимности по одной задаче
 * ({@code BR-LEGIT-2}, {@code BR-LEGIT-3}, {@code BR-LEGIT-5}).
 *
 * <p>Это «свёртка» журнала: у каждого члена ровно одна единица доверия
 * ({@code BR-LEGIT-1}), поэтому состояние — просто отображение «член →
 * кандидат». Передача перезаписывает запись, отзыв её удаляет. Именно из-за
 * того, что единица одна, повторная передача не «накапливается»: человек не
 * может поддержать двоих одной и той же легитимностью.
 *
 * <p>Свёртка считается по каждому предложению отдельно
 * ({@code BR-LEGIT-3}): поддержка по одной задаче не видна в другой, иначе
 * «независимость по задачам» была бы только словами.
 *
 * <p>События ожидаются в порядке журнала (как их отдаёт репозиторий). Порядок
 * важен: «последнее событие побеждает», и перепутанный порядок дал бы чужое
 * состояние.
 */
public final class LegitimacyStanding {

    private final Map<UUID, UUID> delegations;

    private LegitimacyStanding(Map<UUID, UUID> delegations) {
        this.delegations = Map.copyOf(delegations);
    }

    /**
     * Свёртывает журнал в текущее состояние.
     *
     * @param events события одной задачи в порядке добавления
     * @return распределение легитимности
     */
    public static LegitimacyStanding of(List<LegitimacyEvent> events) {
        Map<UUID, UUID> delegations = new HashMap<>();
        for (LegitimacyEvent event : events) {
            UUID memberId = event.getMember().getId();
            if (event.getKind() == LegitimacyEventKind.DELEGATE) {
                delegations.put(memberId, event.getCandidate().getId());
            } else {
                // Отзыв — не «передача себе», а возврат к состоянию «доверие
                // у себя»: записи просто нет (BR-LEGIT-5).
                delegations.remove(memberId);
            }
        }
        return new LegitimacyStanding(delegations);
    }

    /**
     * Пустое состояние: никто никому не передавал доверие.
     *
     * @return распределение без единой передачи
     */
    public static LegitimacyStanding empty() {
        return new LegitimacyStanding(Map.of());
    }

    /**
     * Кому сейчас передана легитимность члена.
     *
     * @param memberId член сообщества
     * @return кандидат; {@code null} — доверие осталось у себя
     */
    public UUID recipientOf(UUID memberId) {
        return delegations.get(memberId);
    }

    /**
     * Передана ли легитимность члена хоть кому-то.
     *
     * @param memberId член сообщества
     * @return {@code true}, если человек сейчас поддерживает кандидата
     */
    public boolean isDelegated(UUID memberId) {
        return delegations.containsKey(memberId);
    }

    /**
     * Сколько членов передали легитимность кандидату — его мандат
     * ({@code BR-LEGIT-4}).
     *
     * @param candidateId кандидат
     * @return число поддерживающих
     */
    public long supportersOf(UUID candidateId) {
        return delegations.values().stream()
                .filter(candidateId::equals)
                .count();
    }

    /**
     * Сколько всего легитимности передано кандидатам.
     *
     * @return число переданных единиц доверия
     */
    public long totalDelegated() {
        return delegations.size();
    }

    /**
     * Кандидаты, которым сейчас кто-то доверил легитимность.
     *
     * @return идентификаторы кандидатов
     */
    public Set<UUID> recipients() {
        return Set.copyOf(delegations.values());
    }

    /**
     * Полное распределение «член → кандидат» без делегировавших себе.
     *
     * @return неизменяемое отображение
     */
    public Map<UUID, UUID> delegations() {
        return delegations;
    }
}

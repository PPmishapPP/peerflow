package ru.peerflow.legitimacy.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.peerflow.legitimacy.domain.MandateEndReason;

/**
 * Лидер задачи и состояние его мандата ({@code BR-LEGIT-4},
 * {@code BR-LEADER-5}, {@code BR-LEADER-6}).
 *
 * <p>Порог удержания отдаётся числом, а не только флагом «под угрозой»:
 * человеку нужна шкала («осталось 8 из 10»), а не бинарный цвет. Считать её на
 * клиенте из процентов значило бы повторить правило в другом месте.
 *
 * @param memberId         аккаунт лидера
 * @param displayName      имя для человека
 * @param support          сколько легитимности у него сейчас
 * @param initialSupport   сколько было при выборах
 * @param requiredSupport  сколько нужно, чтобы удержать мандат
 *                          ({@code BR-LEADER-5})
 * @param startedAt        когда мандат выдан
 * @param active           действует ли мандат
 * @param endReason        почему завершён; {@code null} — действует
 */
public record LeaderResponse(
        UUID memberId,
        String displayName,
        long support,
        long initialSupport,
        long requiredSupport,
        Instant startedAt,
        boolean active,
        MandateEndReason endReason
) {
}

package ru.peerflow.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Пороги MVP из бизнес-описания ({@code docs/peerflow.md}).
 *
 * <p>Пороги — конфигурация, а не «магические числа» в логике: значения
 * приходят из {@code application.yml} (префикс {@code peerflow.thresholds})
 * и проверяются при старте. Значения по умолчанию совпадают с порогами,
 * зафиксированными в бизнес-описании, поэтому приложение работает верно даже
 * с пустой конфигурацией.
 *
 * <p>В будущем значения смогут меняться через предложение сообщества
 * ({@code BR-LIMIT-4}), поэтому они не зашиты в код правил.
 *
 * @param legitimacyRetainRatio доля сохранившейся легитимности лидера, при
 *                              которой мандат ещё действителен;
 *                              {@code 0.70} означает «потерял больше 30% —
 *                              мандат утрачен» ({@code BR-LIMIT-1},
 *                              {@code BR-LEADER-5})
 * @param spamHideMarks         количество меток «спам», начиная с которого
 *                              сообщение скрывается ({@code BR-LIMIT-2},
 *                              {@code BR-SPAM-1})
 * @param joinAcceptRatio       минимальная доля голосов «за» от числа членов
 *                              сообщества для приёма нового участника
 *                              ({@code BR-LIMIT-3}, {@code BR-JOIN-5})
 */
@Validated
@ConfigurationProperties(prefix = "peerflow.thresholds")
public record PeerflowThresholds(

        @DefaultValue("0.70")
        @DecimalMin(value = "0.0", message = "доля удержания легитимности не может быть отрицательной")
        @DecimalMax(value = "1.0", message = "доля удержания легитимности не может быть больше 1")
        double legitimacyRetainRatio,

        @DefaultValue("3")
        @Min(value = 1, message = "для скрытия спама нужна хотя бы одна метка")
        int spamHideMarks,

        @DefaultValue("0.30")
        @DecimalMin(value = "0.0", message = "доля голосов за приём не может быть отрицательной")
        @DecimalMax(value = "1.0", message = "доля голосов за приём не может быть больше 1")
        double joinAcceptRatio
) {
}

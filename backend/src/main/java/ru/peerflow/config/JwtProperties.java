package ru.peerflow.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки токена доступа ({@code BR-JOIN-1}, {@code BR-PRIV-1}).
 *
 * <p>Токен — JWT с подписью HMAC. Секрет и срок жизни — конфигурация, а не
 * константы в коде: в локальной разработке работают умолчания из
 * {@code application.yml}, на сервере секрет задаётся переменной окружения
 * {@code PEERFLOW_JWT_SECRET}.
 *
 * @param secret секрет подписи; для HS256 требуется не меньше 32 байт
 * @param ttl    срок жизни выданного токена
 */
@Validated
@ConfigurationProperties(prefix = "peerflow.security.jwt")
public record JwtProperties(

        @NotBlank
        @Size(min = 32, message = "секрет JWT должен быть не короче 32 символов")
        String secret,

        @NotNull
        @DefaultValue("PT12H")
        Duration ttl
) {
}

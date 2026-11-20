package ru.peerflow.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Криптография доступа: хэширование пароля и подпись токена.
 *
 * <p>Пароль хранится только как BCrypt-хэш ({@code BR-JOIN-1}). Токен — JWT
 * с подписью HMAC-SHA256: одна и та же ключевая пара используется для выпуска
 * ({@link JwtEncoder}) и проверки ({@link JwtDecoder}), внешних ключей в MVP нет.
 *
 * <p>Алгоритм и параметры держим в конфигурации, а не в коде правил: секрет
 * приходит из {@link JwtProperties}, поэтому на сервере он не совпадает
 * с локальным.
 */
@Configuration
public class JwtConfig {

    /** Алгоритм подписи: симметричный ключ, не меньше 256 бит. */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Кодировщик паролей для регистрации и входа.
     *
     * <p>BCrypt с силой 10 — стандартное значение Spring Security: достаточная
     * стойкость при приемлемой скорости проверки на сервере.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Ключ подписи токена из секрета в конфигурации.
     *
     * <p>Секрет — строка в UTF-8; для HS256 нужно не меньше 32 байт, что
     * проверяется в {@link JwtProperties}.
     */
    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(secret, HMAC_ALGORITHM);
    }

    /**
     * Выпуск токенов: тот же ключ, что и для проверки.
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSecretKey));
    }

    /**
     * Проверка токенов, приходящих в заголовке {@code Authorization: Bearer}.
     *
     * <p>Дополнительно к подписи проверяется срок жизни токена.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).build();
        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }
}

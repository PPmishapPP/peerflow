package ru.peerflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Каркас безопасности сервера.
 *
 * <p>Здесь только то, что нужно на этапе скелета: контент сообщества закрыт
 * ({@code BR-PRIV-1}), а проверка живости доступна без входа — её дёргают
 * мониторинг и балансировщик.
 *
 * <p>Аутентификация (BCrypt, JWT, регистрация и вход) настраивается отдельно
 * в задаче 5 плана — здесь её намеренно нет.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Цепочка фильтров: без сессий (REST + токен), CSRF не нужен, потому что
     * состояние запроса не хранится в куки.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}

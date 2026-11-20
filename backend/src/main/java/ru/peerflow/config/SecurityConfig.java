package ru.peerflow.config;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Безопасность сервера.
 *
 * <p>Контент сообщества закрыт ({@code BR-PRIV-1}): любой запрос, кроме
 * проверки живости и самих регистрации/входа, требует действующего токена.
 * Регистрация и вход открыты по необходимости — иначе войти было бы некому
 * ({@code BR-JOIN-1}).
 *
 * <p>Схема без сессий: токен приходит в заголовке {@code Authorization} и
 * проверяется по подписи, состояние на сервере не хранится. CSRF не нужен по
 * той же причине — браузерных куки здесь нет.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Цепочка фильтров: открытые точки входа, JWT для остального.
     *
     * @param http                конфигуратор безопасности
     * @param objectMapper        сериализация тела ошибок
     * @return настроенная цепочка
     * @throws Exception если конфигурация не собралась
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Асинхронная доставка (SSE-поток `/api/events`) идёт
                        // вторым проходом фильтров, уже без запроса клиента:
                        // токен проверен на первом. Без этого разрешения
                        // открытый поток закрывался бы отказом в доступе.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        // BR-JOIN-1: без этих двух точек войти невозможно.
                        .requestMatchers("/api/auth/register", "/api/auth/login")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                request, response, objectMapper,
                                HttpStatus.UNAUTHORIZED, "Требуется вход",
                                "токен отсутствует, истёк или недействителен")))
                // BR-PRIV-1: обращаемся к защищённому контенту без прав.
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                request, response, objectMapper,
                                HttpStatus.FORBIDDEN, "Доступ запрещён",
                                "недостаточно прав для этого действия")))
                .build();
    }

    /**
     * Пишет ошибку безопасности в том же формате, что и остальной API.
     *
     * <p>Пустое тело {@code 401} неудобно клиенту: непонятно, истёк токен или
     * его вовсе не прислали. Поэтому отвечаем {@code application/problem+json}.
     */
    private static void writeProblem(HttpServletRequest request,
                                     HttpServletResponse response,
                                     ObjectMapper objectMapper,
                                     HttpStatus status,
                                     String title,
                                     String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

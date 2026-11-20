package ru.peerflow.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * База для тестов, которым нужен настоящий PostgreSQL.
 *
 * <p>Контейнер объявлен как {@code static} и помечен {@link ServiceConnection}:
 * Spring Boot сам подставит его URL/логин/пароль в {@code spring.datasource.*},
 * поэтому в тестовых свойствах не нужно дублировать настройки подключения.
 * Testcontainers переиспользует один контейнер между классами тестов
 * (JUnit 5 + статический контейнер), а {@code @ServiceConnection} убирает
 * необходимость в {@code @DynamicPropertySource}.
 *
 * <p>Версия образа задаётся свойством {@code peerflow.testcontainers.postgres.image}
 * (по умолчанию {@code postgres:16-alpine}). Если нужно прогнать тесты на той же
 * мажорной версии, что и локальный сервер, её можно переопределить из Gradle:
 * {@code gradlew test -PpeerflowPostgresImage=postgres:13-alpine}.
 *
 * <p>Требуется запущенный Docker. Если Docker недоступен, тесты упадут
 * с явной ошибкой подключения к Docker API — это осознанно: тесты на H2
 * не проверяют то, что действительно важно (типы, ограничения, Flyway).
 */
@Testcontainers
@ActiveProfiles("test")
public abstract class PostgresTestBase {

    private static final String POSTGRES_IMAGE =
            System.getProperty("peerflow.testcontainers.postgres.image", "postgres:16-alpine");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE));
}

package ru.peerflow.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * База для тестов, которым нужен настоящий PostgreSQL.
 *
 * <p>Контейнер помечен {@link ServiceConnection}: Spring Boot сам подставит его
 * URL/логин/пароль в {@code spring.datasource.*}, поэтому в тестовых свойствах
 * не нужно дублировать настройки подключения, а {@code @DynamicPropertySource}
 * не требуется.
 *
 * <p>Контейнер <strong>один на весь прогон тестов</strong> и не принадлежит
 * жизненному циклу отдельного тестового класса: он стартует в статическом
 * инициализаторе и живёт до завершения JVM (остановку выполняет Ryuk).
 * Это важно из-за кэша контекстов Spring: контекст создаётся один раз и
 * переиспользуется следующими классами, а его {@code DataSource} хранит
 * конкретный порт. Если бы контейнер останавливался после класса, кэшированный
 * контекст обращался бы к уже закрытому порту и падал с
 * {@code Connection refused}. Поэтому здесь осознанно <em>нет</em>
 * {@code @Testcontainers} и {@code @Container}.
 *
 * <p>Классы, которые пишут в базу, обязаны сами очищать данные между тестами:
 * база общая для всех тестов прогона.
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
@ActiveProfiles("test")
public abstract class PostgresTestBase {

    private static final String POSTGRES_IMAGE =
            System.getProperty("peerflow.testcontainers.postgres.image", "postgres:16-alpine");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE));

    static {
        POSTGRES.start();
    }
}

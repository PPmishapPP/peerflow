package ru.peerflow.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ru.peerflow.support.PostgresTestBase;

/**
 * Интеграционный тест миграций Flyway на настоящем PostgreSQL (Testcontainers).
 *
 * <p>Проверяем то, от чего зависит вся дальнейшая работа со схемой:
 * <ul>
 *   <li>миграции из {@code classpath:db/migration} накатываются при старте;</li>
 *   <li>в истории Flyway нет неуспешных и «зависших» миграций;</li>
 *   <li>служебные объекты базовой миграции действительно созданы —
 *       расширение {@code pgcrypto} и функция {@code gen_random_uuid()},
 *       на которую опираются UUID-ключи всех доменных таблиц.</li>
 * </ul>
 *
 * <p>Тест осознанно опирается на бин {@link Flyway}, а не на ручной вызов
 * {@code Flyway.configure()}: так проверяется та же конфигурация, с которой
 * приложение поднимается в бою ({@code spring.flyway.*}).
 */
@SpringBootTest
class FlywayMigrationTest extends PostgresTestBase {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Все миграции применены: нет неуспешных и ожидающих")
    void allMigrationsAreApplied() {
        MigrationInfo[] all = flyway.info().all();
        assertThat(all)
                .as("должна быть хотя бы базовая миграция V1")
                .isNotEmpty();

        assertThat(flyway.info().pending())
                .as("после старта не должно остаться неприменённых миграций")
                .isEmpty();

        assertThat(all)
                .as("ни одна миграция не должна быть в состоянии FAILED")
                .noneMatch(info -> info.getState() == MigrationState.FAILED);

        assertThat(all)
                .extracting(MigrationInfo::getVersion)
                .as("базовая миграция V1 входит в историю")
                .anyMatch(version -> version != null && "1".equals(version.getVersion()));
    }

    @Test
    @DisplayName("Повторный migrate() ничего не делает — миграции идемпотентны")
    void repeatedMigrateAppliesNothing() {
        int appliedBefore = flyway.info().applied().length;

        flyway.migrate();

        assertThat(flyway.info().applied())
                .as("схема уже накатана, повторный запуск не применяет миграций")
                .hasSize(appliedBefore);
    }

    @Test
    @DisplayName("Базовая миграция создала gen_random_uuid(), ключи UUID работают")
    void baselineCreatesUuidGenerator() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "select gen_random_uuid(), gen_random_uuid()")) {
            assertThat(resultSet.next()).isTrue();

            UUID first = resultSet.getObject(1, UUID.class);
            UUID second = resultSet.getObject(2, UUID.class);

            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(first).as("генератор не должен повторять значения").isNotEqualTo(second);
        }
    }

    @Test
    @DisplayName("История Flyway доступна и содержит успешную запись о V1")
    void flywayHistoryTableIsAvailable() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "select version, success from flyway_schema_history where version = '1'")) {
            assertThat(resultSet.next()).as("в истории должна быть запись о миграции V1").isTrue();
            assertThat(resultSet.getBoolean("success")).isTrue();
        }
    }
}

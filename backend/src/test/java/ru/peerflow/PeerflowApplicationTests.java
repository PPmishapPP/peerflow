package ru.peerflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import ru.peerflow.config.PeerflowThresholds;
import ru.peerflow.support.PostgresTestBase;

/**
 * Smoke-тест каркаса сервера: контекст поднимается на настоящем PostgreSQL
 * в контейнере, пороги читаются, health-эндпоинт отвечает без аутентификации.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PeerflowApplicationTests extends PostgresTestBase {

    @Autowired
    private PeerflowThresholds thresholds;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Контекст приложения поднимается и пороги MVP доступны как бин")
    void contextLoads() {
        assertThat(thresholds).isNotNull();
    }

    @Test
    @DisplayName("Тесты выполняются на настоящем PostgreSQL в контейнере")
    void runsAgainstRealPostgres() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("select version()")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).contains("PostgreSQL");
        }
    }

    @Test
    @DisplayName("Health-эндпоинт отвечает 200 без аутентификации")
    void healthEndpointIsPubliclyAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\"");
    }
}

package ru.peerflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа сервера PeerFlow.
 *
 * <p>Сервер хранит состояние сообщества и считает бизнес-правила из
 * {@code docs/peerflow.md}; клиент (Android) их не дублирует.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PeerflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(PeerflowApplication.class, args);
    }
}

package ru.peerflow.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Часы как бин.
 *
 * <p>Правила, которые зависят от времени (срок голосования
 * {@code BR-VOTE-9}), не должны читать {@link java.time.Instant#now()}
 * напрямую: иначе их нельзя проверить на границе — «до дедлайна» и «после» —
 * не дожидаясь реальных 48 часов. Через бин тест подставляет сдвинутые часы.
 */
@Configuration
public class ClockConfig {

    /**
     * Системные часы в UTC.
     *
     * @return часы приложения
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

package ru.peerflow.config;

import java.nio.file.Path;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Каталог вложений: аватары и файлы отчётов.
 *
 * <p>По решению планирования вложения лежат на локальном диске сервера, а в БД
 * хранится только путь. В MVP это осознанное упрощение: внешнего объектного
 * хранилища нет.
 *
 * @param root корневой каталог; относительный путь отсчитывается от рабочего
 *             каталога процесса
 */
@Validated
@ConfigurationProperties(prefix = "peerflow.storage")
public record StorageProperties(

        @NotBlank
        @DefaultValue("storage")
        String root
) {

    /**
     * Корневой каталог вложений как абсолютный путь.
     *
     * @return абсолютный путь к каталогу
     */
    public Path rootPath() {
        return Path.of(root).toAbsolutePath().normalize();
    }
}

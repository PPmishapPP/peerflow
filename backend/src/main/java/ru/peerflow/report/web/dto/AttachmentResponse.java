package ru.peerflow.report.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Файл отчёта в том виде, в каком его видит клиент ({@code BR-LIFE-7}).
 *
 * <p>Путь на диске наружу не отдаём: клиенту нужен адрес скачивания и имя
 * файла, а не устройство хранилища сервера.
 *
 * @param id          идентификатор вложения
 * @param fileName    имя, которое файлу дал человек
 * @param contentType тип содержимого
 * @param sizeBytes   размер в байтах
 * @param authorId    кто приложил
 * @param authorName  имя автора
 * @param createdAt   когда приложил
 * @param url         адрес скачивания
 */
public record AttachmentResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        UUID authorId,
        String authorName,
        Instant createdAt,
        String url
) {
}

package ru.peerflow.community.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ru.peerflow.config.StorageProperties;

/**
 * Сохранение аватаров на диск сервера.
 *
 * <p>В БД попадает только относительный путь внутри каталога вложений
 * ({@code peerflow.storage.root}) — так файлы можно перенести, не меняя схему.
 *
 * <p>Что проверяем: файл не пуст, тип — изображение, размер в разумных рамках.
 * Проверка типа по расширению не заменяет антивирус, но отсекает очевидно
 * посторонние файлы; полноценный разбор содержимого в MVP не делаем (YAGNI).
 */
@Service
public class AvatarStorage {

    /** Больше этого аватар не принимаем — картинка профиля не требует больше. */
    static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    /** Разрешённые расширения: только растровые изображения. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    /** Подкаталог каталога вложений, куда складываем аватары. */
    private static final String AVATARS_DIR = "avatars";

    private final StorageProperties properties;

    public AvatarStorage(StorageProperties properties) {
        this.properties = properties;
    }

    /**
     * Сохраняет аватар и возвращает путь относительно корня вложений.
     *
     * @param avatar загруженный файл
     * @return относительный путь вида {@code avatars/<uuid>.png}
     * @throws IllegalArgumentException если файл пуст, слишком большой или
     *                                  не является поддерживаемым изображением
     * @throws IOException              если не удалось записать файл
     */
    public String store(MultipartFile avatar) throws IOException {
        if (avatar == null || avatar.isEmpty()) {
            throw new IllegalArgumentException("файл аватара пуст");
        }
        if (avatar.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException(
                    "аватар больше допустимых " + MAX_AVATAR_BYTES / (1024 * 1024) + " МБ");
        }

        String extension = extensionOf(avatar.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "поддерживаются только изображения: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        String fileName = UUID.randomUUID() + "." + extension;
        Path directory = properties.rootPath().resolve(AVATARS_DIR);
        Files.createDirectories(directory);

        Path target = directory.resolve(fileName).normalize();
        // Страховка от выхода за пределы каталога вложений: имя файла мы
        // генерируем сами, но путь всё равно проверяем.
        if (!target.startsWith(properties.rootPath())) {
            throw new IllegalArgumentException("недопустимый путь файла");
        }

        try (InputStream source = avatar.getInputStream()) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return AVATARS_DIR + "/" + fileName;
    }

    /**
     * Удаляет ранее сохранённый аватар. Ошибки игнорируются: файла может уже
     * не быть, и это не повод ломать регистрацию.
     *
     * @param relativePath путь, возвращённый {@link #store(MultipartFile)}
     */
    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(properties.rootPath().resolve(relativePath).normalize());
        } catch (IOException ignored) {
            // Файл — не критичные данные: аккаунт важнее.
        }
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

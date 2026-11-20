package ru.peerflow.report.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.hibernate.proxy.HibernateProxy;

import ru.peerflow.community.domain.User;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Файл отчёта лидера ({@code BR-LIFE-7}, {@code BR-BUDGET-5}).
 *
 * <p>Отчёт — это файл или написанное сообщение, отдельного формата нет. Текст
 * живёт в обычном сообщении под задачей ({@code BR-DISC-1}), поэтому файл —
 * самостоятельная запись о том, что лидер приложил, а не часть агрегата
 * сообщения. Так «отчёт с текстом» и «отчёт с файлом» устроены одинаково.
 *
 * <p>В базе хранится только описание файла; сам файл лежит на диске сервера
 * под {@code peerflow.storage.root}. Путь относительный, поэтому каталог можно
 * перенести, не меняя строки в базе.
 *
 * <p>Размер и тип фиксируются здесь, а не вычисляются при каждом запросе:
 * файл на диске могли заменить, а сообществу важно то, что лидер отправил.
 */
@Entity
@Table(name = "attachment")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false, updatable = false)
    private Proposal proposal;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    /** Путь относительно корня вложений, например {@code reports/<uuid>.pdf}. */
    @NotBlank
    @Column(name = "path", nullable = false, updatable = false, length = 255)
    private String path;

    /** Имя, которое файлу дал человек: его и видно в списке отчётов. */
    @NotBlank
    @Column(name = "file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @NotBlank
    @Column(name = "content_type", nullable = false, updatable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Attachment() {
    }

    private Attachment(Proposal proposal, User author, String path, String fileName,
                       String contentType, long sizeBytes, Instant now) {
        this.proposal = proposal;
        this.author = author;
        this.path = path;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = now;
    }

    /**
     * Прикрепляет сохранённый файл к задаче ({@code BR-LIFE-7}).
     *
     * @param proposal    задача
     * @param author      кто приложил
     * @param path        путь относительно корня вложений
     * @param fileName    исходное имя файла
     * @param contentType тип содержимого
     * @param sizeBytes   размер в байтах; должен быть больше нуля
     * @param now         момент загрузки
     * @return описание вложения
     * @throws IllegalArgumentException если файл пуст или путь пустой
     */
    public static Attachment attach(Proposal proposal, User author, String path,
                                    String fileName, String contentType,
                                    long sizeBytes, Instant now) {
        Objects.requireNonNull(proposal, "задача обязательна");
        Objects.requireNonNull(author, "автор обязателен");
        Objects.requireNonNull(now, "момент загрузки обязателен");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("путь файла обязателен");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("имя файла обязательно");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("пустой файл приложить нельзя");
        }
        return new Attachment(
                proposal,
                author,
                path,
                fileName.length() > 255 ? fileName.substring(fileName.length() - 255) : fileName,
                contentType == null || contentType.isBlank()
                        ? "application/octet-stream"
                        : contentType,
                sizeBytes,
                now);
    }

    public UUID getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public User getAuthor() {
        return author;
    }

    public String getPath() {
        return path;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> objectEffectiveClass = o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisEffectiveClass != objectEffectiveClass) {
            return false;
        }
        Attachment other = (Attachment) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Attachment{id=" + id + ", fileName=" + fileName + ", sizeBytes=" + sizeBytes + "}";
    }
}

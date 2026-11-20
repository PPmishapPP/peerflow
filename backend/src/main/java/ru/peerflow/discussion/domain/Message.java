package ru.peerflow.discussion.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.hibernate.proxy.HibernateProxy;

import ru.peerflow.community.domain.User;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Сообщение дискуссии под предложением ({@code BR-DISC-1}).
 *
 * <p>Сообщение — это аргумент, поэтому текст не может быть пустым. Ответ на
 * другое сообщение — тот же тип с заполненным {@link #getParent()}: так ветки
 * не разъезжаются по двум таблицам ({@code BR-DISC-3}).
 *
 * <p>Отчёт лидера отдельной сущностью не является ({@code BR-LIFE-7}): это
 * тоже сообщение под задачей, просто написанное лидером. Поэтому дискуссия под
 * отчётом работает теми же правилами без второй реализации.
 *
 * <p>Реакции — часть агрегата сообщения: они не имеют смысла без него и
 * меняются только через {@link #react(User, ReactionKind)}, чтобы правило
 * «одна реакция на человека» нельзя было обойти.
 */
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /**
     * Сообщение, на которое это отвечает; {@code null} — корень ветки
     * ({@code BR-DISC-3}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Message parent;

    @NotBlank
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Reaction> reactions = new LinkedHashSet<>();

    protected Message() {
    }

    private Message(Proposal proposal, User author, Message parent, String body) {
        this.proposal = proposal;
        this.author = author;
        this.parent = parent;
        this.body = body;
    }

    /**
     * Пишет сообщение или ответ на сообщение ({@code BR-DISC-1},
     * {@code BR-DISC-3}).
     *
     * <p>Ответ обязан относиться к тому же предложению, что и родитель: иначе
     * ветка уехала бы в чужую задачу, а «вложенные ветки» потеряли бы смысл.
     *
     * @param proposal предложение, под которым идёт дискуссия
     * @param author   действующий член сообщества
     * @param parent   сообщение, на которое отвечают; {@code null} — корень
     * @param body     текст аргумента
     * @param now      момент написания
     * @return новое сообщение
     * @throws IllegalArgumentException если текст пуст или ответ не к тому предложению
     * @throws IllegalStateException    если автор не действующий член сообщества
     */
    public static Message create(Proposal proposal, User author, Message parent,
                                 String body, Instant now) {
        Objects.requireNonNull(proposal, "предложение обязательно");
        Objects.requireNonNull(author, "автор обязателен");
        Objects.requireNonNull(now, "момент написания обязателен");
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("сообщение не может быть пустым");
        }
        if (!author.isActiveMember()
                || !Objects.equals(author.getCommunity(), proposal.getCommunity())) {
            throw new IllegalStateException("писать в дискуссии может только член сообщества");
        }
        if (parent != null && !Objects.equals(parent.getProposal(), proposal)) {
            throw new IllegalArgumentException(
                    "ответ должен относиться к тому же предложению, что и родитель");
        }
        Message message = new Message(proposal, author, parent, body.trim());
        message.createdAt = now;
        return message;
    }

    /**
     * Ставит или меняет реакцию этого члена ({@code BR-DISC-2},
     * {@code BR-VOTE-6}).
     *
     * <p>Одна реакция на человека: повторное нажатие меняет её вид, а не
     * добавляет вторую. Поэтому «одновременно плюс и спам» невозможно, и
     * скрытие спама нельзя накрутить сменой метки.
     *
     * @param member член сообщества
     * @param kind   вид реакции
     * @throws IllegalStateException если reacts не член сообщества предложения
     */
    public void react(User member, ReactionKind kind) {
        Objects.requireNonNull(member, "реагирующий обязателен");
        Objects.requireNonNull(kind, "вид реакции обязателен");
        if (!member.isActiveMember()
                || !Objects.equals(member.getCommunity(), proposal.getCommunity())) {
            throw new IllegalStateException("реагировать может только член сообщества");
        }
        Reaction existing = reactionOf(member);
        if (existing == null) {
            reactions.add(new Reaction(this, member, kind));
        } else {
            existing.changeTo(kind);
        }
    }

    /**
     * Реакция этого члена, если она есть.
     *
     * @param candidate член сообщества
     * @return реакция или {@code null}
     */
    public Reaction reactionOf(User candidate) {
        return reactions.stream()
                .filter(reaction -> reaction.isMadeBy(candidate))
                .findFirst()
                .orElse(null);
    }

    /**
     * Сколько меток «плюс» ({@code BR-DISC-4}).
     *
     * @return число плюсов
     */
    public long plusCount() {
        return count(ReactionKind.PLUS);
    }

    /**
     * Сколько меток «минус» ({@code BR-DISC-4}).
     *
     * @return число минусов
     */
    public long minusCount() {
        return count(ReactionKind.MINUS);
    }

    /**
     * Сколько меток «спам» ({@code BR-SPAM-1}).
     *
     * @return число меток спама
     */
    public long spamCount() {
        return count(ReactionKind.SPAM);
    }

    /**
     * Скрыто ли сообщение как спам ({@code BR-SPAM-1}).
     *
     * <p>Скрытие, а не удаление: метку можно снять, и сообщение снова станет
     * видимым. Порог приходит из настроек ({@code BR-LIMIT-2}), а не зашит
     * числом: он может измениться без переписывания правил.
     *
     * @param hideMarks порог из настроек
     * @return {@code true}, если меток спама достаточно для скрытия
     */
    public boolean isHidden(int hideMarks) {
        return spamCount() >= hideMarks;
    }

    private long count(ReactionKind kind) {
        return reactions.stream().filter(reaction -> reaction.getKind() == kind).count();
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

    public Message getParent() {
        return parent;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Реакции только для чтения: менять их можно лишь через
     * {@link #react(User, ReactionKind)}.
     *
     * @return неизменяемое представление реакций
     */
    public Set<Reaction> getReactions() {
        return Collections.unmodifiableSet(reactions);
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
        Message other = (Message) o;
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
        return "Message{id=" + id + ", createdAt=" + createdAt + "}";
    }
}

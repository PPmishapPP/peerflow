package ru.peerflow.legitimacy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.proxy.HibernateProxy;

import ru.peerflow.community.domain.User;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Событие журнала легитимности по конкретной задаче ({@code BR-LEGIT-2},
 * {@code BR-LEGIT-3}, {@code BR-LEGIT-5}).
 *
 * <p>Почему журнал, а не поле «кому передана легитимность»: отзыв — не отмена
 * передачи, а новое решение члена. Если хранить только текущее состояние,
 * сообществу нечем объяснить, почему лидер остался без мандата
 * ({@code BR-LEADER-6}). Журнал отвечает на это, не теряя ни одного шага, и
 * остаётся единственным источником правды о доверии.
 *
 * <p>Событие неизменяемо: ни сеттеров, ни «поправить» — только добавить
 * следующее. Это же правило продублировано триггером в базе, чтобы его нельзя
 * было обойти запросом мимо кода.
 *
 * <p>Легитимность считается по каждой задаче отдельно
 * ({@code BR-LEGIT-3}): пара «член + задача» — единица состояния, поэтому
 * поддержка по одной задаче никак не влияет на другую ({@code BR-LEADER-8}).
 */
@Entity
@Table(name = "legitimacy_event")
public class LegitimacyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Порядковый номер в журнале — его выдаёт база.
     *
     * <p>Зачем отдельно от времени: два события могут произойти в одну
     * миллисекунду, и тогда «кто последний» стало бы делом случая. Поэтому
     * порядок задаёт монотонный номер, а не часы. Поле только для чтения:
     * {@code GENERATED ALWAYS AS IDENTITY} не принимает вставку извне.
     */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    /** Задача, по которой передают доверие ({@code BR-LEGIT-3}). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false, updatable = false)
    private Proposal proposal;

    /** Чья легитимность — тот, кто доверяет ({@code BR-LEGIT-2}). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private User member;

    /**
     * Кому передали; {@code null} — отзыв, легитимность вернулась себе
     * ({@code BR-LEGIT-5}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", updatable = false)
    private User candidate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 16)
    private LegitimacyEventKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LegitimacyEvent() {
    }

    private LegitimacyEvent(Proposal proposal, User member, User candidate,
                            LegitimacyEventKind kind, Instant now) {
        this.proposal = proposal;
        this.member = member;
        this.candidate = candidate;
        this.kind = kind;
        this.createdAt = now;
    }

    /**
     * Передаёт легитимность кандидату ({@code BR-LEGIT-2}).
     *
     * <p>Передавать можно только свою легитимность и только по задаче своего
     * сообщества: иначе один человек распоряжался бы доверием другого или
     * поддержкой в чужом сообществе ({@code BR-PRIV-1}).
     *
     * @param proposal задача
     * @param member   член сообщества, передающий своё доверие
     * @param candidate кандидат, которому доверяют
     * @param now      момент передачи
     * @return событие передачи
     * @throws IllegalArgumentException если кандидат — сам член
     * @throws IllegalStateException    если кто-то из них не член сообщества задачи
     */
    public static LegitimacyEvent delegate(Proposal proposal, User member,
                                           User candidate, Instant now) {
        Objects.requireNonNull(candidate, "кандидат обязателен");
        requireSameCommunity(proposal, member, "передавать легитимность");
        requireSameCommunity(proposal, candidate, "получать легитимность");
        if (Objects.equals(member.getId(), candidate.getId())) {
            throw new IllegalArgumentException(
                    "передать легитимность себе нельзя: она уже своя");
        }
        return new LegitimacyEvent(
                proposal, member, candidate, LegitimacyEventKind.DELEGATE, now);
    }

    /**
     * Забирает свою легитимность обратно ({@code BR-LEGIT-5}).
     *
     * <p>Отзыв доступен в любой момент и не требует согласия кандидата:
     * доверие — не договор на срок.
     *
     * @param proposal задача
     * @param member   член сообщества, возвращающий своё доверие себе
     * @param now      момент отзыва
     * @return событие отзыва
     * @throws IllegalStateException если человек не член сообщества задачи
     */
    public static LegitimacyEvent revoke(Proposal proposal, User member, Instant now) {
        requireSameCommunity(proposal, member, "забирать легитимность");
        return new LegitimacyEvent(proposal, member, null, LegitimacyEventKind.REVOKE, now);
    }

    /**
     * Кому сейчас принадлежит голос этого члена по этой задаче.
     *
     * @return кандидат; {@code null} — легитимность у себя
     */
    public User recipient() {
        return candidate;
    }

    /**
     * Проверяет, что событие — передача конкретному кандидату.
     *
     * @param candidateId проверяемый кандидат
     * @return {@code true}, если доверие передано ему
     */
    public boolean isDelegatedTo(UUID candidateId) {
        return kind == LegitimacyEventKind.DELEGATE
                && candidate != null
                && Objects.equals(candidate.getId(), candidateId);
    }

    private static void requireSameCommunity(Proposal proposal, User person, String action) {
        Objects.requireNonNull(proposal, "задача обязательна");
        Objects.requireNonNull(person, "член сообщества обязателен");
        if (!person.isActiveMember()
                || !Objects.equals(person.getCommunity(), proposal.getCommunity())) {
            throw new IllegalStateException(
                    action + " может только действующий член сообщества");
        }
    }

    public UUID getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public User getMember() {
        return member;
    }

    public User getCandidate() {
        return candidate;
    }

    public LegitimacyEventKind getKind() {
        return kind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Порядковый номер события в журнале.
     *
     * @return номер, выданный базой; {@code null} у ещё не сохранённого события
     */
    public Long getSeq() {
        return seq;
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
        LegitimacyEvent other = (LegitimacyEvent) o;
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
        return "LegitimacyEvent{id=" + id + ", kind=" + kind + ", createdAt=" + createdAt + "}";
    }
}

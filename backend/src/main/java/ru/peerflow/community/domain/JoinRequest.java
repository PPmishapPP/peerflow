package ru.peerflow.community.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.proxy.HibernateProxy;

/**
 * Заявка на вступление в сообщество ({@code BR-JOIN-4}).
 *
 * <p>Человек, зарегистрировавшийся после первого, не состоит в сообществе.
 * Он заполняет «о себе», прикладывает фото и нажимает «вступить» — так
 * появляется заявка. Действующие члены голосуют «за» или «против»
 * ({@code BR-VOTE-8}); приём происходит, когда голосов «за» набралось не
 * меньше порога {@code BR-LIMIT-3} от числа действующих членов
 * ({@code BR-JOIN-5}).
 *
 * <p>Голоса — часть заявки: они не имеют смысла вне её и живут ровно столько
 * же, сколько сама заявка. Поэтому список голосов принадлежит заявке
 * ({@code cascade = ALL}, {@code orphanRemoval}). Повторный голос одного
 * человека отсекается и здесь, и уникальным ограничением в базе
 * ({@code BR-VOTE-2}: один человек — один голос).
 */
@Entity
@Table(name = "join_request")
public class JoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Кто подал заявку; ещё не член сообщества. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    /** Сообщество, в которое подана заявка. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /**
     * «О себе» на момент подачи. Хранится в заявке, а не читается из профиля:
     * сообщество решает по тому, что человек показал при подаче.
     */
    @Column(name = "about", columnDefinition = "text")
    private String about;

    /** Путь к фото на момент подачи — по тем же причинам, что и {@link #about}. */
    @Column(name = "avatar_path", length = 255)
    private String avatarPath;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Момент решения; {@code null} — заявка ещё рассматривается. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    @OneToMany(mappedBy = "joinRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<JoinVote> votes = new LinkedHashSet<>();

    protected JoinRequest() {
    }

    private JoinRequest(User applicant, Community community, String about, String avatarPath) {
        this.applicant = applicant;
        this.community = community;
        this.about = about;
        this.avatarPath = avatarPath;
    }

    /**
     * Подаёт заявку на вступление ({@code BR-JOIN-4}).
     *
     * @param applicant человек, подающий заявку; должен быть активным
     *                  аккаунтом, ещё не состоящим в сообществе
     * @param community сообщество, в которое подаётся заявка
     * @param about     «о себе»
     * @param avatarPath путь к фото; может быть {@code null}
     * @return новая заявка в состоянии {@link JoinRequestStatus#PENDING}
     * @throws IllegalStateException если аккаунт уже состоит в сообществе
     *                               ({@code BR-JOIN-2}) или удалён
     */
    public static JoinRequest submit(User applicant, Community community,
                                     String about, String avatarPath) {
        Objects.requireNonNull(applicant, "заявитель обязателен");
        Objects.requireNonNull(community, "сообщество обязательно");
        if (applicant.getStatus() == UserStatus.DELETED) {
            throw new IllegalStateException("удалённый аккаунт не может подать заявку");
        }
        if (applicant.getCommunity() != null) {
            throw new IllegalStateException("аккаунт уже состоит в сообществе");
        }
        return new JoinRequest(applicant, community, about, avatarPath);
    }

    /**
     * Голосует за приём или против ({@code BR-VOTE-8}).
     *
     * <p>Проверки домена: голосовать может только действующий член этого
     * сообщества ({@code BR-VOTE-1}), ровно один раз ({@code BR-VOTE-2}),
     * и только пока заявка рассматривается.
     *
     * @param member член сообщества, голосующий по заявке
     * @param accept {@code true} — «за приём», {@code false} — «против»
     * @throws IllegalStateException если заявка уже решена, голосующий не
     *                               является действующим членом этого
     *                               сообщества или уже голосовал
     */
    public void vote(User member, boolean accept) {
        Objects.requireNonNull(member, "голосующий обязателен");
        if (status != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("заявка уже рассмотрена: " + status);
        }
        if (!member.isActiveMember() || !Objects.equals(member.getCommunity(), community)) {
            throw new IllegalStateException("голосовать может только член сообщества");
        }
        if (hasVotedBy(member)) {
            throw new IllegalStateException("голос по этой заявке уже учтён");
        }
        votes.add(new JoinVote(this, member, accept));
    }

    /**
     * Проверяет, голосовал ли уже этот член по заявке ({@code BR-VOTE-2}).
     *
     * @param member член сообщества
     * @return {@code true}, если голос уже есть
     */
    public boolean hasVotedBy(User member) {
        return votes.stream().anyMatch(vote -> vote.isCastBy(member));
    }

    /**
     * Проверяет, что заявка ещё рассматривается.
     *
     * @return {@code true}, если решения нет
     */
    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    /**
     * Считает голоса «за приём».
     *
     * @return количество голосов «за»
     */
    public long accepts() {
        return votes.stream().filter(JoinVote::isAccept).count();
    }

    /**
     * Считает все поданные голоса — нужны, чтобы понять, что голосовать
     * больше некому.
     *
     * @return количество поданных голосов
     */
    public long voteCount() {
        return votes.size();
    }

    /**
     * Принимает заявку: человек становится членом сообщества
     * ({@code BR-JOIN-5}).
     *
     * <p>Сам факт вступления в сообщество совершает сервис через
     * {@link User#join(Community)}: заявка не должна менять чужой агрегат.
     *
     * @throws IllegalStateException если заявка уже рассмотрена
     */
    public void accept() {
        decide(JoinRequestStatus.ACCEPTED);
    }

    /**
     * Отклоняет заявку.
     *
     * @throws IllegalStateException если заявка уже рассмотрена
     */
    public void reject() {
        decide(JoinRequestStatus.REJECTED);
    }

    private void decide(JoinRequestStatus decision) {
        if (status != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("заявка уже рассмотрена: " + status);
        }
        this.status = decision;
        this.decidedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getApplicant() {
        return applicant;
    }

    public Community getCommunity() {
        return community;
    }

    public String getAbout() {
        return about;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public JoinRequestStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    /**
     * Голоса по заявке только для чтения: менять их можно лишь через
     * {@link #vote(User, boolean)}, чтобы не обойти проверки домена.
     *
     * @return неизменяемое представление голосов
     */
    public Set<JoinVote> getVotes() {
        return Collections.unmodifiableSet(votes);
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
        JoinRequest other = (JoinRequest) o;
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
        return "JoinRequest{id=" + id + ", status=" + status + "}";
    }
}

package ru.peerflow.proposal.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.hibernate.proxy.HibernateProxy;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;

/**
 * Предложение сообщества: то, что решают делать или не делать
 * ({@code BR-OVER-2}, {@code BR-LIFE-1}).
 *
 * <p>Главные правила собраны здесь, а не в сервисе:
 * <ul>
 *   <li>бюджет обязателен и положителен ({@code BR-BUDGET-2});</li>
 *   <li>голосовать может только действующий член сообщества, один голос на
 *       человека ({@code BR-VOTE-1}, {@code BR-VOTE-2});</li>
 *   <li>голос можно поменять, пока голосование не закрыто
 *       ({@code BR-VOTE-13});</li>
 *   <li>стадия меняется только по решению сообщества
 *       ({@code BR-LIFE-4}, {@code BR-LIFE-5}).</li>
 * </ul>
 *
 * <p>Когда голосование закрыто, решает не сущность, а {@link VotingRules} —
 * срок и досрочка зависят от числа членов сообщества и настроек, которых у
 * предложения нет. Сущность только знает, что после закрытия голоса больше не
 * принимаются.
 *
 * <p>Голоса — часть агрегата: они не имеют смысла вне предложения, поэтому
 * живут и умирают вместе с ним ({@code cascade = ALL}, {@code orphanRemoval}).
 */
@Entity
@Table(name = "proposal")
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Кто внёс предложение. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Сообщество, в котором предложение обсуждается ({@code BR-PRIV-1}). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /** Тема — короткая формулировка, по ней предложение узнают в списке. */
    @NotBlank
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Описание: что и зачем делаем. */
    @NotBlank
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /**
     * Бюджет в рублях ({@code BR-BUDGET-1}, {@code BR-BUDGET-2}).
     *
     * <p>В MVP бюджет виртуальный — это учёт, а не касса
     * ({@code BR-BUDGET-6}). Копейки не нужны: целое число не даёт ошибок
     * округления и совпадает с тем, что человек вводит на экране.
     */
    @Positive
    @Column(name = "budget", nullable = false)
    private long budget;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 16)
    private ProposalStage stage = ProposalStage.DISCUSSION;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Когда открылось текущее голосование ({@code BR-VOTE-9}).
     *
     * <p>Отдельно от {@code createdAt}, потому что после утраты мандата
     * сообщество голосует заново ({@code BR-LEADER-7}, {@code BR-LIFE-10}), и
     * новый срок должен отсчитываться от нового голосования. Если бы срок
     * считался от создания, повторное голосование закрывалось бы мгновенно.
     */
    @Column(name = "voting_opened_at")
    private Instant votingOpenedAt;

    /** Момент решения — приняли, отклонили или закрыли; иначе {@code null}. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DecisionVote> votes = new LinkedHashSet<>();

    protected Proposal() {
    }

    private Proposal(User author, Community community, String title,
                     String description, long budget) {
        this.author = author;
        this.community = community;
        this.title = title;
        this.description = description;
        this.budget = budget;
    }

    /**
     * Вносит предложение ({@code BR-OVER-2}, {@code BR-LIFE-1}).
     *
     * <p>Бюджет обязателен: «предложение без бюджета» — не предложение, а
     * идея. Сообщество голосует за то, что можно исполнить и на что есть
     * сумма ({@code BR-BUDGET-2}, {@code BR-BUDGET-3}).
     *
     * @param author      действующий член сообщества
     * @param community   сообщество, где предлагают
     * @param title       тема предложения
     * @param description описание
     * @param budget      бюджет в рублях; должен быть больше нуля
     * @param now         момент внесения — от него отсчитывается срок
     *                    голосования ({@code BR-VOTE-9})
     * @return новое предложение в стадии {@link ProposalStage#DISCUSSION}
     * @throws IllegalArgumentException если бюджет не указан или не положителен
     * @throws IllegalStateException    если автор не действующий член
     *                                  сообщества ({@code BR-VOTE-1})
     */
    public static Proposal create(User author, Community community, String title,
                                  String description, Long budget, Instant now) {
        Objects.requireNonNull(author, "автор обязателен");
        Objects.requireNonNull(community, "сообщество обязательно");
        Objects.requireNonNull(now, "момент внесения обязателен");
        if (budget == null || budget <= 0) {
            throw new IllegalArgumentException("предложение без бюджета внести нельзя");
        }
        if (!author.isActiveMember() || !Objects.equals(author.getCommunity(), community)) {
            throw new IllegalStateException("вносить предложение может только член сообщества");
        }
        Proposal proposal = new Proposal(author, community, title, description, budget);
        proposal.createdAt = now;
        proposal.votingOpenedAt = now;
        return proposal;
    }

    /**
     * Голосует «делаем / не делаем» или меняет свой голос ({@code BR-VOTE-4},
     * {@code BR-VOTE-13}).
     *
     * <p>Повторный голос не создаёт вторую строку: голос человека один, меняется
     * только его направление ({@code BR-VOTE-2}). Это делает быстрые повторные
     * нажатия на клиенте безопасными.
     *
     * @param voter    действующий член сообщества
     * @param inFavor  {@code true} — «делаем», {@code false} — «не делаем»
     * @throws IllegalStateException если голосование закрыто или голосует
     *                               не член этого сообщества
     */
    public void castVote(User voter, boolean inFavor) {
        Objects.requireNonNull(voter, "голосующий обязателен");
        requireVotingOpen();
        if (!voter.isActiveMember() || !Objects.equals(voter.getCommunity(), community)) {
            throw new IllegalStateException("голосовать может только член сообщества");
        }
        DecisionVote existing = voteOf(voter);
        if (existing == null) {
            votes.add(new DecisionVote(this, voter, inFavor));
        } else {
            existing.changeTo(inFavor);
        }
    }

    /**
     * Проверяет, голосовал ли уже этот человек.
     *
     * @param candidate член сообщества
     * @return {@code true}, если голос уже подан
     */
    public boolean hasVotedBy(User candidate) {
        return voteOf(candidate) != null;
    }

    /**
     * Голос этого человека, если он есть.
     *
     * @param candidate член сообщества
     * @return голос или {@code null}
     */
    public DecisionVote voteOf(User candidate) {
        return votes.stream()
                .filter(vote -> vote.isCastBy(candidate))
                .findFirst()
                .orElse(null);
    }

    /**
     * Принимает задачу в работу ({@code BR-LIFE-4}, {@code BR-LIFE-5}).
     *
     * <p>Лидера на этом шаге ещё нет: его выбирают по легитимности, а она
     * появляется в следующей фазе. Поэтому переход означает именно «делаем»,
     * а не «назначен исполнитель».
     *
     * @param now момент решения
     * @throws IllegalStateException если решение уже принято
     */
    public void accept(Instant now) {
        decide(ProposalStage.IN_WORK, now);
    }

    /**
     * Отклоняет предложение ({@code BR-LIFE-4}).
     *
     * @param now момент решения
     * @throws IllegalStateException если решение уже принято
     */
    public void reject(Instant now) {
        decide(ProposalStage.REJECTED, now);
    }

    /**
     * Закрывает выполненную задачу ({@code BR-LIFE-9}).
     *
     * @param now момент закрытия
     * @throws IllegalStateException если задача не в работе
     */
    public void close(Instant now) {
        if (stage != ProposalStage.IN_WORK) {
            throw new IllegalStateException("закрыть можно только задачу в работе: " + stage);
        }
        this.stage = ProposalStage.CLOSED;
        this.decidedAt = now;
    }

    /**
     * Открывает голосование заново после утраты мандата
     * ({@code BR-LEADER-7}, {@code BR-LIFE-10}).
     *
     * <p>Голоса прошлого круга не переносятся: вопрос теперь другой — «продолжать
     * ли задачу и с кем». Если бы прежние «за» остались, новое голосование
     * закрылось бы мгновенно тем же составом, и сообщество не смогло бы
     * передумать.
     *
     * @param now момент открытия нового голосования
     * @throws IllegalStateException если задача не в работе
     */
    public void reopenForRevote(Instant now) {
        if (stage != ProposalStage.IN_WORK) {
            throw new IllegalStateException(
                    "переголосовать можно только задачу в работе: " + stage);
        }
        this.stage = ProposalStage.DISCUSSION;
        this.decidedAt = null;
        this.votingOpenedAt = now;
        this.votes.clear();
    }

    /**
     * Проверяет, что голосование ещё не закрыто самим предложением.
     *
     * <p>Срок и досрочное закрытие проверяет {@link VotingRules} в сервисе:
     * предложение об этом ничего не знает. Здесь — только переходы стадии,
     * которые уже состоялись.
     *
     * @throws IllegalStateException если стадия уже не
     *                               {@link ProposalStage#DISCUSSION}
     */
    public void requireVotingOpen() {
        if (stage != ProposalStage.DISCUSSION) {
            throw new IllegalStateException("голосование по предложению закрыто: " + stage);
        }
    }

    /**
     * Считает голоса по предложению ({@code BR-VOTE-4}).
     *
     * @return сводка голосов «за» и «против»
     */
    public DecisionTally tally() {
        long votesFor = votes.stream().filter(DecisionVote::isInFavor).count();
        return new DecisionTally(votesFor, votes.size() - votesFor);
    }

    /**
     * Число уникальных участников обсуждения ({@code BR-RANK-2}).
     *
     * <p>Без авторов сообщений: их знает только дискуссия, и сервис передаёт
     * их в {@link #participants(java.util.Collection)}. Объём сообщений не
     * считается — иначе шум поднимал бы предложение ({@code BR-RANK-3}).
     *
     * @return количество уникальных людей, причастных к предложению
     */
    public long participants() {
        return participants(Set.of());
    }

    /**
     * Число уникальных участников с учётом авторов сообщений
     * ({@code BR-RANK-2}).
     *
     * <p>Автор сообщения — участник обсуждения, но количество сообщений на
     * метрику не влияет: человек учитывается один раз, сколько бы он ни
     * написал ({@code BR-RANK-3}).
     *
     * @param extraParticipants авторы сообщений, известные вне агрегата
     * @return количество уникальных людей, причастных к предложению
     */
    public long participants(java.util.Collection<UUID> extraParticipants) {
        Set<UUID> people = votes.stream()
                .map(vote -> vote.getVoter().getId())
                .collect(Collectors.toCollection(HashSet::new));
        // Автор тоже участник, даже если сам не голосовал.
        people.add(author.getId());
        people.addAll(extraParticipants);
        return people.size();
    }

    /**
     * Метрика популярности: участники плюс перевес голосов
     * ({@code BR-RANK-2}).
     *
     * <p>Количество сообщений сюда не входит осознанно: иначе громкое и
     * бессмысленное вылезало бы наверх за счёт объёма ({@code BR-RANK-3}).
     *
     * @return значение метрики
     */
    public long popularity() {
        return popularity(Set.of());
    }

    /**
     * Метрика популярности с учётом авторов сообщений ({@code BR-RANK-2}).
     *
     * @param extraParticipants авторы сообщений, известные вне агрегата
     * @return значение метрики
     */
    public long popularity(java.util.Collection<UUID> extraParticipants) {
        DecisionTally tally = tally();
        return participants(extraParticipants) + (tally.votesFor() - tally.votesAgainst());
    }

    private void decide(ProposalStage decision, Instant now) {
        requireVotingOpen();
        this.stage = decision;
        this.decidedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public Community getCommunity() {
        return community;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public long getBudget() {
        return budget;
    }

    public ProposalStage getStage() {
        return stage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Когда открылось текущее голосование ({@code BR-VOTE-9}).
     *
     * @return момент открытия; у старых строк совпадает с датой внесения
     */
    public Instant getVotingOpenedAt() {
        return votingOpenedAt == null ? createdAt : votingOpenedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    /**
     * Голоса только для чтения: менять их можно лишь через
     * {@link #castVote(User, boolean)}, чтобы не обойти проверки домена.
     *
     * @return неизменяемое представление голосов
     */
    public Set<DecisionVote> getVotes() {
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
        Proposal other = (Proposal) o;
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
        return "Proposal{id=" + id + ", stage=" + stage + ", budget=" + budget + "}";
    }
}

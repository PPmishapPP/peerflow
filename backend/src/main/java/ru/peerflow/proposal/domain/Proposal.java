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
     * Потолок бюджета в рублях ({@code BR-BUDGET-1}, {@code BR-BUDGET-2}).
     *
     * <p>Это именно потолок: автор задаёт верхнюю границу того, сколько задача
     * может стоить, а конкретную цену называет тот, кто берётся её выполнить
     * ({@link Candidate}). Сообщество утверждает потолок, голосуя «делаем»
     * ({@code BR-BUDGET-3}).
     *
     * <p>В MVP бюджет виртуальный — это учёт, а не касса
     * ({@code BR-BUDGET-6}). Копейки не нужны: целое число не даёт ошибок
     * округления и совпадает с тем, что человек вводит на экране.
     */
    @Positive
    @Column(name = "budget", nullable = false, updatable = false)
    private long budget;

    /**
     * Бюджет текущей попытки выполнить задачу ({@code BR-LIFE-6},
     * {@code BR-LIFE-10}).
     *
     * <p>Это цена победившего кандидата, а не потолок: сообщество утверждает
     * границу, лидер берётся за конкретную сумму внутри неё. Пока лидера нет,
     * значения нет — задача может быть принята «делаем» и ждать исполнителя.
     * При утрате мандата сбрасывается: новый лидер приносит новую цену
     * ({@code BR-BUDGET-7}).
     */
    @Column(name = "task_budget")
    private Long taskBudget;

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

    /**
     * Когда лидер запустил процедуру закрытия ({@code BR-LIFE-8}).
     *
     * <p>Отдельно от {@link #decidedAt}: итог и начало процедуры — разные
     * события, и срок голосования «выполнено / нет» должен отсчитываться от
     * запуска, а не от решения.
     */
    @Column(name = "closure_opened_at")
    private Instant closureOpenedAt;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DecisionVote> votes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ClosureVote> closureVotes = new LinkedHashSet<>();

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
     * <p>Потолок бюджета обязателен: «предложение без потолка» — не
     * предложение, а идея. Сообщество голосует за то, что можно исполнить и на
     * что есть верхняя граница ({@code BR-BUDGET-2}, {@code BR-BUDGET-3}).
     * Конкретную цену называет кандидат в лидеры ({@link Candidate}).
     *
     * @param author      действующий член сообщества
     * @param community   сообщество, где предлагают
     * @param title       тема предложения
     * @param description описание
     * @param budget      потолок бюджета в рублях; должен быть больше нуля
     * @param now         момент внесения — от него отсчитывается срок
     *                    голосования ({@code BR-VOTE-9})
     * @return новое предложение в стадии {@link ProposalStage#DISCUSSION}
     * @throws IllegalArgumentException если потолок не указан или не положителен
     * @throws IllegalStateException    если автор не действующий член
     *                                  сообщества ({@code BR-VOTE-1})
     */
    public static Proposal create(User author, Community community, String title,
                                  String description, Long budget, Instant now) {
        Objects.requireNonNull(author, "автор обязателен");
        Objects.requireNonNull(community, "сообщество обязательно");
        Objects.requireNonNull(now, "момент внесения обязателен");
        if (budget == null || budget <= 0) {
            throw new IllegalArgumentException("предложение без потолка бюджета внести нельзя");
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
     * Открывает голосование «выполнено / не выполнено» ({@code BR-LIFE-8}).
     *
     * <p>Проверку «это лидер запустил» делает сервис: у предложения нет
     * мандата, и вопрос «кто вправе» — не его. Здесь только правило «процедура
     * уже идёт или задача не в работе».
     *
     * @param now момент запуска — от него отсчитывается срок
     * @throws IllegalStateException если задача не в работе или процедура уже
     *                               запущена
     */
    public void openClosure(Instant now) {
        Objects.requireNonNull(now, "момент запуска обязателен");
        if (stage != ProposalStage.IN_WORK) {
            throw new IllegalStateException(
                    "закрыть можно только задачу в работе: " + stage);
        }
        if (closureOpenedAt != null) {
            throw new IllegalStateException("процедура закрытия уже запущена");
        }
        // Голоса прошлой попытки не переносятся: сообщество отвечает на новый
        // вопрос — «выполнено ли сейчас», а не подтверждает старый ответ.
        closureVotes.clear();
        this.closureOpenedAt = now;
    }

    /**
     * Голосует «выполнено / не выполнено» или меняет ответ
     * ({@code BR-VOTE-5}, {@code BR-OVER-9}).
     *
     * @param voter     действующий член сообщества
     * @param completed {@code true} — «выполнено», {@code false} — «не выполнено»
     * @throws IllegalStateException если процедура закрытия не идёт
     */
    public void castClosureVote(User voter, boolean completed) {
        Objects.requireNonNull(voter, "голосующий обязателен");
        requireClosureOpen();
        if (!voter.isActiveMember() || !Objects.equals(voter.getCommunity(), community)) {
            throw new IllegalStateException("голосовать может только член сообщества");
        }
        ClosureVote existing = closureVoteOf(voter);
        if (existing == null) {
            closureVotes.add(new ClosureVote(this, voter, completed));
        } else {
            existing.changeTo(completed);
        }
    }

    /**
     * Голос этого человека по выполнению, если он есть.
     *
     * @param candidate член сообщества
     * @return голос или {@code null}
     */
    public ClosureVote closureVoteOf(User candidate) {
        return closureVotes.stream()
                .filter(vote -> vote.isCastBy(candidate))
                .findFirst()
                .orElse(null);
    }

    /**
     * Сводка голосов «выполнено / не выполнено».
     *
     * <p>Переиспользует {@link DecisionTally}: для итога важно только
     * соотношение «за» и «против», и отдельная пара чисел повторила бы ту же
     * арифметику. {@code votesFor} здесь означает «выполнено».
     *
     * @return сводка голосов по выполнению
     */
    public DecisionTally closureTally() {
        long completed = closureVotes.stream().filter(ClosureVote::isCompleted).count();
        return new DecisionTally(completed, closureVotes.size() - completed);
    }

    /**
     * Идёт ли сейчас голосование «выполнено / не выполнено».
     *
     * @return {@code true}, если лидер запустил процедуру закрытия
     */
    public boolean isClosureOpen() {
        return stage == ProposalStage.IN_WORK && closureOpenedAt != null;
    }

    /**
     * Отменяет процедуру закрытия: сообщество решило, что задача не выполнена
     * ({@code BR-OVER-9}).
     *
     * <p>Задача остаётся в работе: «не выполнено» — не отказ от задачи, а
     * требование доработать. Голоса очищаются, чтобы следующая попытка
     * начиналась с чистого листа, а не закрывалась старыми ответами.
     *
     * @param now момент решения
     */
    public void cancelClosure(Instant now) {
        requireClosureOpen();
        Objects.requireNonNull(now, "момент решения обязателен");
        this.closureOpenedAt = null;
        this.closureVotes.clear();
    }

    /**
     * Проверяет, что голосование «выполнено / не выполнено» идёт.
     *
     * @throws IllegalStateException если процедура не запущена или задача уже
     *                               не в работе
     */
    public void requireClosureOpen() {
        if (!isClosureOpen()) {
            throw new IllegalStateException(
                    "голосование по выполнению не идёт: " + stage);
        }
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
     * <p>Бюджет задачи сбрасывается вместе с мандатом
     * ({@code BR-BUDGET-7}): он принадлежал прежнему лидеру, а новый принесёт
     * свою цену. Потолок остаётся: его утвердило сообщество, и оно его не
     * отзывало.
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
        // Процедура закрытия относится к прежнему лидеру: после переголо-
        // сования её запускает уже новый лидер (BR-LIFE-10).
        this.closureOpenedAt = null;
        this.closureVotes.clear();
        this.taskBudget = null;
    }

    /**
     * Назначает бюджет задачи по цене победившего кандидата
     * ({@code BR-LIFE-6}, {@code BR-BUDGET-8}).
     *
     * <p>Вызывается только при выдаче мандата: бюджет задачи — это цена
     * действующего лидера, и другого источника у него нет. Поэтому сумма
     * проверяется сразу — мандат без бюджета превратил бы отчёт и траты в
     * задачу без счёта.
     *
     * @param price цена победившего кандидата
     * @throws IllegalArgumentException если цена не положительна или больше
     *                                  потолка
     */
    public void assignTaskBudget(long price) {
        if (price <= 0) {
            throw new IllegalArgumentException("бюджет задачи должен быть больше нуля");
        }
        if (price > budget) {
            throw new IllegalArgumentException(
                    "цена лидера больше потолка бюджета: " + price + " > " + budget);
        }
        this.taskBudget = price;
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

    /**
     * Бюджет текущей попытки: цена победившего кандидата
     * ({@code BR-LIFE-6}).
     *
     * @return сумма; {@code null}, пока лидер не выбран
     */
    public Long getTaskBudget() {
        return taskBudget;
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
     * Когда лидер запустил процедуру закрытия ({@code BR-LIFE-8}).
     *
     * @return момент запуска; {@code null} — процедура не запускалась
     */
    public Instant getClosureOpenedAt() {
        return closureOpenedAt;
    }

    /**
     * Голоса «выполнено / не выполнено» только для чтения.
     *
     * @return неизменяемое представление голосов
     */
    public Set<ClosureVote> getClosureVotes() {
        return Collections.unmodifiableSet(closureVotes);
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
        return "Proposal{id=" + id + ", stage=" + stage + ", budget=" + budget
                + ", taskBudget=" + taskBudget + "}";
    }
}

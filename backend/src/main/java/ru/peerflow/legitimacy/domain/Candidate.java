package ru.peerflow.legitimacy.domain;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.hibernate.proxy.HibernateProxy;

import ru.peerflow.community.domain.User;
import ru.peerflow.proposal.domain.Proposal;

/**
 * Кандидатура члена на роль лидера по задаче ({@code BR-LEADER-1},
 * {@code BR-LEADER-2}).
 *
 * <p>Кандидатом становятся сами, пока идёт обсуждение: отдельного «согласия
 * баллотироваться» нет, потому что лидерство здесь — не должность, а
 * готовность выполнить конкретную задачу ({@code BR-LEGIT-1}).
 *
 * <p>Кандидатура — часть задачи: она не имеет смысла вне предложения и
 * удаляется вместе с ним. По разным задачам человек может быть кандидатом
 * независимо ({@code BR-LEADER-8}).
 */
@Entity
@Table(name = "candidate")
public class Candidate {

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
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private User member;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * За сколько человек берётся выполнить задачу ({@code BR-BUDGET-8}).
     *
     * <p>Цена — это обязательство, а не пожелание: лидер, победив, работает
     * именно за неё, и она становится бюджетом задачи ({@code BR-LIFE-6}).
     * Поэтому она обязательна и не может превышать потолок, утверждённый
     * сообществом ({@code BR-BUDGET-8}). Потолок здесь не хранится — он у
     * задачи, и сравнивать с ним должна та сторона, у которой он есть.
     */
    @Positive
    @Column(name = "price", nullable = false)
    private long price;

    protected Candidate() {
    }

    private Candidate(Proposal proposal, User member, long price, Instant now) {
        this.proposal = proposal;
        this.member = member;
        this.price = price;
        this.createdAt = now;
    }

    /**
     * Выдвигает члена кандидатом по задаче ({@code BR-LEADER-1}).
     *
     * <p>Выдвигаться можно только по задаче своего сообщества и только пока
     * идёт обсуждение: после решения выбирать лидера не из кого
     * ({@code BR-LEADER-3}).
     *
     * @param proposal задача
     * @param member   действующий член сообщества задачи
     * @param price    цена: за сколько человек выполнит задачу; больше нуля
     * @param now      момент выдвижения
     * @return кандидатура
     * @throws IllegalArgumentException если цена не положительна
     * @throws IllegalStateException    если голосование уже закрыто или человек
     *                                  не член сообщества задачи
     */
    public static Candidate nominate(Proposal proposal, User member, long price, Instant now) {
        Objects.requireNonNull(proposal, "задача обязательна");
        Objects.requireNonNull(member, "кандидат обязателен");
        Objects.requireNonNull(now, "момент выдвижения обязателен");
        if (!member.isActiveMember()
                || !Objects.equals(member.getCommunity(), proposal.getCommunity())) {
            throw new IllegalStateException(
                    "выдвигаться может только член сообщества задачи");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("кандидат должен назвать цену больше нуля");
        }
        // Потолок известен с момента внесения: его назвал автор, и сообщество
        // утвердит его тем же голосованием, которое выбирает лидера. Отклонять
        // непомерную цену сразу, а не после решения, честнее: иначе кандидат
        // узнал бы об отказе, когда голосование уже прошло.
        if (price > proposal.getBudget()) {
            throw new IllegalArgumentException(
                    "цена кандидата больше потолка бюджета: " + price
                            + " > " + proposal.getBudget());
        }
        proposal.requireVotingOpen();
        return new Candidate(proposal, member, price, now);
    }

    /**
     * Проверяет, что цена укладывается в потолок, утверждённый сообществом
     * ({@code BR-BUDGET-8}).
     *
     * <p>Отдельным методом, а не внутри {@link #nominate}: потолок живёт у
     * задачи, и сравнивать с ним должна та сторона, у которой он есть.
     *
     * @param ceilingUpperBound потолок задачи
     * @throws IllegalArgumentException если цена больше потолка
     */
    public void requirePriceWithin(long ceilingUpperBound) {
        if (price > ceilingUpperBound) {
            throw new IllegalArgumentException(
                    "цена кандидата больше потолка бюджета: " + price
                            + " > " + ceilingUpperBound);
        }
    }

    /**
     * Меняет цену, за которую кандидат берётся выполнить задачу
     * ({@code BR-BUDGET-8}).
     *
     * <p>Передумать можно, пока идёт обсуждение: цена — это предложение
     * сообществу, а не обязательство, данное однажды. После решения менять
     * нечего: лидер уже работает за ту сумму, которую назвал.
     *
     * @param newPrice новая цена; больше нуля
     * @throws IllegalArgumentException если цена не положительна
     * @throws IllegalStateException    если обсуждение уже закончилось
     */
    public void reprice(long newPrice) {
        if (newPrice <= 0) {
            throw new IllegalArgumentException("кандидат должен назвать цену больше нуля");
        }
        if (newPrice > proposal.getBudget()) {
            throw new IllegalArgumentException(
                    "цена кандидата больше потолка бюджета: " + newPrice
                            + " > " + proposal.getBudget());
        }
        proposal.requireVotingOpen();
        this.price = newPrice;
    }

    /**
     * Тот же ли человек выдвинут.
     *
     * @param candidateId проверяемый аккаунт
     * @return {@code true}, если кандидатура принадлежит ему
     */
    public boolean isOf(UUID candidateId) {
        return Objects.equals(member.getId(), candidateId);
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Цена, за которую кандидат выполнит задачу ({@code BR-BUDGET-8}).
     *
     * @return сумма в рублях; больше нуля
     */
    public long getPrice() {
        return price;
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
        Candidate other = (Candidate) o;
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
        return "Candidate{id=" + id + ", price=" + price + ", createdAt=" + createdAt + "}";
    }
}

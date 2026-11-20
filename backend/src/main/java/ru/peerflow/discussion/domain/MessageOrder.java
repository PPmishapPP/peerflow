package ru.peerflow.discussion.domain;

import java.util.Comparator;

/**
 * Порядок сообщений в ветке ({@code BR-DISC-4}, {@code BR-DISC-5}).
 *
 * <p>Порядок — правило сообщества, а не оформление экрана, поэтому живёт в
 * домене и применяется одинаково к корням и к ответам любого уровня
 * ({@code BR-DISC-6}). Клиент порядок не пересчитывает.
 *
 * <p>Почему популярность — компаратор, а не число «плюсы минус минусы»:
 * сообщение с тремя плюсами и двумя минусами по правилу выше, чем с двумя
 * плюсами и ни одного минуса, хотя разность у них меньше. Сначала сравниваются
 * плюсы, и только при их равенстве в игру вступают минусы.
 */
public final class MessageOrder {

    /**
     * «Популярные»: больше плюсов — выше, при равенстве плюсов меньше минусов —
     * выше ({@code BR-DISC-4}).
     *
     * <p>При полном равенстве порядок добивается по времени создания: иначе
     * два сообщения с одинаковыми метками «прыгали» бы между запросами.
     */
    public static final Comparator<Message> POPULAR = Comparator
            .comparingLong(Message::plusCount).reversed()
            .thenComparing(Comparator.comparingLong(Message::minusCount))
            .thenComparing(Message::getCreatedAt);

    /**
     * «Новые»: свежее сверху, чтобы новое не тонуло под старым
     * ({@code BR-DISC-5}).
     */
    public static final Comparator<Message> NEWEST = Comparator
            .comparing(Message::getCreatedAt)
            .reversed();

    private MessageOrder() {
    }
}

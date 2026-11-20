package ru.peerflow.discussion.service;

import java.util.Locale;

/**
 * Варианты сортировки сообщений дискуссии ({@code BR-DISC-4},
 * {@code BR-DISC-5}).
 *
 * <p>Сортировку выбирает человек на экране, но сам порядок считает сервер:
 * правило «плюсы поднимают, минусы опускают» одно на всех, и дублировать его
 * на клиенте нельзя — иначе ветки выглядели бы по-разному на разных
 * платформах.
 */
public enum MessageSort {

    /** По популярности: больше плюсов — выше ({@code BR-DISC-4}). */
    POPULAR,

    /** По времени создания: свежее сверху ({@code BR-DISC-5}). */
    NEW;

    /**
     * Разбирает значение из запроса.
     *
     * <p>Неизвестное значение — ошибка клиента ({@code 400}), а не молчаливый
     * переход к популярности: иначе человек выбрал бы один порядок, а увидел
     * другой.
     *
     * @param value значение параметра {@code sort}; может быть {@code null}
     * @return вариант сортировки; по умолчанию популярность
     * @throws IllegalArgumentException если значение неизвестно
     */
    public static MessageSort parse(String value) {
        if (value == null || value.isBlank()) {
            return POPULAR;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "неизвестная сортировка сообщений: " + value
                            + ", допустимы POPULAR, NEW");
        }
    }
}

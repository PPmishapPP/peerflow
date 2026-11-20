package ru.peerflow.proposal.service;

/**
 * Варианты сортировки списка предложений ({@code BR-RANK-4}).
 *
 * <p>Сортировку выбирает человек на экране, но сам порядок считает сервер:
 * правило популярности одно на всех ({@code BR-RANK-2}), и дублировать его
 * на клиенте нельзя.
 */
public enum ProposalSort {

    /** По популярности: участники плюс перевес голосов ({@code BR-RANK-2}). */
    POPULAR,

    /** По размеру бюджета, от большего к меньшему ({@code BR-RANK-4}). */
    BUDGET,

    /** По новизне: свежее сверху ({@code BR-RANK-4}), свежее не тонет. */
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
    public static ProposalSort parse(String value) {
        if (value == null || value.isBlank()) {
            return POPULAR;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "неизвестная сортировка: " + value + ", допустимы POPULAR, BUDGET, NEW");
        }
    }
}

package ru.peerflow.proposal.domain;

/**
 * Стадия предложения ({@code BR-LIFE-1}…{@code BR-LIFE-10}).
 *
 * <p>Жизненный цикл: {@link #DISCUSSION} → {@link #IN_WORK} или
 * {@link #REJECTED}, затем {@link #IN_WORK} → {@link #CLOSED}.
 * Отдельный {@code REJECTED} нужен, чтобы «не делаем» было видно в истории,
 * а не выглядело как «предложение забыли».
 */
public enum ProposalStage {

    /** Идёт обсуждение и голосование «делаем / не делаем» ({@code BR-LIFE-2}). */
    DISCUSSION("На обсуждении"),

    /** Решение принято, лидер и бюджет утверждены ({@code BR-LIFE-6}). */
    IN_WORK("В работе"),

    /** Сообщество решило, что задача выполнена ({@code BR-LIFE-9}). */
    CLOSED("Закрыто"),

    /** Сообщество решило «не делаем» ({@code BR-LIFE-4}). */
    REJECTED("Не делаем");

    private final String label;

    ProposalStage(String label) {
        this.label = label;
    }

    /**
     * Название стадии для человека — как в бизнес-описании.
     *
     * @return подпись стадии
     */
    public String label() {
        return label;
    }
}

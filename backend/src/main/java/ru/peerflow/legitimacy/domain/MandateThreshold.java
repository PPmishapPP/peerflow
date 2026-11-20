package ru.peerflow.legitimacy.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Порог удержания легитимности ({@code BR-LEADER-5}, {@code BR-LIMIT-1}).
 *
 * <p>Отдельный тип, а не два числа в мандате: правило одно и то же при выдаче
 * мандата, при проверке при чтении и при показе шкалы клиенту. Если бы каждая
 * из этих точек считала долю сама, «ровно 70%» на одном экране держало бы
 * мандат, а на другом — уже нет.
 *
 * <p>Считаем в {@link BigDecimal}: {@code 0.7 * 10} на {@code double} даёт
 * {@code 6.999999999999999}, и лидер с семью единицами поддержки из десяти
 * терял бы мандат при потере ровно 30%, хотя правило говорит «больше 30%».
 */
public record MandateThreshold(long initialSupport, double retainRatio) {

    /**
     * Проверяет, что порог имеет смысл.
     *
     * @throws IllegalArgumentException если изначальная поддержка отрицательна
     *                                  или доля вне диапазона {@code 0..1}
     */
    public MandateThreshold {
        if (initialSupport < 0) {
            throw new IllegalArgumentException("поддержка не может быть отрицательной");
        }
        if (retainRatio <= 0 || retainRatio > 1) {
            throw new IllegalArgumentException("доля удержания — от 0 до 1");
        }
    }

    /**
     * Держится ли мандат при текущей поддержке.
     *
     * <p>Сравнение «не меньше», а не «больше»: потеря ровно 30% мандат
     * сохраняет — правило запрещает терять <em>больше</em> 30%.
     *
     * @param currentSupport сколько единиц поддержки у лидера сейчас
     * @return {@code true}, если мандат ещё действителен
     */
    public boolean isRetainedBy(long currentSupport) {
        return BigDecimal.valueOf(currentSupport)
                .compareTo(exactRequired())
                >= 0;
    }

    /**
     * Сколько поддержки нужно сохранить.
     *
     * @return минимальное число единиц; округление вверх, чтобы «нужно 6.3»
     *         не превращалось в «хватит 6»
     */
    public long requiredSupport() {
        return exactRequired().setScale(0, RoundingMode.CEILING).longValueExact();
    }

    private BigDecimal exactRequired() {
        return BigDecimal.valueOf(retainRatio)
                .multiply(BigDecimal.valueOf(initialSupport));
    }
}

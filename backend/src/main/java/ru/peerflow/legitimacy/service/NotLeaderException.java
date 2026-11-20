package ru.peerflow.legitimacy.service;

import java.util.UUID;

/**
 * Действие доступно только лидеру задачи ({@code BR-LIFE-6}, {@code BR-LIFE-7},
 * {@code BR-BUDGET-4}, {@code BR-LIFE-8}).
 *
 * <p>Это не «нет доступа к данным» ({@code 404}): задача видна человеку, он
 * член сообщества, но исполнительного мандата у него нет. Поэтому {@code 403}
 * — как и с членством.
 *
 * <p>Живёт в домене легитимности, а не в отчётах: право распоряжаться задачей
 * даёт мандат ({@code BR-LEGIT-4}), и отчитываться, тратить бюджет и запускать
 * закрытие — одно и то же право.
 */
public class NotLeaderException extends RuntimeException {

    /**
     * Создаёт исключение для задачи, которой распоряжается другой человек.
     *
     * @param proposalId задача
     */
    public NotLeaderException(UUID proposalId) {
        super("действие доступно только лидеру задачи: " + proposalId);
    }
}

package ru.peerflow.legitimacy.web.dto;

import java.util.UUID;

/**
 * Кому человек передал легитимность по одной задаче
 * ({@code BR-LEGIT-2}, {@code BR-LEGIT-3}, {@code BR-LEGIT-5}).
 *
 * <p>Одна строка — одна задача, а не одна передача: в профиле важно видеть и
 * задачи, где доверие осталось у себя. Иначе «ничего не передано» выглядело бы
 * как «задач нет».
 *
 * @param proposalId    задача
 * @param proposalTitle название задачи для человека
 * @param candidateId   кому передана легитимность; {@code null} — доверие у себя
 *                      ({@code BR-LEGIT-5})
 * @param candidateName имя получателя; {@code null}, если не передана
 */
public record DelegationResponse(
        UUID proposalId,
        String proposalTitle,
        UUID candidateId,
        String candidateName
) {
}

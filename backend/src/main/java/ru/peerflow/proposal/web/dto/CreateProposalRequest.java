package ru.peerflow.proposal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Новое предложение ({@code BR-LIFE-1}).
 *
 * <p>{@code Long}, а не {@code long}: отсутствие бюджета в запросе — это ошибка
 * клиента, а не молчаливый ноль. Бюджет обязателен по правилу сообщества
 * ({@code BR-BUDGET-2}).
 *
 * @param title       тема
 * @param description описание: что и зачем делаем
 * @param budget      бюджет в рублях; больше нуля
 */
public record CreateProposalRequest(

        @NotBlank(message = "тема обязательна")
        @Size(max = 200, message = "тема не длиннее 200 символов")
        String title,

        @NotBlank(message = "описание обязательно")
        @Size(max = 10000, message = "описание не длиннее 10000 символов")
        String description,

        @NotNull(message = "бюджет обязателен")
        @Positive(message = "бюджет должен быть больше нуля")
        Long budget
) {
}

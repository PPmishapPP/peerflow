package ru.peerflow.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Данные для регистрации ({@code BR-JOIN-1}, {@code BR-JOIN-4}).
 *
 * <p>Аватар сюда не входит: он приходит отдельным файлом (multipart) — так
 * в JSON остаются только текстовые поля.
 *
 * @param login       логин для входа; латиница, цифры, точка, дефис и подчёркивание
 * @param password    пароль в открытом виде; в БД попадёт только хэш
 * @param displayName имя, которое видят другие члены сообщества
 * @param about       рассказ о себе ({@code BR-JOIN-4})
 */
public record RegisterRequest(

        @NotBlank(message = "логин обязателен")
        @Size(min = 3, max = 64, message = "логин должен быть от 3 до 64 символов")
        @Pattern(regexp = "[A-Za-z0-9._-]+",
                message = "логин может содержать только латиницу, цифры, точку, дефис и подчёркивание")
        String login,

        @NotBlank(message = "пароль обязателен")
        @Size(min = 8, max = 72, message = "пароль должен быть от 8 до 72 символов")
        String password,

        @NotBlank(message = "имя обязательно")
        @Size(max = 100, message = "имя не длиннее 100 символов")
        String displayName,

        @Size(max = 2000, message = "«о себе» не длиннее 2000 символов")
        String about
) {
}

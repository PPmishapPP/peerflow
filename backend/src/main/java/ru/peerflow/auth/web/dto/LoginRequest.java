package ru.peerflow.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Данные для входа ({@code BR-JOIN-1}).
 *
 * @param login    логин
 * @param password пароль в открытом виде, сверяется с хэшем в базе
 */
public record LoginRequest(

        @NotBlank(message = "логин обязателен")
        @Size(max = 64, message = "логин не длиннее 64 символов")
        String login,

        @NotBlank(message = "пароль обязателен")
        @Size(max = 72, message = "пароль не длиннее 72 символов")
        String password
) {
}

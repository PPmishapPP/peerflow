package ru.peerflow.community.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Данные заявки на вступление ({@code BR-JOIN-4}).
 *
 * <p>Фото приходит отдельным файлом (multipart), как и при регистрации:
 * в JSON остаются только текстовые поля.
 *
 * @param about рассказ о себе — по нему сообщество решает, принимать ли
 */
public record SubmitJoinRequestRequest(

        @NotBlank(message = "расскажите о себе — без этого сообщество не сможет решить")
        @Size(max = 2000, message = "«о себе» не длиннее 2000 символов")
        String about
) {
}

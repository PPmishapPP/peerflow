package ru.peerflow.auth.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ru.peerflow.auth.service.AuthService;
import ru.peerflow.auth.web.dto.AuthResponse;
import ru.peerflow.auth.web.dto.LoginRequest;
import ru.peerflow.auth.web.dto.ProfileResponse;
import ru.peerflow.auth.web.dto.RegisterRequest;

/**
 * Регистрация и вход ({@code BR-JOIN-1}).
 *
 * <p>Оба метода открыты без токена — иначе войти было бы некому. Всё
 * остальное на сервере закрыто ({@code BR-PRIV-1}).
 *
 * <p>Регистрация принимает {@code multipart/form-data}: JSON-часть
 * {@code request} и необязательный файл {@code avatar}. Так текст и фото
 * приходят одним запросом, и аккаунт не остаётся без аватара из-за второго
 * неудачного вызова.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Регистрирует аккаунт и выдаёт токен.
     *
     * <p>Первый зарегистрировавшийся становится членом сообщества
     * ({@code BR-JOIN-3}); ответ об этом сообщает полем {@code member}.
     *
     * @param request данные регистрации
     * @param avatar  фотография; может не прилагаться
     * @return {@code 201 Created} с токеном
     */
    @PostMapping(
            path = "/register",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(@Valid @RequestPart("request") RegisterRequest request,
                                                 @RequestPart(value = "avatar", required = false)
                                                 MultipartFile avatar) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, avatar));
    }

    /**
     * Проверяет логин и пароль и выдаёт токен.
     *
     * @param request логин и пароль
     * @return {@code 200 OK} с токеном
     */
    @PostMapping(
            path = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Отдаёт профиль владельца токена.
     *
     * <p>Закрыт ({@code BR-PRIV-1}): без действующего токена ответ — {@code 401}.
     * Клиент вызывает его при запуске, чтобы узнать, принят ли он в сообщество
     * ({@code BR-JOIN-4}).
     *
     * @param authentication текущая аутентификация, подставленная фильтром JWT
     * @return профиль аккаунта
     */
    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProfileResponse me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return authService.currentUser(userId);
    }
}

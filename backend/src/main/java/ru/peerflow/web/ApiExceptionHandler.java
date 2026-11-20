package ru.peerflow.web;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import ru.peerflow.auth.service.AccountNotFoundException;
import ru.peerflow.auth.service.InvalidCredentialsException;
import ru.peerflow.auth.service.LoginAlreadyTakenException;
import ru.peerflow.community.service.JoinRequestAlreadySubmittedException;
import ru.peerflow.community.service.JoinRequestNotFoundException;
import ru.peerflow.community.service.MembershipRequiredException;

/**
 * Перевод исключений предметной области в HTTP-ответы.
 *
 * <p>Клиенту нужен код состояния и понятное сообщение, а не стектрейс.
 * Используется {@link ProblemDetail} из Spring — это стандартный формат
 * {@code application/problem+json}, его одинаково удобно разбирать на Android.
 *
 * <p>Что важно для безопасности: неверный логин и неверный пароль дают один и
 * тот же ответ ({@code BR-JOIN-1}) — иначе сервер подсказывал бы, какие логины
 * существуют.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Логин занят: повторная регистрация невозможна, человеку нужно выбрать
     * другой логин.
     */
    @ExceptionHandler(LoginAlreadyTakenException.class)
    public ProblemDetail onLoginTaken(LoginAlreadyTakenException e) {
        return problem(HttpStatus.CONFLICT, "Логин уже занят", e.getMessage());
    }

    /**
     * Неверная пара логин/пароль. Ответ намеренно общий.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail onInvalidCredentials(InvalidCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Вход не выполнен", e.getMessage());
    }

    /**
     * Токен валиден, но аккаунта в базе нет (например, помечен удалённым,
     * {@code BR-PRIV-2}). Клиент должен выйти из аккаунта и войти снова.
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail onAccountNotFound(AccountNotFoundException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Аккаунт недоступен",
                "войдите в аккаунт повторно");
    }

    /**
     * Вложение не принято: пустой файл, неподдерживаемый тип или слишком
     * большой размер.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail onUploadTooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.BAD_REQUEST, "Файл слишком большой",
                "аватар не должен превышать допустимый размер");
    }

    /**
     * Не удалось записать вложение на диск. Это наша проблема, не человека —
     * поэтому {@code 500}, а не {@code 400}.
     */
    @ExceptionHandler(IOException.class)
    public ProblemDetail onStorageFailure(IOException e) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить файл",
                "попробуйте позже");
    }

    /**
     * Некорректные входные данные, которые не поймала валидация DTO.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректные данные", e.getMessage());
    }

    /**
     * Действие доступно только действующему члену сообщества
     * ({@code BR-VOTE-1}, {@code BR-PRIV-1}).
     */
    @ExceptionHandler(MembershipRequiredException.class)
    public ProblemDetail onMembershipRequired(MembershipRequiredException e) {
        return problem(HttpStatus.FORBIDDEN, "Нужно членство в сообществе", e.getMessage());
    }

    /**
     * Заявка на вступление не найдена — скорее всего, её уже рассмотрели,
     * пока экран был открыт.
     */
    @ExceptionHandler(JoinRequestNotFoundException.class)
    public ProblemDetail onJoinRequestNotFound(JoinRequestNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Заявка не найдена", e.getMessage());
    }

    /**
     * Заявка уже подана и рассматривается ({@code BR-JOIN-4}).
     */
    @ExceptionHandler(JoinRequestAlreadySubmittedException.class)
    public ProblemDetail onJoinRequestAlreadySubmitted(JoinRequestAlreadySubmittedException e) {
        return problem(HttpStatus.CONFLICT, "Заявка уже подана", e.getMessage());
    }

    /**
     * Нарушено состояние домена: заявка уже решена, голос уже подан
     * ({@code BR-VOTE-2}), аккаунт уже в сообществе ({@code BR-JOIN-2}).
     *
     * <p>Это не ошибка ввода — данные в порядке, но действие уже не имеет
     * смысла. Поэтому {@code 409}, а не {@code 400}.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail onIllegalState(IllegalStateException e) {
        return problem(HttpStatus.CONFLICT, "Действие уже невозможно", e.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}

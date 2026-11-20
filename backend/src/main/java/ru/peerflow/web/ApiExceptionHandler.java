package ru.peerflow.web;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import ru.peerflow.auth.service.AccountNotFoundException;
import ru.peerflow.auth.service.InvalidCredentialsException;
import ru.peerflow.auth.service.LoginAlreadyTakenException;
import ru.peerflow.community.service.JoinRequestAlreadySubmittedException;
import ru.peerflow.community.service.JoinRequestNotFoundException;
import ru.peerflow.community.service.MembershipRequiredException;
import ru.peerflow.discussion.service.MessageNotFoundException;
import ru.peerflow.legitimacy.service.CandidateNotFoundException;
import ru.peerflow.legitimacy.service.NotLeaderException;
import ru.peerflow.proposal.service.ProposalNotFoundException;
import ru.peerflow.report.service.AttachmentNotFoundException;

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
     * Тело запроса не читается: битый JSON, не тот тип поля и т.п.
     *
     * <p>Обработчик обязателен именно для этого типа, хотя ниже уже есть
     * обработчик {@link IOException}: Spring, не найдя точного совпадения,
     * подбирает обработчик по <em>причине</em> исключения
     * ({@code ExceptionHandlerMethodResolver#resolveExceptionMapping}). Причина
     * {@code HttpMessageNotReadableException} — Jackson {@code JsonParseException},
     * а он наследник {@code IOException}. Без явного обработчика битый JSON
     * попадал бы в {@link #onStorageFailure(IOException)} и отдавал {@code 500}
     * «Не удалось сохранить файл» вместо честного {@code 400}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadableBody(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректный запрос",
                "тело запроса не удалось прочитать");
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
     * Предложение не найдено: его нет или оно в другом сообществе
     * ({@code BR-PRIV-1}).
     *
     * <p>Оба случая неразличимы осознанно: иначе по разнице кодов можно было
     * бы узнать, какие предложения существуют в чужом сообществе.
     */
    @ExceptionHandler(ProposalNotFoundException.class)
    public ProblemDetail onProposalNotFound(ProposalNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Предложение не найдено", e.getMessage());
    }

    /**
     * Сообщение не найдено: его нет или оно в другом сообществе
     * ({@code BR-PRIV-1}).
     *
     * <p>Оба случая неразличимы осознанно — как и с предложениями.
     */
    @ExceptionHandler(MessageNotFoundException.class)
    public ProblemDetail onMessageNotFound(MessageNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Сообщение не найдено", e.getMessage());
    }

    /**
     * Кандидат не найден среди членов своего сообщества ({@code BR-PRIV-1}).
     *
     * <p>Чужой аккаунт неотличим от несуществующего — иначе по коду ответа
     * можно было бы выяснить, кто ещё есть в системе.
     */
    @ExceptionHandler(CandidateNotFoundException.class)
    public ProblemDetail onCandidateNotFound(CandidateNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Кандидат не найден", e.getMessage());
    }

    /**
     * Отчёт или трату бюджета пытается сделать не лидер задачи
     * ({@code BR-LIFE-6}, {@code BR-BUDGET-4}).
     *
     * <p>Задача видна человеку и он член сообщества, но исполнительного
     * мандата у него нет — поэтому {@code 403}, а не {@code 404}.
     */
    @ExceptionHandler(NotLeaderException.class)
    public ProblemDetail onNotLeader(NotLeaderException e) {
        return problem(HttpStatus.FORBIDDEN, "Нужен мандат лидера", e.getMessage());
    }

    /**
     * Файл отчёта не найден: его нет или он в другом сообществе
     * ({@code BR-PRIV-1}).
     *
     * <p>Оба случая неразличимы осознанно — как и с предложениями.
     */
    @ExceptionHandler(AttachmentNotFoundException.class)
    public ProblemDetail onAttachmentNotFound(AttachmentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Файл не найден", e.getMessage());
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

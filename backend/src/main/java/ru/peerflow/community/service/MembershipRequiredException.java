package ru.peerflow.community.service;

/**
 * Действие доступно только действующему члену сообщества.
 *
 * <p>Право голоса есть у каждого члена ({@code BR-VOTE-1}), но не у того, кто
 * ещё ждёт приёма, и не у удалённого аккаунта. Это не «нет доступа к данным»
 * ({@code 401}), а «твоих прав недостаточно» — поэтому {@code 403}.
 */
public class MembershipRequiredException extends RuntimeException {

    public MembershipRequiredException(String message) {
        super(message);
    }
}

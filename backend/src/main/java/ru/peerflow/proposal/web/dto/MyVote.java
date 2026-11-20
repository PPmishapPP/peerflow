package ru.peerflow.proposal.web.dto;

import java.time.Instant;

/**
 * Голос текущего пользователя по предложению — чтобы экран показал, что он уже
 * выбрал ({@code BR-VOTE-13}).
 *
 * @param inFavor {@code true} — «делаем», {@code false} — «не делаем»
 * @param castAt  когда голос подан или последний раз изменён
 */
public record MyVote(boolean inFavor, Instant castAt) {
}

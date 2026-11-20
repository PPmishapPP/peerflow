package ru.peerflow.proposal.web.dto;

import java.time.Instant;

/**
 * Голос текущего пользователя по выполнению задачи ({@code BR-VOTE-5}).
 *
 * @param completed {@code true} — «выполнено», {@code false} — «не выполнено»
 * @param castAt    когда ответ дан или последний раз изменён
 */
public record MyClosureVote(boolean completed, Instant castAt) {
}

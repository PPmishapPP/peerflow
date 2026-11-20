package ru.peerflow.realtime;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ru.peerflow.community.domain.Community;

/**
 * Рассылка событий подписчикам потока {@code /api/events}
 * ({@code BR-DISC-1}, {@code BR-VOTE-4}, {@code BR-LIFE-7}).
 *
 * <p>Подписки живут в памяти процесса: в MVP один сервер, и внешний брокер
 * (Redis, Kafka) был бы лишней инфраструктурой ради трёх видов событий. Когда
 * серверов станет больше, менять придётся только этот класс.
 *
 * <p>Подписка привязана к сообществу: события чужого сообщества не должны
 * утекать даже как «что-то произошло» ({@code BR-PRIV-1}). Фильтрация здесь,
 * а не в контроллере, чтобы забытый вызов не отправил событие всем.
 *
 * <p>Отправка не бросает исключений наружу: подписчик мог закрыть приложение,
 * и это не повод ломать действие, которое публикует событие. Мёртвые эмиттеры
 * убираются из списка при первой неудаче.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    /**
     * Сколько живёт подписка без событий.
     *
     * <p>Конечный срок нужен, чтобы клиент, потерявший сеть, не оставлял
     * вечное соединение на сервере. Клиент подписывается заново — для него это
     * обычная переподписка.
     */
    private static final long TIMEOUT_MILLIS = 30L * 60 * 1000;

    private final Map<UUID, List<SseEmitter>> subscriptions = new ConcurrentHashMap<>();
    private final Clock clock;

    public EventPublisher(Clock clock) {
        this.clock = clock;
    }

    /**
     * Подписывает клиента на события сообщества.
     *
     * @param community сообщество подписчика
     * @return поток, который клиент держит открытым
     */
    public SseEmitter subscribe(Community community) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        List<SseEmitter> list =
                subscriptions.computeIfAbsent(community.getId(), id -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        // Отписка по любому завершению: иначе список рос бы с каждой
        // переподпиской, и память кончалась бы раньше, чем соединения.
        emitter.onCompletion(() -> remove(community.getId(), emitter));
        emitter.onTimeout(() -> {
            remove(community.getId(), emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(community.getId(), emitter));

        // Первое событие сразу: клиент должен узнать, что поток действительно
        // открыт, а не просто «запрос не упал». Без этого прокси, буферизующий
        // ответ, выглядел бы как рабочий канал, который молчит.
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("ok", true)));
        } catch (IOException e) {
            remove(community.getId(), emitter);
        }
        return emitter;
    }

    /**
     * Публикует событие задачи.
     *
     * @param community  чьё сообщество
     * @param type       что произошло
     * @param proposalId задача
     */
    public void publish(Community community, EventType type, UUID proposalId) {
        List<SseEmitter> list = subscriptions.get(community.getId());
        if (list == null || list.isEmpty()) {
            // Никто не слушает — событие и не нужно: остальные получат данные
            // обычным запросом при открытии экрана.
            return;
        }
        ProposalEvent event = new ProposalEvent(type, proposalId, clock.instant().toString());
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(type.name())
                        .data(event));
            } catch (IOException | IllegalStateException e) {
                // Подписчик ушёл: убираем его и продолжаем рассылку остальным.
                log.debug("подписчик отключился: {}", e.getMessage());
                remove(community.getId(), emitter);
            }
        }
    }

    /**
     * Публикует событие после фиксации транзакции.
     *
     * <p>Событие — это приглашение перезапросить экран. Отправив его до
     * коммита, мы бы предложили клиенту прочитать данные, которых ещё нет:
     * голос или сообщение в этот момент видны только внутри транзакции, и
     * клиент показал бы старое состояние.
     *
     * <p>Вне транзакции (например, в тесте) публикуем сразу — иначе событие
     * не ушло бы никогда.
     *
     * @param community  чьё сообщество
     * @param type       что произошло
     * @param proposalId задача
     */
    public void publishAfterCommit(Community community, EventType type, UUID proposalId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publish(community, type, proposalId);
                        }
                    });
            return;
        }
        publish(community, type, proposalId);
    }

    /**
     * Сколько сейчас подписчиков — для проверки, что отключение убирает
     * подписку.
     *
     * @param community сообщество
     * @return число открытых потоков
     */
    public int subscriberCount(Community community) {
        List<SseEmitter> list = subscriptions.get(community.getId());
        return list == null ? 0 : list.size();
    }

    private void remove(UUID communityId, SseEmitter emitter) {
        List<SseEmitter> list = subscriptions.get(communityId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            subscriptions.remove(communityId, list);
        }
    }
}

package ru.peerflow.realtime;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ru.peerflow.auth.service.AccountNotFoundException;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.MembershipRequiredException;

/**
 * Поток событий сообщества ({@code BR-DISC-1}, {@code BR-VOTE-4},
 * {@code BR-LIFE-7}).
 *
 * <p>Односторонний канал сервер → клиент: живую ленту, счётчики голосов и
 * новые отчёты достаточно получать, а отправлять по нему нечего — действия
 * идут обычными запросами. Поэтому SSE, а не WebSocket: проще, работает через
 * обычный HTTP и не требует отдельного протокола на клиенте.
 *
 * <p>Поток закрыт токеном и членством ({@code BR-PRIV-1}): подписаться может
 * только действующий член сообщества, и события другого сообщества ему не
 * придут.
 */
@RestController
public class EventController {

    private final EventPublisher publisher;
    private final UserRepository userRepository;

    public EventController(EventPublisher publisher, UserRepository userRepository) {
        this.publisher = publisher;
        this.userRepository = userRepository;
    }

    /**
     * Открывает поток событий.
     *
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return поток, который клиент держит открытым
     * @throws AccountNotFoundException    если аккаунта нет
     * @throws MembershipRequiredException если аккаунт не действующий член
     */
    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new AccountNotFoundException(memberId));
        if (!member.isActiveMember()) {
            // BR-PRIV-1: гость и ожидающий не должны видеть, что происходит
            // в задачах сообщества, даже в виде «что-то изменилось».
            throw new MembershipRequiredException(
                    "поток событий доступен только действующему члену сообщества");
        }
        return publisher.subscribe(member.getCommunity());
    }
}


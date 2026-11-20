package ru.peerflow.legitimacy.web;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.peerflow.legitimacy.service.LegitimacyService;
import ru.peerflow.legitimacy.web.dto.MyLegitimacyResponse;

/**
 * Легитимность и репутация текущего человека ({@code BR-LEGIT-3},
 * {@code BR-REP-2}).
 *
 * <p>Отдельный путь без задачи: профиль показывает все передачи сразу, и
 * привязка к конкретному предложению заставила бы клиент перебирать задачи,
 * чтобы нарисовать одну карточку.
 */
@RestController
public class MyLegitimacyController {

    private final LegitimacyService legitimacyService;

    public MyLegitimacyController(LegitimacyService legitimacyService) {
        this.legitimacyService = legitimacyService;
    }

    /**
     * Кому текущий человек передал легитимность и какой у него репутация.
     *
     * @param authentication текущая аутентификация из JWT-фильтра
     * @return репутация и передачи доверия по активным задачам
     */
    @GetMapping(path = "/api/me/legitimacy", produces = MediaType.APPLICATION_JSON_VALUE)
    public MyLegitimacyResponse myLegitimacy(Authentication authentication) {
        UUID memberId = UUID.fromString(authentication.getName());
        return legitimacyService.myLegitimacy(memberId);
    }
}

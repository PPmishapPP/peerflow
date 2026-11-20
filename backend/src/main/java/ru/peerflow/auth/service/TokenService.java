package ru.peerflow.auth.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.config.JwtProperties;

/**
 * Выпуск токена доступа ({@code BR-JOIN-1}).
 *
 * <p>Токен — самодостаточное доказательство входа: сервер не хранит сессии,
 * поэтому при каждом запросе личность восстанавливается из подписи и claim'ов.
 * В токен кладём только то, что нужно для проверки доступа и для отображения
 * имени: идентификатор аккаунта, логин, отображаемое имя, сообщество и признак
 * действующего членства ({@code BR-PRIV-1}).
 *
 * <p>Признак членства в токене — снимок на момент входа. Права на операции,
 * которые меняют состояние сообщества, всё равно проверяются по базе
 * (в задачах 6+): токен не должен становиться «пропуском навсегда».
 */
@Service
public class TokenService {

    /** Кто выпустил токен: наш сервер. */
    private static final String ISSUER = "peerflow";

    /** Claim с логином — по нему удобно опознать аккаунт в логах. */
    public static final String CLAIM_LOGIN = "login";

    /** Claim с отображаемым именем — клиенту не нужен лишний запрос. */
    public static final String CLAIM_DISPLAY_NAME = "displayName";

    /** Claim с идентификатором сообщества; отсутствует, пока аккаунт не принят. */
    public static final String CLAIM_COMMUNITY_ID = "communityId";

    /** Claim «является действующим членом сообщества». */
    public static final String CLAIM_MEMBER = "member";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * Выпускает токен для аккаунта.
     *
     * @param user аккаунт, который только что подтвердил личность
     * @return токен и его срок жизни
     */
    public IssuedToken issue(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.ttl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim(CLAIM_LOGIN, user.getLogin())
                .claim(CLAIM_DISPLAY_NAME, user.getDisplayName())
                .claim(CLAIM_MEMBER, user.isActiveMember());

        Community community = user.getCommunity();
        if (community != null) {
            claims.claim(CLAIM_COMMUNITY_ID, community.getId().toString());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();

        return new IssuedToken(value, expiresAt, properties.ttl());
    }

    /**
     * Выданный токен.
     *
     * @param value      значение для заголовка {@code Authorization: Bearer …}
     * @param expiresAt  момент, после которого токен недействителен
     * @param lifetime   полный срок жизни токена
     */
    public record IssuedToken(String value, Instant expiresAt, Duration lifetime) {
    }
}

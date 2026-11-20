package ru.peerflow.auth.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.peerflow.auth.web.dto.AuthResponse;
import ru.peerflow.auth.web.dto.LoginRequest;
import ru.peerflow.auth.web.dto.ProfileResponse;
import ru.peerflow.auth.web.dto.RegisterRequest;
import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.community.service.AvatarStorage;

/**
 * Регистрация и вход ({@code BR-JOIN-1}, {@code BR-JOIN-3}).
 *
 * <p>Пароль никогда не хранится в открытом виде — только BCrypt-хэш. Вход
 * сравнивает пароль с хэшем и выдаёт токен; сервер не ведёт сессий.
 *
 * <p>Первый зарегистрировавшийся сразу становится членом сообщества
 * ({@code BR-JOIN-3}): если сообществ ещё нет, создаётся одно и он вступает в
 * него. Остальные регистрируются «в воздухе» — без сообщества, и ждут
 * голосования действующих членов ({@code BR-JOIN-4}, {@code BR-JOIN-5}).
 */
@Service
public class AuthService {

    /** Имя сообщества, которое создаётся первым зарегистрировавшимся. */
    static final String DEFAULT_COMMUNITY_NAME = "Маяк";

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AvatarStorage avatarStorage;

    public AuthService(UserRepository userRepository,
                       CommunityRepository communityRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       AvatarStorage avatarStorage) {
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.avatarStorage = avatarStorage;
    }

    /**
     * Регистрирует аккаунт и сразу выдаёт токен.
     *
     * <p>Сохранённый аватар удаляется, если аккаунт так и не удалось создать:
     * иначе на диске остались бы файлы без владельца.
     *
     * @param request данные регистрации
     * @param avatar  фотография; может отсутствовать
     * @return токен и данные аккаунта
     * @throws LoginAlreadyTakenException если логин уже занят
     * @throws IllegalArgumentException   если аватар не принят
     * @throws IOException                если аватар не удалось сохранить
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, MultipartFile avatar) throws IOException {
        if (userRepository.existsByLogin(request.login())) {
            throw new LoginAlreadyTakenException(request.login());
        }

        String avatarPath = null;
        if (avatar != null && !avatar.isEmpty()) {
            avatarPath = avatarStorage.store(avatar);
        }

        try {
            User user = User.register(
                    request.login(),
                    passwordEncoder.encode(request.password()),
                    request.displayName());
            user.setAbout(blankToNull(request.about()));
            user.setAvatarPath(avatarPath);

            joinFirstUserIfNeeded(user);

            User saved = userRepository.save(user);
            return response(saved);
        } catch (DataIntegrityViolationException e) {
            // Между проверкой и вставкой логин мог занять параллельный запрос:
            // уникальный индекс в базе — последняя линия защиты.
            avatarStorage.deleteQuietly(avatarPath);
            throw new LoginAlreadyTakenException(request.login());
        } catch (RuntimeException e) {
            avatarStorage.deleteQuietly(avatarPath);
            throw e;
        }
    }

    /**
     * Проверяет логин и пароль и выдаёт токен.
     *
     * @param request логин и пароль
     * @return токен и данные аккаунта
     * @throws InvalidCredentialsException если логин не найден или пароль не подошёл
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByLogin(request.login())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return response(user);
    }

    /**
     * Отдаёт профиль по идентификатору из токена.
     *
     * <p>Данные читаются из базы, а не из claim'ов: членство могло измениться
     * после входа — например, человека только что приняли голосованием
     * ({@code BR-JOIN-5}). Токен говорит «кто ты», база — «что тебе можно».
     *
     * @param userId идентификатор аккаунта из токена
     * @return профиль аккаунта
     * @throws AccountNotFoundException если аккаунт не найден (или удалён)
     */
    @Transactional(readOnly = true)
    public ProfileResponse currentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));
        Community community = user.getCommunity();
        return new ProfileResponse(
                user.getId(),
                user.getLogin(),
                user.getDisplayName(),
                user.getAbout(),
                user.getAvatarPath(),
                user.isActiveMember(),
                community == null ? null : community.getId());
    }

    /**
     * Первый аккаунт на сервере становится членом сообщества автоматически
     * ({@code BR-JOIN-3}).
     *
     * <p>Сообщество создаётся здесь же: пустой сервер не должен требовать
     * отдельного шага «завести сообщество» — иначе первому человеку некуда
     * вступать.
     */
    private void joinFirstUserIfNeeded(User user) {
        if (userRepository.count() > 0) {
            return;
        }
        Community community = communityRepository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> communityRepository.save(
                        Community.create(DEFAULT_COMMUNITY_NAME)));
        user.join(community);
    }

    private AuthResponse response(User user) {
        TokenService.IssuedToken token = tokenService.issue(user);
        Community community = user.getCommunity();
        return new AuthResponse(
                token.value(),
                token.expiresAt(),
                user.getId(),
                user.getLogin(),
                user.getDisplayName(),
                user.isActiveMember(),
                community == null ? null : community.getId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

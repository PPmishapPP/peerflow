package ru.peerflow.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import ru.peerflow.community.domain.Community;
import ru.peerflow.community.domain.User;
import ru.peerflow.community.domain.UserStatus;
import ru.peerflow.community.repo.CommunityRepository;
import ru.peerflow.community.repo.UserRepository;
import ru.peerflow.support.PostgresTestBase;

/**
 * Тесты сущностей и репозитория аккаунтов на настоящем PostgreSQL.
 *
 * <p>Проверяем то, на чём стоит вход и приём в сообщество:
 * уникальность логина ({@code BR-JOIN-1}), допустимость аккаунта без
 * сообщества, членство ({@code BR-JOIN-2}, {@code BR-JOIN-3}), подсчёт членов
 * для порога приёма ({@code BR-JOIN-5}, {@code BR-LIMIT-3}) и то, что удаление —
 * пометка, а не физическое удаление ({@code BR-PRIV-2}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends PostgresTestBase {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityRepository communityRepository;

    private Community community;

    @BeforeEach
    void createCommunity() {
        community = communityRepository.save(Community.create("Маяк"));
        entityManager.flush();
    }

    @Test
    @DisplayName("Аккаунт сохраняется: генерируется UUID, дата создания заполняется")
    void savesUserWithGeneratedId() {
        User user = userRepository.save(
                User.register("ivan", "hash", "Иван"));

        entityManager.flush();
        entityManager.clear();

        assertThat(user.getId()).as("идентификатор выдаёт база (UUID)").isNotNull();
        assertThat(user.getCreatedAt()).as("дата создания заполняется автоматически").isNotNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getCommunity()).as("без приёма сообщества у аккаунта нет").isNull();
        assertThat(user.isActiveMember())
                .as("аккаунт без сообщества — не член и права голоса не даёт (BR-VOTE-1)")
                .isFalse();
    }

    @Test
    @DisplayName("Поиск по логину находит аккаунт, а по неизвестному — пусто (BR-JOIN-1)")
    void findsByLogin() {
        userRepository.save(User.register("ivan", "hash", "Иван"));
        entityManager.flush();

        Optional<User> found = userRepository.findByLogin("ivan");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getDisplayName()).isEqualTo("Иван");
        assertThat(userRepository.findByLogin("petr")).isEmpty();
    }

    @Test
    @DisplayName("Занятый логин: existsByLogin=true и повторная вставка отклоняется базой")
    void rejectsDuplicateLogin() {
        userRepository.save(User.register("ivan", "hash", "Иван"));
        entityManager.flush();

        assertThat(userRepository.existsByLogin("ivan")).isTrue();
        assertThat(userRepository.existsByLogin("petr")).isFalse();

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.register("ivan", "другой-хэш", "Другой Иван")))
                .as("логин уникален — дубль не должен сохраниться (BR-JOIN-1)")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Первый зарегистрировавшийся становится членом сообщества (BR-JOIN-3)")
    void firstUserBecomesActiveMember() {
        User user = User.register("ivan", "hash", "Иван");
        user.join(community);

        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findByLogin("ivan").orElseThrow();

        assertThat(reloaded.getCommunity()).isNotNull();
        assertThat(reloaded.getCommunity().getId()).isEqualTo(community.getId());
        assertThat(reloaded.getJoinedAt()).as("дата вступления фиксируется").isNotNull();
        assertThat(reloaded.isActiveMember()).isTrue();
    }

    @Test
    @DisplayName("Повторное вступление отклоняется: человек состоит в одном сообществе (BR-JOIN-2)")
    void rejectsSecondMembership() {
        Community other = communityRepository.save(Community.create("Другое"));

        User user = User.register("ivan", "hash", "Иван");
        user.join(community);

        assertThatThrownBy(() -> user.join(other))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже состоит");
    }

    @Test
    @DisplayName("Удаление — пометка статуса, строка остаётся в базе (BR-PRIV-2)")
    void deletionIsMarkOnly() {
        User user = User.register("ivan", "hash", "Иван");
        user.join(community);
        userRepository.save(user);
        entityManager.flush();

        user.markDeleted();
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findByLogin("ivan").orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(reloaded.isActiveMember())
                .as("удалённый аккаунт теряет права члена, хотя строка сохранена")
                .isFalse();
    }

    @Test
    @DisplayName("Подсчёт членов сообщества не учитывает удалённых (BR-JOIN-5, BR-LIMIT-3)")
    void countsOnlyActiveMembers() {
        User first = User.register("ivan", "hash", "Иван");
        first.join(community);
        userRepository.save(first);

        User second = User.register("petr", "hash", "Пётр");
        second.join(community);
        userRepository.save(second);

        User pending = User.register("anna", "hash", "Анна");
        userRepository.save(pending);

        entityManager.flush();

        assertThat(userRepository.countByCommunityAndStatus(community, UserStatus.ACTIVE))
                .as("учитываются только принятые в сообщество")
                .isEqualTo(2);

        second.markDeleted();
        userRepository.save(second);
        entityManager.flush();

        assertThat(userRepository.countByCommunityAndStatus(community, UserStatus.ACTIVE))
                .as("удалённый член не участвует в базе для порога 30%")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Сообщество сервера находится без знания идентификатора (BR-JOIN-2)")
    void returnsSingleCommunity() {
        entityManager.flush();

        assertThat(communityRepository.findFirstByOrderByCreatedAtAsc())
                .isPresent()
                .get()
                .extracting(Community::getName)
                .isEqualTo("Маяк");
    }

    @Test
    @DisplayName("Дата создания сообщества проставляется автоматически")
    void communityHasCreationTime() {
        Instant createdAt = community.getCreatedAt();

        assertThat(community.getId()).isNotNull();
        assertThat(createdAt).isNotNull();
        assertThat(createdAt).isBeforeOrEqualTo(Instant.now());
    }
}

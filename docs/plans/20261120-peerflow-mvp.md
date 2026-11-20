# План реализации PeerFlow MVP

Дата: 20.11.2026

## Overview

**Что делаем.** Превращаем демо-прототип в реально работающее приложение для
сообщества «Маяк»: Android-клиент подключаем к настоящему бэкенду, данные живут
в PostgreSQL, решения и голоса — постоянные и проверяемые.

**Какую задачу решает.** Бизнес-описание (`docs/peerflow.md`) описывает модель власти и
правила, но приложение пока существует только как прототип на выдуманных данных
в памяти. Чтобы внедрять, нужен сервер: общие данные, вход в аккаунт, приём
новых членов, реальные голоса и легитимность.

**Ключевая выгода.** Сообщество получает один общий список предложений и
честный механизм: договорились → выбрали исполнителя → он отчитался → сообщество
решило, сделано ли. Власть исполнителя временная и отзываемая.

**Как встраивается в существующее.** Репозиторий переведён в монорепо:
`prototype/` → `android/` (там остаётся готовый UI), рядом появляется `backend/`.
Документация — в `docs/`. Продуктовые правила не меняем — берём ровно то, что
описано в бизнес-описании (`docs/peerflow.md`).

## Context (from discovery)

**Что уже есть:**
- `docs/peerflow.md` — бизнес-описание: три сущности власти, пороги MVP, правила.
- `android/` (ранее `prototype/`) — Android-приложение (Kotlin + Jetpack Compose)
  на демо-данных: 7 экранов, Hilt и Navigation Compose уже подключены,
  7 модульных тестов.
  - Экраны: боль-мессенджер, список предложений, новое предложение, карточка
    предложения (обсуждение / в работе), отчёт лидера, закрытие задачи, профиль.
  - `app/src/main/java/ru/peerflow/demo/data/DemoData.kt` — вся модель в памяти.
  - `app/src/main/java/ru/peerflow/demo/MainActivity.kt` — навигация на состоянии.
- `presentation/` — слайды (приложение понравилось, показываем его).

**Чего нет:**
- Бэкенда как такового: ни Spring Boot, ни БД, ни миграций, ни API.
- Никакого хранения данных за пределами памяти процесса.

**Зависимости и факты по стеку:**
- Бизнес-описание задаёт: Java 21, Spring Boot 3.x, Gradle, Flyway, PostgreSQL,
  JUnit 5 + Testcontainers, realtime (WebSocket/SSE), Android только Kotlin/Compose.
- Прототип: Gradle 9.8.2 / AGP 9.5, compileSdk 37, minSdk 26, JDK 21 в сборке.
- Пороги MVP (константы системы): удержание легитимности 70%, спам 3 метки,
  приём в сообщество 30%.

**Решения, принятые на этапе планирования:**
- Монорепо: `android/` + `backend/`.
- Подход: вертикальными срезами — каркас, затем по одной сквозной функции от БД
  до экрана. После каждой фазы есть работающий кусок.
- Доступ к БД: Spring Data JPA / Hibernate.
- Вложения к отчётам: локальный диск сервера + метаданные в БД.
- Тесты: обычный подход — сначала код, затем тесты.
- Название приложения остаётся **PeerFlow** (рабочее имя). У сообщества есть домен
  **`mayakspace.com`** — использовать его для бэкенда/клиента, когда дойдём до
  сети (базовый URL, идентификаторы пакетов не меняем).

## Development Approach

- **testing approach**: обычный — сначала код, затем тесты в той же задаче.
- каждую задачу доводим до конца, прежде чем начать следующую.
- изменения небольшие и сфокусированные.
- **КРИТИЧНО: каждая задача заканчивается тестами** на новый и изменённый код.
  Тесты — не опция, а обязательная часть задачи.
- **КРИТИЧНО: все тесты должны проходить до перехода к следующей задаче.**
- **КРИТИЧНО: при изменении объёма — обновлять этот файл.**
- держим совместимость: прототип в `android/` должен запускаться на каждом шаге,
  пока его не заменит подключение к сети.
- ничего из бизнес-описания не расширяем «на будущее» — YAGNI: делаем ровно пороги и
  механизмы MVP, без эскроу, без push, без CI/CD (их в MVP нет).

## Testing Strategy

- **Модульные тесты (бэкенд):** обязательны в каждой задаче — на пороги,
  агрегацию легитимности, подсчёт голосов, ранжирование. JUnit 5.
- **Интеграционные тесты (бэкенд):** JUnit 5 + Testcontainers с реальным
  PostgreSQL для репозиториев, миграций и эндпоинтов — с той же строгостью.
- **Тесты Android:** модульные тесты на ViewModel/репозитории; существующий
  `DemoDataTest` со временем заменяется тестами на реальные источники данных.
- **Инструментальные тесты Android:** `androidTest` уже есть в проекте; для
  новых экранов добавляем проверки навигации, где это оправдано.

## Progress Tracking

- выполненные пункты отмечаем `[x]` сразу.
- новые задачи, найденные по ходу, — с префиксом `[+]`.
- проблемы и блокеры — с префиксом `[!]`.
- если реализация отходит от плана — обновляем план.
- держим план в соответствии с фактической работой.

## Solution Overview

**Архитектура.** Классический клиент-сервер: Android-приложение общается с
Spring Boot по REST, «горячие» поверхности — по realtime-каналу. Состояние —
в PostgreSQL, схема накатывается Flyway. Домен — в сервисном слое, правила
(пороги, выбор лидера, агрегация легитимности) — детерминированные функции.

**Модульность бэкенда.** Один Gradle-проект Spring Boot. Пакеты по доменам:
`auth`, `community`, `proposal`, `discussion`, `legitimacy`, `report`,
`reputation`, `realtime`, `config`. Слои внутри домена: `web` (контроллеры),
`service`, `domain`, `repo`.

**Ключевые решения и почему:**
1. **Легитимность — append-only журнал событий.** Никаких «текущих значений»
   в изменяемом поле: пишем события «передал / отозвал», текущий агрегат считаем
   детерминированно. Это даёт аудит, воспроизводимость и простой откат ошибок.
2. **Голоса идемпотентны.** Уникальные индексы `(user_id, target_id, type)`:
   быстрые повторные нажатия в Android не создают дубли.
3. **Ранжирование считает сервер.** Метрику популярности предложения
   (`участники + (за − против)`) и порядок сообщений клиент не дублирует —
   иначе правила разъедутся.
4. **Виртуальный бюджет.** Только учёт: суммы, траты, отчёты. Переводов денег,
   эскроу и платёжной интеграции нет.
5. **Realtime — SSE.** Односторонний поток сервер→клиент покрывает ленту,
   счётчики голосов и уведомления; на Android это простой `OkHttp`-EventSource.
   Если по ходу понадобится двусторонний канал — меняем на WebSocket/STOMP.

**Модель данных (набросок, уточняется в задачах):**

| Таблица | Назначение |
|---|---|
| `app_user` | Аккаунт: логин, хэш пароля, имя, «о себе», аватар, дата вступления, статус |
| `community` | Сообщество; в MVP одна строка |
| `join_request`, `join_vote` | Заявки на вступление и голоса за приём (порог 30%) |
| `proposal` | Предложение: автор, тема, описание, бюджет, стадия |
| `proposal_decision_vote` | Голос «делаем / не делаем» |
| `candidate` | Кандидаты в лидеры по задаче |
| `message` | Сообщения дискуссии (под предложением и под отчётом), с ответами |
| `reaction` | Реакции на сообщение: `+ / − / спам` |
| `legitimacy_event` | Append-only журнал передачи и отзыва легитимности |
| `report`, `attachment` | Отчёты лидера и файлы (метаданные в БД, файл на диске) |
| `closure_vote` | Голос «выполнено / не выполнено» |
| `reputation_ledger` | Append-only начисления репутации при закрытии задач |

Стадии предложения: `DISCUSSION` («На обсуждении») → `IN_WORK` («В работе»)
→ `CLOSED` (закрыто сообществом). Есть `REJECTED` для решения «не делаем».

**API (набросок):**
- `POST /api/auth/register`, `POST /api/auth/login` → токен.
- `GET /api/community`, заявки: `/api/join-requests`, голоса за приём.
- Предложения: `GET /api/proposals?sort=`, `POST /api/proposals`,
  `GET /api/proposals/{id}`, голос «делаем/не делаем».
- Дискуссия: сообщения и реакции `/api/proposals/{id}/messages`,
  `/api/messages/{id}/reactions`.
- Легитимность: `POST /api/proposals/{id}/legitimacy`, кандидаты.
- Отчёты и файлы: `POST /api/proposals/{id}/reports` + загрузка вложения.
- Закрытие: `POST /api/proposals/{id}/closure-votes`.
- Realtime: `GET /api/events` (SSE).

**Пороги (константы конфигурации, не хардкод в логике):**
- `LEGITIMACY_RETAIN_RATIO = 0.70` (ниже — мандат утрачен).
- `SPAM_HIDE_MARKS = 3`.
- `JOIN_ACCEPT_RATIO = 0.30`.

## Technical Details

- **Идентификаторы:** `UUID` (или `BIGSERIAL` — уточняется в задаче сущностей).
- **Время:** `Instant` в UTC, формат ISO-8601 в API.
- **Файлы:** каталог задаётся свойством `peerflow.storage.root`; в БД — имя,
  путь, тип, размер. Проверяем размер и тип при загрузке.
- **Легитимность:** агрегат = последнее событие на пару (задача, участник).
  «Моя легитимность передана лидеру» = есть активная передача.
- **Тай-брейкеры лидера:** больше легитимности → больше репутации → раньше
  вступление.
- **Ранжирование сообщений:** по сумме `+`, при равенстве — меньше `−`.
  Спам при `SPAM_HIDE_MARKS` метках скрывается, но не удаляется.
- **Метрика популярности предложения:** `уникальные участники + (за − против)`.
- **Безопасность:** Spring Security, пароль — BCrypt, токен — JWT (Bearer).
  Приватность: контент виден только членам сообщества.
- **Удаление аккаунта:** помечаем `status = DELETED`, физически не стираем.

## What Goes Where

- **Implementation Steps** (`[ ]`): правки кода, тесты, документация — всё, что
  делается внутри репозитория.
- **Post-Completion** (без чекбоксов): ручная проверка, внешние системы,
  настройки окружения.

## Implementation Steps

### Фаза 0. Каркас и инфраструктура

### Task 1: Перевести репозиторий в монорепо

**Files:**
- Modify: структура репозитория (`prototype/` → `android/`)
- Create: `android/README.md` (если нужно), обновить ссылки в `README.md` и `docs/`

**Требования:** инфраструктурная задача, продуктовых требований нет (подготовка к работе).

- [x] вынести `README.md` в корень проекта
- [x] перенести бизнес-описание в `docs/peerflow.md`, переписать чистым бизнес-языком с ID требований
- [x] завести `AGENTS.md` с рабочим процессом и правилом актуальности документов
- [x] завести `docs/architecture.md` (каркас описания системы)
- [x] переместить `prototype/` в `android/` (репозиторий без git — `Move-Item`; история отсутствует)
- [x] обновить пути: корневой `README.md`, `AGENTS.md`, `docs/architecture.md`, `android/README.md`
- [x] проверить сборку: `cd android; .\gradlew.bat :app:assembleDebug`
- [x] проверить тесты: `cd android; .\gradlew.bat :app:testDebugUnitTest` (7 тестов, зелёные)
- [x] запустить приложение на эмуляторе и убедиться, что демо работает как раньше

### Task 2: Создать скелет бэкенда Spring Boot

**Files:**
- Create: `backend/build.gradle.kts`, `backend/settings.gradle.kts`, `backend/gradle.properties`, wrapper
- Create: `backend/src/main/java/ru/peerflow/PeerflowApplication.java`
- Create: `backend/src/main/java/ru/peerflow/config/PeerflowThresholds.java`
- Create: `backend/src/main/java/ru/peerflow/config/SecurityConfig.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml.example`
- Create: `backend/src/test/java/ru/peerflow/PeerflowApplicationTests.java`
- Create: `backend/src/test/java/ru/peerflow/config/PeerflowThresholdsTest.java`
- Create: `backend/src/test/java/ru/peerflow/support/PostgresTestBase.java`
- Create: `backend/src/test/resources/application-test.yml`, `backend/README.md`

**Требования:** `BR-LIMIT-1`, `BR-LIMIT-2`, `BR-LIMIT-3` (пороги MVP как конфигурация).

- [x] завести Gradle-проект: Java 21, Spring Boot 3.x
- [x] подключить зависимости: web, validation, security, data-jpa, flyway, postgresql
- [x] создать класс приложения и `application.yml` с параметрами БД из окружения
- [x] добавить health-эндпоинт (Spring Actuator) для проверки живости
- [x] написать smoke-тест, что контекст поднимается
- [x] вынести пороги MVP в типизированный `@ConfigurationProperties`
- [x] написать тесты на загрузку порогов и значения по умолчанию
- [x] перевести интеграционные тесты на настоящий PostgreSQL в контейнере
      (Testcontainers, `PostgresTestBase`) вместо встроенной H2
- [x] запустить тесты — зелёные до перехода к следующей задаче (8 тестов)

**Решения по ходу:**
- Spring Boot **3.5.16** (последний стабильный 3.x), Gradle **8.9** через wrapper:
  в проекте уже есть этот дистрибутив в кэше, Boot 3.5 не поддерживает Gradle 9.
- **Подключение к БД — локально установленный PostgreSQL**, Docker не используется
  (`BR-…` не затрагивается). Изначально значения были пустыми, но по итогам
  проверки решено прописать в `application.yml` **рабочие умолчания для
  локальной разработки** — `jdbc:postgresql://localhost:5432/peerflow`, `usr`/`usr`:
  у разработчика уже есть эта база, сервер поднимается без ручной настройки.
  Умолчания переопределяются через `application-local.yml` (в `.gitignore`,
  шаблон — `application-local.yml.example`) или переменными `PEERFLOW_DB_URL`,
  `PEERFLOW_DB_USERNAME`, `PEERFLOW_DB_PASSWORD`.
- Тесты идут на **настоящем PostgreSQL в контейнере** (Testcontainers), H2 не
  используется: H2 не воспроизводит типы, ограничения и поведение миграций
  PostgreSQL, поэтому зелёные тесты на нём ничего не гарантировали. База —
  `backend/src/test/java/ru/peerflow/support/PostgresTestBase.java`:
  `@Testcontainers` + статический `PostgreSQLContainer` с `@ServiceConnection`
  (Spring Boot сам подставляет URL/логин/пароль в `spring.datasource.*`,
  поэтому в `application-test.yml` параметров подключения нет). Один контейнер
  переиспользуется всеми классами тестов в JVM. Требуется запущенный Docker —
  если его нет, тесты падают явной ошибкой, «тихо» уходить на H2 нельзя.
  Образ по умолчанию `postgres:16-alpine`, версия переопределяется свойством
  `-PpeerflowPostgresImage=postgres:13-alpine` (например, чтобы совпасть с
  локальным PostgreSQL 13.3). Проверено на обоих образах — 8 тестов зелёные.
- Добавлен `SecurityConfig`: `/actuator/health` открыт (`BR-PRIV-1` — остальное
  закрыто, аутентификация по плану в Task 5).
- `spring.jpa.hibernate.ddl-auto=validate`: схему меняет только Flyway.

### Task 3: Поднять PostgreSQL и первые миграции Flyway

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__baseline.sql`
- Create: `backend/src/test/java/ru/peerflow/migration/FlywayMigrationTest.java`
- Modify: `backend/README.md` (раздел про миграции)
- Modify: `docs/architecture.md` (схема БД и соглашения миграций)

> **Уточнение (после Task 2):** БД — **локально установленный PostgreSQL**, а не
> Docker. Поэтому `backend/docker-compose.yml` не создаём: разработчик поднимает
> свой инстанс и заполняет `application-local.yml` (см. `backend/README.md`).
> Testcontainers в тестах остаётся — он не требует ручной подготовки.

**Требования:** `BR-PRIV-2` (данные живут в БД; удаление — пометкой).

> **Сделано досрочно в Task 2:** базовый класс тестов на Testcontainers
> (`PostgresTestBase`) и замена H2 в smoke-тестах каркаса на PostgreSQL
> в контейнере. Осталось только содержимое миграции и её проверка.

- [x] настроить Flyway, создать базовую миграцию (служебная таблица/расширения)
- [x] поднять базовый класс тестов на Testcontainers (переиспользуемый контейнер)
  — `backend/src/test/java/ru/peerflow/support/PostgresTestBase.java`
- [x] написать интеграционный тест: миграции накатываются, схема доступна
- [x] заменить H2 в smoke-тестах каркаса на Testcontainers-базу
- [x] задокументировать в `backend/README.md`, как поднять БД и запустить сервер
  (требование Docker для тестов и `PostgresTestBase` уже описаны)
- [x] запустить тесты — зелёные до перехода к следующей задаче (12 тестов)

**Решения по ходу:**
- Базовая миграция — `V1__baseline.sql`: создаёт расширение **`pgcrypto`**, чтобы
  функция `gen_random_uuid()` была доступна независимо от сборки PostgreSQL.
  Пользовательских таблиц в ней нет: каждая доменная миграция отвечает за свои
  таблицы, базовая только задаёт инфраструктуру (генератор UUID).
- `pgcrypto` помечен как trusted — миграция проходит под владельцем базы
  (`OWNER usr`), прав суперпользователя не требует.
- Соглашения для всех последующих миграций: `PK = uuid` c `DEFAULT gen_random_uuid()`,
  время — `timestamptz` в UTC, удаление — пометкой статуса, а не `DELETE`
  (`BR-PRIV-2`).
- Тест миграций — `backend/src/test/java/ru/peerflow/migration/FlywayMigrationTest.java`:
  проверяет через бин `Flyway` (та же конфигурация, что в бою), что миграции
  применены, нет `FAILED`/`PENDING`, повторный `migrate()` ничего не делает,
  `gen_random_uuid()` работает и уникален, а `flyway_schema_history` содержит
  успешную запись о `V1`.
- `spring.flyway.baseline-on-migrate=true` оставлен: позволяет подключиться к
  существующей локальной базе без истории Flyway, не ломая старт.
- Тесты прогнаны на обоих образах — `postgres:16-alpine` (по умолчанию) и
  `postgres:13-alpine` (версия локального сервера разработчика): 12 тестов
  зелёные, `CREATE EXTENSION pgcrypto` проходит и там, и там.

### Фаза 1. Сообщество, вход и приём

### Task 4: Сущности аккаунта и сообщества

**Files:**
- Create: `backend/src/main/java/ru/peerflow/community/domain/User.java`
- Create: `backend/src/main/java/ru/peerflow/community/domain/Community.java`
- Create: `backend/src/main/java/ru/peerflow/community/repo/UserRepository.java`
- Create: `backend/src/main/resources/db/migration/V2__user_community.sql`
- Create: `backend/src/test/java/ru/peerflow/community/UserRepositoryTest.java`

> **Требуется навык spring-data-jpa.** Перед написанием кода сущностей: активировать навык и следовать его правилам. Любое отклонение — сначала спросить разработчика.

**Требования:** `BR-JOIN-1`, `BR-JOIN-2`, `BR-JOIN-3`, `BR-PRIV-2`.

- [x] описать сущность `User` (логин, хэш пароля, имя, «о себе», аватар, статус, дата вступления)
- [x] описать сущность `Community` (одна строка в MVP)
- [x] создать миграцию `V2__user_community.sql`
- [x] написать репозитории с нужными запросами (поиск по логину и т.п.)
- [x] написать тесты репозитория (успешные и граничные случаи)
- [x] запустить тесты — зелёные до перехода к следующей задаче

**Результат:** `User` (`id`, `login`, `passwordHash`, `displayName`, `about`,
`avatarPath`, `status`, `joinedAt`) с `UserStatus { ACTIVE, DELETED }`;
`Community` — одна строка с `name` и связью с участниками. Миграция
`V2__user_community.sql` создаёт `app_user` и `community`, уникальный индекс
по `login` и индекс по сообществу. `UserRepository`
(`findByLogin`, `existsByLogin`, `countByCommunityAndStatus`),
`CommunityRepository`. `UserRepositoryTest` — 9 тестов, зелёные.

### Task 5: Регистрация, вход и токен

**Files:**
- Create: `backend/src/main/java/ru/peerflow/auth/web/AuthController.java`
- Create: `backend/src/main/java/ru/peerflow/auth/service/AuthService.java`
- Create: `backend/src/main/java/ru/peerflow/config/SecurityConfig.java`
- Create: `backend/src/test/java/ru/peerflow/auth/AuthControllerTest.java`

**Требования:** `BR-JOIN-1`, `BR-JOIN-3`, `BR-PRIV-1`, `BR-PRIV-2`.

- [x] включить Spring Security, настроить BCrypt и JWT-фильтр
- [x] реализовать регистрацию: логин, пароль, «о себе», загрузка аватара
- [x] реализовать вход и выдачу токена
- [x] первый зарегистрировавшийся автоматически становится членом сообщества
- [x] написать тесты: успешная регистрация, занятый логин, неверный пароль
- [x] запустить тесты — зелёные до перехода к следующей задаче

**Результат:** `SecurityConfig` (stateless, `permitAll` только для
`/api/auth/register`, `/api/auth/login`, health/info), ошибки в `ProblemDetail`
через `ApiExceptionHandler`. JWT HS256: `JwtProperties`
(`peerflow.security.jwt.secret` ≥ 32 символов, `ttl = PT12H`) + `JwtConfig`
(кодировщик/декодировщик) + `TokenService`. `AuthService` регистрирует
пользователя (`multipart/form-data`: JSON-часть + необязательный аватар через
`AvatarStorage`), выдаёт токен, отдаёт профиль по `GET /api/auth/me`. Первый
зарегистрировавшийся получает сообщество (`UserStatus.ACTIVE` + `community`),
остальные — аккаунт без сообщества и членства.
`AuthControllerTest` — 12 тестов, зелёные.

### Task 6: Заявки на вступление и голосование за приём

**Files:**
- Create: `backend/src/main/java/ru/peerflow/community/domain/JoinRequest.java`
- Create: `backend/src/main/java/ru/peerflow/community/service/JoinRequestService.java`
- Create: `backend/src/main/java/ru/peerflow/community/web/JoinRequestController.java`
- Create: `backend/src/main/resources/db/migration/V3__join_request.sql`
- Create: `backend/src/test/java/ru/peerflow/community/JoinRequestServiceTest.java`

> **Требуется навык spring-data-jpa.** Перед написанием кода сущностей: активировать навык и следовать его правилам. Любое отклонение — сначала спросить разработчика.

**Требования:** `BR-JOIN-4`, `BR-JOIN-5`, `BR-VOTE-8`, `BR-LIMIT-3`.

- [x] описать сущности заявки и голоса за приём, миграция `V3__join_request.sql`
- [x] реализовать подачу заявки («о себе» + фото) и её список
- [x] реализовать голосование за приём; приём — при ≥30% голосов за
- [x] принять решение о состоянии заявки (принята / отклонена) и завести члена
- [x] написать тесты: приём достигается на пороге 30%, не достигается ниже
- [x] запустить тесты — зелёные до перехода к следующей задаче

**Результат:** агрегат `JoinRequest` (`status`, `about`, `avatarPath`) с
`Set<JoinVote>` и `JoinRequestStatus { PENDING, ACCEPTED, REJECTED }`.
Миграция `V3__join_request.sql`: `join_request`, `join_vote`, частичный
уникальный индекс `uk_join_request_pending_applicant WHERE status='PENDING'`
и уникальность пары «заявка + голосующий». `JoinRequestService` считает порог
как `max(1, ceil(действующих × 0.30))` (`BigDecimal` + `RoundingMode.CEILING`),
досрочно отклоняет заявку, если порог уже недостижим, и при достижении порога
переводит заявку в `ACCEPTED` и создаёт члена сообщества. Эндпоинты:
`POST /api/join-requests`, `GET /api/join-requests`, `GET /api/join-requests/mine`
(`204`, если заявки нет), `POST /api/join-requests/{id}/votes`.
Тесты: `JoinRequestServiceTest` — 11, `JoinRequestControllerTest` — 13,
`JoinRequestThresholdTest` — 4, все зелёные.

### Task 7: Android — вход и приём

**Files:**
- Modify: `android/app/build.gradle.kts` (Retrofit/OkHttp, сериализация)
- Create: `android/app/src/main/java/ru/peerflow/demo/data/api/...` (клиент API, токен)
- Create: экраны входа и заявки на вступление
- Modify: `android/app/src/main/java/ru/peerflow/demo/MainActivity.kt` (новая навигация)

**Требования:** `BR-JOIN-1`, `BR-JOIN-4`, `BR-PRIV-1`.

- [x] добавить сетевой слой (Retrofit + сериализация) и хранение токена
- [x] сделать экран входа и регистрации
- [x] сделать экран «вступить в сообщество» («о себе» + фото)
- [x] подключить реальные вызовы вместо демо-данных на этих экранах
- [x] написать модульные тесты ViewModel входа и заявки (успех и ошибки)
- [x] запустить тесты — зелёные до перехода к следующей задаче

**Результат:** Retrofit 3 + OkHttp 5 + kotlinx-serialization, `BuildConfig.BASE_URL`
(`debug` → `http://10.0.2.2:8080/`, `release` → `https://mayakspace.com/`),
cleartext-трафик разрешён только в debug-манифесте. Слой данных: `Dto.kt`,
`PeerflowApi`, `TokenStore`, `AuthInterceptor`, обёртка `ApiCall` с
`ApiException`/`NetworkException`, Hilt-модули `NetworkModule`/`RepositoryModule`
и интерфейсы репозиториев. Экраны: `LoginScreen`, `RegisterScreen`
(с `AvatarPicker` и `CredentialRules`), `JoinCommunityScreen`; навигация в
`MainActivity`, единая точка входа — `SessionViewModel`.
Тесты: `LoginViewModelTest` — 11, `RegisterViewModelTest` — 11,
`JoinRequestViewModelTest` — 13, `SessionViewModelTest` — 12, `DemoDataTest` — 7;
итого 54 теста, зелёные. `:app:assembleDebug` — `BUILD SUCCESSFUL`.

**Итог фазы 1:** бэкенд — 61 тест, Android — 54 теста, все зелёные.

**Статус плана:** фаза 1 закрыта (`[x]` во всех задачах 4–7). Файл остаётся
в `docs/plans/`, пока не сделаны фазы 2–7: перенос в `docs/plans/completed/`
выполняется в Task 24 вместе с итоговым обновлением документов.

**Правки после верификации фазы 1 (20.11.2026):**

- [+] `ApiExceptionHandler`: добавлен `@ExceptionHandler(HttpMessageNotReadableException.class)` → `400 «Некорректный запрос»`. Без него общий обработчик `IOException` (запись аватара) перехватывал ошибку разбора тела по *причине* (`JsonParseException extends IOException`), и битый JSON на `login`/`votes` отдавал `500 «Не удалось сохранить файл»`. Тесты: `AuthControllerTest.rejectsMalformedJsonBody`, `JoinRequestControllerTest.rejectsMalformedVoteBody`.
- Исправлены расхождения документов с кодом: `UserStatus { ACTIVE, DELETED }` (а не `PENDING/MEMBER`), таблица `join_vote` (а не `join_request_vote`), состав `community`/`join_request`, ссылки `BR-JOIN-1`/`BR-JOIN-3` в `MainActivity.kt`.

### Решения по ходу фазы 1

- **Порог приёма считается от действующих членов.** `requiredAccepts =
  max(1, ceil(activeMembers × 0.30))`, расчёт через `BigDecimal` и
  `RoundingMode.CEILING` — без ошибок округления на границах (9 × 0.30 = 2.7 → 3).
- **Досрочное отклонение заявки.** Если порог уже недостижим при текущем числе
  участников и оставшихся голосующих, заявка сразу переводится в `REJECTED`,
  чтобы не оставлять её «висеть».
- **Голоса — часть агрегата заявки** (`JoinRequest` → `Set<JoinVote>`), а не
  отдельный агрегат: приём решения всегда происходит в транзакции заявки.
- **Идемпотентность на уровне БД.** Частичный уникальный индекс
  `uk_join_request_pending_applicant WHERE status = 'PENDING'` не даёт завести
  вторую активную заявку; `uk_join_vote_request_member` — второй голос от того
  же члена.
- **`GET /api/join-requests/mine` отдаёт `204`**, если активной заявки нет:
  клиенту это позволяет отличить «нет заявки» от ошибки сети.
- **Первый зарегистрировавшийся сразу получает сообщество** (`UserStatus.ACTIVE`
  + `community`), остальные — аккаунт без членства. Проверка
  членства (`MembershipRequiredException`) отделена от проверки аутентификации:
  `401` и `403` различаются явно.
- **Ошибки — в `ProblemDetail`** через единый `ApiExceptionHandler`, отдельно
  переопределены `authenticationEntryPoint` и `accessDeniedHandler`.
- **Аватар хранится на диске**, в БД — только относительный путь:
  `peerflow.storage.root` (по умолчанию `storage`), подкаталог `avatars/`,
  имя — `UUID.<ext>`, допустимы `png/jpg/jpeg/webp`, размер ≤ 5 МБ.
- **Сетевой стек Android:** Retrofit 3 + OkHttp 5 + kotlinx-serialization вместо
  Gson — типы DTO строгие, лишние поля не ломают разбор.
- **`BASE_URL` в `BuildConfig`:** `debug` → `http://10.0.2.2:8080/` (эмулятор →
  хост), `release` → `https://mayakspace.com/`. Cleartext разрешён только в
  debug-манифесте, в release — обычный HTTPS.
- **Репозитории как интерфейсы + Hilt-модули.** ViewModel зависит от
  интерфейса, поэтому модульные тесты используют фейки без MockK.
- **`CredentialRules` — зеркало серверной валидации** (длины и шаблон логина):
  клиент показывает ошибку до запроса, сервер остаётся источником правды.
- **Валидация по `trim()`.** Проверяется и отправляется одна и та же строка —
  иначе «логин из пробелов» проходил проверку длины, но падал на сервере.
- **`ApiCall` + `ApiException`/`NetworkException`** — единое место разбора
  `ProblemDetail` и сетевых сбоев, ViewModel не работает с `Response<T>`.
- **`SessionViewModel` — единая точка входа**: при старте проверяет наличие
  токена и вызывает `GET /api/auth/me`, выбирая между входом, заявкой и лентой.
- **Состояния и события.** `MutableStateFlow` + иммутабельный `UiState`,
  одноразовые события — `Channel(BUFFERED).receiveAsFlow()`.
- **Тесты ViewModel:** `MainDispatcherRule` (Unconfined), фейки с «гейтами» на
  `CompletableDeferred`, для событий — `async { events.first() }` до действия
  и `await()` после. Вариант с `backgroundScope.launch { toList() }` оказался
  гонкой: подписка не успевала стартовать.

### Фаза 2. Предложения и голосование

### Task 8: Сущность предложения и создание

**Files:**
- Create: `backend/src/main/java/ru/peerflow/proposal/domain/Proposal.java`
- Create: `backend/src/main/java/ru/peerflow/proposal/service/ProposalService.java`
- Create: `backend/src/main/java/ru/peerflow/proposal/web/ProposalController.java`
- Create: `backend/src/main/resources/db/migration/V4__proposal.sql`
- Create: `backend/src/test/java/ru/peerflow/proposal/ProposalServiceTest.java`

> **Требуется навык spring-data-jpa.** Перед написанием кода сущностей: активировать навык и следовать его правилам. Любое отклонение — сначала спросить разработчика.

**Требования:** `BR-LIFE-1`, `BR-BUDGET-1`, `BR-BUDGET-2`, `BR-OVER-2`.

- [ ] описать сущность `Proposal` со стадиями `DISCUSSION/IN_WORK/CLOSED/REJECTED`
- [ ] создать миграцию `V4__proposal.sql`
- [ ] реализовать создание предложения с обязательным бюджетом
- [ ] написать тесты: создание с бюджетом, отказ без бюджета, границы значений
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 9: Список предложений, сортировки и секции

**Files:**
- Modify: `backend/src/main/java/ru/peerflow/proposal/service/ProposalService.java`
- Create: `backend/src/main/java/ru/peerflow/proposal/web/dto/...`
- Create: `backend/src/test/java/ru/peerflow/proposal/ProposalRankingTest.java`

**Требования:** `BR-RANK-1`, `BR-RANK-2`, `BR-RANK-3`, `BR-RANK-4`, `BR-RANK-5`.

- [ ] реализовать выдачу списка с секциями «В работе» / «На обсуждении»
- [ ] реализовать сортировки: по популярности, по бюджету, по новизне
- [ ] реализовать метрику популярности `участники + (за − против)`
- [ ] написать тесты: объём сообщений НЕ поднимает предложение; шум оседает вниз
- [ ] написать тесты сортировок по бюджету и новизне
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 10: Голосование «делаем / не делаем»

**Files:**
- Create: `backend/src/main/java/ru/peerflow/proposal/domain/DecisionVote.java`
- Modify: `backend/src/main/java/ru/peerflow/proposal/service/ProposalService.java`
- Create: `backend/src/main/resources/db/migration/V5__decision_vote.sql`
- Create: `backend/src/test/java/ru/peerflow/proposal/DecisionVoteTest.java`

> **Требуется навык spring-data-jpa.** Перед написанием кода сущностей: активировать навык и следовать его правилам. Любое отклонение — сначала спросить разработчика.

**Требования:** `BR-LIFE-4`, `BR-BUDGET-3`, `BR-VOTE-4`, `BR-VOTE-7`, `BR-RISK-1`.

- [ ] описать сущность голоса и уникальный индекс `(proposal_id, voter_id)`
- [ ] реализовать голосование «за / против» с идемпотентностью
- [ ] при положительном решении переводить предложение в «В работе»
- [ ] при отрицательном — в `REJECTED`
- [ ] написать тесты: повторное нажатие не создаёт дубль; смена голоса работает
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 11: Android — список и создание предложения из сети

**Files:**
- Modify: `android/.../ui/screens/ProposalListScreen.kt` (источник данных)
- Modify: `android/.../ui/screens/NewProposalScreen.kt` (отправка на сервер)
- Create: `android/.../feature/proposal/ProposalViewModel.kt`

**Требования:** `BR-OVER-2`, `BR-LIFE-1`, `BR-RANK-1`–`BR-RANK-5`, `BR-VOTE-4`.

- [ ] заменить `DemoData` в списке на данные из API (секции и сортировки)
- [ ] подключить создание предложения к серверу
- [ ] показать голосование «делаем / не делаем» из реальных голосов
- [ ] написать тесты ViewModel (загрузка, сортировки, ошибки сети)
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Фаза 3. Дискуссия и реакции

### Task 12: Сообщения, ответы и реакции

**Files:**
- Create: `backend/src/main/java/ru/peerflow/discussion/domain/Message.java`
- Create: `backend/src/main/java/ru/peerflow/discussion/domain/Reaction.java`
- Create: `backend/src/main/java/ru/peerflow/discussion/service/DiscussionService.java`
- Create: `backend/src/main/resources/db/migration/V6__discussion.sql`
- Create: `backend/src/test/java/ru/peerflow/discussion/DiscussionServiceTest.java`

> **Требуется навык spring-data-jpa.** Перед написанием кода сущностей: активировать навык и следовать его правилам. Любое отклонение — сначала спросить разработчика.

**Требования:** `BR-DISC-1`, `BR-DISC-2`, `BR-DISC-3`, `BR-VOTE-6`, `BR-PRIV-1`.

- [ ] описать сущности сообщения (с ответами) и реакции; миграция `V6__discussion.sql`
- [ ] реализовать создание сообщения и ответа на сообщение
- [ ] реализовать реакции `+ / − / спам` с уникальностью на пользователя
- [ ] написать тесты: ответ вкладывается; реакция одна на пользователя, меняется
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 13: Ранжирование сообщений и скрытие спама

**Files:**
- Modify: `backend/src/main/java/ru/peerflow/discussion/service/DiscussionService.java`
- Create: `backend/src/test/java/ru/peerflow/discussion/MessageRankingTest.java`

**Требования:** `BR-DISC-4`, `BR-DISC-5`, `BR-DISC-6`, `BR-SPAM-1`, `BR-LIMIT-2`.

- [ ] реализовать сортировки сообщений «Популярные» / «Новые»
- [ ] реализовать скрытие при 3 и более метках «спам» (без удаления)
- [ ] решить вопрос производительности: считать на лету или денормализовать score
- [ ] написать тесты: «+» поднимает, «−» понижает при равенстве, 3 метки скрывают
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 14: Android — реальная дискуссия

**Files:**
- Modify: `android/.../ui/screens/ProposalDetailScreen.kt`
- Create: `android/.../feature/discussion/DiscussionViewModel.kt`

**Требования:** `BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1`.

- [ ] подключить загрузку сообщений и ответов из API
- [ ] подключить отправку сообщений и постановку реакций
- [ ] показать сортировки «Популярные / Новые» и скрытый спам
- [ ] написать тесты ViewModel (загрузка, отправка, реакция, ошибка)
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Фаза 4. Легитимность и выбор лидера

### Task 15: Append-only журнал легитимности

**Files:**
- Create: `backend/src/main/java/ru/peerflow/legitimacy/domain/LegitimacyEvent.java`
- Create: `backend/src/main/java/ru/peerflow/legitimacy/service/LegitimacyService.java`
- Create: `backend/src/main/resources/db/migration/V7__legitimacy.sql`
- Create: `backend/src/test/java/ru/peerflow/legitimacy/LegitimacyServiceTest.java`

> **Требуется навык spring-data-jpa.** Перед написанием кода сущностей: активировать навык и следовать его правилам. Любое отклонение — сначала спросить разработчика.

**Требования:** `BR-LEGIT-1`, `BR-LEGIT-2`, `BR-LEGIT-3`, `BR-LEGIT-5`.

- [ ] описать события `DELEGATE` / `REVOKE` (только добавление); миграция `V7__legitimacy.sql`
- [ ] реализовать передачу и отзыв легитимности по конкретной задаче
- [ ] реализовать расчёт текущего агрегата (кто кому передал) детерминированно
- [ ] написать тесты: передача, отзыв, независимость по разным задачам
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 16: Кандидаты, выбор лидера и утрата мандата

**Files:**
- Create: `backend/src/main/java/ru/peerflow/legitimacy/domain/Candidate.java`
- Modify: `backend/src/main/java/ru/peerflow/legitimacy/service/LegitimacyService.java`
- Create: `backend/src/main/resources/db/migration/V8__candidate.sql`
- Create: `backend/src/test/java/ru/peerflow/legitimacy/LeaderElectionTest.java`

> **Требуется навык spring-data-jpa.** Перед написанием кода сущностей: активировать навык и следовать его правилам. Любое отклонение — сначала спросить разработчика.

**Требования:** `BR-LEGIT-4`, `BR-LEGIT-6`, `BR-LEADER-1`–`BR-LEADER-8`, `BR-LIFE-5`, `BR-LIFE-10`, `BR-LIMIT-1`, `BR-REP-4`.

- [ ] реализовать выдвижение кандидатов и передачу им легитимности
- [ ] реализовать определение лидера: больше легитимности → репутация → дата вступления
- [ ] реализовать порог удержания: ниже 70% — мандат утрачен
- [ ] при утрате мандата возвращать задачу на переголосование (по бизнес-описанию)
- [ ] написать тесты: тай-брейкеры; падение ниже 70%; падение ровно до 70% держит мандат
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 17: Android — лидер и отзыв легитимности из сети

**Files:**
- Modify: `android/.../ui/screens/ProposalDetailScreen.kt` (карточка лидера)
- Modify: `android/.../ui/screens/ProfileScreen.kt`
- Create: `android/.../feature/legitimacy/LegitimacyViewModel.kt`

**Требования:** `BR-LEGIT-2`, `BR-LEGIT-3`, `BR-LEGIT-5`, `BR-LEADER-1`, `BR-LEADER-5`, `BR-REP-2`.

- [ ] показать лидера, остаток доверия и шкалу из реальных данных
- [ ] подключить кнопку «Забрать свою легитимность» к серверу
- [ ] показать в профиле, кому и по каким задачам передана легитимность
- [ ] написать тесты ViewModel (передача, отзыв, ошибка)
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Фаза 5. Отчёты, файлы и закрытие

### Task 18: Отчёты лидера и вложения

**Files:**
- Create: `backend/src/main/java/ru/peerflow/report/domain/Report.java`, `Attachment.java`
- Create: `backend/src/main/java/ru/peerflow/report/service/ReportService.java`
- Create: `backend/src/main/java/ru/peerflow/report/web/ReportController.java`
- Create: `backend/src/main/resources/db/migration/V9__report.sql`
- Create: `backend/src/test/java/ru/peerflow/report/ReportServiceTest.java`

> **Требуется навык spring-data-jpa.** Перед написанием кода сущностей: активировать навык и следовать его правилам. Любое отклонение — сначала спросить разработчика.

**Требования:** `BR-OVER-7`, `BR-LIFE-7`, `BR-BUDGET-4`, `BR-BUDGET-5`.

- [ ] описать отчёт и вложение (метаданные в БД); миграция `V9__report.sql`
- [ ] реализовать хранение файла на диске (`peerflow.storage.root`) с проверкой типа и размера
- [ ] отчёт = любое сообщение или файл под задачей (отдельного формата нет)
- [ ] реализовать дискуссию под отчётами (переиспользовать механизм сообщений)
- [ ] написать тесты: загрузка файла, недопустимый тип/размер, отчёт с текстом
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 19: Закрытие задачи и переход легитимности в репутацию

**Files:**
- Create: `backend/src/main/java/ru/peerflow/reputation/domain/ReputationEntry.java`
- Create: `backend/src/main/java/ru/peerflow/reputation/service/ReputationService.java`
- Create: `backend/src/main/java/ru/peerflow/reputation/web/ClosureController.java`
- Create: `backend/src/main/resources/db/migration/V10__closure_reputation.sql`
- Create: `backend/src/test/java/ru/peerflow/reputation/ClosureServiceTest.java`

> **Требуется навык spring-data-jpa.** Перед написанием кода сущностей: активировать навык и следовать его правилам. Любое отклонение — сначала спросить разработчика.

**Требования:** `BR-LIFE-8`, `BR-LIFE-9`, `BR-VOTE-5`, `BR-REP-1`, `BR-REP-3`, `BR-OVER-9`.

- [ ] описать голос «выполнено / не выполнено» и append-only начисления репутации
- [ ] лидер запускает закрытие; сообщество голосует
- [ ] при закрытии вся оставшаяся легитимность лидера переходит в репутацию
- [ ] закрывать задачу по решению сообщества (стадия `CLOSED`)
- [ ] написать тесты: переход легитимности в репутацию; суммы сходятся; повторное закрытие запрещено
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 20: Android — отчёты и закрытие из сети

**Files:**
- Modify: `android/.../ui/screens/LeaderReportScreen.kt`
- Modify: `android/.../ui/screens/CloseTaskScreen.kt`
- Create: `android/.../feature/report/ReportViewModel.kt`

**Требования:** `BR-LIFE-7`, `BR-LIFE-8`, `BR-LIFE-9`, `BR-VOTE-5`, `BR-REP-1`, `BR-REP-2`.

- [ ] подключить отчёты и загрузку вложения к серверу
- [ ] подключить экран закрытия к реальному голосованию
- [ ] показать переход легитимность → репутация после закрытия
- [ ] написать тесты ViewModel (загрузка файла, голосование, ошибки)
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Фаза 6. Realtime

### Task 21: SSE-канал для «горячих» поверхностей

**Files:**
- Create: `backend/src/main/java/ru/peerflow/realtime/EventController.java`
- Create: `backend/src/main/java/ru/peerflow/realtime/EventPublisher.java`
- Create: `backend/src/test/java/ru/peerflow/realtime/EventStreamTest.java`

**Требования:** `BR-DISC-1` (живая лента), `BR-VOTE-4` (живые счётчики), `BR-LIFE-7` (новый отчёт).

- [ ] реализовать SSE-поток `/api/events` c событиями по задачам
- [ ] публиковать события: новое сообщение, изменение счётчиков голосов, новый отчёт
- [ ] не расширять на всё приложение — только «горячие» поверхности
- [ ] написать тесты: подписка, получение события, отключение клиента
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Task 22: Android — живое обновление ленты

**Files:**
- Modify: `android/.../feature/discussion/DiscussionViewModel.kt`
- Create: `android/.../data/api/EventSource.kt` (потребитель SSE)

**Требования:** `BR-DISC-1`, `BR-DISC-5`, `BR-VOTE-4`.

- [ ] подключить SSE-клиент, обновлять ленту и счётчики без перезагрузки
- [ ] корректно закрывать поток при уходе с экрана
- [ ] написать тесты на обработку входящих событий (обновление состояния)
- [ ] запустить тесты — зелёные до перехода к следующей задаче

### Фаза 7. Проверка и документация

### Task 23: Проверка критериев приёмки

**Требования:** все `BR-…` из `docs/peerflow.md` (сводная проверка покрытия).

- [ ] проверить, что покрыты все требования из Overview и бизнес-описания (`docs/peerflow.md`, все `BR-…`)
- [ ] проверить граничные случаи: пороги, тай-брейкеры, идемпотентность голосов
- [ ] прогнать полный набор тестов бэкенда: `cd backend; .\gradlew.bat test`
- [ ] прогнать полный набор тестов Android: `cd android; .\gradlew.bat :app:testDebugUnitTest`
- [ ] проверить, что реальные сценарии бизнес-описания проходятся end-to-end вручную

### Task 24: Обновить документацию и закрыть план

- [ ] обновить `docs/architecture.md`: фактическая структура, слои, трассировка «требование → код»
- [ ] обновить корневой `README.md` (карта документов, фактическая структура)
- [ ] обновить `backend/README.md` (запуск, конфигурация, хранение файлов)
- [ ] обновить `android/README.md` (подключение к бэкенду, базовый URL)
- [ ] зафиксировать в `docs/peerflow.md` расхождения, если появятся по ходу
- [ ] перенести этот план в `docs/plans/completed/`

## Post-Completion

*Пункты, требующие ручного вмешательства или внешних систем — без чекбоксов.*

**Ручная проверка:**
- Прогнать вживую сценарий сообщества от регистрации до закрытия задачи на
  нескольких устройствах или аккаунтах.
- Проверить на слабом канале: поведение SSE при обрыве связи, повторное
  подключение.
- Проверить поведение при одновременных голосах, чтобы убедиться в
  идемпотентности.

**Внешние системы и окружение:**
- Понадобится доступное окружение с PostgreSQL: локально через `docker-compose`,
  на сервере — по факту развёртывания.
- Каталог для вложений (`peerflow.storage.root`) должен существовать, быть
  доступным для записи и включён в резервное копирование.
- Секреты (пароль БД, ключ подписи JWT) — через переменные окружения, не в коде.

**Осознанно вне рамок MVP (не делать сейчас):**
- Реальные деньги, эскроу, платёжные интеграции.
- Push-уведомления.
- Деплой и CI/CD.
- Защита от сговора массовыми метками «спам» (известный вектор, ждём реального кейса).
- Минимальный кворум при голосовании (осознанное упрощение MVP).

# PeerFlow — устройство системы

Обновлено: 20.11.2026

Этот документ описывает, **как система устроена сейчас**. Не «что нужно
сделать» (это в [`plans/`](plans/)), а «как оно есть на самом деле». Здесь
технический специалист и агент смотрят, где в коде живёт то или иное правило из
[бизнес-описания](peerflow.md).

**Правило ведения.** После каждой реализации задача не считается закрытой, пока
этот документ не приведён в соответствие: если код изменился — меняем и здесь.
Ссылаемся на ID требований (`BR-…`) из [`peerflow.md`](peerflow.md).

---

## 1. Состояние на сегодня

| Часть | Состояние |
|---|---|
| Мобильное приложение | Два слоя в одном модуле (`android/`): рабочие экраны входа, регистрации и вступления в сообщество на реальном API; плюс демо-прототип на выдуманных данных для показа сообществу |
| Сервер | Spring Boot в `backend/`: регистрация, вход, JWT-аутентификация, профиль, заявки на вступление с голосованием членов. Домены `auth` и `community` наполнены |
| База данных | PostgreSQL (локальная установка). Flyway накатывает `V1` (база схемы), `V2` (`app_user`, `community`), `V3` (`join_request`, `join_vote`) |
| Realtime | Ещё нет |
| Тесты | Бэкенд — 61 тест на реальном PostgreSQL в контейнере; клиент — 54 модульных теста ViewModel и демо-данных |
| Развёртывание | Тестовый сервер `https://demiapp.ru:8443/` (Docker, отдельный порт); файлы в `backend/Dockerfile`, `backend/compose.prod.yaml`, `backend/deploy/` |

> Фаза 1 MVP сделана: человек может зарегистрироваться, войти и подать заявку
> на вступление в сообщество, а действующие члены — проголосовать за неё.
> Порог вступления считается на сервере. Остальные домены (предложения,
> дискуссии, легитимность, отчёты, репутация) пока живут только в
> демо-прототипе.

---

## 2. Общая архитектура

Монорепозиторий из двух частей:

- **Клиент** (`android/`) — Android, Kotlin + Jetpack
  Compose. Общается с сервером по сети.
- **Сервер** (`backend/`) — Java 21 + Spring Boot 3.5. Хранит состояние,
  считает правила. Реализованы домены `auth` (вход, токены) и `community`
  (члены, заявки на вступление, голоса); остальные домены — впереди.

```
Андроид-приложение  ──HTTP/JSON──►  Сервер (Spring Boot)  ──►  PostgreSQL
        ▲                                   │
        └──────────real-time события────────┘
```

Правило: **бизнес-правила считает сервер**, клиент их не дублирует. Иначе разные
платформы разойдутся в трактовке.

---

## 3. Клиент (Android)

### Рабочий слой (подключён к серверу)

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`…/data/api/PeerflowApi.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/PeerflowApi.kt) | Описание HTTP-API Retrofit: регистрация, вход, профиль, заявка, голос | `BR-JOIN-*`, `BR-PRIV-1` |
| [`…/data/api/Dto.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/Dto.kt) | Транспортные DTO под JSON сервера | — |
| [`…/data/api/ApiCall.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/ApiCall.kt) | Единая обёртка вызовов: перевод кодов и тела ошибки в `ApiException` / `NetworkException` | — |
| [`…/data/api/TokenStore.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/TokenStore.kt) | Хранение JWT между запусками | `BR-JOIN-1` |
| [`…/data/api/AuthInterceptor.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/AuthInterceptor.kt) | Подстановка `Authorization: Bearer` в каждый запрос | `BR-PRIV-1` |
| [`…/data/repo/Repositories.kt`](../android/app/src/main/java/ru/peerflow/demo/data/repo/Repositories.kt) | Интерфейсы и реализации репозиториев аккаунта и заявки | `BR-JOIN-2`–`BR-JOIN-5` |
| [`…/di/NetworkModule.kt`](../android/app/src/main/java/ru/peerflow/demo/di/NetworkModule.kt), [`…/di/RepositoryModule.kt`](../android/app/src/main/java/ru/peerflow/demo/di/RepositoryModule.kt) | Hilt: клиент HTTP, JSON, репозитории | — |
| [`…/ui/session/SessionViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/session/SessionViewModel.kt) | Единая точка входа: есть токен → профиль, 401 → выход, нет сети → сообщение | `BR-JOIN-1`, `BR-PRIV-1` |
| [`…/ui/auth/LoginScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/LoginScreen.kt), [`LoginViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/LoginViewModel.kt) | Вход по логину и паролю | `BR-JOIN-1` |
| [`…/ui/auth/RegisterScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/RegisterScreen.kt), [`RegisterViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/RegisterViewModel.kt) | Регистрация: логин, пароль, имя, о себе, аватар | `BR-JOIN-1` |
| [`…/ui/auth/CredentialRules.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/CredentialRules.kt) | Правила ввода на клиенте — зеркало серверной валидации, чтобы ошибку было видно до отправки | `BR-JOIN-1` |
| [`…/ui/join/JoinCommunityScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/join/JoinCommunityScreen.kt), [`JoinRequestViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/join/JoinRequestViewModel.kt) | Заявка на вступление и её состояние: на рассмотрении, принята, отклонена | `BR-JOIN-2`–`BR-JOIN-5` |
| [`…/ui/components/AvatarPicker.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/components/AvatarPicker.kt) | Выбор аватара из галереи, отдаёт уже прочитанные байты | `BR-JOIN-1` |
| [`…/MainActivity.kt`](../android/app/src/main/java/ru/peerflow/demo/MainActivity.kt) | Навигация: проверка сессии → вход/регистрация → вступление → приложение | — |

`BASE_URL` приходит из `BuildConfig` (поле `baseUrl` в
[`android/app/build.gradle.kts`](../android/app/build.gradle.kts)); HTTP без TLS
разрешён только в debug-сборке через
[`android/app/src/debug/AndroidManifest.xml`](../android/app/src/debug/AndroidManifest.xml).

### Демо-прототип (без сети)

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| `android/…/data/DemoData.kt` | Все данные и модель в памяти | Заглушка вместо сервера |
| `android/…/ui/screens/PainMessengerScreen.kt` | Экран «боль»: мок мессенджера | Иллюстрация `BR-PROB-1`–`BR-PROB-4` |
| `android/…/ui/screens/ProposalListScreen.kt` | Список предложений, секции и сортировки | `BR-RANK-1`–`BR-RANK-5` |
| `android/…/ui/screens/NewProposalScreen.kt` | Внесение предложения с бюджетом | `BR-LIFE-1`, `BR-BUDGET-2` |
| `android/…/ui/screens/ProposalDetailScreen.kt` | Обсуждение, карточка лидера и отзыв легитимности | `BR-DISC-*`, `BR-LEGIT-5` |
| `android/…/ui/screens/LeaderReportScreen.kt` | Отчёт лидера | `BR-LIFE-7` |
| `android/…/ui/screens/CloseTaskScreen.kt` | Закрытие задачи, переход легитимности в репутацию | `BR-LIFE-9`, `BR-REP-1` |
| `android/…/ui/screens/ProfileScreen.kt` | Профиль: репутация и переданная легитимность | `BR-REP-2`, `BR-LEGIT-3` |

### Соглашения клиента

- ViewModel зависит от **интерфейсов** репозиториев, а не от Retrofit: тесты
  подставляют фейки, сеть в тестах не поднимается.
- Состояние экрана — один `UiState` (`Loading` / `Form` / результат / `Failure`),
  одноразовые события (например «стал членом») — через `Channel` и
  `receiveAsFlow()`, чтобы не срабатывали повторно при пересоздании экрана.
- Валидация проверяет **то же значение**, что уйдёт на сервер: пробелы по краям
  обрезаются до проверки длины.
- `CancellationException` пробрасывается первой, иначе отмена запроса выглядит
  как ошибка пользователю.

### Дальше на клиенте

Остальные домены (предложения, дискуссии, легитимность, отчёты, репутация) пока
работают на демо-данных; раздел будет пополняться по мере их реализации.

---

## 4. Сервер

### Общая часть

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`backend/build.gradle.kts`](../backend/build.gradle.kts) | Java 21, Spring Boot 3.5.16, Gradle 9.8.2 (wrapper) | Инфраструктура |
| [`backend/…/PeerflowApplication.java`](../backend/src/main/java/ru/peerflow/PeerflowApplication.java) | Точка входа, `@ConfigurationPropertiesScan` | — |
| [`backend/…/config/PeerflowThresholds.java`](../backend/src/main/java/ru/peerflow/config/PeerflowThresholds.java) | Типизированные пороги MVP с проверкой границ | `BR-LIMIT-1`, `BR-LIMIT-2`, `BR-LIMIT-3` |
| [`backend/…/config/SecurityConfig.java`](../backend/src/main/java/ru/peerflow/config/SecurityConfig.java) | Безопасность: `/api/auth/*` и health открыты, остальное требует JWT, сессий нет | `BR-PRIV-1` |
| [`backend/…/config/JwtProperties.java`](../backend/src/main/java/ru/peerflow/config/JwtProperties.java), [`JwtConfig.java`](../backend/src/main/java/ru/peerflow/config/JwtConfig.java) | Настройки и бин разбора JWT (HS256) | `BR-JOIN-1` |
| [`backend/…/web/ApiExceptionHandler.java`](../backend/src/main/java/ru/peerflow/web/ApiExceptionHandler.java) | Перевод доменных исключений в `ProblemDetail` с русским текстом | — |
| [`backend/src/main/resources/application.yml`](../backend/src/main/resources/application.yml) | Рабочие умолчания БД для локальной разработки, JPA `validate`, Flyway, Actuator, секрет JWT | — |
| [`backend/src/main/resources/application-local.yml.example`](../backend/src/main/resources/application-local.yml.example) | Шаблон локального переопределения настроек БД | — |
| [`backend/…/support/PostgresTestBase.java`](../backend/src/test/java/ru/peerflow/support/PostgresTestBase.java) | База интеграционных тестов: настоящий PostgreSQL в контейнере, настройки подставляет `@ServiceConnection` | — |
| `backend/README.md` | Как настроить локальный PostgreSQL и запустить сервер | — |

### Домен `auth`

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`…/auth/service/AuthService.java`](../backend/src/main/java/ru/peerflow/auth/service/AuthService.java) | Регистрация и вход: проверка занятости логина, сверка пароля, выпуск токена | `BR-JOIN-1` |
| [`…/auth/service/TokenService.java`](../backend/src/main/java/ru/peerflow/auth/service/TokenService.java) | Подпись и разбор JWT (HS256), срок жизни токена | `BR-JOIN-1` |
| [`…/auth/web/AuthController.java`](../backend/src/main/java/ru/peerflow/auth/web/AuthController.java) | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` | `BR-JOIN-1` |
| [`…/auth/web/dto/`](../backend/src/main/java/ru/peerflow/auth/web/dto) | Запросы регистрации и входа, ответ с токеном и профилем | `BR-JOIN-1` |
| [`…/community/service/AvatarStorage.java`](../backend/src/main/java/ru/peerflow/community/service/AvatarStorage.java) | Сохранение файла аватара на диск, в БД — только путь | `BR-PRIV-2` |

### Домен `community`

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`…/community/domain/Community.java`](../backend/src/main/java/ru/peerflow/community/domain/Community.java) | Сообщество (в MVP одно) | `BR-JOIN-2` |
| [`…/community/domain/User.java`](../backend/src/main/java/ru/peerflow/community/domain/User.java), [`UserStatus.java`](../backend/src/main/java/ru/peerflow/community/domain/UserStatus.java) | Аккаунт, профиль, статус членства | `BR-JOIN-1`, `BR-PRIV-2` |
| [`…/community/domain/JoinRequest.java`](../backend/src/main/java/ru/peerflow/community/domain/JoinRequest.java), [`JoinVote.java`](../backend/src/main/java/ru/peerflow/community/domain/JoinVote.java), [`JoinRequestStatus.java`](../backend/src/main/java/ru/peerflow/community/domain/JoinRequestStatus.java) | Агрегат заявки: сама заявка и её голоса | `BR-JOIN-4`, `BR-JOIN-5`, `BR-VOTE-8` |
| [`…/community/service/JoinRequestService.java`](../backend/src/main/java/ru/peerflow/community/service/JoinRequestService.java) | Подача заявки, голосование, подсчёт порога, досрочное отклонение | `BR-JOIN-3`–`BR-JOIN-5`, `BR-LIMIT-3` |
| [`…/community/web/JoinRequestController.java`](../backend/src/main/java/ru/peerflow/community/web/JoinRequestController.java) | `POST /api/join-requests`, `GET /api/join-requests/mine`, `POST /api/join-requests/{id}/votes` | `BR-JOIN-2`–`BR-JOIN-5` |
| [`…/community/repo/JoinRequestRepository.java`](../backend/src/main/java/ru/peerflow/community/repo/JoinRequestRepository.java), [`UserRepository.java`](../backend/src/main/java/ru/peerflow/community/repo/UserRepository.java), [`CommunityRepository.java`](../backend/src/main/java/ru/peerflow/community/repo/CommunityRepository.java) | Доступ к данным, подсчёт действующих членов, поиск ожидающей заявки | `BR-JOIN-4` |

### Правило порога вступления

Порог считает только сервер:
`requiredAccepts = max(1, ceil(активныеЧлены × joinAcceptRatio))`, где
`joinAcceptRatio` по умолчанию `0.30` (`BR-LIMIT-3`). Деление — `BigDecimal` с
`RoundingMode.CEILING`, чтобы дробный порог не «терял» голос: при 4 членах нужно
2 принятия, а не 1. Если оставшихся членов уже не хватает, чтобы порог набрать,
заявка отклоняется досрочно — человек не ждёт впустую.

Голоса идемпотентны: частичный уникальный индекс
`uk_join_request_pending_applicant WHERE status = 'PENDING'` не даёт завести
вторую ожидающую заявку, а пара «заявка + голосующий» — проголосовать дважды.

Подключение к БД: **локально установленный PostgreSQL**. В `application.yml`
прописаны рабочие умолчания для локальной разработки —
`jdbc:postgresql://localhost:5432/peerflow`, пользователь `usr`. Они
переопределяются переменными окружения (`PEERFLOW_DB_URL`,
`PEERFLOW_DB_USERNAME`, `PEERFLOW_DB_PASSWORD`) или файлом
`application-local.yml` (в `.gitignore`, шаблон —
`application-local.yml.example`). Схему создаёт и меняет только Flyway,
Hibernate её сверяет (`ddl-auto=validate`). Если PostgreSQL недоступен,
сервер не стартует (fail-fast) — это осознанное поведение, оно описано в
[`backend/README.md`](../backend/README.md).

Структура пакетов — по доменам, слои внутри домена:

- **Существующие домены:** `auth` (вход и токены), `community` (члены,
  заявки, голоса).
- **Запланированные домены:** `proposal`, `discussion`, `legitimacy`,
  `report`, `reputation`, `realtime`; общая конфигурация — `config`,
  сквозные HTTP-ошибки — `web`.
- **Слои внутри домена:** `web` (HTTP + DTO), `service` (правила), `domain`
  (сущности и перечисления), `repo` (доступ к данным).
- **Миграции БД:** Flyway, по одной на домен.

### Схема БД и миграции

Единственный источник схемы — Flyway: файлы в
`backend/src/main/resources/db/migration`, имена `V<номер>__<тема>.sql`.
Flyway накатывает неприменённое до старта контекста Spring и ведёт историю в
таблице `flyway_schema_history`; применённые файлы не редактируются (Flyway
сверяет контрольную сумму — изменение ломает старт). Hibernate схему не
создаёт: `spring.jpa.hibernate.ddl-auto=validate`, расхождение сущностей и
таблиц — ошибка старта, а не автоправка.

| Миграция | Что создаёт | Требования |
|---|---|---|
| `V1__baseline.sql` | Расширение `pgcrypto` → функция `gen_random_uuid()`: основа UUID-ключей всех доменных таблиц | `BR-PRIV-2` |
| `V2__user_community.sql` | `app_user` (логин, хеш пароля, имя, «о себе», путь к аватару, статус, сообщество) и `community` (название) | `BR-JOIN-1`, `BR-JOIN-2`, `BR-PRIV-2` |
| `V3__join_request.sql` | `join_request` (заявитель, «о себе», путь к аватару, статус, время решения) и `join_vote` (голос члена), частичный уникальный индекс ожидающей заявки | `BR-JOIN-4`, `BR-JOIN-5`, `BR-VOTE-8` |

Соглашения для последующих миграций:

- Первичные ключи — `uuid` со значением по умолчанию `gen_random_uuid()`.
- Время — `timestamptz`, приложение пишет в UTC (`hibernate.jdbc.time_zone=UTC`),
  в API — ISO-8601.
- Удаление аккаунта — не `DELETE`, а пометка статуса (`BR-PRIV-2`).


Ниже — заметки о решениях, которые уже приняты и должны соблюдаться.

### Модель данных (набросок)

Таблицы без пометки — уже существуют и накатаны миграциями; с пометкой
«план» — появятся вместе со своими доменами.

| Таблица | Назначение | Требования |
|---|---|---|
| `app_user` | Аккаунт, профиль, статус | `BR-JOIN-*`, `BR-PRIV-2` |
| `community` | Сообщество (в MVP одно) | `BR-JOIN-2` |
| `join_request`, `join_vote` | Заявки на вступление и голоса | `BR-JOIN-4`, `BR-JOIN-5`, `BR-VOTE-8` |
| `proposal` *(план)* | Предложение, стадия, бюджет | `BR-LIFE-1`, `BR-BUDGET-2` |
| `proposal_decision_vote` *(план)* | Голос «делаем / не делаем» | `BR-LIFE-4`, `BR-VOTE-4` |
| `candidate` *(план)* | Кандидаты в лидеры | `BR-LEADER-1` |
| `message` *(план)* | Сообщения дискуссии и ответы | `BR-DISC-1`, `BR-DISC-3` |
| `reaction` *(план)* | Реакции на сообщения | `BR-DISC-2`, `BR-VOTE-6` |
| `legitimacy_event` *(план)* | Журнал передачи и отзыва легитимности | `BR-LEGIT-2`, `BR-LEGIT-5` |
| `report`, `attachment` *(план)* | Отчёты и файлы | `BR-LIFE-7`, `BR-BUDGET-5` |
| `closure_vote` *(план)* | Голос «выполнено / нет» | `BR-LIFE-9`, `BR-VOTE-5` |
| `reputation_ledger` *(план)* | Начисления репутации | `BR-REP-1` |

### Принятые технические решения

- **Голоса идемпотентны:** уникальные ограничения не дают повторным нажатиям
  создавать дубли. Касается реакций, голосов по решению и по закрытию.
- **Легитимность — журнал только на добавление:** пишем события «передал /
  отозвал», текущее состояние считаем из них. Даёт аудит и воспроизводимость.
- **Пороги — константы конфигурации**, не «зашиты» в логику: `BR-LIMIT-1`,
  `BR-LIMIT-2`, `BR-LIMIT-3`.
- **Файлы отчётов** хранятся на диске сервера, в БД — только описание файла.
- **Realtime** — только для «горячих» поверхностей: лента дискуссии, счётчики
  голосов, уведомления.
- **Обработчики ошибок в `ApiExceptionHandler` точечные.** Spring подбирает
  `@ExceptionHandler` и по типу исключения, и по его *причине*
  (`ExceptionHandlerMethodResolver`). Поэтому общий обработчик
  `@ExceptionHandler(IOException.class)` (ошибки записи файла) перехватывал бы
  `HttpMessageNotReadableException` — у неё причина `JsonParseException`, тоже
  наследник `IOException` — и битый JSON отдавал бы `500`. От этого защищает
  отдельный обработчик `HttpMessageNotReadableException` → `400`, объявленный
  явно.

---

## 5. Трассировка: требование → код

Таблица-указатель: где в системе реализовано каждое требование. Заполняется по
мере реализации, чтобы в новой сессии быстро сориентироваться.

| Требование | Где реализовано | Тесты |
|---|---|---|
| `BR-JOIN-1` (регистрация и вход, профиль) | `AuthService`, `AuthController`, `TokenService`; клиент: `LoginViewModel`, `RegisterViewModel`, `SessionViewModel` | `AuthControllerTest`, `LoginViewModelTest`, `RegisterViewModelTest`, `SessionViewModelTest` |
| `BR-JOIN-2` (подача заявки на вступление) | `JoinRequestService.submit`, `POST /api/join-requests`; клиент: `JoinRequestViewModel` | `JoinRequestServiceTest`, `JoinRequestControllerTest`, `JoinRequestViewModelTest` |
| `BR-JOIN-4` (голосование действующих членов) | `JoinRequestService.vote`, `POST /api/join-requests/{id}/votes` | `JoinRequestServiceTest`, `JoinRequestControllerTest` |
| `BR-JOIN-5` (решение по заявке) | `JoinRequestService`: подсчёт порога и смена статуса | `JoinRequestServiceTest`, `JoinRequestControllerTest` |
| `BR-LIMIT-3` (приём при 30%) | `PeerflowThresholds.joinAcceptRatio` → `JoinRequestService` (`BigDecimal` + `CEILING`) | `PeerflowThresholdsTest`, `JoinRequestThresholdTest` |
| `BR-VOTE-2` (один человек — один голос) | уникальность пары «заявка + голосующий» в `join_vote` | `JoinRequestServiceTest`, `JoinRequestControllerTest` |
| `BR-VOTE-8` (голос по вступлению) | `JoinVote`, `JoinRequestService.vote` | `JoinRequestServiceTest`, `JoinRequestControllerTest` |
| `BR-RANK-2` (метрика популярности) | прототип: `DemoData.popularity` | `DemoDataTest` |
| `BR-SPAM-1` (скрытие при 3 метках) | пока нет; порог задан в `PeerflowThresholds.spamHideMarks` | `PeerflowThresholdsTest` |
| `BR-LIMIT-1` (порог удержания 70%) | `PeerflowThresholds.legitimacyRetainRatio` | `PeerflowThresholdsTest` |
| `BR-LIMIT-2` (3 метки спама) | `PeerflowThresholds.spamHideMarks` | `PeerflowThresholdsTest` |
| `BR-LEADER-5` (мандат утрачен ниже 70%) | пока нет; порог задан в `PeerflowThresholds` | `PeerflowThresholdsTest` |
| `BR-REP-1` (легитимность → репутация) | прототип: `CloseTaskScreen` (визуально) | — |
| `BR-PRIV-1` (контент только для членов) | `SecurityConfig`: открыты `/api/auth/register`, `/api/auth/login` и health, остальное требует JWT; клиент: `AuthInterceptor`, `SessionViewModel` | `AuthControllerTest`, `JoinRequestControllerTest`, `SessionViewModelTest` |
| `BR-PRIV-2` (удалённый аккаунт не стирается, а помечается) | схема и инварианты БД: миграции Flyway, соглашение «PK = uuid, удаление — пометкой» (`User.status`), аватары на диске | `FlywayMigrationTest`, `UserRepositoryTest` |

> Таблица неполная намеренно: она растёт вместе с системой. Если правило
> реализовано и его тут нет — это дефект документа, добавьте строку.

---

## 6. Технологический стек

| Часть | Технологии |
|---|---|
| Клиент | Android (compileSdk 37, minSdk 26), Kotlin 2.3, Jetpack Compose (Material 3), Hilt, Navigation Compose, Retrofit 3 + OkHttp 5 + kotlinx-serialization |
| Сервер | Java 21, Spring Boot 3.5.16, Gradle 9.8.2 (wrapper), Spring Security 6 (stateless JWT HS256) |
| Данные | PostgreSQL (локальная установка), Flyway, JPA/Hibernate |
| Тесты | Бэкенд: JUnit 5, Testcontainers с PostgreSQL 16 (`PostgresTestBase`). Клиент: JUnit 4, kotlinx-coroutines-test, фейки репозиториев |
| Realtime | SSE (односторонний поток «сервер → клиент») — ещё не реализовано |

Техническое обоснование выбора стека — в истории решений (при необходимости
заведём журнал решений отдельно).

---

## 7. Развёртывание

Окружения:

| Окружение | Адрес | Что это |
|---|---|---|
| Локальная разработка | `http://localhost:8080` | PostgreSQL локально, `bootRun` |
| Эмулятор | `http://10.0.2.2:8080/` | хост компьютера с точки зрения эмулятора |
| **Тестовый сервер** | `https://demiapp.ru:8443/` | временная площадка для проверки на реальном устройстве |
| Кооперативный сервер | `https://mayakspace.com/` | будущий постоянный хост, пока недоступен |

Сервер на тестовой площадке поднимается как два контейнера (PostgreSQL +
backend), наружу публикуется только хост-nginx на порту `8443` с существующим
сертификатом `demiapp.ru`. Отдельный порт выбран потому, что 443 уже занят
другим приложением, а поддомен требует новых DNS-записей.

| Место | Что задаёт |
|---|---|
| [`backend/Dockerfile`](../backend/Dockerfile) | Сборка образа: temurin 21, непривилегированный пользователь, `gradle.properties` в образ не копируется |
| [`backend/compose.prod.yaml`](../backend/compose.prod.yaml) | Стек `postgres` + `backend`, том вложений, лимит памяти JVM |
| [`backend/.env.example`](../backend/.env.example) | Шаблон секретов (`POSTGRES_PASSWORD`, `PEERFLOW_JWT_SECRET`) |
| [`backend/deploy/nginx-peerflow.conf`](../backend/deploy/nginx-peerflow.conf) | Проксирование `8443` → `127.0.0.1:8082` |

Адрес сервера в Android задаётся на сборке (`BASE_URL` из `BuildConfig`,
переопределяется свойствами `-PpeerflowDebugBaseUrl` / `-PpeerflowReleaseBaseUrl`)
— код при смене окружения не правится.

Развёртывание и CI/CD осознанно вне рамок MVP (`docs/peerflow.md`, раздел 10):
эти файлы нужны только для ручной проверки на реальном устройстве.

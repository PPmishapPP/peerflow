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
| Мобильное приложение | Один модуль (`android/`) на реальном API: вход, регистрация, вступление в сообщество, список и карточка предложения с голосованием, дискуссия, легитимность и лидер, отчёт лидера с бюджетом, закрытие задачи и живое обновление ленты. Демо-данные остались только на экране «боль» |
| Сервер | Spring Boot в `backend/`: регистрация, вход, JWT-аутентификация, профиль, заявки на вступление с голосованием членов, предложения с обязательным потолком бюджета, список с секциями и сортировками, голосование «делаем / не делаем», дискуссия с ответами и реакциями, передача легитимности, выбор лидера и мандат, цена кандидата и бюджет задачи, репутация, отчёты с вложениями и тратами бюджета, закрытие задачи голосованием, SSE-поток событий. Домены `auth`, `community`, `proposal`, `discussion`, `legitimacy`, `reputation`, `report`, `realtime` наполнены |
| База данных | PostgreSQL (локальная установка). Flyway накатывает `V1` (база схемы), `V2` (`app_user`, `community`), `V3` (`join_request`, `join_vote`), `V4` (`proposal`), `V5` (`proposal_decision_vote`), `V6` (`message`, `message_reaction`), `V7` (`legitimacy_event`), `V8` (`candidate`, `leader_mandate`), `V9` (`reputation_ledger`), `V10` (`attachment`, `budget_expense`), `V11` (`closure_vote`), `V12` (цена кандидата, бюджет задачи), `V13` (трата привязана к мандату) |
| Realtime | SSE-поток `GET /api/events`: события `MESSAGE_POSTED`, `VOTE_CHANGED`, `REPORT_POSTED`, `TASK_CLOSED` |
| Тесты | Бэкенд — 274 теста на реальном PostgreSQL в контейнере; клиент — 156 модульных тестов ViewModel, разбора SSE и демо-данных |
| Развёртывание | Тестовый сервер `https://demiapp.ru:8443/` (Docker, отдельный порт); файлы в `backend/Dockerfile`, `backend/compose.prod.yaml`, `backend/deploy/` |

> MVP собран целиком: человек вносит предложение с обязательным бюджетом,
> видит список двумя секциями с сортировками и голосует «делаем / не делаем».
> Голосование закрывается по сроку или досрочно — но не первым голосом и без
> требования кворума. Дальше сообщество передаёт легитимность и выбирает
> лидера задачи; лидер отчитывается файлами и сообщениями, распоряжается
> бюджетом и запускает закрытие, а сообщество решает, выполнена ли работа.
> Остаток легитимности лидера при закрытии становится репутацией. Дискуссия и
> счётчики обновляются по SSE без перезагрузки экрана.

---

## 2. Общая архитектура

Монорепозиторий из двух частей:

- **Клиент** (`android/`) — Android, Kotlin + Jetpack
  Compose. Общается с сервером по сети.
- **Сервер** (`backend/`) — Java 21 + Spring Boot 3.5. Хранит состояние,
  считает правила. Реализованы домены `auth` (вход, токены), `community`
  (члены, заявки на вступление, голоса), `proposal` (предложения, бюджет,
  сортировки, голосование «делаем / не делаем», закрытие), `discussion`
  (сообщения, реакции), `legitimacy` (доверие, кандидаты, мандат),
  `reputation` (журнал репутации), `report` (отчёты, вложения, траты бюджета) и
  `realtime` (SSE-поток событий).

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
| [`…/data/api/PeerflowApi.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/PeerflowApi.kt) | Описание HTTP-API Retrofit: регистрация, вход, профиль, заявка, голос, предложения и голосование по ним, дискуссия и реакции, легитимность, отчёт и закрытие задачи | `BR-JOIN-*`, `BR-PRIV-1`, `BR-RANK-4`, `BR-VOTE-4`, `BR-DISC-1`–`BR-DISC-3`, `BR-LIFE-7`, `BR-LIFE-9` |
| [`…/data/api/Dto.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/Dto.kt) | Транспортные DTO под JSON сервера | — |
| [`…/data/api/EventSource.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/EventSource.kt) | Потребитель SSE: одно соединение на приложение, переподключение, разбор `event:`/`data:` по строкам | `BR-DISC-1`, `BR-VOTE-4`, `BR-LIFE-7` |
| [`…/data/api/ApiCall.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/ApiCall.kt) | Единая обёртка вызовов: перевод кодов и тела ошибки в `ApiException` / `NetworkException` | — |
| [`…/data/api/TokenStore.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/TokenStore.kt) | Хранение JWT между запусками | `BR-JOIN-1` |
| [`…/data/api/AuthInterceptor.kt`](../android/app/src/main/java/ru/peerflow/demo/data/api/AuthInterceptor.kt) | Подстановка `Authorization: Bearer` в каждый запрос | `BR-PRIV-1` |
| [`…/data/model/ProposalModels.kt`](../android/app/src/main/java/ru/peerflow/demo/data/model/ProposalModels.kt) | Модели предложения для экранов: стадия, сортировка, секции, «мой голос» | `BR-RANK-2`, `BR-RANK-4`, `BR-RANK-5` |
| [`…/data/model/DiscussionModels.kt`](../android/app/src/main/java/ru/peerflow/demo/data/model/DiscussionModels.kt) | Модели дискуссии: сообщение с ответами, вид реакции, сортировка, число скрытых | `BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1` |
| [`…/data/model/LegitimacyModels.kt`](../android/app/src/main/java/ru/peerflow/demo/data/model/LegitimacyModels.kt) | Модели легитимности: кандидат, лидер со шкалой удержания, доска задачи, передача доверия, своя легитимность | `BR-LEGIT-2`–`BR-LEGIT-5`, `BR-LEADER-1`–`BR-LEADER-6`, `BR-REP-2` |
| [`…/data/model/ReportModels.kt`](../android/app/src/main/java/ru/peerflow/demo/data/model/ReportModels.kt) | Модели отчёта: файл, трата бюджета, остаток и доля потраченного, готовый к отправке файл, состояние закрытия с долями и «сколько молчит» | `BR-LIFE-7`–`BR-LIFE-9`, `BR-BUDGET-1`, `BR-BUDGET-4`, `BR-BUDGET-5`, `BR-VOTE-5`, `BR-REP-1` |
| [`…/data/repo/Repositories.kt`](../android/app/src/main/java/ru/peerflow/demo/data/repo/Repositories.kt) | Интерфейсы и реализации репозиториев аккаунта, заявки, предложений, дискуссии, легитимности, отчёта и закрытия | `BR-JOIN-2`–`BR-JOIN-5`, `BR-RANK-5`, `BR-VOTE-4`, `BR-DISC-1`–`BR-DISC-3`, `BR-LEGIT-2`–`BR-LEGIT-5`, `BR-LIFE-7`, `BR-LIFE-9` |
| [`…/di/NetworkModule.kt`](../android/app/src/main/java/ru/peerflow/demo/di/NetworkModule.kt), [`…/di/RepositoryModule.kt`](../android/app/src/main/java/ru/peerflow/demo/di/RepositoryModule.kt) | Hilt: клиент HTTP, JSON, репозитории | — |
| [`…/ui/session/SessionViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/session/SessionViewModel.kt) | Единая точка входа: есть токен → профиль, 401 → выход, нет сети → сообщение | `BR-JOIN-1`, `BR-PRIV-1` |
| [`…/ui/auth/LoginScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/LoginScreen.kt), [`LoginViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/LoginViewModel.kt) | Вход по логину и паролю | `BR-JOIN-1` |
| [`…/ui/auth/RegisterScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/RegisterScreen.kt), [`RegisterViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/RegisterViewModel.kt) | Регистрация: логин, пароль, имя, о себе, аватар | `BR-JOIN-1` |
| [`…/ui/auth/CredentialRules.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/auth/CredentialRules.kt) | Правила ввода на клиенте — зеркало серверной валидации, чтобы ошибку было видно до отправки | `BR-JOIN-1` |
| [`…/ui/join/JoinCommunityScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/join/JoinCommunityScreen.kt), [`JoinRequestViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/join/JoinRequestViewModel.kt) | Своя заявка на вступление и её состояние: на рассмотрении, принята, отклонена | `BR-JOIN-2`, `BR-JOIN-3`, `BR-JOIN-4` |
| [`…/ui/join/JoinRequestsScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/join/JoinRequestsScreen.kt), [`JoinRequestsViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/join/JoinRequestsViewModel.kt) | Очередь заявок для действующего члена: прогресс до порога, голос «принять / отклонить» | `BR-JOIN-5`, `BR-VOTE-1`, `BR-VOTE-8`, `BR-LIMIT-3` |
| [`…/ui/join/PendingJoinRequestsViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/join/PendingJoinRequestsViewModel.kt) | Счётчик ожидающих заявок для отметки в шапке главного экрана | `BR-JOIN-5` |
| [`…/feature/proposal/ProposalViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/feature/proposal/ProposalViewModel.kt), [`…/ui/screens/ProposalListScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/screens/ProposalListScreen.kt) | Список предложений двумя секциями, переключение сортировки, вход в очередь заявок со счётчиком | `BR-RANK-4`, `BR-RANK-5`, `BR-JOIN-5` |
| [`…/feature/proposal/NewProposalViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/feature/proposal/NewProposalViewModel.kt), [`…/ui/screens/NewProposalScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/screens/NewProposalScreen.kt) | Внесение предложения: тема, описание, обязательный бюджет | `BR-OVER-2`, `BR-LIFE-1`, `BR-BUDGET-2` |
| [`…/feature/proposal/ProposalDetailViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/feature/proposal/ProposalDetailViewModel.kt), [`…/ui/screens/ProposalDetailScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/screens/ProposalDetailScreen.kt) | Карточка предложения и голосование «делаем / не делаем» со сменой голоса; под карточкой — обсуждение | `BR-VOTE-4`, `BR-VOTE-9`–`BR-VOTE-13`, `BR-DISC-1` |
| [`…/feature/discussion/DiscussionViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/feature/discussion/DiscussionViewModel.kt), [`…/ui/components/DiscussionSection.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/components/DiscussionSection.kt) | Дискуссия под предложением: загрузка ветки, выбор сортировки, отправка сообщения и ответа, реакции, пояснение о скрытом спаме | `BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1` |
| [`…/feature/legitimacy/LegitimacyViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/feature/legitimacy/LegitimacyViewModel.kt), [`…/ui/components/LegitimacySection.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/components/LegitimacySection.kt) | Легитимность под задачей: кандидаты, лидер со шкалой удержания, выдвижение, передача и отзыв доверия; профиль — свои передачи и репутация | `BR-LEGIT-2`–`BR-LEGIT-5`, `BR-LEADER-1`, `BR-LEADER-5`, `BR-LEADER-6`, `BR-REP-2` |
| [`…/feature/report/ReportViewModel.kt`](../android/app/src/main/java/ru/peerflow/demo/feature/report/ReportViewModel.kt), [`…/ui/screens/LeaderReportScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/screens/LeaderReportScreen.kt), [`…/ui/screens/CloseTaskScreen.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/screens/CloseTaskScreen.kt) | Отчёт лидера (файлы, журнал трат, остаток бюджета), публикация отчёта с выбором файла, запись траты; голосование «выполнено / нет» и показ перехода легитимности в репутацию | `BR-LIFE-7`–`BR-LIFE-9`, `BR-BUDGET-4`, `BR-BUDGET-5`, `BR-VOTE-5`, `BR-OVER-7`, `BR-OVER-9`, `BR-REP-1` |
| [`…/ui/components/DetailChrome.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/components/DetailChrome.kt) | Общая обвязка внутренних экранов: верхняя панель, центр для загрузки и ошибки, плашка ошибки поверх показанных данных | — |
| [`…/ui/components/AvatarPicker.kt`](../android/app/src/main/java/ru/peerflow/demo/ui/components/AvatarPicker.kt) | Выбор аватара из галереи, отдаёт уже прочитанные байты | `BR-JOIN-1` |
| [`…/MainActivity.kt`](../android/app/src/main/java/ru/peerflow/demo/MainActivity.kt) | Навигация: проверка сессии → вход/регистрация → вступление → приложение | — |

`BASE_URL` приходит из `BuildConfig` (поле `baseUrl` в
[`android/app/build.gradle.kts`](../android/app/build.gradle.kts)); HTTP без TLS
разрешён только в debug-сборке через
[`android/app/src/debug/AndroidManifest.xml`](../android/app/src/debug/AndroidManifest.xml).

### Демо-прототип (без сети)

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| `android/…/data/DemoData.kt` | Данные и модели экрана «боль» в памяти | Заглушка вместо сервера |
| `android/…/ui/screens/PainMessengerScreen.kt` | Экран «боль»: мок мессенджера | Иллюстрация `BR-PROB-1`–`BR-PROB-4` |

Все рабочие экраны (`ProposalListScreen`, `NewProposalScreen`,
`ProposalDetailScreen`, `LeaderReportScreen`, `CloseTaskScreen`,
`ProfileScreen`, очередь заявок) работают на реальном API. Демо-данные остались
только для вступительного экрана «боль».

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

Домены MVP реализованы целиком. Что осознанно отложено — см.
[`peerflow.md`](peerflow.md), раздел 10: push-уведомления, офлайн-режим,
защита от сговора.

---

## 4. Сервер

### Общая часть

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`backend/build.gradle.kts`](../backend/build.gradle.kts) | Java 21, Spring Boot 3.5.16, Gradle 9.8.2 (wrapper) | Инфраструктура |
| [`backend/…/PeerflowApplication.java`](../backend/src/main/java/ru/peerflow/PeerflowApplication.java) | Точка входа, `@ConfigurationPropertiesScan` | — |
| [`backend/…/config/PeerflowThresholds.java`](../backend/src/main/java/ru/peerflow/config/PeerflowThresholds.java) | Типизированные пороги MVP с проверкой границ | `BR-LIMIT-1`, `BR-LIMIT-2`, `BR-LIMIT-3`, `BR-LIMIT-5`, `BR-LIMIT-6` |
| [`backend/…/config/ClockConfig.java`](../backend/src/main/java/ru/peerflow/config/ClockConfig.java) | Бин `Clock`: единственный источник «сейчас» | `BR-VOTE-9` |
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

### Домен `proposal`

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`…/proposal/domain/Proposal.java`](../backend/src/main/java/ru/peerflow/proposal/domain/Proposal.java), [`ProposalStage.java`](../backend/src/main/java/ru/peerflow/proposal/domain/ProposalStage.java) | Агрегат предложения: бюджет, стадия, голоса; фабрика `create` требует действующего члена и положительный бюджет | `BR-LIFE-1`, `BR-BUDGET-2`, `BR-VOTE-1` |
| [`…/proposal/domain/DecisionVote.java`](../backend/src/main/java/ru/peerflow/proposal/domain/DecisionVote.java), [`DecisionTally.java`](../backend/src/main/java/ru/peerflow/proposal/domain/DecisionTally.java) | Голос «делаем / не делаем» и сводка голосов | `BR-VOTE-4`, `BR-VOTE-13` |
| [`…/proposal/domain/VotingRules.java`](../backend/src/main/java/ru/peerflow/proposal/domain/VotingRules.java), [`VotingOutcome.java`](../backend/src/main/java/ru/peerflow/proposal/domain/VotingOutcome.java) | Правило закрытия голосования: срок, досрочное закрытие при активности и перевесе, отказ без голосов | `BR-VOTE-9`–`BR-VOTE-12`, `BR-LIMIT-5`, `BR-LIMIT-6` |
| [`…/proposal/service/ProposalService.java`](../backend/src/main/java/ru/peerflow/proposal/service/ProposalService.java) | Создание, список с секциями и сортировками, карточка, голосование; ленивое закрытие истёкших голосований | `BR-OVER-2`, `BR-RANK-4`, `BR-RANK-5`, `BR-VOTE-4` |
| [`…/proposal/service/ProposalSort.java`](../backend/src/main/java/ru/peerflow/proposal/service/ProposalSort.java) | Разбор параметра `sort`: `popular`, `budget`, `new` | `BR-RANK-4` |
| [`…/proposal/web/ProposalController.java`](../backend/src/main/java/ru/peerflow/proposal/web/ProposalController.java) | `GET /api/proposals?sort=`, `GET /api/proposals/{id}`, `POST /api/proposals`, `POST /api/proposals/{id}/votes` | `BR-OVER-2`, `BR-RANK-5`, `BR-VOTE-4` |
| [`…/proposal/repo/ProposalRepository.java`](../backend/src/main/java/ru/peerflow/proposal/repo/ProposalRepository.java) | Запросы с `fetch join` голосов: список по сообществу и стадиям, карточка | `BR-RANK-5` |

### Домен `discussion`

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`…/discussion/domain/Message.java`](../backend/src/main/java/ru/peerflow/discussion/domain/Message.java) | Сообщение под предложением: текст, автор, родитель для ответа; реакции — часть агрегата | `BR-DISC-1`, `BR-DISC-3` |
| [`…/discussion/domain/Reaction.java`](../backend/src/main/java/ru/peerflow/discussion/domain/Reaction.java), [`ReactionKind.java`](../backend/src/main/java/ru/peerflow/discussion/domain/ReactionKind.java) | Реакция члена: «плюс», «минус», «спам» — ровно одна на человека | `BR-DISC-2`, `BR-VOTE-6` |
| [`…/discussion/domain/MessageOrder.java`](../backend/src/main/java/ru/peerflow/discussion/domain/MessageOrder.java) | Порядок сообщений: «Популярные» (плюсы, затем минусы) и «Новые» | `BR-DISC-4`, `BR-DISC-5` |
| [`…/discussion/service/DiscussionService.java`](../backend/src/main/java/ru/peerflow/discussion/service/DiscussionService.java) | Создание сообщения и ответа, реакции, сборка дерева, скрытие спама | `BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1` |
| [`…/discussion/service/MessageSort.java`](../backend/src/main/java/ru/peerflow/discussion/service/MessageSort.java) | Разбор параметра `sort`: `popular`, `new` | `BR-DISC-4`, `BR-DISC-5` |
| [`…/discussion/web/DiscussionController.java`](../backend/src/main/java/ru/peerflow/discussion/web/DiscussionController.java) | `GET/POST /api/proposals/{id}/messages`, `POST /api/messages/{id}/reactions` | `BR-DISC-1`–`BR-DISC-3` |
| [`…/discussion/repo/MessageRepository.java`](../backend/src/main/java/ru/peerflow/discussion/repo/MessageRepository.java) | Ветка с авторами и реакциями одним запросом; авторы сообщений для метрики популярности | `BR-DISC-1`, `BR-RANK-2` |

### Правило дискуссии

Порядок и вложенность считает сервер: клиент показывает дерево как есть
(`BR-DISC-6`). «Популярные» сравнивают сначала плюсы, затем минусы, при полном
равенстве — по времени создания (`MessageOrder.POPULAR`); «Новые» — по времени
создания без учёта меток. Скрытие спама — фильтр при чтении, а не удаление:
метку можно снять, и сообщение вернётся (`BR-SPAM-1`). Порог скрытия берётся из
`PeerflowThresholds.spamHideMarks` (по умолчанию 3, `BR-LIMIT-2`).

Скрытый корень уносит с собой всю ветку, поэтому ответ клиенту приходит парой
`{messages, hidden}`: число скрытых сообщений считается как разница между всеми
сообщениями предложения и показанными. Без этого числа человек видел бы пустоту
и не знал, что обсуждение было.

### Домен `legitimacy` (легитимность и лидер)

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`…/legitimacy/domain/LegitimacyEvent.java`](../backend/src/main/java/ru/peerflow/legitimacy/domain/LegitimacyEvent.java), [`LegitimacyEventKind.java`](../backend/src/main/java/ru/peerflow/legitimacy/domain/LegitimacyEventKind.java) | Событие журнала «передал / отозвал»; `seq` (identity) задаёт порядок, а не время | `BR-LEGIT-2`, `BR-LEGIT-5` |
| [`…/legitimacy/domain/LegitimacyStanding.java`](../backend/src/main/java/ru/peerflow/legitimacy/domain/LegitimacyStanding.java) | Свёртка журнала в текущее состояние: кому передано, кто поддерживает кандидата, сколько всего передано | `BR-LEGIT-3`, `BR-LEGIT-6` |
| [`…/legitimacy/domain/Candidate.java`](../backend/src/main/java/ru/peerflow/legitimacy/domain/Candidate.java) | Кандидат в лидеры конкретной задачи и его цена | `BR-LEADER-1`, `BR-LEADER-8`, `BR-BUDGET-8` |
| [`…/legitimacy/domain/LeaderElection.java`](../backend/src/main/java/ru/peerflow/legitimacy/domain/LeaderElection.java) | Выбор лидера: поддержка → репутация → дата вступления → id | `BR-LEGIT-4`, `BR-REP-4` |
| [`…/legitimacy/domain/LeaderMandate.java`](../backend/src/main/java/ru/peerflow/legitimacy/domain/LeaderMandate.java), [`MandateEndReason.java`](../backend/src/main/java/ru/peerflow/legitimacy/domain/MandateEndReason.java) | Мандат лидера: изначальная поддержка, завершение по `MANDATE_LOST` / `TASK_CLOSED` | `BR-LEADER-3`, `BR-LEADER-5`, `BR-LIFE-9` |
| [`…/legitimacy/domain/MandateThreshold.java`](../backend/src/main/java/ru/peerflow/legitimacy/domain/MandateThreshold.java) | Порог удержания на `BigDecimal` с округлением вверх: «ровно 70%» держит мандат | `BR-LIMIT-1`, `BR-LEADER-5` |
| [`…/legitimacy/service/LegitimacyService.java`](../backend/src/main/java/ru/peerflow/legitimacy/service/LegitimacyService.java) | Передача и отзыв доверия, выдвижение, доска задачи, выбор лидера, пересчёт мандата при чтении, личная легитимность | `BR-LEGIT-1`–`BR-LEGIT-6`, `BR-LEADER-1`–`BR-LEADER-7` |
| [`…/legitimacy/web/LegitimacyController.java`](../backend/src/main/java/ru/peerflow/legitimacy/web/LegitimacyController.java), [`MyLegitimacyController.java`](../backend/src/main/java/ru/peerflow/legitimacy/web/MyLegitimacyController.java) | `GET/POST /api/proposals/{id}/legitimacy`, `POST …/candidates`, `DELETE …/legitimacy`, `POST /api/proposals/{id}/legitimacy/candidates`, `GET /api/me/legitimacy` | `BR-LEGIT-2`–`BR-LEGIT-5`, `BR-REP-2` |
| [`…/legitimacy/repo/`](../backend/src/main/java/ru/peerflow/legitimacy/repo) | Журнал по задаче и сразу по нескольким задачам, кандидаты, мандаты | `BR-LEGIT-3` |
| [`…/reputation/domain/ReputationEntry.java`](../backend/src/main/java/ru/peerflow/reputation/domain/ReputationEntry.java), [`ReputationService.java`](../backend/src/main/java/ru/peerflow/reputation/service/ReputationService.java) | Журнал репутации и её сумма; причина `TASK_CLOSED` | `BR-REP-1`, `BR-REP-2`, `BR-REP-4` |

**Правило легитимности.** Легитимность — не «голос», а право распоряжаться
своей долей решения по одной задаче (`BR-LEGIT-1`): передал — значит твой голос
учитывается вместе с голосом кандидата. Поэтому журнал только дописывается
(`DELEGATE` / `REVOKE`), а текущее состояние — последнее событие по паре «задача
+ член». Порядок задаёт `seq`, а не время: передача и отзыв в одну миллисекунду
иначе упорядочивались бы случайно.

Передача себе запрещена (`BR-LEGIT-5`): «передать себе» — это и есть текущее
состояние, отдельного события не нужно. Повторная передача тому же кандидату и
отзыв без передачи в журнал не пишутся — двойное нажатие не создаёт истории.

**Правило лидера.** Лидер — кандидат, за которым больше всего переданной
легитимности; при равенстве — больше репутации, затем раньше вступивший, затем
меньший id (`LeaderElection`, `BR-LEGIT-4`, `BR-REP-4`). Мандат держится, пока
поддержка не упала ниже 70% от изначальной (`BR-LEADER-5`), порог считается на
`BigDecimal`: `0.7 * 10` на `double` даёт `6.999999999999999` и «ровно 70%»
теряло бы мандат. Утрата мандата возвращает задачу в `DISCUSSION`, очищает
голоса прошлого круга и переоткрывает голосование (`BR-LEADER-7`); для этого у
предложения появилось `voting_opened_at` — срок считается от текущего круга, а
не от создания. Тогда же сбрасывается `task_budget`: цена прежнего лидера
больше ни к чему не относится, и новый лидер приносит свою (`BR-BUDGET-7`,
`BR-LIFE-10`).

Выбор лидера и проверка мандата происходят **при чтении** карточки, списка и
голосования (`ProposalService` зовёт `LegitimacyService.reconcile`): планировщика
в MVP нет, а итог — детерминированная функция от данных. Действующий мандат у
задачи ровно один — это держит частичный уникальный индекс
`uk_leader_mandate_active`.

### Домен `report` (отчёт, вложения, траты бюджета)

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`…/report/domain/Attachment.java`](../backend/src/main/java/ru/peerflow/report/domain/Attachment.java) | Файл отчёта: задача, автор, путь на диске, имя, тип, размер | `BR-LIFE-7` |
| [`…/report/domain/BudgetExpense.java`](../backend/src/main/java/ru/peerflow/report/domain/BudgetExpense.java) | Одна трата бюджета задачи: кто, сколько, на что и в рамках какого мандата | `BR-BUDGET-4`, `BR-BUDGET-7` |
| [`…/report/service/ReportStorage.java`](../backend/src/main/java/ru/peerflow/report/service/ReportStorage.java) | Запись файла на диск, белый список расширений, лимит 10 МБ | `BR-LIFE-7`, `BR-BUDGET-5` |
| [`…/report/service/ReportService.java`](../backend/src/main/java/ru/peerflow/report/service/ReportService.java) | Публикация отчёта (текст и/или файл), чтение отчёта с остатком бюджета, запись траты, выдача файла на скачивание; право даёт мандат | `BR-LIFE-7`, `BR-BUDGET-4`, `BR-BUDGET-5`, `BR-LEGIT-4` |
| [`…/report/web/ReportController.java`](../backend/src/main/java/ru/peerflow/report/web/ReportController.java) | `GET /api/proposals/{id}/report`, `POST /api/proposals/{id}/reports` (multipart), `POST /api/proposals/{id}/expenses`, `GET /api/attachments/{id}` | `BR-LIFE-7`, `BR-BUDGET-4`, `BR-PRIV-1` |

**Правило отчёта.** Отдельной сущности «отчёт» нет (`BR-LIFE-7`): отчётом
считается любое сообщение под задачей или приложенный файл. Поэтому текст
отчёта — обычное `Message` (и он виден в дискуссии), а файл — строка в
`attachment` с прямой ссылкой на задачу и автора, без привязки к сообщению:
файл самостоятелен, текст может отсутствовать. Текст и файл уходят одной
multipart-формой, чтобы «отчёт с чеком» не распадался на два запроса, второй из
которых может не дойти. Файл пишется на диск **до** записи в БД: при
недоступном диске в базе не остаётся битого вложения.

**Правило бюджета.** Бюджет в MVP виртуальный — это учёт, а не касса
(`BR-BUDGET-1`): траты пишутся журналом `budget_expense`, остаток считается
сервером как `max(0, бюджет − потрачено)` и приходит готовым числом. Считать
его на клиенте значило бы иметь два разных остатка на двух экранах. Тратить
может только действующий лидер (`BR-BUDGET-4`, `BR-LEGIT-4`), и трата не может
превысить остаток.

**Две суммы вместо одной.** У задачи две разные величины, и путать их нельзя:
`proposal.budget` — **потолок**, утверждённый сообществом (`BR-BUDGET-2`,
`BR-BUDGET-3`), он не меняется никогда; `proposal.task_budget` — **цена
действующего лидера** (`BR-LIFE-6`), которую тот назвал при выдвижении
(`BR-BUDGET-8`). Кандидат задаёт цену в `candidate.price`, и она не может
превышать потолок; при выдаче мандата цена победителя становится
`task_budget`. Пока лидера нет, `task_budget` пуст, а отчёт показывает потолок
как ожидаемый бюджет (`BR-BUDGET-10`) — тратить всё равно некому.

**Траты принадлежат мандату.** `budget_expense.mandate_id` привязывает трату к
попытке выполнить задачу, а не к задаче целиком. Поэтому после смены лидера
(`BR-BUDGET-7`) остаток считается заново от цены нового лидера, а траты
прошлого остаются в журнале как история: деньги действительно были потрачены,
но новый лидер не наследует чужой счёт без решения сообщества.

### Домен `realtime` (живые обновления)

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`…/realtime/EventType.java`](../backend/src/main/java/ru/peerflow/realtime/EventType.java) | Виды событий: `MESSAGE_POSTED`, `VOTE_CHANGED`, `REPORT_POSTED`, `TASK_CLOSED` | `BR-DISC-1`, `BR-VOTE-4`, `BR-LIFE-7` |
| [`…/realtime/ProposalEvent.java`](../backend/src/main/java/ru/peerflow/realtime/ProposalEvent.java) | Событие: вид, задача, время. Данных задачи нет — клиент перезапрашивает экран | `BR-DISC-1` |
| [`…/realtime/EventPublisher.java`](../backend/src/main/java/ru/peerflow/realtime/EventPublisher.java) | Подписки в памяти процесса с привязкой к сообществу, публикация после коммита транзакции | `BR-PRIV-1`, `BR-DISC-1` |
| [`…/realtime/EventController.java`](../backend/src/main/java/ru/peerflow/realtime/EventController.java) | `GET /api/events` (`text/event-stream`), только действующему члену сообщества | `BR-PRIV-1` |

**Правило realtime.** Поток односторонний: сервер говорит «что изменилось», а
клиент перезапрашивает экран. Данные в событие не кладутся — иначе клиент
показывал бы устаревшую копию, а формат события пришлось бы менять вместе с
форматом ответов. Событие публикуется **после коммита**: отправленное раньше
приглашало бы клиента прочитать то, чего в базе ещё нет. Подписка привязана к
сообществу, поэтому события чужого сообщества не утекают даже как «что-то
произошло» (`BR-PRIV-1`). In-memory достаточно, пока сервер один; при переходе
к нескольким узлам меняется только `EventPublisher`.

### Правило голосования «делаем / не делаем»

Правило закрытия — одна функция от времени и голосов
(`VotingOutcome.evaluate`), поэтому итог одинаков при чтении списка, открытии
карточки и голосовании. Закрытия «по расписанию» нет: в MVP нет планировщика, а
итог детерминирован, поэтому он пересчитывается в момент чтения.

Порядок проверок (`BR-VOTE-9`–`BR-VOTE-12`):

1. **Срок истёк** (`BR-LIMIT-5`, по умолчанию 48 часов) — решает большинство
   проголосовавших; кворума нет. Если не проголосовал никто, предложение
   отклоняется: молчание — не согласие.
2. **Срок не истёк, но есть активность и перевес** — досрочное закрытие. Нужны
   одновременно: не меньше `ceil(члены × proposalEarlyCloseRatio)` (30%,
   `BR-LIMIT-6`) участников, **минимум два голоса** и перевес не меньше
   `proposalEarlyCloseLead` (2:1). Требование «минимум два голоса» — не
   украшение: в маленьком сообществе 30% — это один человек, и без него первый
   же голос «за» закрывал бы голосование (`BR-VOTE-11`).
3. Иначе голосование продолжается.

Голос один на человека; повторное нажатие меняет сторону, а не добавляет голос
(`BR-VOTE-13`). Уникальный индекс `(proposal_id, voter_id)` держит это на
уровне БД, а не только в коде.

### Правило закрытия задачи

Закрытие — отдельное голосование (`closure_vote`), а не переиспользование
голосов «делаем / не делаем»: вопрос другой («сделано ли»), и старые голоса не
должны решать новый вопрос. Правила голосования при этом общие
(`VotingOutcome`/`VotingRules`): срок, досрочное закрытие и простое большинство
— иначе сообщество голосовало бы по двум правилам об одном (`BR-LIFE-9`,
`BR-VOTE-5`).

Лидер не может объявить свою работу выполненной сам: он лишь открывает
процедуру (`BR-LIFE-8`), а решает сообщество (`BR-OVER-9`). Ответ «не
выполнено» не отказывает от задачи, а возвращает её в работу: голоса
очищаются, и лидер может начать процедуру заново. При закрытии остаток
легитимности лидера становится репутацией (`BR-REP-1`) — количество считается
как `supportersOf(leader)`, при нуле запись не создаётся. Защита от повторного
начисления — уникальный индекс `uk_reputation_ledger_proposal`, а не проверка в
коде: два одновременных закрытия иначе дали бы две записи.

Итог голосования пересчитывается при чтении (`ClosureService.reconcile` из
`ProposalService`), как и остальные правила MVP: планировщика нет.

### Порог вступления

Порог считает только сервер:
`requiredAccepts = max(1, ceil(активныеЧлены × joinAcceptRatio))`, где
`joinAcceptRatio` по умолчанию `0.30` (`BR-LIMIT-3`). Деление — `BigDecimal` с
`RoundingMode.CEILING`, чтобы дробный порог не «терял» голос: при 4 членах нужно
2 принятия, а не 1. Если оставшихся членов уже не хватает, чтобы порог набрать,
заявка отклоняется досрочно — человек не ждёт впустую.

Заявитель в этот счёт не входит: по своей заявке он не голосует
(`BR-VOTE-8`), а сразу после принятия сам становится активным членом. Без
вычета порог уже решённой заявки «подрос» бы от её же принятия, и карточка
показывала бы «за» 1 из 2 при фактически набранном пороге.

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
  заявки, голоса), `proposal` (предложения, бюджет, голосование по решению,
  закрытие задачи), `discussion` (сообщения, реакции), `legitimacy` (доверие,
  кандидаты, мандат), `reputation` (журнал репутации), `report` (отчёты,
  вложения, траты бюджета), `realtime` (SSE-поток событий); общая
  конфигурация — `config`, сквозные HTTP-ошибки — `web`.
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
| `V4__proposal.sql` | `proposal` (автор, сообщество, тема, описание, бюджет, стадия, время создания и решения), проверки «бюджет > 0» и допустимой стадии, индекс по сообществу и стадии | `BR-LIFE-1`, `BR-BUDGET-2`, `BR-BUDGET-3` |
| `V5__decision_vote.sql` | `proposal_decision_vote` (предложение, голосующий, «за/против», время голоса и изменения), уникальность пары «предложение + голосующий» | `BR-VOTE-4`, `BR-VOTE-13` |
| `V6__discussion.sql` | `message` (предложение, автор, родитель, текст, время) и `message_reaction` (сообщение, член, вид, время создания и изменения), уникальность пары «сообщение + член», проверка «текст не пустой» | `BR-DISC-1`–`BR-DISC-3`, `BR-DISC-2`, `BR-SPAM-1` |
| `V7__legitimacy.sql` | `legitimacy_event` (задача, член, кандидат, вид, время) с колонкой `seq` для порядка, индекс по `(proposal_id, seq)`; триггер запрещает `UPDATE` | `BR-LEGIT-2`, `BR-LEGIT-3`, `BR-LEGIT-5` |
| `V8__leader.sql` | `candidate` (задача + член, уникальность пары), `leader_mandate` (изначальная поддержка, начало, конец и причина), частичный уникальный индекс действующего мандата, `proposal.voting_opened_at` | `BR-LEADER-1`, `BR-LEADER-3`, `BR-LEADER-5`, `BR-LIFE-10` |
| `V9__reputation.sql` | `reputation_ledger` (член, задача, очки, причина), уникальность начисления на задачу, триггер запрещает `UPDATE` | `BR-REP-1`, `BR-REP-4` |
| `V10__report.sql` | `attachment` (задача, автор, путь, имя, тип, размер, время; проверки непустых размера/пути/имени) и `budget_expense` (задача, член, сумма, пояснение, время; проверка «сумма > 0») | `BR-LIFE-7`, `BR-BUDGET-4` |
| `V11__closure.sql` | `closure_vote` (задача, голосующий, «выполнено/нет», время создания и изменения), уникальность пары «задача + голосующий»; `proposal.closure_opened_at` | `BR-LIFE-9`, `BR-VOTE-5` |
| `V12__candidate_price.sql` | `candidate.price` (цена кандидата, «> 0»), `proposal.task_budget` (бюджет текущего мандата, «> 0 или пусто»); существующим строкам цена проставляется из потолка | `BR-BUDGET-2`, `BR-BUDGET-3`, `BR-BUDGET-8`, `BR-LIFE-6`, `BR-LIFE-10`, `BR-LEADER-1` |
| `V13__expense_mandate.sql` | `budget_expense.mandate_id` (мандат, в рамках которого сделана трата) с внешним ключом и индексом; существующие траты привязываются к действовавшему мандату | `BR-BUDGET-4`, `BR-BUDGET-7` |

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
| `proposal` | Предложение, стадия, потолок бюджета (`budget`), цена действующего лидера (`task_budget`) | `BR-LIFE-1`, `BR-BUDGET-2`, `BR-LIFE-6` |
| `proposal_decision_vote` | Голос «делаем / не делаем» | `BR-LIFE-4`, `BR-VOTE-4`, `BR-VOTE-13` |
| `message` | Сообщения дискуссии и ответы | `BR-DISC-1`, `BR-DISC-3` |
| `message_reaction` | Реакции на сообщения: плюс, минус, спам | `BR-DISC-2`, `BR-VOTE-6` |
| `candidate` | Кандидаты в лидеры и их цена | `BR-LEADER-1`, `BR-BUDGET-8` |
| `leader_mandate` | Мандаты лидера: поддержка, начало и конец | `BR-LEADER-3`, `BR-LEADER-5`, `BR-LEADER-6` |
| `legitimacy_event` | Журнал передачи и отзыва легитимности | `BR-LEGIT-2`, `BR-LEGIT-5` |
| `attachment` | Файлы отчётов лидера | `BR-LIFE-7` |
| `budget_expense` | Журнал трат бюджета задачи, с привязкой к мандату | `BR-BUDGET-4`, `BR-BUDGET-7` |
| `closure_vote` | Голос «выполнено / нет» | `BR-LIFE-9`, `BR-VOTE-5` |
| `reputation_ledger` | Начисления репутации | `BR-REP-1` |

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
| `BR-JOIN-5` (решение по заявке, очередь у члена) | `JoinRequestService`: подсчёт порога и смена статуса; заявитель исключён из электората своей заявки, иначе принятие «поднимало» бы порог уже решённой заявки; клиент: `JoinRequestsViewModel`, `JoinRequestsScreen`, `PendingJoinRequestsViewModel` | `JoinRequestServiceTest`, `JoinRequestControllerTest`, `JoinRequestsViewModelTest` |
| `BR-LIMIT-3` (приём при 30%) | `PeerflowThresholds.joinAcceptRatio` → `JoinRequestService` (`BigDecimal` + `CEILING`) | `PeerflowThresholdsTest`, `JoinRequestThresholdTest` |
| `BR-VOTE-2` (один человек — один голос) | уникальность пары «заявка + голосующий» в `join_vote` | `JoinRequestServiceTest`, `JoinRequestControllerTest` |
| `BR-VOTE-7` (утверждение потолка бюджета предложения) | Потолок утверждается тем же решением «делаем / не делаем»: `ProposalService.vote` → `VotingOutcome` переводит стадию, отдельного голоса по бюджету нет (`BR-BUDGET-3`) | `ProposalServiceTest`, `DecisionVoteTest` |
| `BR-VOTE-8` (голос по вступлению) | `JoinVote`, `JoinRequestService.vote`; клиент: `JoinRequestsViewModel.vote` | `JoinRequestServiceTest`, `JoinRequestControllerTest`, `JoinRequestsViewModelTest` |
| `BR-OVER-2`, `BR-LIFE-1` (внесение предложения, потолок бюджета обязателен) | `Proposal.create`, `ProposalService.create`, `POST /api/proposals`; клиент: `NewProposalViewModel` | `ProposalServiceTest`, `ProposalControllerTest`, `NewProposalViewModelTest` |
| `BR-BUDGET-2`, `BR-BUDGET-3` (потолок обязателен и положителен) | `Proposal.create` (`budget` — потолок), проверка `budget > 0` в `V4__proposal.sql`; клиент: `NewProposalViewModel` | `ProposalServiceTest`, `ProposalControllerTest`, `NewProposalViewModelTest`, `FlywayMigrationTest` |
| `BR-RANK-4` (сортировки списка) | `ProposalSort`, `ProposalService.comparatorFor`, `GET /api/proposals?sort=`; клиент: `ProposalViewModel.selectSort` | `ProposalRankingTest`, `ProposalControllerTest`, `ProposalViewModelTest` |
| `BR-RANK-5` (секции «В работе» / «На обсуждении») | `ProposalService.list` → `ProposalListResponse`; клиент: `ProposalSections` | `ProposalServiceTest`, `ProposalControllerTest`, `ProposalViewModelTest` |
| `BR-RANK-2` (метрика популярности) | сервер: `Proposal.popularity` (`участники + (за − против)`), участники включают авторов сообщений (`MessageRepository.findParticipants`); клиент берёт готовое число | `ProposalRankingTest`, `ProposalServiceTest` |
| `BR-VOTE-4` (голос «делаем / не делаем») | `DecisionVote`, `Proposal.castVote`, `ProposalService.vote`, `POST /api/proposals/{id}/votes`; клиент: `ProposalDetailViewModel` | `DecisionVoteTest`, `ProposalControllerTest`, `ProposalDetailViewModelTest` |
| `BR-VOTE-9` (срок голосования) | `VotingRules.deadlineFor`, `PeerflowThresholds.proposalVotingWindow`, `ClockConfig` | `VotingOutcomeTest`, `DecisionVoteTest` |
| `BR-VOTE-10` (итог по большинству, без кворума) | `VotingOutcome.evaluate` (ветка «срок истёк») | `VotingOutcomeTest`, `DecisionVoteTest` |
| `BR-VOTE-11` (досрочное закрытие: активность + перевес 2:1, не меньше двух голосов) | `VotingOutcome.evaluate`, `MIN_VOTES_FOR_EARLY_CLOSE`, `PeerflowThresholds.proposalEarlyCloseRatio`/`proposalEarlyCloseLead` | `VotingOutcomeTest`, `DecisionVoteTest` |
| `BR-VOTE-12` (нет голосов — не делаем) | `VotingOutcome.evaluate` (ветка «нет ни одного голоса») | `VotingOutcomeTest`, `DecisionVoteTest` |
| `BR-VOTE-13` (один голос на человека, можно менять) | уникальность `(proposal_id, voter_id)` в `V5__decision_vote.sql`, `Proposal.castVote` | `DecisionVoteTest`, `ProposalControllerTest`, `FlywayMigrationTest` |
| `BR-LIMIT-5` (срок голосования 48 часов) | `PeerflowThresholds.proposalVotingWindow` | `PeerflowThresholdsTest`, `VotingOutcomeTest` |
| `BR-LIMIT-6` (активность для досрочного закрытия 30%) | `PeerflowThresholds.proposalEarlyCloseRatio` | `PeerflowThresholdsTest`, `VotingOutcomeTest` |
| `BR-SPAM-1` (скрытие при 3 метках) | `Message.isHidden`, `DiscussionService` (фильтр при чтении + число скрытых), порог `PeerflowThresholds.spamHideMarks`; клиент: `DiscussionSection` | `MessageRankingTest`, `DiscussionServiceTest`, `DiscussionViewModelTest` |
| `BR-LIMIT-1` (порог удержания 70%) | `PeerflowThresholds.legitimacyRetainRatio` | `PeerflowThresholdsTest` |
| `BR-LIMIT-2` (3 метки спама) | `PeerflowThresholds.spamHideMarks` → `DiscussionService` | `PeerflowThresholdsTest`, `MessageRankingTest` |
| `BR-DISC-1` (сообщение под предложением) | `Message`, `DiscussionService.post`, `POST /api/proposals/{id}/messages`; клиент: `DiscussionViewModel.post` | `DiscussionServiceTest`, `DiscussionControllerTest`, `DiscussionViewModelTest` |
| `BR-DISC-2` (одна реакция: плюс / минус / спам) | `ReactionKind`, `Reaction`, `Message.react`, уникальность `(message_id, member_id)` в `V6`; клиент: `DiscussionViewModel.react` | `DiscussionServiceTest`, `DiscussionControllerTest`, `DiscussionViewModelTest`, `FlywayMigrationTest` |
| `BR-DISC-3` (вложенные ответы) | `Message.parent`, проверка «ответ в то же предложение» в `DiscussionService.loadParent`; клиент: `DiscussionSection` (`parentId`) | `DiscussionServiceTest`, `DiscussionControllerTest` |
| `BR-DISC-4` (сортировка «Популярные») | `MessageOrder.POPULAR`, `MessageSort.parse`, `GET /api/proposals/{id}/messages?sort=` | `MessageRankingTest` |
| `BR-DISC-5` (сортировка «Новые») | `MessageOrder.NEWEST`, `MessageSort.parse`; клиент: `DiscussionViewModel.selectSort` | `MessageRankingTest`, `DiscussionViewModelTest` |
| `BR-DISC-6` (вложенные ветки подчиняются тем же правилам) | `DiscussionService.response` рекурсивно применяет порядок и скрытие | `MessageRankingTest` |
| `BR-LEADER-5` (мандат утрачен ниже 70%) | `MandateThreshold`, `LeaderMandate`, `LegitimacyService.reconcile`; клиент: `LegitimacySection` (шкала удержания); порог — `PeerflowThresholds.legitimacyRetainRatio` | `MandateThresholdTest`, `LeaderElectionTest`, `LegitimacyControllerTest` |
| `BR-LEGIT-2` (передача легитимности кандидату) | `LegitimacyEvent` (`DELEGATE`), `LegitimacyService.delegate`, `POST /api/proposals/{id}/legitimacy/{candidateId}`; клиент: `LegitimacyViewModel.delegate` | `LegitimacyServiceTest`, `LegitimacyControllerTest`, `LegitimacyViewModelTest` |
| `BR-LEGIT-3` (независимость по задачам) | журнал и свёртка по `proposal_id` (`LegitimacyStanding.of`, `findAllForProposal(s)`); профиль: `GET /api/me/legitimacy` | `LegitimacyServiceTest`, `LegitimacyControllerTest`, `MyLegitimacyViewModelTest` |
| `BR-LEGIT-4` (лидер — больше всех легитимности) | `LeaderElection.elect`, `LegitimacyService.electLeader`, `LeaderMandate`; клиент: `Leader` в `LegitimacyBoard` | `LeaderElectionTest`, `LegitimacyServiceTest`, `LegitimacyControllerTest` |
| `BR-LEGIT-5` (вернуть легитимность себе) | `LegitimacyEvent` (`REVOKE`), `LegitimacyService.revoke`, `DELETE /api/proposals/{id}/legitimacy`; клиент: `LegitimacyViewModel.revoke` | `LegitimacyServiceTest`, `LegitimacyControllerTest`, `LegitimacyViewModelTest` |
| `BR-LEGIT-6` (мандат прекращается при потере доверия) | `LeaderMandate.end(MANDATE_LOST)`, `Proposal.reopenForRevote`, `LegitimacyService.reconcile` | `LegitimacyServiceTest`, `LeaderElectionTest` |
| `BR-LEADER-1` (выдвижение кандидата с ценой) | `Candidate.nominate`, `LegitimacyService.nominate`, `POST /api/proposals/{id}/legitimacy/candidates` с телом `NominateRequest`, уникальность `uk_candidate_proposal_member`; клиент: `LegitimacyViewModel.nominate(price)`, `NominateForm` | `LeaderElectionTest`, `LegitimacyControllerTest`, `LegitimacyViewModelTest` |
| `BR-LEADER-4` (тай-брейкеры выбора) | `LeaderElection.elect`: поддержка → репутация → дата вступления → id | `LeaderElectionTest` |
| `BR-LEADER-6`, `BR-LEADER-7` (утрата мандата, повторное решение) | `LegitimacyService.reconcile`, `Proposal.reopenForRevote`, `Proposal.votingOpenedAt`; клиент: `LegitimacySection` (пояснение) | `LegitimacyControllerTest`, `LegitimacyViewModelTest` |
| `BR-REP-1` (легитимность → репутация) | `ReputationService.awardForClosedTask`, `ClosureService.finish` (при закрытии задачи), уникальный индекс `uk_reputation_ledger_proposal`; клиент: `CloseTaskScreen` (`reputationAwarded`) | `ClosureServiceTest`, `ClosureControllerTest`, `ReportViewModelTest` |
| `BR-REP-2` (репутация рядом с аватаром) | `ReputationService.reputationOf`, `GET /api/me/legitimacy`; клиент: `MyLegitimacy.reputation`, профиль | `LegitimacyControllerTest`, `MyLegitimacyViewModelTest` |
| `BR-REP-3` (репутация как витрина достижений) | `reputation_ledger` хранит причину и задачу начисления, профиль показывает сумму и последние записи | `LegitimacyControllerTest`, `MyLegitimacyViewModelTest` |
| `BR-REP-4` (репутация как тай-брейкер) | `LeaderElection` (сравнение репутации кандидатов) | `LeaderElectionTest` |
| `BR-BUDGET-1` (бюджет виртуальный, только учёт) | `BudgetExpense` + `V10__report.sql`: журнал трат без платежей; остаток считает сервер | `ReportServiceTest`, `ReportControllerTest` |
| `BR-BUDGET-4` (траты бюджета лидером) | `BudgetExpense.of`, `ReportService.spend`, `POST /api/proposals/{id}/expenses`; трата пишется на действующий мандат, остаток считается по нему; клиент: `ReportViewModel.spend` | `ReportServiceTest`, `ReportControllerTest`, `ReportViewModelTest` |
| `BR-BUDGET-5` (отдельного формата отчётности по тратам нет) | Отчёт — файлы и сообщения лидера (`ReportService.post`), журнал трат `budget_expense` — учёт, а не форма отчётности; остаток считается в `ReportService.reportOf` | `ReportServiceTest`, `ReportControllerTest` |
| `BR-BUDGET-6` (реальных денег и эскроу нет) | В схеме и API нет платёжных сущностей; бюджет — только числа | — (осознанно вне MVP) |
| `BR-BUDGET-7` (бюджет назначается заново при смене лидера) | `candidate.price` → `Proposal.assignTaskBudget` при выдаче мандата, `Proposal.reopenForRevote` сбрасывает `task_budget`; `budget_expense.mandate_id` (миграция `V13`) отделяет траты новой попытки от прошлых, которые остаются в журнале | `LegitimacyServiceTest`, `ReportServiceTest`, `FlywayMigrationTest` |
| `BR-BUDGET-8`, `BR-BUDGET-9` (цена кандидата обязательна, не выше потолка и видна до голосования) | `Candidate.nominate`/`reprice`, `NominateRequest`, `CandidateResponse.price`; клиент: `NominateForm` в `LegitimacySection`, `LegitimacyViewModel.nominate(price)` | `LeaderElectionTest`, `LegitimacyControllerTest`, `LegitimacyViewModelTest` |
| `BR-BUDGET-10` (без лидера виден потолок как ожидаемый бюджет) | `ReportService.budgetOf`: `task_budget`, а при его отсутствии `proposal.budget`; трата всё равно требует мандата (`requireLeader`) | `ReportServiceTest` |
| `BR-LIFE-2`, `BR-LIFE-3` (дискуссия и кандидаты параллельно решению) | `ProposalStage.DISCUSSION`: `DiscussionService`, `LegitimacyService.nominate` доступны до решения о выполнении | `DiscussionServiceTest`, `LegitimacyServiceTest` |
| `BR-LIFE-5`, `BR-LIFE-6` (лидер и исполнительный мандат) | `LegitimacyService.electLeader` при переходе в `IN_WORK`, `LeaderMandate`; право на отчёт и траты — `ReportService.requireLeader` | `LeaderElectionTest`, `ReportServiceTest` |
| `BR-LIFE-7` (отчёт — файл или сообщение) | `ReportService.post`, `attachment` + обычное `Message`; клиент: `LeaderReportScreen`, `ReportViewModel.postReport` | `ReportServiceTest`, `ReportControllerTest`, `ReportViewModelTest` |
| `BR-LIFE-8` (лидер запускает закрытие) | `Proposal.openClosure`, `ClosureService.open`, `POST /api/proposals/{id}/closure`; клиент: `CloseTaskScreen` | `ClosureServiceTest`, `ClosureControllerTest`, `ReportViewModelTest` |
| `BR-LIFE-9` (сообщество решает, выполнено ли) | `ClosureVote`, `Proposal.castClosureVote`/`closureTally`, `ClosureService.vote`/`finish` | `ClosureServiceTest`, `ClosureControllerTest` |
| `BR-OVER-7` (отчёты и дискуссия под ними) | Отчёт-текст пишется в `Message` и виден в ветке; файлы — `attachment` | `ReportServiceTest`, `DiscussionServiceTest` |
| `BR-OVER-9` (лидер не решает за сообщество) | `ClosureService.open` требует мандат, но не даёт голоса за выполнение; итог считает `VotingOutcome` | `ClosureServiceTest`, `ClosureControllerTest` |
| `BR-RANK-1`, `BR-RANK-3` (живость обсуждения без награды за объём) | `Proposal.popularity`: число участников и перевес голосов, а не количество сообщений | `ProposalRankingTest` |
| `BR-LEADER-2` (кандидатам можно сразу передавать доверие) | `LegitimacyService.nominate` + `delegate` доступны на стадии `DISCUSSION` | `LegitimacyServiceTest`, `LegitimacyControllerTest` |
| `BR-DISC-1` (живая лента) | `realtime/`: `EventPublisher`, `EventController`, `GET /api/events`; клиент: `ApiRealtimeClient`, `DiscussionViewModel` | `EventStreamTest`, `SseParserTest`, `DiscussionViewModelTest` |
| `BR-VOTE-4` (живые счётчики) | `EventType.VOTE_CHANGED` из `ProposalService.vote` и `ClosureService.vote`; клиент перезапрашивает карточку | `EventStreamTest`, `DiscussionViewModelTest` |
| `BR-VOTE-3` (право голоса неотчуждаемо) | Легитимность отделена от права голоса: `LegitimacyEvent` влияет на выбор лидера, но `DecisionVote` остаётся за каждым членом | `LegitimacyServiceTest`, `DecisionVoteTest` |
| `BR-VOTE-5` (голос «выполнено / нет») | `ClosureVote`, `Proposal.castClosureVote`, `POST /api/proposals/{id}/closure/votes`; клиент: `ReportViewModel.vote` | `ClosureServiceTest`, `ClosureControllerTest`, `ReportViewModelTest` |
| `BR-OVER-1`, `BR-OVER-3`, `BR-OVER-4` (равенство членов, дискуссия, решение) | Нет привилегий создателя: все действия проверяют только членство и мандат; дискуссия и голосование — `DiscussionService`, `ProposalService.vote` | `JoinRequestServiceTest`, `DiscussionServiceTest`, `ProposalControllerTest` |
| `BR-OVER-5`, `BR-OVER-6` (ситуативный лидер и его власть) | `LeaderMandate` привязан к задаче; `ReportService.requireLeader` и `ClosureService.requireLeader` | `LeaderElectionTest`, `ReportServiceTest`, `ClosureServiceTest` |
| `BR-OVER-8` (репутация рядом с аватаром после выполнения) | `ReputationService.awardForClosedTask` → `reputation_ledger`; профиль и `Leader` в доске задачи | `ClosureServiceTest`, `LegitimacyControllerTest` |
| `BR-PROB-1`–`BR-PROB-4` (иллюстрация проблемы) | Вступительный экран «боль» на демо-данных: шумный чат, спам, крикливые вместо умных, размытая ответственность | `DemoDataTest` |
| `BR-SPAM-2`, `BR-RISK-1`, `BR-LIMIT-4` (осознанно отложено) | В коде отсутствует по решению MVP — см. `peerflow.md`, раздел 10 | — |
| `BR-PRIV-1` (контент только для членов) | `SecurityConfig`: открыты `/api/auth/register`, `/api/auth/login` и health, остальное требует JWT; `ProposalService.loadProposal` ищет предложение только в своём сообществе; клиент: `AuthInterceptor`, `SessionViewModel` | `AuthControllerTest`, `JoinRequestControllerTest`, `ProposalControllerTest`, `SessionViewModelTest` |
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
| Realtime | SSE (односторонний поток «сервер → клиент»): Spring `SseEmitter`, на клиенте — OkHttp с построчным разбором |

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
| [`backend/compose.prod.yaml`](../backend/compose.prod.yaml) | Стек `postgres` + `backend`, named volumes `postgres_data` и `attachments`, лимит памяти JVM |
| [`backend/.env.example`](../backend/.env.example) | Шаблон секретов (`POSTGRES_PASSWORD`, `PEERFLOW_JWT_SECRET`) |
| [`backend/deploy/nginx-peerflow.conf`](../backend/deploy/nginx-peerflow.conf) | Проксирование `8443` → `127.0.0.1:8082` |
| [`backend/deploy.sh`](../backend/deploy.sh) | Обновление: `git pull` + пересборка и перезапуск стека на сервере |
| [`backend/deploy/README.md`](../backend/deploy/README.md) | Инструкция: первый запуск, обновление, откат |

Данные хранятся в named volumes, а не в каталогах на хосте: Docker сам
выставляет владельца из образа. Bind-mount каталога вложений принадлежал бы
`root`, а backend пишет под непривилегированным пользователем, и загрузка
аватара падала бы с `500`.

Обновление кода на сервере — `bash backend/deploy.sh` из клона репозитория
(`/opt/peerflow/app`); секреты (`.env`) лежат вне клона.

Адрес сервера в Android задаётся на сборке (`BASE_URL` из `BuildConfig`,
переопределяется свойствами `-PpeerflowDebugBaseUrl` / `-PpeerflowReleaseBaseUrl`)
— код при смене окружения не правится.

Развёртывание и CI/CD осознанно вне рамок MVP (`docs/peerflow.md`, раздел 10):
эти файлы нужны только для ручной проверки на реальном устройстве.

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
| Мобильное приложение | Демо-прототип на выдуманных данных (`android/`). Сеть не используется |
| Сервер | Каркас Spring Boot в `backend/`: приложение стартует, доступен health-эндпоинт, пороги MVP вынесены в конфигурацию. Бизнес-логики и API пока нет |
| База данных | PostgreSQL (локальная установка). Миграций ещё нет: Flyway подключён, схему пока создавать нечем |
| Realtime | Ещё нет |

> Сервер — пока только каркас: он поднимается и отвечает на проверку живости,
> но не хранит данных и не считает правил. Пока данных нет, поведение
> приложения остаётся прототипом для показа сообществу.

---

## 2. Общая архитектура

Монорепозиторий из двух частей:

- **Клиент** (`android/`) — Android, Kotlin + Jetpack
  Compose. Общается с сервером по сети.
- **Сервер** (`backend/`) — Java 21 + Spring Boot 3.5. Хранит состояние,
  считает правила. Сейчас — каркас без бизнес-логики.

```
Андроид-приложение  ──HTTP/JSON──►  Сервер (Spring Boot)  ──►  PostgreSQL
        ▲                                   │
        └──────────real-time события────────┘
```

Правило: **бизнес-правила считает сервер**, клиент их не дублирует. Иначе разные
платформы разойдутся в трактовке.

---

## 3. Клиент (Android)

### Что уже есть (демо-прототип)

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| `android/…/data/DemoData.kt` | Все данные и модель в памяти | Заглушка вместо сервера |
| `android/…/MainActivity.kt` | Навигация по экранам на состоянии | — |
| `android/…/ui/screens/PainMessengerScreen.kt` | Экран «боль»: мок мессенджера | Иллюстрация `BR-PROB-1`–`BR-PROB-4` |
| `android/…/ui/screens/ProposalListScreen.kt` | Список предложений, секции и сортировки | `BR-RANK-1`–`BR-RANK-5` |
| `android/…/ui/screens/NewProposalScreen.kt` | Внесение предложения с бюджетом | `BR-LIFE-1`, `BR-BUDGET-2` |
| `android/…/ui/screens/ProposalDetailScreen.kt` | Обсуждение, карточка лидера и отзыв легитимности | `BR-DISC-*`, `BR-LEGIT-5` |
| `android/…/ui/screens/LeaderReportScreen.kt` | Отчёт лидера | `BR-LIFE-7` |
| `android/…/ui/screens/CloseTaskScreen.kt` | Закрытие задачи, переход легитимности в репутацию | `BR-LIFE-9`, `BR-REP-1` |
| `android/…/ui/screens/ProfileScreen.kt` | Профиль: репутация и переданная легитимность | `BR-REP-2`, `BR-LEGIT-3` |

### Как будет (после подключения к серверу)

Раздел наполняется по ходу реализации. Здесь появятся: сетевой слой, экраны
входа и вступления, ViewModel-и по доменам и разметка экранов к требованиям.

---

## 4. Сервер

### Что уже есть (каркас)

| Место в коде | Что делает | Отношение к требованиям |
|---|---|---|
| [`backend/build.gradle.kts`](../backend/build.gradle.kts) | Java 21, Spring Boot 3.5.16, Gradle 8.9 (wrapper) | Инфраструктура |
| [`backend/…/PeerflowApplication.java`](../backend/src/main/java/ru/peerflow/PeerflowApplication.java) | Точка входа, `@ConfigurationPropertiesScan` | — |
| [`backend/…/config/PeerflowThresholds.java`](../backend/src/main/java/ru/peerflow/config/PeerflowThresholds.java) | Типизированные пороги MVP с проверкой границ | `BR-LIMIT-1`, `BR-LIMIT-2`, `BR-LIMIT-3` |
| [`backend/…/config/SecurityConfig.java`](../backend/src/main/java/ru/peerflow/config/SecurityConfig.java) | Каркас безопасности: health открыт, остальное закрыто, сессий нет | `BR-PRIV-1` |
| [`backend/src/main/resources/application.yml`](../backend/src/main/resources/application.yml) | Рабочие умолчания БД для локальной разработки, JPA `validate`, Flyway, Actuator | — |
| [`backend/src/main/resources/application-local.yml.example`](../backend/src/main/resources/application-local.yml.example) | Шаблон локального переопределения настроек БД | — |
| [`backend/…/support/PostgresTestBase.java`](../backend/src/test/java/ru/peerflow/support/PostgresTestBase.java) | База интеграционных тестов: настоящий PostgreSQL в контейнере, настройки подставляет `@ServiceConnection` | — |
| `backend/README.md` | Как настроить локальный PostgreSQL и запустить сервер | — |

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

Пакеты появятся по мере реализации, слои — как задумано:

- **Домены:** `auth`, `community`, `proposal`, `discussion`, `legitimacy`,
  `report`, `reputation`, `realtime`; общая конфигурация — `config`.
- **Слои внутри домена:** `web` (HTTP), `service` (правила), `domain`
  (сущности), `repo` (доступ к данным).
- **Миграции БД:** Flyway, по одной на домен.

Ниже — заметки о решениях, которые уже приняты и должны соблюдаться.

### Модель данных (набросок)

| Таблица | Назначение | Требования |
|---|---|---|
| `app_user` | Аккаунт, профиль, статус | `BR-JOIN-*`, `BR-PRIV-2` |
| `community` | Сообщество (в MVP одно) | `BR-JOIN-2` |
| `join_request`, `join_request_vote` | Заявки на вступление и голоса | `BR-JOIN-4`, `BR-JOIN-5`, `BR-VOTE-8` |
| `proposal` | Предложение, стадия, бюджет | `BR-LIFE-1`, `BR-BUDGET-2` |
| `proposal_decision_vote` | Голос «делаем / не делаем» | `BR-LIFE-4`, `BR-VOTE-4` |
| `candidate` | Кандидаты в лидеры | `BR-LEADER-1` |
| `message` | Сообщения дискуссии и ответы | `BR-DISC-1`, `BR-DISC-3` |
| `reaction` | Реакции на сообщения | `BR-DISC-2`, `BR-VOTE-6` |
| `legitimacy_event` | Журнал передачи и отзыва легитимности | `BR-LEGIT-2`, `BR-LEGIT-5` |
| `report`, `attachment` | Отчёты и файлы | `BR-LIFE-7`, `BR-BUDGET-5` |
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

---

## 5. Трассировка: требование → код

Таблица-указатель: где в системе реализовано каждое требование. Заполняется по
мере реализации, чтобы в новой сессии быстро сориентироваться.

| Требование | Где реализовано | Тесты |
|---|---|---|
| `BR-VOTE-2` (один человек — один голос) | пока нет | — |
| `BR-RANK-2` (метрика популярности) | прототип: `DemoData.popularity` | `DemoDataTest` |
| `BR-SPAM-1` (скрытие при 3 метках) | пока нет; порог задан в `PeerflowThresholds.spamHideMarks` | `PeerflowThresholdsTest` |
| `BR-LIMIT-1` (порог удержания 70%) | `PeerflowThresholds.legitimacyRetainRatio` | `PeerflowThresholdsTest` |
| `BR-LIMIT-2` (3 метки спама) | `PeerflowThresholds.spamHideMarks` | `PeerflowThresholdsTest` |
| `BR-LIMIT-3` (приём при 30%) | `PeerflowThresholds.joinAcceptRatio` | `PeerflowThresholdsTest` |
| `BR-LEADER-5` (мандат утрачен ниже 70%) | пока нет; порог задан в `PeerflowThresholds` | `PeerflowThresholdsTest` |
| `BR-REP-1` (легитимность → репутация) | прототип: `CloseTaskScreen` (визуально) | — |
| `BR-PRIV-1` (контент только для членов) | `SecurityConfig`: закрыто всё, кроме `/actuator/health` | `PeerflowApplicationTests` |

> Таблица неполная намеренно: она растёт вместе с системой. Если правило
> реализовано и его тут нет — это дефект документа, добавьте строку.

---

## 6. Технологический стек

| Часть | Технологии |
|---|---|
| Клиент | Android, Kotlin, Jetpack Compose |
| Сервер | Java 21, Spring Boot 3.5.16, Gradle 8.9 (wrapper) |
| Данные | PostgreSQL (локальная установка), Flyway, JPA/Hibernate |
| Тесты | JUnit 5, Testcontainers с PostgreSQL 16 (`PostgresTestBase`), JUnit (клиент) |
| Realtime | SSE (односторонний поток «сервер → клиент») |

Техническое обоснование выбора стека — в истории решений (при необходимости
заведём журнал решений отдельно).

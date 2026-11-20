# PeerFlow — сервер

Бэкенд сообщества: Java 21 + Spring Boot 3.5, PostgreSQL, Flyway, JPA/Hibernate.
Правила продукта живут в [`docs/peerflow.md`](../docs/peerflow.md), устройство
системы — в [`docs/architecture.md`](../docs/architecture.md).

## Требования

- JDK 21 (`C:/Program Files/Java/jdk-21`, путь зафиксирован в `gradle.properties`).
- Локально установленный PostgreSQL 13+ (Docker не обязателен для запуска;
  проверено на 13.3).
- Docker — **только для тестов**: они поднимают настоящий PostgreSQL
  в контейнере (Testcontainers).

## Настройка подключения к БД

В [`src/main/resources/application.yml`](src/main/resources/application.yml)
прописаны **рабочие умолчания для локальной разработки**, поэтому сервер
поднимается сразу после установки PostgreSQL — без дополнительной настройки:

| Параметр | Значение по умолчанию |
|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/peerflow` |
| `spring.datasource.username` | `usr` |
| `spring.datasource.password` | `usr` |

Если ваши значения отличаются, переопределите их — двумя способами, оба
не попадают в репозиторий:

1. Локальный файл `src/main/resources/application-local.yml` (в `.gitignore`).
   За основу можно взять шаблон
   [`application-local.yml.example`](src/main/resources/application-local.yml.example):

   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/peerflow
       username: usr
       password: usr
   ```

   Запуск: `.\gradlew.bat bootRun --args="--spring.profiles.active=local"`.

2. Переменные окружения (имеют приоритет над умолчаниями):

   | Переменная | Что задаёт |
   |---|---|
   | `PEERFLOW_DB_URL` | JDBC-URL, например `jdbc:postgresql://localhost:5432/peerflow` |
   | `PEERFLOW_DB_USERNAME` | пользователь БД |
   | `PEERFLOW_DB_PASSWORD` | пароль БД |

Схему создаёт и меняет только Flyway (`spring.jpa.hibernate.ddl-auto=validate`),
при старте Hibernate лишь сверяет сущности с таблицами.

Если PostgreSQL не запущен или база/пользователь не созданы, сервер не
стартует — с ошибкой подключения к DataSource. Это fail-fast, а не дефект:
сервер без БД бесполезен, и лучше увидеть это сразу, чем получить «работающее»
приложение, которое молча теряет данные.

Подготовка базы (один раз, из `psql` под суперпользователем):

```sql
CREATE USER usr WITH PASSWORD 'usr';
CREATE DATABASE peerflow OWNER usr;
```

Расширение `pgcrypto` создаётся миграцией `V1__baseline.sql` от имени владельца
базы, поэтому прав суперпользователя для этого не нужно — достаточно, чтобы
база принадлежала `usr`, как в примере выше.

## Миграции БД

Схему создаёт и меняет **только Flyway**. Миграции лежат в
[`src/main/resources/db/migration`](src/main/resources/db/migration),
имена — `V<номер>__<тема>.sql`. Hibernate схему не трогает:
`spring.jpa.hibernate.ddl-auto=validate`, поэтому расхождение сущности и таблицы
роняет старт сервера с явной ошибкой — это защита, а не дефект.

| Миграция | Что делает |
|---|---|
| [`V1__baseline.sql`](src/main/resources/db/migration/V1__baseline.sql) | Базовая: расширение `pgcrypto` (функция `gen_random_uuid()` для UUID-ключей) |
| [`V2__user_community.sql`](src/main/resources/db/migration/V2__user_community.sql) | Аккаунты и сообщество: `app_user` (логин, хэш пароля, статус, `BR-JOIN-1`/`BR-JOIN-2`), `community` |
| [`V3__join_request.sql`](src/main/resources/db/migration/V3__join_request.sql) | Заявки и голоса: `join_request`, `join_vote`; частичный уникальный индекс `uk_join_request_pending_applicant` (одна заявка «в работе» на человека), уникальность пары «заявка + голосующий» (`BR-VOTE-2`) |
| [`V4__proposal.sql`](src/main/resources/db/migration/V4__proposal.sql) | Предложения: `proposal` (автор, сообщество, тема, описание, бюджет, стадия), проверки «бюджет > 0» и допустимой стадии (`BR-LIFE-1`, `BR-BUDGET-2`) |
| [`V5__decision_vote.sql`](src/main/resources/db/migration/V5__decision_vote.sql) | Голоса «делаем / не делаем»: `proposal_decision_vote`, уникальность пары «предложение + голосующий» (`BR-VOTE-13`) |
| [`V6__discussion.sql`](src/main/resources/db/migration/V6__discussion.sql) | Дискуссия: `message`, `message_reaction`, одна реакция на человека (`BR-DISC-1`–`BR-DISC-3`, `BR-SPAM-1`) |
| [`V7__legitimacy.sql`](src/main/resources/db/migration/V7__legitimacy.sql) | Журнал легитимности `legitimacy_event` с колонкой `seq` (порядок событий); триггер запрещает `UPDATE` (`BR-LEGIT-2`, `BR-LEGIT-3`) |
| [`V8__leader.sql`](src/main/resources/db/migration/V8__leader.sql) | `candidate`, `leader_mandate` (частичный уникальный индекс действующего мандата), `proposal.voting_opened_at` (`BR-LEADER-1`–`BR-LEADER-5`) |
| [`V9__reputation.sql`](src/main/resources/db/migration/V9__reputation.sql) | Журнал репутации `reputation_ledger`, одно начисление на задачу, триггер запрещает `UPDATE` (`BR-REP-1`) |
| [`V10__report.sql`](src/main/resources/db/migration/V10__report.sql) | Отчёты: `attachment` (файлы лидера) и `budget_expense` (журнал трат; сумма > 0) (`BR-LIFE-7`, `BR-BUDGET-4`) |
| [`V11__closure.sql`](src/main/resources/db/migration/V11__closure.sql) | `closure_vote` (голос «выполнено / нет», уникальность пары «задача + голосующий») и `proposal.closure_opened_at` (`BR-LIFE-9`, `BR-VOTE-5`) |
| [`V12__candidate_price.sql`](src/main/resources/db/migration/V12__candidate_price.sql) | Бюджет как обязательство исполнителя: `candidate.price` (цена кандидата, > 0) и `proposal.task_budget` (цена действующего лидера) (`BR-BUDGET-2`, `BR-BUDGET-3`, `BR-BUDGET-8`, `BR-LIFE-6`, `BR-LIFE-10`) |
| [`V13__expense_mandate.sql`](src/main/resources/db/migration/V13__expense_mandate.sql) | `budget_expense.mandate_id`: трата принадлежит попытке выполнить задачу, поэтому после смены лидера остаток считается заново, а прошлые траты остаются в журнале (`BR-BUDGET-4`, `BR-BUDGET-7`) |

Что делает миграция при запуске:

- Flyway накатывает всё неприменённое **до старта** контекста Spring;
  `spring.flyway.baseline-on-migrate=true` позволяет подключиться к уже
  существующей базе без истории Flyway (она станет базовой точкой).
- Успешные миграции записываются в служебную таблицу `flyway_schema_history` —
  повторно они не применяются.
- Файл миграции после применения **не редактируется**: Flyway сверяет
  контрольную сумму. Изменилось правило — добавьте новую миграцию
  `V<n+1>__…`, а не правьте старую.

Проверить, что накатилось, можно прямо в базе:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Команды

```powershell
cd backend
.\gradlew.bat test        # модульные + smoke-тесты
.\gradlew.bat bootRun     # запуск сервера на :8080
.\gradlew.bat build       # сборка и тесты
```

## Проверка живости

`GET http://localhost:8080/actuator/health` — открыт без аутентификации,
возвращает `{"status":"UP"}`. Остальные эндпоинты требуют токен (`BR-PRIV-1`):
без него или с просроченным — `401`, с действующим, но без нужных прав —
`403`. Оба ответа приходят как `application/problem+json`.

## API

Все ответы — JSON, ошибки — `ProblemDetail` (`application/problem+json`,
поля `title`, `detail`, `status`, `timestamp`).

| Метод | Путь | Доступ | Что делает |
|---|---|---|---|
| `POST` | `/api/auth/register` | открыт | Регистрация: `multipart/form-data`, часть `request` (JSON) + необязательный файл `avatar`. `201` с токеном. Первый зарегистрировавшийся становится членом сообщества (`BR-JOIN-3`) |
| `POST` | `/api/auth/login` | открыт | Вход по логину и паролю, `200` с токеном. Неверная пара — `401` без подсказки, что именно неверно (`BR-JOIN-1`) |
| `GET` | `/api/auth/me` | токен | Профиль владельца токена, в том числе признак членства (`BR-JOIN-4`) |
| `POST` | `/api/join-requests` | токен | Подать заявку: `multipart/form-data`, часть `request` + необязательный `avatar`. `201`; повторная заявка при уже поданной — `409` |
| `GET` | `/api/join-requests` | токен + членство | Заявки в состоянии `PENDING` на рассмотрение (`BR-JOIN-4`, `BR-VOTE-1`) |
| `GET` | `/api/join-requests/mine` | токен | Последняя своя заявка в любом состоянии; `200` с телом или `204`, если заявок не было |
| `POST` | `/api/join-requests/{id}/votes` | токен + членство | Голос «за»/«против» (`{"accept": true}`). Повторный голос — `409` (`BR-VOTE-2`), отсутствующая заявка — `404` |
| `GET` | `/api/proposals?sort=` | токен + членство | Список предложений двумя секциями; `sort` — `popular` (по умолчанию), `budget`, `new` (`BR-RANK-4`, `BR-RANK-5`) |
| `GET` | `/api/proposals/{id}` | токен + членство | Карточка предложения: счётчики, свой голос, стадия (`BR-VOTE-4`) |
| `POST` | `/api/proposals` | токен + членство | Внести предложение: тема, описание, обязательный бюджет (`BR-LIFE-1`, `BR-BUDGET-2`) |
| `POST` | `/api/proposals/{id}/votes` | токен + членство | Голос «делаем / не делаем», повторный меняет сторону (`BR-VOTE-4`, `BR-VOTE-13`) |
| `GET` | `/api/proposals/{id}/messages?sort=` | токен + членство | Ветка обсуждения: `messages` + число `hidden` (скрытый спам); `sort` — `popular`, `new` (`BR-DISC-1`–`BR-DISC-5`) |
| `POST` | `/api/proposals/{id}/messages` | токен + членство | Сообщение или ответ (`parentId`) (`BR-DISC-1`, `BR-DISC-3`) |
| `POST` | `/api/messages/{id}/reactions` | токен + членство | Реакция «плюс / минус / спам», одна на человека (`BR-DISC-2`, `BR-VOTE-6`) |
| `GET` | `/api/proposals/{id}/legitimacy` | токен + членство | Доска задачи: кандидаты, лидер, шкала удержания (`BR-LEGIT-2`, `BR-LEADER-3`) |
| `POST` | `/api/proposals/{id}/legitimacy/{candidateId}` | токен + членство | Передать легитимность кандидату (`BR-LEGIT-2`) |
| `DELETE` | `/api/proposals/{id}/legitimacy` | токен + членство | Вернуть свою легитимность себе (`BR-LEGIT-5`) |
| `POST` | `/api/proposals/{id}/legitimacy/candidates` | токен + членство | Выдвинуть себя кандидатом в лидеры (`BR-LEADER-1`) |
| `GET` | `/api/me/legitimacy` | токен | Своя репутация и кому передана легитимность, по задачам (`BR-LEGIT-3`, `BR-REP-2`) |
| `GET` | `/api/proposals/{id}/report` | токен + членство | Отчёт: файлы, журнал трат, бюджет, потрачено и остаток (`BR-LIFE-7`, `BR-BUDGET-4`) |
| `POST` | `/api/proposals/{id}/reports` | токен + мандат лидера | Опубликовать отчёт: `multipart/form-data`, поле `text` и/или часть `file`. `201` (`BR-LIFE-7`) |
| `POST` | `/api/proposals/{id}/expenses` | токен + мандат лидера | Записать трату (`{"amount": 500, "note": "аренда"}`). Трата сверх остатка — `400` (`BR-BUDGET-4`) |
| `GET` | `/api/attachments/{id}` | токен + членство | Скачать файл отчёта этого сообщества (`BR-LIFE-7`, `BR-PRIV-1`) |
| `GET` | `/api/proposals/{id}/closure` | токен + членство | Состояние голосования «выполнено / нет»: счётчики, срок, свой ответ, начисленная репутация (`BR-LIFE-9`) |
| `POST` | `/api/proposals/{id}/closure` | токен + мандат лидера | Лидер запускает процедуру закрытия. `201`; повторно — `409` (`BR-LIFE-8`) |
| `POST` | `/api/proposals/{id}/closure/votes` | токен + членство | Голос «выполнено / нет», повторный меняет ответ (`BR-VOTE-5`) |
| `GET` | `/api/events` | токен + членство | SSE-поток событий сообщества (`text/event-stream`): `MESSAGE_POSTED`, `VOTE_CHANGED`, `REPORT_POSTED`, `TASK_CLOSED` (`BR-DISC-1`, `BR-VOTE-4`) |

Коды состояний по смыслу: `400` — некорректные данные, слишком большой файл
или трата сверх остатка, `401` — нет/просрочен токен либо аккаунт недоступен,
`403` — действие требует членства или мандата лидера, `404` — объекта нет или
он в чужом сообществе (неразличимо осознанно, `BR-PRIV-1`), `409` — действие
уже невозможно (заявка подана, голос уже подан, голосование не идёт).

Ошибки приходят в формате `application/problem+json` (`title`, `detail`,
`status`, `timestamp`), поэтому клиент показывает `detail` человеку.

## Правило приёма в сообщество

Порог считается от числа **действующих членов** на момент голосования
(`BR-LIMIT-3`):

```
нужно «за» = max(1, ceil(действующих членов × peerflow.thresholds.join-accept-ratio))
```

Считаем в `BigDecimal` с округлением вверх (`CEILING`) — иначе при 10 членах
30 % дали бы 2,999… и порог занизился бы. Минимум 1 — сообщество не может
требовать «дробного» согласия, а первый член уже есть (`BR-JOIN-3`).
Если оставшихся неголосовавших членов не хватает, чтобы порог стал
достижим, заявка отклоняется досрочно, не дожидаясь конца голосования.

## Токен доступа

Токен — JWT с подписью HS256. Сервер его не хранит: состояние отсутствует,
поэтому сессии и CSRF не нужны. Настройки — в
[`application.yml`](src/main/resources/application.yml), префикс
`peerflow.security.jwt`:

| Свойство | Переменная окружения | По умолчанию |
|---|---|---|
| `peerflow.security.jwt.secret` | `PEERFLOW_JWT_SECRET` | `peerflow-local-development-only-secret-key-32+` |
| `peerflow.security.jwt.ttl` | — | `PT12H` |

Секрет из репозитория — **только для локальной разработки**: он лежит в
открытом виде, значит тайной не является. На сервере `PEERFLOW_JWT_SECRET`
обязателен и не совпадает с этим значением; короче 32 символов сервер не
примет (`JwtProperties` падает на старте — HS256 требует 256-битный ключ).

## Файлы вложений

Файлы сохраняются на диск, в БД попадает только путь относительно корня
вложений:

| Свойство | По умолчанию | Что задаёт |
|---|---|---|
| `peerflow.storage.root` | `storage` | каталог вложений, относительный к рабочему каталогу процесса (`backend/`) |

- [`AvatarStorage`](src/main/java/ru/peerflow/community/service/AvatarStorage.java)
  кладёт аватары в подкаталог `avatars/` под именем `UUID.<ext>`. Принимаются
  только `png`, `jpg`, `jpeg`, `webp`, размер — не больше 5 МБ.
- [`ReportStorage`](src/main/java/ru/peerflow/report/service/ReportStorage.java)
  кладёт файлы отчётов в подкаталог `reports/`. Принимаются `pdf`, `txt`, `md`,
  `csv`, `png`, `jpg`, `jpeg`, `webp`, `doc`, `docx`, `xls`, `xlsx`, `ppt`,
  `pptx`, `odt`, `ods`, `odp`, размер — не больше 10 МБ.

Файл пишется на диск **до** записи в БД: при недоступном диске в базе не
остаётся вложения без файла. Каталог `storage/` в `.gitignore`: это данные, а
не исходный код, и он должен попадать в резервное копирование.

## Живые обновления (SSE)

`GET /api/events` открывает односторонний поток `text/event-stream`. Событие
несёт только вид изменения и идентификатор задачи — данных задачи в нём нет:

```
event: MESSAGE_POSTED
data: {"type":"MESSAGE_POSTED","proposalId":"…","at":"2026-11-20T10:00:00Z"}
```

Клиент по такому событию перезапрашивает экран и получает согласованное
состояние, посчитанное сервером. Событие публикуется **после коммита**
транзакции: отправленное раньше приглашало бы клиента прочитать то, чего в
базе ещё нет.

Подписки живут в памяти процесса (`EventPublisher`) и привязаны к сообществу:
события чужого сообщества не приходят (`BR-PRIV-1`). Поток открыт только
действующему члену — `403` для ожидающего, `401` без токена. Первое событие
`ready` подтверждает открытие потока; сервер закрывает его через 30 минут, и
клиент переподписывается сам.

Пока сервер один, внешний брокер (Redis, Kafka) не нужен. При переходе к
нескольким узлам меняется только `EventPublisher`.

## Пороги MVP

Пороги — конфигурация, а не «магические числа» в коде: бин
[`PeerflowThresholds`](src/main/java/ru/peerflow/config/PeerflowThresholds.java)
читает префикс `peerflow.thresholds`, проверяет границы при старте и имеет
значения по умолчанию из бизнес-описания.

| Свойство | По умолчанию | Требование |
|---|---|---|
| `peerflow.thresholds.legitimacy-retain-ratio` | `0.70` | `BR-LIMIT-1` |
| `peerflow.thresholds.spam-hide-marks` | `3` | `BR-LIMIT-2` |
| `peerflow.thresholds.join-accept-ratio` | `0.30` | `BR-LIMIT-3` |
| `peerflow.thresholds.proposal-voting-window` | `PT48H` | `BR-LIMIT-5` |
| `peerflow.thresholds.proposal-early-close-ratio` | `0.30` | `BR-LIMIT-6` |
| `peerflow.thresholds.proposal-early-close-lead` | `2` | `BR-VOTE-11` |

## Развёртывание (Docker)

Сервер собирается и запускается как контейнер: [`Dockerfile`](Dockerfile) +
[`compose.prod.yaml`](compose.prod.yaml) (PostgreSQL + backend в одном стеке).

```bash
# на сервере, из клона репозитория:
bash backend/deploy.sh
```

- `backend/deploy.sh` — обновляет код из git и пересобирает стек; это же и
  способ деплоя изменений. Подробно — в [`deploy/README.md`](deploy/README.md).
- `Dockerfile` — многоступенчатая сборка на `eclipse-temurin:21`; файл
  `gradle.properties` в образ не копируется (в нём локальный путь к JDK
  Windows, чужой для Linux).
- `compose.prod.yaml` — `postgres:17` и backend; наружу публикуется только
  `127.0.0.1:8082`, его проксирует хост-nginx
  ([`deploy/nginx-peerflow.conf`](deploy/nginx-peerflow.conf)).
- Память JVM ограничена (`JAVA_OPTS=-Xmx256m`): сервер делят с другим
  приложением.
- Секреты — в `.env` вне клона (в git не попадает): `POSTGRES_PASSWORD`,
  `PEERFLOW_JWT_SECRET` (не короче 32 символов).
- Вложения (аватары и файлы отчётов) переживают пересоздание контейнеров:
  каталог `peerflow.storage.root` смонтирован named volume
  `peerflow_attachments`.
- Фактическое окружение PeerFlow — `https://demiapp.ru:8443/` (тестовый
  сервер), `mayakspace.com` — будущий кооперативный хост.

## Тесты

Сейчас их **274** в 25 классах. Полный прогон:

```powershell
.\gradlew.bat test
```

| Класс | Тестов | Что проверяет |
|---|---|---|
| `PeerflowApplicationTests` | 3 | Контекст поднимается на PostgreSQL в контейнере, health открыт без аутентификации, подключение действительно к PostgreSQL |
| `AuthControllerTest` | 12 | Регистрация (в том числе первый член сообщества, `BR-JOIN-3`), вход, `GET /api/auth/me`, единый ответ на неверный логин и пароль (`BR-JOIN-1`), `401` без токена (`BR-PRIV-1`), `403` без членства |
| `JoinRequestServiceTest` | 11 | Подача заявки, повторная подача (`409`), порог приёма, досрочное отклонение, агрегат «заявка + голоса», запрет голоса вне сообщества |
| `JoinRequestControllerTest` | 13 | Все четыре эндпоинта заявок: коды `201`/`204`/`400`/`403`/`404`/`409`, содержимое ответов |
| `UserRepositoryTest` | 9 | Поиск по логину, подсчёт действующих членов, уникальность логина, статусы `app_user` |
| `JoinRequestThresholdTest` | 4 | Арифметика порога `max(1, ceil(действующих × 0.30))`, включая округление вверх |
| `PeerflowThresholdsTest` | 5 | Значения по умолчанию, переопределение из конфигурации, отказ при выходящих за границы значениях |
| `FlywayMigrationTest` | 4 | Миграции накатываются при старте, неприменённых и упавших нет, повторный `migrate()` ничего не меняет, `gen_random_uuid()` создан, в истории Flyway есть успешные записи |
| `ProposalServiceTest` | 11 | Создание предложения с бюджетом, отказ без бюджета и с неположительным, секции и сортировки, переход стадий |
| `ProposalControllerTest` | 15 | `GET`/`POST /api/proposals`, карточка, голосование, коды ошибок и закрытость точек |
| `ProposalRankingTest` | 13 | Метрика популярности (участники и перевес, а не объём сообщений), сортировки, секции |
| `DecisionVoteTest` | 15 | Голос «делаем / не делаем», смена стороны, один голос на человека |
| `VotingOutcomeTest` | 15 | Срок, досрочное закрытие (активность + перевес 2:1 + минимум два голоса), отказ без голосов |
| `DiscussionServiceTest` | 16 | Сообщения, ответы, реакции, скрытие спама, порядок веток |
| `DiscussionControllerTest` | 9 | Точки обсуждения и реакций: коды, вложенность ответов, закрытость |
| `MessageRankingTest` | 10 | Сортировки «Популярные / Новые», скрытие при 3 метках, подчинение веток тем же правилам |
| `LegitimacyServiceTest` | 15 | Передача и отзыв доверия, независимость по задачам, пересчёт мандата, «себе нельзя», повтор не пишет журнал |
| `LegitimacyControllerTest` | 11 | Точки легитимности, выдвижение, передача, отзыв, `GET /api/me/legitimacy` |
| `MandateThresholdTest` | 7 | Порог удержания на `BigDecimal`: ровно 70% держит мандат, 69% — нет |
| `LeaderElectionTest` | 13 | Тай-брейкеры: поддержка → репутация → дата вступления → id |
| `ReportServiceTest` | 13 | Публикация отчёта (текст, файл, оба), право лидера, траты и остаток, лимит размера и типов |
| `ReportControllerTest` | 10 | `GET /report`, `POST /reports` (multipart), `POST /expenses`, скачивание вложения, коды ошибок |
| `ClosureServiceTest` | 14 | Открытие процедуры, голоса «выполнено / нет», пересчёт итога, переход легитимности в репутацию, отказ от закрытия |
| `ClosureControllerTest` | 8 | Точки закрытия через HTTP: `201` на открытие, `403` не лидеру, `409` при повторном запуске, изоляция сообществ |
| `EventStreamTest` | 7 | SSE-поток: `ready`, событие сообщения и голоса, изоляция сообществ, `403` не члену, `401` без токена, отписка освобождает подписку |

Интеграционные тесты идут на **настоящем PostgreSQL в контейнере**
(Testcontainers), а не на H2: H2 не воспроизводит типы, ограничения и поведение
миграций PostgreSQL, поэтому зелёные тесты на нём ничего не гарантируют.

- База для тестов —
  [`PostgresTestBase`](src/test/java/ru/peerflow/support/PostgresTestBase.java):
  `@Testcontainers`, статический `PostgreSQLContainer` с `@ServiceConnection`.
  Spring Boot сам подставляет URL/логин/пароль контейнера в
  `spring.datasource.*`, поэтому в `src/test/resources/application-test.yml`
  параметров подключения нет.
- Один контейнер переиспользуется всеми классами тестов в рамках JVM.
- Нужен **запущенный Docker**. Если Docker недоступен, тесты падают явной
  ошибкой подключения к Docker API — это осознанно, «тихо» переходить на H2
  нельзя.
- Образ по умолчанию — `postgres:16-alpine`. Версию можно переопределить, чтобы
  прогнать тесты на той же мажорной версии, что и локальный сервер:

  ```powershell
  .\gradlew.bat test -PpeerflowPostgresImage=postgres:13-alpine
  ```


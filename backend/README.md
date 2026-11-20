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

Коды состояний по смыслу: `400` — некорректные данные или слишком большой
файл, `401` — нет/просрочен токен либо аккаунт недоступен, `403` — действие
требует членства в сообществе, `404` — заявки нет, `409` — действие уже
невозможно (заявка подана, заявка решена, голос уже подан).

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

Аватары сохраняются на диск, в БД попадает только путь относительно корня
вложений:

| Свойство | По умолчанию | Что задаёт |
|---|---|---|
| `peerflow.storage.root` | `storage` | каталог вложений, относительный к рабочему каталогу процесса (`backend/`) |

Файлы кладёт [`AvatarStorage`](src/main/java/ru/peerflow/community/service/AvatarStorage.java)
в подкаталог `avatars/` под именем `UUID.<ext>`. Принимаются только
`png`, `jpg`, `jpeg`, `webp`, размер — не больше 5 МБ. Каталог `storage/`
в `.gitignore`: это данные, а не исходный код.

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
  `PEERFLOW_JWT_SECRET` (не короче 32 символов), `PEERFLOW_STORAGE_DIR`.
- Вложения (аватары) переживают пересоздание контейнеров: каталог
  `peerflow.storage.root` смонтирован томом.
- Фактическое окружение PeerFlow — `https://demiapp.ru:8443/` (тестовый
  сервер), `mayakspace.com` — будущий кооперативный хост.

## Тесты

Сейчас их **59** в восьми классах:

| Класс | Тестов | Что проверяет |
|---|---|---|
| [`PeerflowApplicationTests`](src/test/java/ru/peerflow/PeerflowApplicationTests.java) | 3 | Контекст поднимается на PostgreSQL в контейнере, health открыт без аутентификации, подключение действительно к PostgreSQL |
| [`AuthControllerTest`](src/test/java/ru/peerflow/auth/AuthControllerTest.java) | 11 | Регистрация (в том числе первый член сообщества, `BR-JOIN-3`), вход, `GET /api/auth/me`, единый ответ на неверный логин и пароль (`BR-JOIN-1`), `401` без токена (`BR-PRIV-1`), `403` без членства |
| [`JoinRequestServiceTest`](src/test/java/ru/peerflow/community/JoinRequestServiceTest.java) | 11 | Подача заявки, повторная подача (`409`), порог приёма, досрочное отклонение, агрегат «заявка + голоса», запрет голоса вне сообщества |
| [`JoinRequestControllerTest`](src/test/java/ru/peerflow/community/JoinRequestControllerTest.java) | 12 | Все четыре эндпоинта заявок: коды `201`/`204`/`400`/`403`/`404`/`409`, содержимое ответов |
| [`UserRepositoryTest`](src/test/java/ru/peerflow/community/UserRepositoryTest.java) | 9 | Поиск по логину, подсчёт действующих членов, уникальность логина, статусы `app_user` |
| [`JoinRequestThresholdTest`](src/test/java/ru/peerflow/community/service/JoinRequestThresholdTest.java) | 4 | Арифметика порога `max(1, ceil(действующих × 0.30))`, включая округление вверх |
| `PeerflowThresholdsTest` | 5 | Значения по умолчанию, переопределение из конфигурации, отказ при выходящих за границы значениях |
| [`FlywayMigrationTest`](src/test/java/ru/peerflow/migration/FlywayMigrationTest.java) | 4 | Миграции накатываются при старте, неприменённых и упавших нет, повторный `migrate()` ничего не меняет, `gen_random_uuid()` создан, в истории Flyway есть успешные записи о `V1`–`V3` |

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

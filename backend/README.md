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

## Команды

```powershell
cd backend
.\gradlew.bat test        # модульные + smoke-тесты
.\gradlew.bat bootRun     # запуск сервера на :8080
.\gradlew.bat build       # сборка и тесты
```

## Проверка живости

`GET http://localhost:8080/actuator/health` — открыт без аутентификации,
возвращает `{"status":"UP"}`. Остальные эндпоинты требуют аутентификации
(`BR-PRIV-1`); проверка входа появится в задаче 5 плана.

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

## Тесты

- `PeerflowApplicationTests` — контекст поднимается на PostgreSQL в контейнере,
  health открыт без аутентификации, подключение действительно к PostgreSQL.
- `PeerflowThresholdsTest` — значения по умолчанию, переопределение из
  конфигурации и отказ при выходящих за границы значениях.

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

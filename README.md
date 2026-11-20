# PeerFlow

Приложение для организации работы сообщества, у которого нет постоянного
лидера. Власть исполнителя выдаётся сообществом на время конкретной задачи и
может быть отозвана в любой момент: вместе решаем, **что** делать, **кто**
исполняет и **достаточно ли хорошо** сделано.

## Что где лежит

| Путь | Что внутри |
|---|---|
| [`docs/peerflow.md`](docs/peerflow.md) | **Бизнес-описание.** Что делаем и по каким правилам. Источник правды по продукту |
| [`docs/architecture.md`](docs/architecture.md) | Как система устроена сейчас, со ссылками на требования |
| [`docs/plans/`](docs/plans/) | Планы реализации (текущий и завершённые) |
| [`backend/`](backend/README.md) | Сервер: Java 21 + Spring Boot. Регистрация и вход по JWT, профиль, заявки на вступление с голосованием членов |
| [`android/`](android/README.md) | Приложение: Kotlin + Jetpack Compose. Вход, регистрация и заявка на вступление работают через API; остальные экраны — демо-прототип |
| `presentation/` | Слайды для показа сообществу |

## Как всё устроено

Репозиторий — монорепо: рядом живут приложение, сервер и документация.
Продуктовые правила описаны бизнес-языком в `docs/peerflow.md`; техническое
состояние — в `docs/architecture.md`. Порядок работы задан в
[`AGENTS.md`](AGENTS.md).

## Быстрый старт

Сервер (нужен PostgreSQL; параметры — в `backend/README.md`):

```powershell
cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Приложение (эмулятор ходит на `http://10.0.2.2:8080/`):

```powershell
cd android
.\gradlew.bat :app:installDebug
adb shell am start -n ru.peerflow.demo/.MainActivity
```

Проверка на реальном устройстве (адрес сервера можно переопределить при сборке):

```powershell
cd android
.\gradlew.bat :app:assembleDebug -PpeerflowDebugBaseUrl=http://192.168.1.10:8080/
```

Подробности — в [`backend/README.md`](backend/README.md) и
[`android/README.md`](android/README.md). Развёртывание сервера — там же
(разделы про Docker); тестовый сервер PeerFlow — `https://demiapp.ru:8443/`.

## Тесты

```powershell
cd backend; .\gradlew.bat test                     # 59 тестов, Testcontainers (нужен Docker)
cd android; .\gradlew.bat :app:testDebugUnitTest    # 54 теста
```

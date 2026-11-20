# Развёртывание на сервере

Как обновлять backend на тестовом сервере (`https://demiapp.ru:8443/`).

## Как устроено

Схема: **git-клон репозитория + docker compose**. Код обновляется через
`git pull`, образ пересобирается на сервере.

```
/opt/peerflow/            — каталог развёртывания (в git не попадает)
├── .env                  — секреты (права 600)
├── storage/              — вложения: аватары
└── app/                  — git-клон репозитория PeerFlow
    └── backend/
        ├── compose.prod.yaml
        ├── deploy.sh
        └── deploy/nginx-peerflow.conf
```

Секреты и данные лежат **вне** клона, поэтому обновление кода их не трогает.
Имя проекта зафиксировано в `compose.prod.yaml` (`name: peerflow`), так что
имена контейнеров и том БД не зависят от каталога.

## Первый запуск (уже сделано на тестовом сервере)

1. Клонировать репозиторий в `/opt/peerflow/app`:
   ```bash
   git clone https://github.com/PPmishapPP/peerflow.git /opt/peerflow/app
   ```
2. Создать `/opt/peerflow/.env` (по образцу `backend/.env.example`) со
   случайными `POSTGRES_PASSWORD` и `PEERFLOW_JWT_SECRET` (≥ 32 символов):
   ```bash
   cp backend/.env.example ../.env
   openssl rand -hex 24      # пароль БД
   openssl rand -base64 48   # секрет JWT
   ```
3. Подключить nginx-конфиг и перезагрузить его:
   ```bash
   cp backend/deploy/nginx-peerflow.conf /etc/nginx/sites-available/peerflow
   ln -sf /etc/nginx/sites-available/peerflow /etc/nginx/sites-enabled/peerflow
   nginx -t && systemctl reload nginx
   ```
4. Запустить:
   ```bash
   cd /opt/peerflow/app && bash backend/deploy.sh
   ```

## Обновление после изменений в backend

На сервере — одна команда:

```bash
cd /opt/peerflow/app && bash backend/deploy.sh
```

Скрипт `backend/deploy.sh`:
- обновляет ветку (`git fetch` + `pull --ff-only`), по умолчанию `master`;
  другую ветку можно передать аргументом: `bash backend/deploy.sh phase-2`;
- пересобирает образ и перезапускает стек
  (`docker compose up -d --build`);
- показывает статус и ждёт живости `http://127.0.0.1:8082/actuator/health`.

Миграции Flyway применяются сами при старте backend — отдельного шага нет.
Данные и аватары сохраняются: том БД и каталог вложений не пересоздаются.

## Полезные команды

```bash
cd /opt/peerflow/app
docker compose --env-file /opt/peerflow/.env -f backend/compose.prod.yaml ps
docker compose --env-file /opt/peerflow/.env -f backend/compose.prod.yaml logs -f backend
docker compose --env-file /opt/peerflow/.env -f backend/compose.prod.yaml restart backend
```

## Откат

Вернуться на предыдущий коммит и пересобрать:

```bash
cd /opt/peerflow/app
git log --oneline -5
git checkout <нужный-коммит>
docker compose --env-file /opt/peerflow/.env -f backend/compose.prod.yaml up -d --build
```

> Миграции Flyway «вперёд» откатить нельзя: если новая миграция уже применена,
> возврат к старому коду потребует отката схемы отдельно. Для тестовой площадки
> проще поднять чистый том.

## Ограничения тестовой площадки

- Сертификат и домен — `demiapp.ru` (общий с соседним приложением); порт `8443`.
- CI/CD не настроен (вне рамок MVP): обновление запускается вручную.
- Резервное копирование не настроено.

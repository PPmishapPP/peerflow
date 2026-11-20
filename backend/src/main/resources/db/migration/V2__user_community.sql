-- V2__user_community.sql
--
-- Аккаунты и сообщество (BR-JOIN-1, BR-JOIN-2, BR-JOIN-3, BR-PRIV-2).
--
-- Соглашения из V1__baseline.sql: PK = uuid DEFAULT gen_random_uuid(),
-- время = timestamptz в UTC, удаление — пометкой статуса, а не DELETE.

-- Один сервер обслуживает одно сообщество (BR-JOIN-2): в MVP здесь одна строка.
CREATE TABLE community (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name       varchar(100) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

-- Аккаунт (BR-JOIN-1, BR-PRIV-2).
--
-- community_id пустой означает «аккаунт есть, но в сообщество ещё не принят»:
-- первый зарегистрировавшийся получает сообщество сразу (BR-JOIN-3), остальные —
-- только после того, как сообщество их примет (BR-JOIN-4, BR-JOIN-5).
--
-- status: ACTIVE — действующий аккаунт, DELETED — удалённый. Физического
-- удаления нет: строка остаётся, чтобы не рвать связи с историей сообщества
-- (BR-PRIV-2).
CREATE TABLE app_user (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    login         varchar(64)  NOT NULL,
    password_hash varchar(100) NOT NULL,
    display_name  varchar(100) NOT NULL,
    about         text,
    avatar_path   varchar(255),
    status        varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    joined_at     timestamptz,
    community_id  uuid,
    created_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uk_app_user_login UNIQUE (login),
    CONSTRAINT fk_app_user_community
        FOREIGN KEY (community_id) REFERENCES community (id),

    CONSTRAINT ck_app_user_status
        CHECK (status IN ('ACTIVE', 'DELETED'))
);

-- Поиск по логину — единственный частый запрос входа (BR-JOIN-1);
-- уникальный индекс уже создан ограничением uk_app_user_login.
-- Подсчёт членов нужен для порога приёма (BR-LIMIT-3).
CREATE INDEX idx_app_user_community_id ON app_user (community_id);

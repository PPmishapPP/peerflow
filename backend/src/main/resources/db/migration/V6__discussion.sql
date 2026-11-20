-- V6__discussion.sql
--
-- Дискуссия под предложением: сообщения, ответы и реакции
-- (BR-DISC-1, BR-DISC-2, BR-DISC-3, BR-VOTE-6).
--
-- Соглашения из V1__baseline.sql: PK = uuid DEFAULT gen_random_uuid(),
-- время = timestamptz в UTC, удаление — пометкой статуса, а не DELETE.
--
-- Сообщение принадлежит предложению, а не отчёту: отчёт в MVP — это тоже
-- сообщение под задачей (BR-LIFE-7), отдельной таблицы для него нет.
--
-- parent_id — ответ на другое сообщение (BR-DISC-3). Родитель проверяется
-- в домене на принадлежность тому же предложению: ветка не должна уезжать
-- в чужую задачу.
--
-- Текст храним как есть, без отдельного счётчика реакций: метки «плюс /
-- минус / спам» — это строки message_reaction, и все числа считаются из них.
-- Денормализовать score в MVP не нужно: ветка — это десятки сообщений, а
-- формула ранжирования живёт в домене и не должна дублироваться в схеме.
CREATE TABLE message (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id uuid        NOT NULL,
    author_id   uuid        NOT NULL,
    parent_id   uuid,
    body        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_message_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_author
        FOREIGN KEY (author_id) REFERENCES app_user (id),
    CONSTRAINT fk_message_parent
        FOREIGN KEY (parent_id) REFERENCES message (id),

    -- BR-DISC-1: сообщение — это аргумент, а не пустая строка.
    CONSTRAINT ck_message_body_not_blank CHECK (btrim(body) <> '')
);

-- Лента обсуждения предложения: и корни, и ответы читаются одним запросом
-- (BR-DISC-1, BR-DISC-3).
CREATE INDEX idx_message_proposal_created_at
    ON message (proposal_id, created_at);

-- Сборка веток: ответы ищутся по родителю (BR-DISC-3, BR-DISC-6).
CREATE INDEX idx_message_parent_id ON message (parent_id);

CREATE TABLE message_reaction (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id uuid        NOT NULL,
    member_id  uuid        NOT NULL,
    kind       varchar(8)  NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_message_reaction_message
        FOREIGN KEY (message_id) REFERENCES message (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_reaction_member
        FOREIGN KEY (member_id) REFERENCES app_user (id),

    -- BR-DISC-2, BR-VOTE-6: у одного члена — одна реакция на сообщение.
    -- Смена реакции — UPDATE этой строки, а не вторая строка: иначе «плюс»
    -- и «спам» от одного человека считались бы одновременно.
    CONSTRAINT uk_message_reaction_message_member UNIQUE (message_id, member_id),

    CONSTRAINT ck_message_reaction_kind
        CHECK (kind IN ('PLUS', 'MINUS', 'SPAM'))
);

-- Подсчёт меток по сообщению — самый частый запрос при показе ветки
-- (BR-SPAM-1: скрытие по числу меток «спам»).
CREATE INDEX idx_message_reaction_message_id ON message_reaction (message_id);

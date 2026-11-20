-- V4__proposal.sql
--
-- Предложения сообщества (BR-OVER-2, BR-LIFE-1, BR-BUDGET-1, BR-BUDGET-2).
--
-- Соглашения из V1__baseline.sql: PK = uuid DEFAULT gen_random_uuid(),
-- время = timestamptz в UTC, удаление — пометкой статуса, а не DELETE.
--
-- Почему бюджет — целое число рублей, а не numeric: в MVP бюджет виртуальный,
-- это учёт, а не касса (BR-BUDGET-1, BR-BUDGET-6). Копейки в учёте не нужны,
-- а целое число не даёт ошибок округления и совпадает с тем, что человек
-- вводит на экране.
--
-- stage: DISCUSSION — идёт обсуждение и голосование «делаем / не делаем»,
-- IN_WORK — решение принято, есть лидер и бюджет (BR-LIFE-6),
-- CLOSED — сообщество закрыло задачу (BR-LIFE-9),
-- REJECTED — сообщество решило «не делаем» (BR-LIFE-4).
CREATE TABLE proposal (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id    uuid         NOT NULL,
    community_id uuid         NOT NULL,
    title        varchar(200) NOT NULL,
    description  text         NOT NULL,
    budget       bigint       NOT NULL,
    stage        varchar(16)  NOT NULL DEFAULT 'DISCUSSION',
    created_at   timestamptz  NOT NULL DEFAULT now(),
    decided_at   timestamptz,

    CONSTRAINT fk_proposal_author
        FOREIGN KEY (author_id) REFERENCES app_user (id),
    CONSTRAINT fk_proposal_community
        FOREIGN KEY (community_id) REFERENCES community (id),

    CONSTRAINT ck_proposal_stage
        CHECK (stage IN ('DISCUSSION', 'IN_WORK', 'CLOSED', 'REJECTED')),

    -- BR-LIFE-1, BR-BUDGET-2: предложение без бюджета не существует.
    -- Проверка в базе — последняя линия: логика проверяет то же самое раньше.
    CONSTRAINT ck_proposal_budget_positive CHECK (budget > 0)
);

-- Список предложений сообщества: свежие сверху (BR-RANK-4, BR-RANK-5).
CREATE INDEX idx_proposal_community_created_at
    ON proposal (community_id, created_at DESC);

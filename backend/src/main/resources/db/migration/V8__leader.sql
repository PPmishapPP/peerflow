-- V8__leader.sql
--
-- Кандидаты в лидеры, действующий мандат и срок текущего голосования
-- (BR-LEADER-1, BR-LEADER-3, BR-LEADER-5, BR-LIFE-5, BR-LIFE-10).
--
-- Кандидат — не роль в аккаунте, а участие в конкретной задаче: по одной
-- задаче человек может быть кандидатом, по другой — нет (BR-LEGIT-3,
-- BR-LEADER-8).
CREATE TABLE candidate (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id uuid        NOT NULL,
    member_id   uuid        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_candidate_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_member
        FOREIGN KEY (member_id) REFERENCES app_user (id)
);

-- Одна кандидатура на человека по задаче: повторное выдвижение — тот же факт,
-- а не второй голос (BR-LEADER-1).
CREATE UNIQUE INDEX uk_candidate_proposal_member
    ON candidate (proposal_id, member_id);

-- Мандат лидера: кто ведёт задачу и с какой поддержкой он её получил.
--
-- Почему initial_support хранится, а не считается заново: порог удержания —
-- «потерял больше 30% изначально переданной легитимности» (BR-LEADER-5).
-- Без снимка на момент выборов сравнивать было бы не с чем: текущая поддержка
-- сама по себе не говорит, много это или мало.
--
-- Почему мандат отдельная строка, а не поле в proposal: мандатов у задачи
-- может быть несколько (утрата → переголосование → новый лидер,
-- BR-LIFE-10), и история мандатов объясняет, что произошло.
CREATE TABLE leader_mandate (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id     uuid        NOT NULL,
    leader_id       uuid        NOT NULL,
    initial_support bigint      NOT NULL,
    started_at      timestamptz NOT NULL DEFAULT now(),
    ended_at        timestamptz,
    end_reason      varchar(32),

    CONSTRAINT fk_leader_mandate_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_leader_mandate_leader
        FOREIGN KEY (leader_id) REFERENCES app_user (id),

    CONSTRAINT ck_leader_mandate_support CHECK (initial_support >= 0),

    -- Мандат либо действует, либо завершён с причиной. «Завершён без причины»
    -- сделал бы разбор «почему лидер сменился» невозможным.
    CONSTRAINT ck_leader_mandate_end
        CHECK ((ended_at IS NULL AND end_reason IS NULL)
            OR (ended_at IS NOT NULL AND end_reason IS NOT NULL)),

    CONSTRAINT ck_leader_mandate_reason
        CHECK (end_reason IS NULL OR end_reason IN ('MANDATE_LOST', 'TASK_CLOSED'))
);

-- Действующий мандат у задачи ровно один: два лидера одновременно — это не
-- «конкуренция», а невозможность понять, кто распоряжается бюджетом
-- (BR-LEGIT-4).
CREATE UNIQUE INDEX uk_leader_mandate_active
    ON leader_mandate (proposal_id)
    WHERE ended_at IS NULL;

CREATE INDEX idx_leader_mandate_proposal
    ON leader_mandate (proposal_id, started_at);

-- Срок текущего голосования (BR-VOTE-9).
--
-- Отдельно от created_at: после утраты мандата сообщество голосует заново
-- (BR-LEADER-7, BR-LIFE-10), и новый срок должен отсчитываться от нового
-- голосования. Если бы срок считался от создания предложения, повторное
-- голосование закрывалось бы мгновенно.
ALTER TABLE proposal
    ADD COLUMN voting_opened_at timestamptz;

-- Существующие предложения уже голосуются с момента внесения.
UPDATE proposal SET voting_opened_at = created_at WHERE voting_opened_at IS NULL;

ALTER TABLE proposal
    ALTER COLUMN voting_opened_at SET NOT NULL;

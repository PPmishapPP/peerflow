-- V7__legitimacy.sql
--
-- Журнал легитимности (BR-LEGIT-2, BR-LEGIT-3, BR-LEGIT-5).
--
-- Событие — факт: «член X передал свою легитимность кандидату Y по задаче Z»
-- или «член X забрал свою легитимность по задаче Z». Ничего не обновляется:
-- текущее состояние — это последнее событие по паре «задача + член». Так отзыв
-- не стирает историю, а дописывает её, и можно объяснить, почему лидер потерял
-- мандат.
--
-- candidate_id = NULL допустим только для REVOKE: отзыв возвращает
-- легитимность себе, и указывать получателя некому.
CREATE TABLE legitimacy_event (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Порядок событий — по этой колонке, а не по времени: два события одного
    -- мгновения (передача и отзыв в одну секунду) иначе нельзя было бы
    -- упорядочить, и «текущее состояние» стало бы недетерминированным.
    seq          bigint GENERATED ALWAYS AS IDENTITY,
    proposal_id  uuid        NOT NULL,
    member_id    uuid        NOT NULL,
    candidate_id uuid,
    kind         varchar(16) NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_legitimacy_event_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_legitimacy_event_member
        FOREIGN KEY (member_id) REFERENCES app_user (id),
    CONSTRAINT fk_legitimacy_event_candidate
        FOREIGN KEY (candidate_id) REFERENCES app_user (id),

    CONSTRAINT ck_legitimacy_event_kind
        CHECK (kind IN ('DELEGATE', 'REVOKE')),

    -- Передаче нужен получатель, отзыву — нет. Перепутать их — значит
    -- потерять легитимность из виду: состояние стало бы неопределённым.
    CONSTRAINT ck_legitimacy_event_candidate
        CHECK ((kind = 'DELEGATE' AND candidate_id IS NOT NULL)
            OR (kind = 'REVOKE' AND candidate_id IS NULL)),

    -- Себе передавать нечего: легитимность уже своя.
    CONSTRAINT ck_legitimacy_event_self
        CHECK (candidate_id IS NULL OR candidate_id <> member_id),

    CONSTRAINT uk_legitimacy_event_seq UNIQUE (seq)
);

-- Текущее состояние по задаче: читаем события предложения по порядку
-- (BR-LEGIT-3, BR-LEADER-5).
CREATE INDEX idx_legitimacy_event_proposal
    ON legitimacy_event (proposal_id, seq);

-- Журнал только дописывается: UPDATE сломал бы объяснимость «почему мандат
-- утрачен» (BR-LEGIT-5), поэтому запрещён на уровне базы, а не только
-- соглашением в коде.
--
-- DELETE не запрещён осознанно: история задачи не имеет смысла без самой
-- задачи, и удаление предложения (административное действие, в API его нет)
-- уносит её журнал через ON DELETE CASCADE. Запретить DELETE значило бы
-- сделать удаление задачи невозможным вовсе.
CREATE FUNCTION legitimacy_event_is_append_only()
    RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'журнал легитимности append-only: % запрещено', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_legitimacy_event_no_update
    BEFORE UPDATE ON legitimacy_event
    FOR EACH ROW EXECUTE FUNCTION legitimacy_event_is_append_only();

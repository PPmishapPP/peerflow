-- V9__reputation.sql
--
-- Журнал репутации (BR-REP-1, BR-REP-3, BR-REP-4).
--
-- Таблица появилась раньше закрытия задач осознанно: репутация — второй
-- тай-брейкер при выборе лидера (BR-REP-4), и без неё правило выбора нельзя ни
-- реализовать, ни проверить. Начисление при закрытии добавляется в следующей
-- миграции, а форма таблицы та же.
--
-- Почему журнал, а не счётчик в app_user: репутация — витрина достижений
-- (BR-REP-3), и человеку важно, за что именно она начислена. Счётчик пришлось
-- бы пересчитывать и невозможно было бы объяснить.
--
-- Почему proposal_id обязателен: репутация начисляется только за закрытую
-- задачу (BR-REP-1). Записи «просто так» сделали бы шкалу недоверяемой.
CREATE TABLE reputation_ledger (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id   uuid        NOT NULL,
    proposal_id uuid        NOT NULL,
    points      bigint      NOT NULL,
    reason      varchar(32) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_reputation_member
        FOREIGN KEY (member_id) REFERENCES app_user (id),
    CONSTRAINT fk_reputation_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,

    CONSTRAINT ck_reputation_points_positive CHECK (points > 0),
    CONSTRAINT ck_reputation_reason
        CHECK (reason IN ('TASK_CLOSED'))
);

-- Витрина и тай-брейкер: сумма по человеку, поэтому индекс ведёт от члена.
CREATE INDEX idx_reputation_ledger_member
    ON reputation_ledger (member_id);

-- Одно начисление на задачу: повторное закрытие не должно удваивать
-- репутацию (BR-REP-1).
CREATE UNIQUE INDEX uk_reputation_ledger_proposal
    ON reputation_ledger (proposal_id);

-- Репутация только дописывается: «отозвать» её нельзя, иначе витрина
-- достижений превратилась бы в переговорный ресурс.
CREATE FUNCTION reputation_ledger_is_append_only()
    RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'журнал репутации append-only: % запрещено', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reputation_ledger_no_update
    BEFORE UPDATE ON reputation_ledger
    FOR EACH ROW EXECUTE FUNCTION reputation_ledger_is_append_only();

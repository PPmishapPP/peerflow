-- V13__expense_mandate.sql
--
-- Траты принадлежат попытке выполнить задачу, а не задаче целиком
-- (BR-BUDGET-4, BR-BUDGET-7, BR-LIFE-10).
--
-- Почему понадобилась эта колонка. После утраты мандата и выбора нового лидера
-- у задачи новая цена и новая попытка выполнить работу. Если считать остаток
-- от всех трат за всю историю, новый лидер получил бы счёт, уже частично
-- потраченный предыдущим, и «бюджет 8000» превратился бы в «2000» без
-- какого-либо решения сообщества. Поэтому остаток считается по тратам
-- действующего мандата, а прошлые траты остаются в журнале как история: деньги
-- действительно были потрачены, и скрывать это нельзя.
--
-- Внешний ключ ведёт на мандат, а не на лидера: у одного человека в одной
-- задаче теоретически может быть несколько мандатов, и траты должны
-- привязываться к попытке, а не к личности.
ALTER TABLE budget_expense
    ADD COLUMN mandate_id uuid;

-- Заполнение для уже записанных трат: каждая относится к мандату, который
-- действовал в момент траты. Если такого нет (трата раньше первого мандата —
-- в корректных данных невозможно), берём самый ранний мандат задачи, чтобы
-- колонка стала обязательной без потери строк.
UPDATE budget_expense expense
SET mandate_id = coalesce(
        (SELECT mandate.id
         FROM leader_mandate mandate
         WHERE mandate.proposal_id = expense.proposal_id
           AND mandate.started_at <= expense.created_at
         ORDER BY mandate.started_at DESC, mandate.id
         LIMIT 1),
        (SELECT mandate.id
         FROM leader_mandate mandate
         WHERE mandate.proposal_id = expense.proposal_id
         ORDER BY mandate.started_at, mandate.id
         LIMIT 1)
    );

ALTER TABLE budget_expense
    ALTER COLUMN mandate_id SET NOT NULL,
    ADD CONSTRAINT fk_budget_expense_mandate
        FOREIGN KEY (mandate_id) REFERENCES leader_mandate (id) ON DELETE CASCADE;

COMMENT ON COLUMN budget_expense.mandate_id IS
    'Мандат, в рамках которого сделана трата (BR-BUDGET-4, BR-BUDGET-7)';

-- Остаток считается суммой по действующему мандату — индекс ведёт от него.
CREATE INDEX idx_budget_expense_mandate
    ON budget_expense (mandate_id);

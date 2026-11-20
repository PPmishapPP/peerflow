-- V10__report.sql
--
-- Отчёты лидера: файлы и траты бюджета (BR-LIFE-7, BR-BUDGET-4, BR-BUDGET-5).
--
-- Отдельной таблицы «отчёт» нет осознанно: отчётом считается любой
-- прикреплённый файл или написанное сообщение под задачей, и отдельного
-- формата нет (BR-LIFE-7, BR-BUDGET-5). Написанное сообщение — это строка
-- message (V6), и обсуждается оно там же; файл получает одну таблицу
-- метаданных. Завести ещё и таблицу report значило бы хранить один и тот же
-- факт дважды и решать, какая из двух записей главная.
--
-- В БД — только описание файла, сам файл лежит на диске
-- (peerflow.storage.root/reports). Так резервное копирование базы не тянет
-- десятки мегабайт, а файл можно отдать потоком, не читая его в память.
CREATE TABLE attachment (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id  uuid         NOT NULL,
    author_id    uuid         NOT NULL,
    path         varchar(255) NOT NULL,
    file_name    varchar(255) NOT NULL,
    content_type varchar(128) NOT NULL,
    size_bytes   bigint       NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_attachment_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_attachment_author
        FOREIGN KEY (author_id) REFERENCES app_user (id),

    -- Пустой файл — не отчёт: ноль байт ничего не сообщает сообществу.
    CONSTRAINT ck_attachment_size CHECK (size_bytes > 0),
    CONSTRAINT ck_attachment_path_not_blank CHECK (btrim(path) <> ''),
    CONSTRAINT ck_attachment_name_not_blank CHECK (btrim(file_name) <> '')
);

-- Отчёты задачи читаются списком, свежие сверху.
CREATE INDEX idx_attachment_proposal_created_at
    ON attachment (proposal_id, created_at);

-- Трата бюджета задачей (BR-BUDGET-4).
--
-- Почему журнал, а не поле «сколько потрачено» в proposal: бюджет виртуальный
-- (BR-BUDGET-1), и сообществу важно видеть, на что ушли деньги, а не только
-- остаток. Счётчик пришлось бы пересчитывать, и объяснить число было бы нечем.
CREATE TABLE budget_expense (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id uuid         NOT NULL,
    member_id   uuid         NOT NULL,
    amount      bigint       NOT NULL,
    note        varchar(500),
    created_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_budget_expense_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_expense_member
        FOREIGN KEY (member_id) REFERENCES app_user (id),

    -- Нулевая или отрицательная трата — не трата: возвратов в MVP нет.
    CONSTRAINT ck_budget_expense_amount CHECK (amount > 0)
);

-- Остаток считается суммой по задаче — индекс ведёт от неё.
CREATE INDEX idx_budget_expense_proposal
    ON budget_expense (proposal_id, created_at);

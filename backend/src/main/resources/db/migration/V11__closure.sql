-- V11__closure.sql
--
-- Закрытие задачи: голосование «выполнено / не выполнено» и момент начала
-- процедуры (BR-LIFE-8, BR-LIFE-9, BR-VOTE-5, BR-OVER-9).
--
-- Почему закрытие — отдельное голосование, а не переиспользование
-- proposal_decision_vote: вопрос другой. «Делаем ли» решается до работы, и
-- голос «за» там означает согласие начать. «Выполнено ли» решается после, и
-- тот же человек вправе ответить иначе. Одна таблица с флагом «этап» сделала
-- бы два разных вопроса одним и позволила бы старым голосам решать новый.
CREATE TABLE closure_vote (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id uuid        NOT NULL,
    voter_id    uuid        NOT NULL,
    completed   boolean     NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_closure_vote_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_closure_vote_voter
        FOREIGN KEY (voter_id) REFERENCES app_user (id)
);

-- Один голос на человека, как и в решении «делаем / не делаем»
-- (BR-VOTE-2): передумать можно, проголосовать дважды — нет.
CREATE UNIQUE INDEX uk_closure_vote_proposal_voter
    ON closure_vote (proposal_id, voter_id);

-- Момент, когда лидер запустил процедуру закрытия (BR-LIFE-8).
--
-- Отдельно от decided_at: decided_at — момент итога, а здесь нужно время
-- начала голосования, чтобы отсчитывать его срок. Хранить отдельно от
-- voting_opened_at тоже нужно: голосование «делаем» и голосование «выполнено»
-- — разные процедуры с разными сроками.
ALTER TABLE proposal
    ADD COLUMN closure_opened_at timestamptz;

-- V5__decision_vote.sql
--
-- Голоса «делаем / не делаем» по предложению (BR-VOTE-4, BR-VOTE-7,
-- BR-LIFE-4).
--
-- Голос храним, а не считаем на лету из счётчика: нужна история и защита от
-- повторного голоса (BR-VOTE-2: один человек — один голос). Голос можно
-- поменять, пока голосование не закрыто (BR-VOTE-13), поэтому строка
-- обновляется, а не добавляется вторая.
--
-- Сроки голосования (BR-VOTE-9) в схеме не хранятся: они считаются от
-- created_at предложения и порогов конфигурации. Иначе порог нельзя было бы
-- поменять, не переписывая историю.
CREATE TABLE proposal_decision_vote (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id uuid        NOT NULL,
    voter_id    uuid        NOT NULL,
    in_favor    boolean     NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_decision_vote_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_decision_vote_voter
        FOREIGN KEY (voter_id) REFERENCES app_user (id),

    -- BR-VOTE-2, BR-VOTE-4: один член голосует по предложению ровно один раз.
    -- Смена голоса — UPDATE этой строки, а не вторая строка.
    CONSTRAINT uk_decision_vote_proposal_voter UNIQUE (proposal_id, voter_id)
);

-- Подсчёт голосов по предложению и проверка «голосовал ли я» — самый частый
-- запрос при показе списка и голосовании.
CREATE INDEX idx_decision_vote_proposal_id ON proposal_decision_vote (proposal_id);

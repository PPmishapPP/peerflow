-- V3__join_request.sql
--
-- Заявки на вступление и голоса за приём
-- (BR-JOIN-4, BR-JOIN-5, BR-VOTE-8, BR-LIMIT-3).
--
-- Соглашения из V1__baseline.sql: PK = uuid DEFAULT gen_random_uuid(),
-- время = timestamptz в UTC, удаление — пометкой статуса, а не DELETE.
--
-- Почему две таблицы, а не «флаг в app_user»: заявка — это история решения
-- сообщества (кого и когда приняли), а голоса — доказательство, на основании
-- которого решение принято. Их нельзя перезаписывать.

-- Заявка на вступление (BR-JOIN-4).
--
-- status: PENDING — ждёт голосования, ACCEPTED — заявку приняли и человек стал
-- членом сообщества, REJECTED — набрать порог уже невозможно
-- (BR-JOIN-5, BR-LIMIT-3).
--
-- about и avatar_path дублируют профиль не случайно: человек заполняет их
-- именно при подаче заявки, и сообщество должно видеть то, что он показал
-- в момент заявки, даже если потом он изменит профиль.
CREATE TABLE join_request (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id uuid         NOT NULL,
    community_id uuid         NOT NULL,
    about        text,
    avatar_path  varchar(255),
    status       varchar(16)  NOT NULL DEFAULT 'PENDING',
    created_at   timestamptz  NOT NULL DEFAULT now(),
    decided_at   timestamptz,

    CONSTRAINT fk_join_request_applicant
        FOREIGN KEY (applicant_id) REFERENCES app_user (id),
    CONSTRAINT fk_join_request_community
        FOREIGN KEY (community_id) REFERENCES community (id),

    CONSTRAINT ck_join_request_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

-- Один человек — одна заявка в работе. Отклонённую заявку можно подать
-- заново, поэтому уникальность касается только статуса PENDING.
CREATE UNIQUE INDEX uk_join_request_pending_applicant
    ON join_request (applicant_id)
    WHERE status = 'PENDING';

-- Список заявок сообщества: свежие сверху.
CREATE INDEX idx_join_request_community_created_at
    ON join_request (community_id, created_at DESC);

-- Голос члена сообщества за приём (BR-VOTE-8).
--
-- is_accept = true — «за приём», false — «против». Голос храним, а не считаем
-- на лету из счётчика: нужна история и защита от повторного голоса
-- (BR-VOTE-2: один человек — один голос).
CREATE TABLE join_vote (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    join_request_id uuid        NOT NULL,
    member_id       uuid        NOT NULL,
    is_accept       boolean     NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_join_vote_request
        FOREIGN KEY (join_request_id) REFERENCES join_request (id) ON DELETE CASCADE,
    CONSTRAINT fk_join_vote_member
        FOREIGN KEY (member_id) REFERENCES app_user (id),

    -- BR-VOTE-2: один член сообщества голосует по заявке ровно один раз.
    CONSTRAINT uk_join_vote_request_member UNIQUE (join_request_id, member_id)
);

-- Подсчёт голосов по заявке — самый частый запрос при голосовании.
CREATE INDEX idx_join_vote_request_id ON join_vote (join_request_id);

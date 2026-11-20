-- V1__baseline.sql
--
-- Базовая миграция PeerFlow: служебные объекты, на которых стоят остальные
-- миграции. Пользовательских таблиц здесь нет — они появляются в доменных
-- миграциях (V2__user_community.sql и далее).
--
-- Соглашения, которые задаёт эта миграция и соблюдают все последующие:
--   * первичные ключи — UUID, значение по умолчанию gen_random_uuid();
--   * время — timestamptz в UTC (Hibernate настроен на UTC в application.yml);
--   * схему меняет только Flyway, Hibernate её лишь сверяет
--     (spring.jpa.hibernate.ddl-auto=validate).

-- gen_random_uuid() входит в ядро PostgreSQL начиная с версии 13, но
-- расширение pgcrypto делает ту же функцию доступной и на более старых
-- сборках. Оно «доверенное» (trusted): владельцу базы не нужны права
-- суперпользователя. См. backend/README.md — база создаётся с OWNER usr.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

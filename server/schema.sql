-- MEGA CREATOR AI: справочная схема PostgreSQL для будущей миграции.
-- Секреты и ключи здесь НЕ хранятся: только пользовательские и биллинговые данные.
--
-- Рабочий сервер (server.js) сейчас использует JSON-хранилище
-- server/data/store.json с коллекциями users, sessions, codes, projects,
-- generations, payments, usage. Типы id в JSON — строки, а не BIGSERIAL.
-- ID в БД и в JSON можно сопоставить как строковое значение числа/hex.
-- Код подтверждения и токен хранятся только хэшами SHA-256, с сроком жизни;
-- коллекция usage соответствует таблице limits (счётчик за текущий день).

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    phone         VARCHAR(32) UNIQUE,
    email         VARCHAR(255) UNIQUE,
    password_hash TEXT,
    role          VARCHAR(24) NOT NULL DEFAULT 'user',
    tariff        VARCHAR(16) NOT NULL DEFAULT 'FREE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS sessions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS projects (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category   VARCHAR(48) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    content    TEXT NOT NULL,
    file_url   TEXT,
    favorite   BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS generations (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    feature     VARCHAR(32) NOT NULL,
    provider    VARCHAR(64),
    cost_units  INTEGER NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS generations_user_day_idx
    ON generations (user_id, created_at);

CREATE TABLE IF NOT EXISTS payments (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider           VARCHAR(64) NOT NULL,
    external_id        VARCHAR(255) UNIQUE,
    amount_rub         NUMERIC(10, 2) NOT NULL,
    status             VARCHAR(32) NOT NULL,
    tariff             VARCHAR(16),
    verified_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS limits (
    user_id     BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    feature     VARCHAR(32) NOT NULL,
    used_today  INTEGER NOT NULL DEFAULT 0,
    day         DATE NOT NULL DEFAULT CURRENT_DATE
);

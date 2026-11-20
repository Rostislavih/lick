-- Первая версия схемы (раздел 7.2 гайда).
-- Уже применённую миграцию не редактируем: ошибся — пиши V2.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    display_name  TEXT NOT NULL,
    password_hash TEXT NOT NULL,                     -- не пароль, а хэш
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chats (
    id             BIGSERIAL PRIMARY KEY,
    type           TEXT NOT NULL CHECK (type IN ('DIRECT', 'GROUP')),
    title          TEXT,
    direct_key     TEXT UNIQUE,                      -- "minUserId:maxUserId" для личек, NULL для групп
    last_seq       BIGINT NOT NULL DEFAULT 0,        -- счётчик сообщений: выдача messages.seq
    last_event_seq BIGINT NOT NULL DEFAULT 0,        -- счётчик изменений: выдача chat_events.event_seq
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE members (
    chat_id       BIGINT NOT NULL REFERENCES chats(id),
    user_id       BIGINT NOT NULL REFERENCES users(id),
    role          TEXT NOT NULL DEFAULT 'MEMBER',
    last_read_seq BIGINT NOT NULL DEFAULT 0,
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chat_id, user_id)
);
CREATE INDEX members_by_user ON members(user_id);    -- «все чаты пользователя»

CREATE TABLE messages (
    id                BIGSERIAL PRIMARY KEY,
    chat_id           BIGINT NOT NULL REFERENCES chats(id),
    seq               BIGINT NOT NULL,
    sender_id         BIGINT NOT NULL REFERENCES users(id),
    client_message_id UUID   NOT NULL,
    content           JSONB  NOT NULL,               -- {"type":"text","text":"..."}
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at         TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ,                   -- soft delete
    UNIQUE (chat_id, seq),                           -- он же индекс для пагинации
    UNIQUE (sender_id, client_message_id)            -- идемпотентность
);

-- Журнал изменений чата: всё, что клиент должен узнать, даже если был офлайн.
CREATE TABLE chat_events (
    chat_id    BIGINT NOT NULL REFERENCES chats(id),
    event_seq  BIGINT NOT NULL,
    type       TEXT   NOT NULL,                      -- message_new / message_edited / message_deleted / reaction / read
    message_id BIGINT REFERENCES messages(id),       -- NULL, если событие не про сообщение
    payload    JSONB  NOT NULL,                      -- ChatChange в JSON
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chat_id, event_seq)                 -- он же индекс для «события после N»
);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,               -- он же sid: вход на конкретном устройстве
    user_id     BIGINT NOT NULL REFERENCES users(id),
    token_hash  TEXT   NOT NULL UNIQUE,              -- сам токен не храним, только хэш
    device_name TEXT,                                -- «Pixel 8», «Desktop» — для списка сессий
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),  -- «когда входил» в списке сессий
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ                          -- не NULL = сессия отозвана
);
CREATE INDEX refresh_tokens_by_user ON refresh_tokens(user_id);   -- список сессий, logout-all

-- Push-токены устройств (раздел 11.2).
CREATE TABLE devices (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    platform   TEXT   NOT NULL CHECK (platform IN ('android', 'ios')),
    push_token TEXT   NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX devices_by_user ON devices(user_id);    -- «куда слать пуш этому пользователю»

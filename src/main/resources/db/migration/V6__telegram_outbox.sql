CREATE TABLE telegram_outbox (
    id               BIGSERIAL PRIMARY KEY,
    chat_id          BIGINT       NOT NULL,
    message_type     VARCHAR(32)  NOT NULL,
    payload_json     TEXT         NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    attempts         INTEGER      NOT NULL DEFAULT 0,
    max_attempts     INTEGER      NOT NULL DEFAULT 8,
    next_attempt_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at          TIMESTAMP WITH TIME ZONE,
    last_error       TEXT,
    dedup_key        VARCHAR(128)
);

CREATE INDEX idx_telegram_outbox_pending
    ON telegram_outbox (status, next_attempt_at);

CREATE INDEX idx_telegram_outbox_chat
    ON telegram_outbox (chat_id, created_at DESC);

CREATE UNIQUE INDEX idx_telegram_outbox_dedup
    ON telegram_outbox (dedup_key);

CREATE TABLE telegram_user (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT       NOT NULL UNIQUE,
    chat_id          BIGINT       NOT NULL,
    language_code    VARCHAR(16),
    timezone         VARCHAR(64),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE telegram_user_preference (
    telegram_user_id  BIGINT       PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE,
    digest_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    digest_hour       SMALLINT     NOT NULL DEFAULT 6,
    quiet_hours_start SMALLINT,
    quiet_hours_end   SMALLINT,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE beach_subscription (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT       NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    beach_id         BIGINT       NOT NULL REFERENCES beach(id) ON DELETE CASCADE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (telegram_user_id, beach_id)
);

CREATE INDEX idx_beach_subscription_beach
    ON beach_subscription (beach_id);

CREATE TABLE beach_decision_history (
    id            BIGSERIAL PRIMARY KEY,
    beach_id      BIGINT       NOT NULL REFERENCES beach(id) ON DELETE CASCADE,
    recommendation VARCHAR(32) NOT NULL,
    reason_codes  TEXT         NOT NULL,
    freshness_bucket VARCHAR(16) NOT NULL,
    signature_hash VARCHAR(64) NOT NULL,
    generated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_beach_decision_history_latest
    ON beach_decision_history (beach_id, generated_at DESC);

CREATE TABLE alert_delivery (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT       NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    beach_id         BIGINT       NOT NULL REFERENCES beach(id) ON DELETE CASCADE,
    signature_hash   VARCHAR(64)  NOT NULL,
    sent_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (telegram_user_id, beach_id, signature_hash)
);

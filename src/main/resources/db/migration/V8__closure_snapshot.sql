-- Closure snapshot: latest known open/closed status per beach, whether from a municipal feed
-- (BEACH_CLOSURE adapter) or an admin override.
CREATE TABLE IF NOT EXISTS closure_snapshot (
    id                   BIGSERIAL PRIMARY KEY,
    beach_id             BIGINT NOT NULL REFERENCES beach(id) ON DELETE CASCADE,
    closed               BOOLEAN NOT NULL,
    reason               VARCHAR(255),
    source               VARCHAR(32) NOT NULL,
    effective_from       TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_until      TIMESTAMP WITH TIME ZONE,
    raw_payload_json     TEXT,
    captured_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_closure_snapshot_beach_captured
    ON closure_snapshot (beach_id, captured_at DESC);

CREATE INDEX IF NOT EXISTS idx_closure_snapshot_effective
    ON closure_snapshot (effective_from, effective_until);

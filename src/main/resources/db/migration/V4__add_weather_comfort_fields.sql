ALTER TABLE sea_condition_snapshot
    ADD COLUMN IF NOT EXISTS relative_humidity_pct DOUBLE PRECISION;

ALTER TABLE sea_condition_snapshot
    ADD COLUMN IF NOT EXISTS uv_index DOUBLE PRECISION;

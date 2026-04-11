-- Reference tables

CREATE TABLE city (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    country    VARCHAR(100) NOT NULL,
    timezone   VARCHAR(50)  NOT NULL
);

CREATE TABLE beach (
    id                  BIGSERIAL PRIMARY KEY,
    city_id             BIGINT       NOT NULL REFERENCES city(id),
    display_name        VARCHAR(200) NOT NULL,
    slug                VARCHAR(100) NOT NULL UNIQUE,
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_swimming   BOOLEAN      NOT NULL DEFAULT TRUE,
    has_lifeguards      BOOLEAN      NOT NULL DEFAULT FALSE,
    has_camera          BOOLEAN      NOT NULL DEFAULT FALSE,
    has_jellyfish_source BOOLEAN     NOT NULL DEFAULT FALSE,
    notes               TEXT
);

CREATE TABLE beach_alias (
    beach_id BIGINT       NOT NULL REFERENCES beach(id),
    alias    VARCHAR(200) NOT NULL,
    PRIMARY KEY (beach_id, alias)
);

CREATE TABLE beach_source_mapping (
    id            BIGSERIAL PRIMARY KEY,
    beach_id      BIGINT      NOT NULL REFERENCES beach(id),
    source_type   VARCHAR(50) NOT NULL,
    external_key  VARCHAR(200),
    external_name VARCHAR(200),
    is_primary    BOOLEAN     NOT NULL DEFAULT TRUE,
    metadata_json TEXT,
    UNIQUE (beach_id, source_type)
);

-- Snapshot tables

CREATE TABLE sea_condition_snapshot (
    id                   BIGSERIAL PRIMARY KEY,
    beach_id             BIGINT      NOT NULL REFERENCES beach(id),
    source_type          VARCHAR(50) NOT NULL,
    captured_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_from           TIMESTAMP WITH TIME ZONE,
    valid_to             TIMESTAMP WITH TIME ZONE,
    wave_height_m        DOUBLE PRECISION,
    sea_risk_level       VARCHAR(20),
    wind_speed_mps       DOUBLE PRECISION,
    wind_direction       VARCHAR(10),
    sea_temperature_c    DOUBLE PRECISION,
    air_temperature_c    DOUBLE PRECISION,
    raw_payload_json     TEXT,
    source_confidence    VARCHAR(20),
    interval_is_inferred BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_sea_snap_beach_captured ON sea_condition_snapshot (beach_id, captured_at DESC);

CREATE TABLE health_advisory_snapshot (
    id               BIGSERIAL PRIMARY KEY,
    beach_id         BIGINT      NOT NULL REFERENCES beach(id),
    source_type      VARCHAR(50) NOT NULL,
    captured_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_from       TIMESTAMP WITH TIME ZONE,
    valid_to         TIMESTAMP WITH TIME ZONE,
    is_active        BOOLEAN     NOT NULL DEFAULT FALSE,
    advisory_type    VARCHAR(100),
    message          TEXT,
    raw_payload_json TEXT
);

CREATE INDEX idx_advisory_beach_captured ON health_advisory_snapshot (beach_id, captured_at DESC);

-- Schedule tables

CREATE TABLE lifeguard_schedule (
    id             BIGSERIAL PRIMARY KEY,
    beach_id       BIGINT     NOT NULL REFERENCES beach(id),
    schedule_type  VARCHAR(20) NOT NULL,
    effective_from DATE,
    effective_to   DATE,
    day_of_week    INT,
    open_time      TIME,
    close_time     TIME,
    is_active      BOOLEAN    NOT NULL DEFAULT TRUE,
    source_type    VARCHAR(50),
    captured_at    TIMESTAMP WITH TIME ZONE
);

-- Jellyfish

CREATE TABLE jellyfish_report_aggregate (
    id               BIGSERIAL PRIMARY KEY,
    beach_id         BIGINT      NOT NULL REFERENCES beach(id),
    source_type      VARCHAR(50) NOT NULL,
    captured_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    window_start     TIMESTAMP WITH TIME ZONE,
    window_end       TIMESTAMP WITH TIME ZONE,
    report_count     INT,
    severity_level   VARCHAR(20),
    confidence_level VARCHAR(20),
    raw_payload_json TEXT
);

CREATE INDEX idx_jellyfish_beach_captured ON jellyfish_report_aggregate (beach_id, captured_at DESC);

-- Camera

CREATE TABLE camera_endpoint (
    id              BIGSERIAL PRIMARY KEY,
    beach_id        BIGINT       NOT NULL REFERENCES beach(id),
    provider_name   VARCHAR(200),
    live_url        TEXT,
    snapshot_url    TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    health_status   VARCHAR(20),
    metadata_json   TEXT
);

CREATE TABLE camera_snapshot (
    id               BIGSERIAL PRIMARY KEY,
    camera_id        BIGINT      NOT NULL REFERENCES camera_endpoint(id),
    captured_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    storage_url      TEXT,
    width            INT,
    height           INT,
    analysis_status  VARCHAR(50),
    crowd_level      VARCHAR(20),
    visibility_level VARCHAR(20)
);

-- Operational tables

CREATE TABLE ingestion_run (
    id              BIGSERIAL PRIMARY KEY,
    source_type     VARCHAR(50) NOT NULL,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at     TIMESTAMP WITH TIME ZONE,
    status          VARCHAR(20) NOT NULL,
    records_fetched INT,
    records_saved   INT,
    error_summary   TEXT
);

CREATE TABLE bot_interaction_log (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT,
    request_type     VARCHAR(50),
    beach_id         BIGINT,
    requested_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    response_status  VARCHAR(20),
    latency_ms       BIGINT
);

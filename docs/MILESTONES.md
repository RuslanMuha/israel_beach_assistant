# Beach Assistant — Milestone Delivery Plan

Each milestone is a vertical slice: ingestion → decision → API → Telegram → tests.

---

## Milestone 1 — Core Status MVP

**Goal:** Users can ask about any Ashdod beach and get a safety recommendation backed by sea forecast and health advisory data.

### Deliverables

- [ ] Spring Boot project skeleton (Maven, profiles: local/dev/prod)
- [ ] `docker-compose.yml` (app + postgres)
- [ ] Flyway migration: `city`, `beach`, `beach_source_mapping`, `sea_condition_snapshot`, `health_advisory_snapshot`, `lifeguard_schedule`, `ingestion_run`
- [ ] Seed data: Ashdod city, 2-5 beaches with aliases
- [ ] `SourceAdapter<T>` interface + `FetchResult<T>`
- [ ] Sea/forecast adapter (stub + real provider when available)
- [ ] Health advisory adapter (stub + real provider when available)
- [ ] Lifeguard schedule support (static seed + override table)
- [ ] Ingestion scheduler with per-source cadence config
- [ ] Idempotent ingest writes (upsert by source_key + captured_at)
- [ ] `DecisionEngine` v1: priority matrix, confidence, freshness
- [ ] `BeachDecision` output with reason codes + explainability
- [ ] RU response formatter with FRESH/STALE/EXPIRED phrasing
- [ ] Telegram `/start` — welcome + beach list
- [ ] Telegram `/beaches` — formatted beach list
- [ ] Telegram `/status <beach>` — decision card with inline buttons (Refresh, Camera, Live, Hours)
- [ ] Telegram free-text alias routing
- [ ] REST `GET /api/v1/beaches`
- [ ] REST `GET /api/v1/beaches/{slug}/status`
- [ ] REST `GET /api/v1/beaches/{slug}/hours`
- [ ] Unit tests: decision engine, freshness logic, alias resolution, RU formatter
- [ ] Scenario tests: 4 spec scenarios from DECISION_RULES.md

### Acceptance criteria

- `/status yud-alef` returns a recommendation with confidence and freshness metadata
- Stale data responses use "по последним доступным данным" phrasing
- Decision explanation includes reason codes and source list
- No source failure crashes the response flow

---

## Milestone 2 — Camera Support

**Goal:** Users can get a live camera link and optionally a recent snapshot.

### Deliverables

- [ ] Flyway migration: `camera_endpoint`, `camera_snapshot`
- [ ] Seed data: camera URLs for Ashdod beaches (public links)
- [ ] `CameraService`: live URL lookup, snapshot fetch, health check
- [ ] Camera health check scheduler (every 10-15 min)
- [ ] Snapshot download + storage (local filesystem dev / S3 prod)
- [ ] Telegram `/live <beach>` — live URL with provider info
- [ ] Telegram `/cam <beach>` — snapshot image or unavailable fallback
- [ ] REST `GET /api/v1/beaches/{slug}/camera`
- [ ] REST `GET /api/v1/beaches/{slug}/camera/snapshot`
- [ ] Graceful fallback: link-only mode when snapshot extraction not permitted
- [ ] Integration tests: camera fetch, storage, fallback paths

### Acceptance criteria

- `/live yud-alef` sends a clickable camera URL
- `/cam yud-alef` sends snapshot image when available, or friendly message when not
- Camera unavailability does not affect `/status` response

---

## Milestone 3 — Jellyfish Support

**Goal:** Jellyfish level is shown in status responses and available via dedicated command.

### Deliverables

- [ ] Flyway migration: `jellyfish_report_aggregate`
- [ ] Jellyfish adapter (stub + real provider when identified)
- [ ] Jellyfish ingestion scheduler (every 1-3 hours)
- [ ] Jellyfish signal integrated into decision engine (CAUTION if HIGH)
- [ ] Telegram `/jellyfish <beach>` — severity + window + freshness
- [ ] REST `GET /api/v1/beaches/{slug}/jellyfish`
- [ ] `/status` response updated to include jellyfish field
- [ ] Unit tests: jellyfish decision integration
- [ ] Integration tests: jellyfish adapter + persistence

### Acceptance criteria

- Jellyfish HIGH severity contributes `JELLYFISH_REPORTS_HIGH` reason code to decision
- `/jellyfish` shows time window and confidence
- Stale jellyfish data uses correct STALE phrasing

---

## Milestone 4 — Operations and Hardening

**Goal:** System is observable, manageable, and stable under partial source failures.

### Deliverables

- [ ] Flyway migration: `bot_interaction_log`
- [ ] Admin REST `POST /api/v1/admin/ingest/{sourceType}`
- [ ] Source diagnostics endpoint: last ingestion run per source
- [ ] Structured logging (SLF4J + JSON, MDC: request_id, beach_slug, source_type)
- [ ] Micrometer metrics:
  - `source_fetch_total{source,result}`
  - `decision_generation_total{result}`
  - `decision_confidence_total{level}`
  - `bot_request_total{command,result}`
  - `freshness_status_total{source,status}`
- [ ] Spring Cache (Caffeine) for status decisions and alias resolution
- [ ] Cache invalidation on new ingestion
- [ ] Improved error messages for partial availability (mentions which source is down)
- [ ] Bot interaction logging
- [ ] Performance: p95 `/status` from cache < 1.5s, uncached < 3s
- [ ] Integration tests: admin endpoints, cache invalidation
- [ ] Load test: 50 concurrent `/status` requests

### Acceptance criteria

- Admin can trigger manual re-ingest via REST
- Source fetch failure is logged with structured fields
- p95 latency targets met
- Partial availability returns degraded but valid response

---

## Phase 2 (future)

| Feature | Notes |
|---|---|
| `/compare <beach1> <beach2>` | Rank up to 3 beaches |
| Subscriptions / alerts | Notify on status change or jellyfish appearance |
| Hebrew/English language support | i18n templates |
| Crowd estimation | Optional computer vision on snapshots |
| Quartz scheduler | Replace Spring `@Scheduled` if complexity grows |
| Redis | Replace Caffeine if deployment needs distributed cache |

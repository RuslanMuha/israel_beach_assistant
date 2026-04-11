# Beach Assistant — Architecture

## Runtime architecture

The MVP is deployed as a **single Spring Boot 3 application** (Java 21) using a **modular monolith** pattern. All modules share the same JVM, database connection pool, and in-process event bus, but enforce strict package-level boundaries. Extraction into separate services is deferred until load or operational complexity justifies it.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        Spring Boot Application                           │
│                                                                          │
│  ┌────────────────┐   ┌──────────────────┐   ┌────────────────────────┐ │
│  │   telegram     │──▶│      app         │──▶│      decision          │ │
│  │  (handlers,    │   │ (orchestration,  │   │  (engine, freshness,   │ │
│  │   formatters,  │   │  use-cases)      │   │   confidence,          │ │
│  │   keyboards)   │   └────────┬─────────┘   │   explainability)      │ │
│  └────────────────┘            │             └────────────────────────┘ │
│                                │                                         │
│  ┌────────────────┐   ┌────────▼─────────┐   ┌────────────────────────┐ │
│  │   scheduler    │──▶│   source         │──▶│      persistence       │ │
│  │  (Spring @Sch- │   │  (adapters: sea, │   │  (Flyway, JPA repos,   │ │
│  │   eduled)      │   │   advisory,      │   │   PostgreSQL)          │ │
│  └────────────────┘   │   lifeguard,     │   └────────────────────────┘ │
│                       │   jellyfish,     │                               │
│  ┌────────────────┐   │   camera)        │   ┌────────────────────────┐ │
│  │   config       │   └──────────────────┘   │      common            │ │
│  │  (properties,  │                           │  (enums, DTOs,         │ │
│  │   beans)       │                           │   exceptions, utils)   │ │
│  └────────────────┘                           └────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

## Module responsibilities

| Module | Responsibility |
|---|---|
| `telegram` | Parse Telegram updates, route commands/free-text, format Russian responses, send inline keyboards |
| `app` | Orchestrate use-cases; bridge telegram/REST layer to domain services |
| `decision` | Combine multi-source signals into recommendation, compute confidence and freshness status |
| `source` | Isolated adapters per source type; normalize raw payload to domain snapshots |
| `persistence` | Spring Data JPA repositories; Flyway migrations; no business logic |
| `scheduler` | Spring `@Scheduled` jobs; per-source cadence configuration |
| `config` | `@ConfigurationProperties` classes; profile-specific beans |
| `common` | Shared enums, domain DTOs, exception hierarchy, time utilities |

## Data flow: ingestion

```
External source
      │
      ▼ HTTP (WebClient)
SourceAdapter.fetch()
      │ raw payload preserved
      ▼
Normalize → domain snapshot object
      │ idempotent upsert
      ▼
PostgreSQL (snapshot table)
      │
      └──► IngestionRun log (status, counts, errors)
```

Invariants:
- Raw payload JSON is always stored alongside normalized record.
- A failed fetch does NOT delete the previous successful snapshot.
- Each ingestion run is recorded in `ingestion_run` table.

## Data flow: request serving

```
Telegram command / REST request
      │
      ▼
BeachResolver (alias → beach slug)
      │
      ▼
Load latest snapshots from PostgreSQL (per source type)
      │
      ▼
DecisionEngine.evaluate(signals)
      │  priority: hard-block → caution → unknown → can_swim
      ▼
BeachDecision (recommendation, confidence, reasons, freshness)
      │
      ▼
ResponseFormatter (RU text with freshness-aware phrasing)
      │
      ▼
Telegram message / REST JSON
```

## Module package layout

```
com.beachassistant
├── telegram
│   ├── handler
│   ├── formatter
│   └── keyboard
├── app
│   └── usecase
├── decision
│   ├── engine
│   ├── freshness
│   └── confidence
├── source
│   ├── contract          ← SourceAdapter<T> interface, FetchResult<T>
│   ├── sea
│   ├── advisory
│   ├── lifeguard
│   ├── jellyfish
│   └── camera
├── domain
│   ├── model             ← domain entities (no JPA annotations here)
│   └── port              ← repository interfaces (implemented in persistence)
├── persistence
│   ├── entity            ← JPA @Entity classes
│   ├── repository        ← Spring Data repos
│   └── migration         ← Flyway SQL scripts (resources/db/migration)
├── scheduler
├── config
└── common
    ├── enums
    ├── exception
    └── util
```

## Technology stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.x |
| Persistence | PostgreSQL, Spring Data JPA, Flyway |
| HTTP client | Spring WebClient |
| Telegram | TelegramBots library (direct HTTP wrapper) |
| Serialization | Jackson |
| Scheduling | Spring `@Scheduled` (Quartz deferred) |
| Cache | Spring Cache + Caffeine (Redis deferred to Phase 4) |
| Object storage | Local filesystem dev / S3-compatible prod |
| Testing | JUnit 5, Testcontainers, WireMock |
| Build | Maven |

## Timezone handling

All timestamps stored in UTC in the database.  
All presentation and freshness comparisons use `Asia/Jerusalem` (configurable per city).  
`ZonedDateTime` used throughout; `LocalDateTime` avoided.

## Deployment

MVP: single Docker container + managed PostgreSQL.  
`docker-compose.yml` for local development (app + postgres).  
Environment profiles: `local`, `dev`, `prod`.

## Extraction candidates (post-MVP)

When load justifies it, extract:
1. `source-ingestion-worker` — remove scheduler module, drive via message queue
2. `camera-worker` — CPU-bound snapshot processing
3. `notification-service` — subscriptions and alerts (Phase 2)

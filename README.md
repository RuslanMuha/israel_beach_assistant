# Beach Assistant

Telegram-first assistant for Ashdod beaches: current swim safety, sources, and freshness. Spring Boot 3, Java 21, PostgreSQL.

## Prerequisites

- Java 21  
- Maven 3.9+  
- Docker (PostgreSQL for local run; Testcontainers for integration tests)

## Local run

**1. Start PostgreSQL**

```bash
docker compose up postgres -d
```

**2. Bot credentials** (from [@BotFather](https://t.me/BotFather))

```bash
export TELEGRAM_BOT_TOKEN=your_token_here
export TELEGRAM_BOT_USERNAME=your_bot_username
```

**3. Run**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or build a JAR:

```bash
mvn clean package -DskipTests
java -Dspring.profiles.active=local -jar target/beach-assistant-*.jar
```

**Full stack with Docker Compose** (multi-stage `Dockerfile` builds the JAR inside the image):

```bash
export TELEGRAM_BOT_TOKEN=your_token_here
export TELEGRAM_BOT_USERNAME=your_bot_username
docker compose up --build
```

API base URL in Compose: `http://localhost:8080` (use a real token; a placeholder will not talk to Telegram).

## Telegram commands

| Command | Description |
|--------|-------------|
| `/start` | Welcome + beach list |
| `/beaches` | Supported beaches |
| `/status <beach>` | Safety status |
| `/hours <beach>` | Lifeguard hours (today) |
| `/jellyfish <beach>` | Jellyfish info |
| `/live <beach>` | Live camera link |
| `/cam <beach>` | Camera snapshot (when enabled) |

Optional (when subscriptions are enabled in config): `/subscribe`, `/unsubscribe`, `/mysubs`, `/digest`.

Example: `/status yud alef`

## REST API

Base URL (local): `http://localhost:8080`

```
GET  /api/v1/beaches
GET  /api/v1/beaches/{slug}/status
GET  /api/v1/beaches/{slug}/hours
GET  /api/v1/beaches/{slug}/jellyfish
GET  /api/v1/beaches/{slug}/camera
GET  /api/v1/beaches/{slug}/camera/snapshot
POST /api/v1/admin/ingest/{sourceType}
```

Details: `docs/API.md`. Architecture: `docs/ARCHITECTURE.md`. Adding data sources: `docs/ADAPTERS.md`.

## Beaches (Ashdod)

Display names and user-facing aliases include English, Hebrew, and Russian forms where applicable. Slugs and metadata come from Flyway seeds and optional YAML catalog extension (`beach.catalog` in `application.yml`). See `V5__ashdod_beaches_expand_and_profile.sql` and `docs/PHASE2_ASHDOD_PLAN.md` for background.

## Tests

```bash
mvn test
```

Integration tests use Testcontainers (Docker required).

## Webhook mode (HTTPS)

For production behind a public HTTPS URL, set `BEACH_TELEGRAM_MODE=webhook`, `TELEGRAM_PUBLIC_BASE_URL` to your origin (no path, no trailing slash), and configure PostgreSQL via `SPRING_DATASOURCE_*`. Health: `GET /actuator/health`. The app listens on `PORT` when set.

Optional: `BEACH_TELEGRAM_WEBHOOK_AUTO_REGISTER`, `BEACH_TELEGRAM_WEBHOOK_PATH`, `TELEGRAM_WEBHOOK_SECRET`. Local Compose defaults to long polling so you do not need a public URL.

## Data sources

Ingestion uses pluggable `SourceAdapter` implementations (see `com.beachassistant.source` and `docs/ADAPTERS.md`). Many providers are toggled with `beach.providers.*.enabled` in configuration.

# Beach Assistant

A Telegram-first beach safety assistant for Ashdod beaches. Tells you whether it's safe to swim right now, with full freshness transparency.

## Quick start (local)

### Prerequisites
- Java 21
- Maven 3.9+
- Docker (for PostgreSQL)

### 1. Start PostgreSQL

```bash
docker compose up postgres -d
```

### 2. Configure Telegram bot token

Copy your bot token from [@BotFather](https://t.me/BotFather) and set it as an env variable:

```bash
export TELEGRAM_BOT_TOKEN=your_token_here
export TELEGRAM_BOT_USERNAME=your_bot_username
```

### 3. Build and run

```bash
mvn clean package -DskipTests
java -Dspring.profiles.active=local -jar target/beach-assistant-*.jar
```

Or with Maven directly:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 4. Run with Docker Compose (full stack)

The `app` image uses a **multi-stage** `Dockerfile` (Maven builds the JAR inside Docker), so you do not need to run `mvn package` on the host first.

```bash
export TELEGRAM_BOT_TOKEN=your_token_here
export TELEGRAM_BOT_USERNAME=your_bot_username
docker compose up --build
```

PowerShell:

```powershell
$env:TELEGRAM_BOT_TOKEN="your_token_here"
$env:TELEGRAM_BOT_USERNAME="your_bot_username"
docker compose up --build
```

API: `http://localhost:8080` (profile `prod` in Compose). Set a real `TELEGRAM_BOT_TOKEN`; the placeholder `CONFIGURE_ME` will not start the bot against Telegram.

## Telegram commands

| Command | Description |
|---|---|
| `/start` | Welcome message + beach list |
| `/beaches` | List of supported beaches |
| `/status <beach>` | Beach safety status |
| `/hours <beach>` | Lifeguard hours today |
| `/jellyfish <beach>` | Jellyfish reports |
| `/live <beach>` | Live camera link |
| `/cam <beach>` | Camera snapshot |

**Example:** `/status yud alef`

## REST API

Base URL: `http://localhost:8080`

```
GET  /api/v1/beaches
GET  /api/v1/beaches/{slug}/status
GET  /api/v1/beaches/{slug}/hours
GET  /api/v1/beaches/{slug}/jellyfish
GET  /api/v1/beaches/{slug}/camera
GET  /api/v1/beaches/{slug}/camera/snapshot
POST /api/v1/admin/ingest/{sourceType}
```

See `docs/API.md` for full request/response schemas.

## Architecture

See `docs/ARCHITECTURE.md` for module layout, data flows, and tech stack.

## Supported beaches (Ashdod)

- Yud Alef (aliases: `yud alef`, `юд алеф`, `יא`, `11`)
- Oranim (aliases: `ораним`, `оранім`)
- Lido (aliases: `лидо`)
- Dolfin (aliases: `долфин`, `dolphin`)
- Haof Hatzafoni (aliases: `north beach`, `северный пляж`)
- Mei Ami (`mei-ami`, `מי עמי`, …)
- Hakshatot (`hakshatot`, `הקשתות`, …)
- Marina (`marina`, `מרינה`, …)
- Separate (`separate-beach`, `החוף הנפרד`, …)
- Gil (`gil`, `חוף גיל`, …)
- Gandhi (`gandhi`, `חוף גנדי`, …)

Structured display metadata lives in `beach.profile_json` (see Flyway `V5__ashdod_beaches_expand_and_profile.sql`). Design notes: `docs/PHASE2_ASHDOD_PLAN.md`.

## Running tests

```bash
mvn test
```

Integration tests use Testcontainers (requires Docker).

## Deploying on Render (webhook)

The app listens on **`PORT`** (default `8080` in `application.yml`). Health check: **`GET /actuator/health`**.

### Required environment variables (webhook)

| Variable | Purpose |
|----------|---------|
| `TELEGRAM_BOT_TOKEN` | Bot token from BotFather |
| `TELEGRAM_BOT_USERNAME` | Bot username (without `@`) |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | PostgreSQL from Render or external |
| `BEACH_TELEGRAM_MODE` | Set to **`webhook`** |
| `TELEGRAM_PUBLIC_BASE_URL` | HTTPS origin only, e.g. `https://beach-assistant.onrender.com` (no path, no trailing slash) |

### Optional

| Variable | Purpose |
|----------|---------|
| `BEACH_TELEGRAM_WEBHOOK_AUTO_REGISTER` | Default `true`: call Telegram `setWebhook` on startup |
| `BEACH_TELEGRAM_WEBHOOK_PATH` | Default `/api/telegram/webhook` — must match the path Telegram posts to |
| `TELEGRAM_WEBHOOK_SECRET` | If set, sent as `secret_token` to Telegram and validated on each request via `X-Telegram-Bot-Api-Secret-Token` |
| `PORT` | Render sets this automatically |

### Webhook URL

Telegram receives updates at:

`{TELEGRAM_PUBLIC_BASE_URL}{BEACH_TELEGRAM_WEBHOOK_PATH}`

Example: `https://beach-assistant.onrender.com/api/telegram/webhook`

### Registration

When `BEACH_TELEGRAM_WEBHOOK_AUTO_REGISTER=true`, the app calls `setWebhook` on startup with that full URL (and optional secret). If you set `BEACH_TELEGRAM_WEBHOOK_AUTO_REGISTER=false`, you must register the webhook yourself (e.g. `curl` to Bot API or BotFather).

### Build and start (Docker / Render)

- **Build:** image uses the repo [`Dockerfile`](Dockerfile) (`mvn package` inside the build stage).
- **Start:** `java -jar app.jar` (default `ENTRYPOINT` in the Dockerfile).
- **Health check path:** `/actuator/health`

### Local Docker Compose

Default `BEACH_TELEGRAM_MODE` in Compose is **polling** so long polling works without a public URL. Switch to webhook for Render-style testing only if you expose HTTPS and set `TELEGRAM_PUBLIC_BASE_URL` accordingly.

## Data sources

Currently using stub adapters. Replace with real providers in:
- `src/main/java/com/beachassistant/source/sea/SeaForecastAdapter.java`
- `src/main/java/com/beachassistant/source/advisory/HealthAdvisoryAdapter.java`
- `src/main/java/com/beachassistant/source/jellyfish/JellyfishAdapter.java`

# Phase 2 — Change plan (Ashdod expansion)

This document implements the planning deliverable from the Phase 1 audit: model direction, Ashdod beach inventory, Telegram layout options, boundaries, and files to touch.

## 1. Data / model changes

### 1.1 Target concepts (pragmatic, not over-engineered)

| Concept | Approach |
|--------|----------|
| **Beach** | Existing `beach` row + new optional `profile_json` (TEXT) for display metadata not worth normalizing yet. |
| **BeachProfile** (Java) | Immutable record: `description`, `categories`, `BeachFacilities`, `accessibilityNotes`, `parkingNotes`, `notes`, `lifeguardNotes`, `waterQualityPlaceholder`, `jellyfishPlaceholder`. Parsed from JSON with Jackson; unknown keys ignored. |
| **BeachFacilities** | Record of booleans: showers, toilets, playground, sportsFacilities, accessible, parking. |
| **BeachStatus** (runtime) | Continues to be `BeachDecision` + optional `BeachProfile` for presentation; no duplicate “status entity” in DB. |
| **FlagStatus / swim flags** | **Application knowledge only** (enum `SwimFlagKnowledge`): code, Russian labels, safety meaning, swim stance, short explanation. Used for a compact **legend** in Telegram until municipal flag feeds exist. **Not** replacing `Recommendation` / `ReasonCode` from `DecisionEngine`. |
| **LifeguardSchedule** | Unchanged DB model; `lifeguardNotes` in profile text for seasonal caveats. |
| **Water quality / jellyfish placeholders** | String fields in profile for “not live” transparency; real water-quality ingestion remains future work. |

### 1.2 Schema delta

- `ALTER TABLE beach ADD COLUMN profile_json TEXT NULL;`
- New Flyway migration `V5__...` inserts additional Ashdod beaches + aliases + lifeguard rows + optional `profile_json` updates for existing and new rows.

### 1.3 Weak points addressed

- Centralized structured beach knowledge in **one column** + typed Java view (`BeachProfile`).
- Municipal **flag semantics** documented in code (`SwimFlagKnowledge`), not ad hoc strings in the formatter.
- **Weather comfort** heuristic moved out of `ResponseFormatter` into `WeatherComfortEvaluator` (pure function / small component).

---

## 2. Ashdod content expansion

**Reference:** Ashdod tourism / municipality listings (e.g. “golden beaches”, declared bathing beaches, sport beaches). Exact naming reconciled for app slugs.

**Already in app (unchanged slugs):** Yud Alef, Oranim, Lido, Dolfin, Haof Hatzafoni (`north-beach`).

**Added in V5 (6 beaches):**

| Display name | Slug | Notes |
|--------------|------|--------|
| Mei Ami | `mei-ami` | Declared bathing beach |
| Hakshatot | `hakshatot` | Arches beach |
| Marina | `marina` | Marina area beach |
| Separate | `separate-beach` | Gender-separated / religious |
| Gil | `gil` | Sport / water-sport beach |
| Gandhi | `gandhi` | Sport beach |

Each row: plausible coordinates, `has_lifeguards`/`has_jellyfish_source` aligned with MVP defaults, aliases (EN/RU/HE where useful), `profile_json` with categories (e.g. family, sport, religious) and facilities flags.

---

## 3. Telegram response — layout options

### Option A — “Safety-first stack”

1. **Summary** — beach, city, headline recommendation  
2. **Safety** — reasons + human summary  
3. **Conditions** — sea + comfort (compact lines)  
4. **Sources** — freshness (bullets)  
5. **Beach info** — profile (facilities, notes) if present  
6. **Actions** — flag legend (compact) + confidence + generated time  

### Option B — “Two-message split” (not chosen)

- Message 1: summary + safety only  
- Message 2: conditions + rest  
- **Rejected:** extra notification noise, harder to scan in chat history.

### Option C — “Minimal + link” (not chosen)

- Ultra-short status + “use /hours /jellyfish”  
- **Rejected:** hides transparency (sources/freshness) already core to the product.

### Chosen: **Option A**

**Reasoning:** Single message, mobile-friendly, **safety and recommendation first**, conditions next, static beach info and flag legend at the end without burying operational transparency (sources). Matches “compact sections” and scalability (each section is a method).

---

## 4. Refactor boundaries

| Layer | Responsibility |
|-------|------------------|
| **Persistence** | `BeachEntity` + `profile_json` |
| **Domain** | `BeachProfile`, `BeachFacilities`, `WeatherComfortEvaluator`, `SwimFlagKnowledge` |
| **Application** | `BeachProfileParser` (parse/empty on failure), `BeachBotHandler` resolves beach → profile → formatter |
| **Telegram** | `ResponseFormatter` — section assembly and Russian copy only; no comfort scoring, no flag semantics beyond calling `SwimFlagKnowledge` |

---

## 5. Files to add / change (Phase 3)

| Action | Path |
|--------|------|
| Add | `src/main/resources/db/migration/V5__ashdod_beaches_expand_and_profile.sql` |
| Add | `.../domain/model/BeachFacilities.java` |
| Add | `.../domain/model/BeachProfile.java` |
| Add | `.../domain/flag/SwimFlagKnowledge.java` |
| Add | `.../domain/comfort/WeatherComfortEvaluator.java` |
| Add | `.../app/BeachProfileParser.java` |
| Modify | `.../persistence/entity/BeachEntity.java` |
| Modify | `.../telegram/formatter/ResponseFormatter.java` |
| Modify | `.../telegram/handler/BeachBotHandler.java` |
| Modify | `README.md` (supported beaches) |
| Add | `src/test/java/.../BeachProfileParserTest.java`, `SwimFlagKnowledgeTest.java`, `WeatherComfortEvaluatorTest.java` |
| Modify | `BeachStatusIntegrationTest.java` (beach count ≥ 11) |

---

## 6. Assumptions / TODOs

- Municipal **live** flag API not integrated; legend is educational only alongside model-driven recommendation.  
- `profile_json` content is **curated static** text; i18n remains Russian-only for Telegram in this iteration.  
- Coordinates are approximate for forecasting/jellyfish radius; refinement can follow real survey data.

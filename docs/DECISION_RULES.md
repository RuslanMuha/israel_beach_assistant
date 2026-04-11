# Beach Assistant — Decision Rules

## Overview

The decision engine takes normalized source signals and produces a single `BeachDecision` per evaluation. Rules are evaluated in strict priority order — the first matched priority level wins.

---

## Priority matrix

| Priority | Condition | Recommendation |
|---|---|---|
| 1 (hard block) | Active health advisory | `DO_NOT_RECOMMEND` |
| 1 (hard block) | Beach officially closed | `DO_NOT_RECOMMEND` |
| 1 (hard block) | Sea risk = SEVERE | `DO_NOT_RECOMMEND` |
| 2 (caution) | Sea risk = HIGH or SIGNIFICANT | `CAUTION` |
| 2 (caution) | Lifeguards off duty (sea not forbidden) | `CAUTION` |
| 2 (caution) | Jellyfish severity = HIGH | `CAUTION` |
| 2 (caution) | Conflicting signals between major sources | `CAUTION` |
| 3 (unknown) | All safety-relevant sources are STALE or EXPIRED | `UNKNOWN` |
| 3 (unknown) | No valid source mapping for beach | `UNKNOWN` |
| 3 (unknown) | Source failures prevent safe inference | `UNKNOWN` |
| 4 (normal) | No hard blocks, no cautions, freshness acceptable | `CAN_SWIM` |

---

## Reason codes

| Code | Trigger |
|---|---|
| `SEA_RISK_HIGH` | Wave/wind risk level is HIGH |
| `SEA_RISK_SEVERE` | Wave/wind risk level is SEVERE |
| `HEALTH_ADVISORY_ACTIVE` | An official advisory is active and fresh |
| `LIFEGUARDS_OFF_DUTY` | Lifeguards not scheduled or schedule unknown |
| `NO_FRESH_DATA` | All relevant sources are STALE or EXPIRED |
| `JELLYFISH_REPORTS_HIGH` | Jellyfish aggregate severity = HIGH |
| `BEACH_TEMPORARILY_CLOSED` | Beach flagged closed in source |
| `CAMERA_UNAVAILABLE` | Camera health check failed (informational) |
| `SOURCE_CONFLICT` | Two authoritative sources disagree on safety |

A decision includes 1-3 reason codes. Reason codes are listed highest-priority first.

---

## Freshness thresholds

| Category | Threshold | UX phrasing rule |
|---|---|---|
| `FRESH` | age ≤ 24h | Present as current; still show `updated_at` |
| `STALE` | 24h < age ≤ 72h | Must say "according to the latest available data from [date]" |
| `EXPIRED` | age > 72h | Mark as `UNKNOWN`; say "last available data is too old" |

Thresholds are configurable per source type in `application.yml`. Stricter thresholds may apply — for example sea forecast may use a 6h freshness cutoff for `FRESH`.

---

## Confidence model

| Level | Conditions |
|---|---|
| `HIGH` | Fresh official advisory + fresh sea forecast |
| `MEDIUM` | Fresh sea forecast + stale municipal schedule, or partial source coverage |
| `LOW` | Stale data, weak signals only, or single-source decision |

---

## Decision output

Every `BeachDecision` must include:

| Field | Required |
|---|---|
| `recommendation` | Yes |
| `confidence` | Yes |
| `reasonCodes` (1-3 items) | Yes |
| `humanSummary` | Yes |
| `freshnessStatus` (overall worst-case) | Yes |
| `effectiveFrom` | If determinable |
| `effectiveTo` | If determinable |
| `sourcesUsed` (list of source types + freshness) | Yes |
| `generatedAt` | Yes |

---

## Effective time window

If the sea forecast provides `valid_from` / `valid_to`, those become the decision window.  
If only `captured_at` is available, `valid_from = captured_at`, and `valid_to` is inferred from source config (e.g. +3h for hourly forecast).  
Inferred windows are flagged `intervalIsInferred = true` in the output.

---

## Partial availability behavior

If one source fails but others succeed:
1. Continue evaluation with available sources.
2. Downgrade confidence by at least one level.
3. Include missing source in `missingSourceTypes` field.
4. If missing source is hard-block capable (health advisory), downgrade to `UNKNOWN` unless another hard-block signal is already present.

---

## Scenario test cases

| Scenario | Expected recommendation | Expected confidence |
|---|---|---|
| Active health advisory + fresh forecast | `DO_NOT_RECOMMEND` | `HIGH` |
| Sea risk SEVERE + no advisory | `DO_NOT_RECOMMEND` | `HIGH` |
| Sea risk HIGH + lifeguards on duty | `CAUTION` | `HIGH` |
| Lifeguards off duty + sea calm + fresh data | `CAUTION` | `MEDIUM` |
| Jellyfish HIGH + sea calm | `CAUTION` | `MEDIUM` |
| All sources fresh + no risks | `CAN_SWIM` | `HIGH` |
| Sea forecast stale (>24h) + no advisory | `UNKNOWN` or `CAUTION` with stale label | `LOW` |
| All sources expired (>72h) | `UNKNOWN` | `LOW` |
| Sea fetch failure + advisory OK + no risk | `CAN_SWIM` with reduced confidence | `LOW` |
| Two sources conflict on sea risk | `CAUTION` reason `SOURCE_CONFLICT` | `LOW` |

---

## RU phrasing templates

```
# FRESH
Купание разрешено.
Море спокойное.

# FRESH with CAUTION
Купайтесь осторожно: {{reason}}.
Спасатели дежурят до {{close_time}}.

# DO_NOT_RECOMMEND
Купаться не рекомендуется: {{reason}}.
Обновлено: {{updated_at}}.

# STALE data
По последним доступным данным от {{date}}, {{status}}.

# EXPIRED / UNKNOWN
Актуальных данных недостаточно для уверенной рекомендации.
Последние данные получены: {{last_known_date}}.

# FORECAST
Прогноз действителен с {{valid_from}} по {{valid_to}}.
```

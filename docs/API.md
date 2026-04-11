# Beach Assistant — REST API Contracts

Base path: `/api/v1`  
All responses: `Content-Type: application/json`  
All timestamps: ISO-8601 with timezone offset (`2026-04-09T17:20:00+03:00`)

---

## GET /api/v1/beaches

Returns the list of supported beaches.

### Response 200

```json
[
  {
    "id": "yud-alef",
    "displayName": "Yud Alef",
    "city": "Ashdod",
    "aliases": ["yud alef", "יא", "11"],
    "hasCamera": true,
    "hasLifeguards": true,
    "hasJellyfishSource": true,
    "isActive": true
  }
]
```

---

## GET /api/v1/beaches/{slug}/status

Returns the latest beach decision with full freshness metadata.

### Path params
- `slug` — beach identifier (e.g. `yud-alef`)

### Response 200

```json
{
  "beach": "Yud Alef",
  "city": "Ashdod",
  "recommendation": "CAUTION",
  "confidence": "MEDIUM",
  "reasons": ["SEA_RISK_HIGH"],
  "summary": "Sea conditions are rough",
  "freshnessStatus": "FRESH",
  "updatedAt": "2026-04-09T17:20:00+03:00",
  "validFrom": "2026-04-09T12:00:00+03:00",
  "validTo": "2026-04-09T18:00:00+03:00",
  "sources": [
    {
      "sourceType": "SEA_FORECAST",
      "freshnessStatus": "FRESH",
      "capturedAt": "2026-04-09T17:00:00+03:00"
    },
    {
      "sourceType": "HEALTH_ADVISORY",
      "freshnessStatus": "FRESH",
      "capturedAt": "2026-04-09T15:00:00+03:00"
    }
  ]
}
```

### Recommendation enum values
- `CAN_SWIM`
- `CAUTION`
- `DO_NOT_RECOMMEND`
- `UNKNOWN`

### Confidence enum values
- `HIGH`
- `MEDIUM`
- `LOW`

### Freshness enum values
- `FRESH` — data age ≤ 24h
- `STALE` — data age > 24h and ≤ 72h
- `EXPIRED` — data age > 72h

### Reason code values
- `SEA_RISK_HIGH`
- `SEA_RISK_SEVERE`
- `HEALTH_ADVISORY_ACTIVE`
- `LIFEGUARDS_OFF_DUTY`
- `NO_FRESH_DATA`
- `JELLYFISH_REPORTS_HIGH`
- `BEACH_TEMPORARILY_CLOSED`
- `SOURCE_CONFLICT`

### Response 404
```json
{ "error": "BEACH_NOT_FOUND", "message": "No beach found for slug: xyz" }
```

---

## GET /api/v1/beaches/{slug}/hours

Returns today's lifeguard schedule.

### Response 200

```json
{
  "beach": "Yud Alef",
  "onDuty": true,
  "openTime": "09:00",
  "closeTime": "18:00",
  "scheduleType": "REGULAR",
  "freshnessStatus": "FRESH",
  "capturedAt": "2026-04-09T06:00:00+03:00"
}
```

---

## GET /api/v1/beaches/{slug}/jellyfish

Returns latest jellyfish aggregate.

### Response 200

```json
{
  "beach": "Yud Alef",
  "severityLevel": "HIGH",
  "confidenceLevel": "MEDIUM",
  "reportCount": 5,
  "windowStart": "2026-04-08T00:00:00+03:00",
  "windowEnd": "2026-04-09T17:20:00+03:00",
  "freshnessStatus": "FRESH",
  "capturedAt": "2026-04-09T16:00:00+03:00"
}
```

### Severity level values
- `NONE`
- `LOW`
- `MEDIUM`
- `HIGH`

---

## GET /api/v1/beaches/{slug}/camera

Returns camera metadata and live URL.

### Response 200

```json
{
  "beach": "Yud Alef",
  "providerName": "BeachCam IL",
  "liveUrl": "https://example.com/cam/yud-alef",
  "isActive": true,
  "healthStatus": "OK",
  "lastCheckedAt": "2026-04-09T17:10:00+03:00"
}
```

### Response 404
```json
{ "error": "CAMERA_NOT_FOUND", "message": "No active camera for beach: yud-alef" }
```

---

## GET /api/v1/beaches/{slug}/camera/snapshot

Returns latest snapshot metadata or redirects to object storage.

### Response 200

```json
{
  "beach": "Yud Alef",
  "capturedAt": "2026-04-09T17:15:00+03:00",
  "storageUrl": "https://storage.example.com/snapshots/yud-alef/20260409-1715.jpg",
  "width": 1280,
  "height": 720
}
```

### Response 404
```json
{ "error": "SNAPSHOT_NOT_AVAILABLE", "message": "No recent snapshot available for beach: yud-alef" }
```

---

## POST /api/v1/admin/ingest/{sourceType}

Manually trigger ingestion for a specific source type. Admin only.

### Path params
- `sourceType` — one of: `SEA_FORECAST`, `HEALTH_ADVISORY`, `LIFEGUARD_SCHEDULE`, `JELLYFISH`, `CAMERA_HEALTH`

### Response 200

```json
{
  "sourceType": "SEA_FORECAST",
  "startedAt": "2026-04-09T17:25:00+03:00",
  "status": "SUCCESS",
  "recordsFetched": 12,
  "recordsSaved": 10
}
```

### Response 400
```json
{ "error": "INVALID_SOURCE_TYPE", "message": "Unknown source type: XYZ" }
```

---

## Error response format

All error responses follow a consistent structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable explanation",
  "timestamp": "2026-04-09T17:25:00+03:00"
}
```

## Common HTTP status codes used

| Code | Meaning |
|---|---|
| 200 | Success |
| 400 | Invalid request (bad input) |
| 404 | Resource not found |
| 503 | Temporary source unavailability (partial response possible) |

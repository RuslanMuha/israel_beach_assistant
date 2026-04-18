# Writing a Source Adapter

This guide walks through adding a new data source to Beach Assistant. Every adapter implements
the `SourceAdapter` contract and is auto-discovered at startup via `SourceRegistry`.

## 1. Contract

```java
public interface SourceAdapter<T> {
    SourceType sourceType();
    FetchResult<T> fetch(SourceRequest request);
    default SourceDescriptor descriptor() {
        return SourceDescriptor.legacy(sourceType(), Duration.ofHours(1));
    }
}
```

- `sourceType()` — legacy enum for back-compat; required.
- `fetch(SourceRequest)` — does the actual work; must be idempotent.
- `descriptor()` — declarative metadata (id, display name, cadence). Override for new adapters;
  legacy default is fine for existing types.

## 2. Skeleton

Start from `source/_template/` (see the files in that directory) and rename:

- `NewThingAdapter` — the adapter bean.
- `NewThingRecord` — the immutable record returned inside `FetchResult`.
- `NewThingAdapterTest` — WireMock-driven unit test covering happy path, 429, and 5xx.

## 3. Configure

Per-adapter toggles live under `beach.providers.<id>.*`:

```yaml
beach:
  providers:
    new-thing:
      enabled: false    # off by default when still validating
      cadence: 1h       # overrides SourceDescriptor.defaultCadence()
```

Where `<id>` matches `descriptor().id()`. The `SourceProviderProperties` bean is the single
source of truth that `SourceRegistry.enabledAdapters()` consults.

## 4. Scheduling

- Existing adapters are still driven by `IngestionScheduler` (fixed-delay per `SourceType`).
- New adapters can rely on the default cadence in their descriptor; a descriptor-driven
  scheduler will eventually run alongside the legacy scheduler. Until then, add a
  `@Scheduled` method in `IngestionScheduler` (copy an existing one).

## 5. Freshness / storage

Adapters do not write to the DB directly — they return a typed record, and the ingestion
pipeline (`IngestionUseCase`) persists, computes freshness, and emits metrics. If you need a
new snapshot table, add a Flyway migration (`src/main/resources/db/migration/V*__*.sql`) and
a JPA entity under `com.beachassistant.persistence.entity`.

## 6. Testing

Every adapter ships with:

- A unit test using WireMock (`WireMockExtension.newInstance()`), covering:
  success, 4xx non-retryable, 429 with Retry-After, 5xx with backoff.
- An optional "smoke" integration test guarded by a profile if an external service is involved.

## 7. Documentation

Update this file with a one-paragraph description of the adapter plus a pointer to its config
keys. That gives ops and contributors a single index of sources.

## Checklist

- [ ] Adapter + record + descriptor id
- [ ] `@ConditionalOnProperty` or `SourceProviderProperties.isEnabled(id)` gate
- [ ] Cadence (descriptor default or config override)
- [ ] Flyway migration if a new table is required
- [ ] WireMock test (happy path + 429 + 5xx)
- [ ] Entry in this ADAPTERS.md

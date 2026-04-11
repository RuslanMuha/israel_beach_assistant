---
name: caching
description: Add, review, or modify caching with explicit data semantics, deterministic keys, TTL/invalidation strategy, stampede protection, profiles, and observability.
---

# Caching

## When to Use
- Introduce a cache for a read path.
- Modify cache key format, TTL, invalidation, or fallback behavior.
- Review whether an existing cache is safe and useful.

## When NOT to Use
- General performance tuning without a proven cacheable access pattern.
- Pure DB query review when no cache is involved.
- Stateless computations where caching adds complexity without clear gain.

## Required Input
- What data is being cached.
- Read/write pattern and expected hot keys.
- Freshness requirements and invalidation triggers.
- Current key format and storage layer if one exists.

## Workflow
1. Define the cache contract:
   - source of truth
   - staleness tolerance
   - miss behavior
   - error behavior
2. Design a deterministic namespaced versioned key.
3. Put TTL and feature toggles in typed config.
4. Decide whether stampede protection is required.
5. Decide whether local/test profiles need alternate cache wiring.
6. Add hit/miss/load/error metrics.
7. Add tests for key generation and behavior around miss/stale/invalidations.
8. State rollback steps: disable switch, TTL shrink, or key version bump.

## Output Contract
Return:
1. `Cache Contract`
2. `Key Design`
3. `Invalidation / TTL`
4. `Profiles / Wiring`
5. `Metrics and Tests`
6. `Rollback Plan`

## Guardrails
- Do not cache before defining freshness and invalidation semantics.
- Do not build keys from raw object serialization or unstable ordering.
- Do not hide the cache behind silent magic; name it and make it observable.

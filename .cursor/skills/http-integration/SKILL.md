---
name: http-integration
description: Add or review an outbound HTTP integration with explicit timeout budgets, retry policy, resilience placement, error mapping, profiles/test-safe wiring, and observability.
---

# HTTP Integration

## When to Use
- Add a new downstream HTTP call.
- Modify client timeout, retry, circuit breaker, bulkhead, fallback, or profile-specific client wiring.
- Review an existing integration for transport-level correctness and operational safety.

## When NOT to Use
- REST controller implementation for incoming requests.
- Database access changes with no outbound call.
- Generic architecture discussion with no client behavior to design or review.

## Required Input
- Downstream purpose and criticality.
- Idempotency or replay safety.
- Expected failure handling and timeout constraints.
- Existing client wrapper or integration code if present.

## Workflow
1. Define the total request budget.
2. Derive connect/read/pool/acquire limits that fit inside that budget.
3. Decide whether retries are allowed based on idempotency and side effects.
4. Place retry/circuit breaker/bulkhead at the client boundary, not deep in business code.
5. Map downstream failures to stable internal error categories.
6. Decide whether local/test profiles need alternate adapters or stubs.
7. Add metrics and spans for latency, status class, failures, and retry outcomes.
8. Test happy path, timeout, retryable failure, non-retryable failure, and degraded behavior.

## Output Contract
Return:
1. `Budget and Policy`
2. `Client Design`
3. `Failure Mapping`
4. `Profiles / Test Wiring`
5. `Tests`
6. `Runtime Knobs`

## Guardrails
- Do not retry non-idempotent operations unless explicit protection exists.
- Do not leave timeouts implicit.
- Do not emit one ERROR log per retry attempt on a hot path.
- Do not use resilience libraries for local deterministic loops that are not outbound integration failures.

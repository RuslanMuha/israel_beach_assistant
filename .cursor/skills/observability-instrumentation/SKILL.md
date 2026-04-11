---
name: observability-instrumentation
description: Implement or review Micrometer metrics, timers, tracing hooks, and observability placement so instrumentation stays out of business logic and runtime signals remain useful.
---

# Observability Instrumentation

## When to Use
- Add or refactor Micrometer counters, timers, distribution summaries, gauges, or tracing hooks.
- Review whether observability code is leaking into services or controllers.
- Standardize metric names, tags, and telemetry ownership.

## When NOT to Use
- Generic logging review with no metric/tracing work.
- Pure business logic changes with no observability requirement.

## Required Input
- The hot path or workflow being instrumented.
- Existing metric names/tags if any.
- Traffic sensitivity and cardinality concerns if known.

## Workflow
1. Identify the intent-level signals needed: latency, hit/miss, success/failure, saturation, retry outcome, backlog, etc.
2. Decide who owns instrumentation: dedicated `*Metrics`/`*Telemetry` component, boundary adapter, or tiny local inline metric if truly trivial.
3. Keep business services free of raw metric registration logic, meter naming, and tag assembly where instrumentation is non-trivial.
4. Normalize names and low-cardinality tags.
5. Add timers/counters at stable boundaries, not inside every branch unless the branch itself is the signal.
6. Verify no secrets, IDs, raw URLs, or exception messages leak into tags.
7. Test both behavior and metric emission where practical.

## Output Contract
Return:
1. `Signals to Emit`
2. `Placement`
3. `Metric Design`
4. `Code Changes`
5. `Tests / Verification`

## Guardrails
- Do not inject `MeterRegistry` and several raw meters straight into business services unless the instrumentation is extremely small and obvious.
- Do not build metric names or dynamic high-cardinality tags inline in hot-path business code.
- Do not use logs where counters/timers are the correct normal-path signal.

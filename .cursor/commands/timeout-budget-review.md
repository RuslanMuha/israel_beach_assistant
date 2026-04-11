# /timeout-budget-review

Review timeouts/resilience for production risk.

Hard rules:
- Only comment on code shown/scanned.
- No invented issues/examples.

Check:
- Explicit timeout budget exists for the operation.
- Timeout hierarchy: pool wait < connect < read <= overall budget.
- Retries: transient only, budgeted (attempts + backoff + jitter), no retry inside DB tx.
- Resilience4j: CB/Bulkhead/RateLimiter config, no unbounded queues, observable metrics.
- Config-driven values (Duration/props), no magic numbers.

Output:
- Top Risks (max 3 bullets)
- Fixed-format comments (Issue/Suggestion/Example/Impact/Priority)
Finish with: Tests to run + tuning knobs (timeouts/retry/CB/bulkhead) + metrics to watch.

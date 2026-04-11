# /risk-review-all

Run a full production risk review suite on the provided diff/context.

Hard rules:
- Do not assume; use only code shown/scanned.
- No invented issues/examples.
- No snippet => no comment.

Process:
1) Give "Top Risks" (max 7 bullets).
2) Then run these checks (only where relevant to the diff):
   A) Logging/Observability (log-flood, dup logs, stacktraces, JSON fields, PII, high-cardinality)
   B) Timeouts/Resilience (budget + hierarchy + retry policy + bulkheads/CB + config-driven values)
   C) Data/JPA (N+1, pagination/limits, tx boundaries, isolation only if needed)
   D) API & Errors (ProblemDetail consistency, stable error codes, 4xx vs 5xx mapping)
   E) Migrations (Liquibase safety: expand/migrate/contract; batching; indexes/constraints)

For EACH comment use fixed format:
Issue/Concern:
Suggestion:
Example:
- Existing snippet (short excerpt + file path)
- Improved version
Impact:
Priority: Min|Mid|Max

Finish with:
- Tests: exact commands to run
- Tuning knobs: what must be tuned in prod
- Metrics to watch: p95/p99, error rate, retry rate, CB opens, pool saturation, slow queries, log volume.

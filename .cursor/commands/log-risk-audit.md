# /log-risk-audit

Do a logging/observability risk audit on the provided diff/context.

Hard rules:
- Do not assume. Use only shown/scanned code.
- No invented issues or generic examples.
- If you can't cite a real snippet, do not comment.

Check (only where relevant):
- log-flood risks: per-request WARN/ERROR, stacktrace in hot path, per-item logs in loops/batches
- duplicated logs across layers (same event logged multiple times)
- parameterized logging only (no "+"), expensive serialization guarded by log level
- structured JSON fields and correlation: traceId/spanId, stable event key
- secrets/PII exposure
- high-cardinality fields/tags in logs/metrics

Output:
- Top Risks (max 3 bullets)
- Comments in fixed format:
  Issue/Concern:
  Suggestion:
  Example: (short excerpt + file path; improved snippet)
  Impact:
  Priority: Min|Mid|Max
Finish with: Tests to run + tuning knobs (sampling/rate limit) + metrics to watch.

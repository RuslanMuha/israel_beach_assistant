# /jpa-risk-review

Review JPA/data access changes for correctness and performance.

Hard rules:
- Only use shown/scanned code; no assumptions.

Check:
- N+1 risks; fetch strategy (LAZY by default), EntityGraph/projections/join fetch used intentionally.
- No unbounded reads; pagination/limits/sorting safe and indexed when possible.
- Transaction boundaries: @Transactional on service, avoid downstream HTTP inside tx.
- Migration impact if schema changes are implied.

Output:
- Top Risks (max 3 bullets)
- Fixed-format comments (Issue/Suggestion/Example/Impact/Priority)
Finish with: Tests to run + tuning knobs + metrics to watch (slow query timer).

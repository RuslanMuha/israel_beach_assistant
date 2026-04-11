# /migrations-review

Review Liquibase migrations for rollout safety (expand/migrate/contract).

Hard rules:
- Comment only on migration files shown/scanned.
- No generic advice unless tied to actual code/diff.

Check:
- Additive-first (expand -> migrate/backfill -> contract).
- No massive UPDATE/DELETE without batching on large tables.
- Index/constraint changes planned (online/concurrent where supported).
- Changesets are small, stable id+author, never edit applied changeset.
- Safety: statement/lock timeouts strategy (if present), runbook notes for risky migrations.

Output:
- Top Risks (max 3 bullets)
- Fixed-format comments (Issue/Suggestion/Example/Impact/Priority)
Finish with: How to verify (CI steps/tests) + tuning knobs (batch size/timeouts).

---
name: logging-audit
description: Review or refactor logging and observability for duplicate logs, hot-path cost, stacktrace abuse, noisy retry loops, weak correlation, and false confidence in verification output.
---

# Logging Audit

## When to Use
- Review logging strategy in a service or diff.
- Remove duplicate logging across layers.
- Reduce hot-path logging overhead or noisy failure amplification.
- Verify correlation fields, metrics, tracing coverage, and verification honesty in outputs.

## When NOT to Use
- Feature delivery where logging is only a tiny incidental detail.
- Pure business logic refactoring with no observability concerns.
- Architecture discussion with no concrete code or log strategy.

## Required Input
- Relevant code or diff.
- Traffic sensitivity if known.
- Current logging format, correlation setup, and observability stack if known.

## Workflow
1. Identify boundary owners for logs.
2. Find duplicate logs for the same event/failure across layers.
3. Find per-item/per-attempt logs in loops, retries, polling, or batch flows.
4. Check for expensive logging patterns: eager formatting, payload dumps, stacktraces on hot paths.
5. Check that request correlation exists even without tracing.
6. Recommend metrics/traces where logs are being used for normal-path visibility.
7. Verify with examples of before/after logging behavior.

## Output Contract
Return:
1. `Findings`
2. `Why They Matter`
3. `Recommended Changes`
4. `Verification`
5. `Residual Risks` - only if any remain

## Guardrails
- Do not say `reduce logs` without identifying which events should disappear, move level, or become metrics.
- Do not keep duplicate failure logs just because they exist in different classes.

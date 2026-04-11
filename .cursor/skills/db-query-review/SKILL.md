---
name: db-query-review
description: Review or design repository and query changes for correctness, bounded access patterns, transaction safety, explicit semantics, and predictable performance.
---

# DB Query Review

## When to Use
- Review JPA, Hibernate, Spring Data, JDBC, or SQL query changes.
- Investigate N+1 risk, missing pagination, fetch strategy, transactional misuse, or repository contract ambiguity.
- Design repository access for a new feature.

## When NOT to Use
- Full feature implementation when query design is only a small sub-step.
- Pure schema migration work with no repository/query behavior.
- Logging-only or caching-only reviews.

## Required Input
- Query/repository code, entity model, or SQL.
- Access pattern: expected cardinality, filters, sort order, and latency sensitivity.
- Transaction and consistency expectations if known.

## Workflow
1. Identify the exact access pattern.
2. Check boundedness: limits, pagination, deterministic ordering.
3. Check fetch strategy and N+1 risk.
4. Check transaction scope and whether external calls are mixed into the transaction.
5. Check index expectations implied by the query shape.
6. Check whether repository methods expose explicit enough outcome semantics.
7. Recommend the smallest safe fix: projection, entity graph, query rewrite, batching, boundary change, or a more explicit repository result type.
8. Verify with tests or explain what needs measuring.

## Output Contract
Return:
1. `Query Shape`
2. `Risks Found`
3. `Recommended Fix`
4. `Verification`
5. `Open Questions` - only if they materially affect the recommendation

## Guardrails
- Do not hand-wave with `optimize query` or `add indexes` without tying it to the actual query shape.
- Do not assume a collection is small unless the code or requirement proves it.
- Do not translate broad persistence errors into a narrow business meaning without checking what failure class is actually expected.

---
name: refactor-complexity
description: Refactor complex code into smaller, testable units while preserving behavior, keeping diff size controlled, and avoiding abstraction inflation.
---

# Refactor Complexity

## When to Use
- A method or class is hard to reason about because of branching, nesting, or mixed responsibilities.
- You need to reduce cyclomatic complexity without changing behavior.
- You want a safe intermediate refactor before adding new behavior.

## When NOT to Use
- Feature work where refactoring would be speculative or unrelated.
- Pure style cleanup with no complexity problem.
- Performance optimization where the main issue is algorithmic cost, not structure.

## Required Input
- The code to refactor.
- Existing tests or observable behavior.
- Constraints on public API stability.

## Workflow
1. Lock behavior with tests or explicit examples.
2. Identify the real complexity source: branching, mixed responsibilities, state mutation, duplicated conditions, or hidden temporal coupling.
3. Classify whether the complexity comes from:
   - real business branching
   - mixed concerns
   - low-level technical mechanism embedded in a higher-level class
   - missing boundary between reusable mechanism and use-case logic
4. Apply the smallest effective refactor:
   - guard clauses
   - extraction of cohesive methods
   - explicit state object or enum
   - strategy/policy extraction only when multiple stable variants exist
5. Re-check allocation and algorithmic cost so the refactor does not accidentally regress runtime behavior.
6. Run a cleanup pass for obsolete classes, methods, fields, configs, tests, imports, and dependencies left behind by the refactor.
7. Finish with a behavior-preserving summary.

## Output Contract
Return:
1. `Complexity Source`
2. `Refactor Plan`
3. `Behavior Safety`
4. `Cleanup Pass`
5. `Tests`
6. `Trade-offs`

## Guardrails
- Do not introduce patterns just to make the code look `cleaner`.
- Do not split code into many tiny abstractions if one small extraction solves the real problem.
- When the main issue is mixed concerns, prefer extracting the non-business concern into a cohesive collaborator over splitting business flow into many tiny methods.
- Consider whether the extracted collaborator should be feature-local concrete code or a reusable project primitive; do not default to generic design.
- Do not leave both the new and old code paths in place after the refactor unless an explicit compatibility phase is required.
- Do not leave dead imports, stale tests, unused fields, or superseded helpers behind after extraction.

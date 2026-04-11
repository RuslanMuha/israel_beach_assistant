---
name: abstraction-boundary-review
description: Decide whether logic should stay local, move to a dedicated component, or become a reusable abstraction. Use when code mixes concerns or when considering a generic helper/service/policy extraction.
---

# Abstraction Boundary Review

## When to Use
- A class is mixing business logic with technical mechanisms.
- You are considering extracting a helper, policy, utility, or generic component.
- You need to decide whether code should remain feature-local or become reusable within the project.

## When NOT to Use
- Pure feature implementation with no design uncertainty.
- Trivial refactors where the target boundary is already obvious.
- Style-only cleanup.

## Workflow
1. Classify the logic:
   - business/use-case logic
   - domain rule
   - infrastructure concern
   - cross-cutting concern
   - low-level technical mechanism

2. Define current and realistic reuse scope:
   - single method
   - single class
   - feature-local
   - project-wide
   - library-grade

3. Decide the target shape:
   - keep local
   - extract private method
   - extract feature-local concrete collaborator
   - extract reusable project-level abstraction

4. Extract only if all are true:
   - the responsibility is cohesive
   - the contract is clear
   - coupling decreases
   - testability/readability improves
   - the abstraction is not more complex than the current problem

5. Prefer concrete, narrow names first. Make a component generic only if the mechanism is truly domain-agnostic and realistic reuse exists.

## Output Contract
Return:
1. `Concern Classification`
2. `Reuse Scope`
3. `Recommended Boundary`
4. `Why Not the Other Options`
5. `Refactor Shape`

## Guardrails
- Do not generalize from one use site without a real boundary reason.
- Do not keep low-level technical mechanics inside application services when a narrow collaborator would isolate them cleanly.
- Do not create framework-like abstractions for feature-local logic.
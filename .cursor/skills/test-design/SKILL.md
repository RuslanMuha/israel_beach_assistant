---
name: test-design
description: Design or review Java/Spring tests with correct scope, profile isolation, realistic dependencies, and honest verification reporting.
---

# Test Design

## When to Use
- Design tests for a new feature or milestone.
- Review whether existing tests use the right slice, profile, and dependency strategy.
- Decide between unit, slice, integration, Testcontainers, local test adapters, and mocks.

## When NOT to Use
- Pure production code implementation with no test design question.
- Performance/load testing plans.
- Broad architecture design not centered on verification strategy.

## Required Input
- Target behavior to verify.
- Infrastructure involved: DB, HTTP, storage, messaging, cloud services.
- Available profiles/testcontainers/stubs if known.

## Workflow
1. Identify the observable behavior, contract, or failure semantic that the test must prove.
2. Choose the narrowest test type that can verify that behavior honestly.
3. Decide which dependencies should be real, containerized, local-safe, fake, or mocked.
4. Check whether the proposed test is asserting behavior or merely mirroring implementation details.
5. Remove mocks, stubs, and verifications that do not affect the asserted behavior.
6. If reflection, full-context bootstrapping, or global state tricks seem necessary, evaluate whether the real problem is production design rather than test mechanics.
7. If wiring differs from production, use a dedicated test profile.
8. Keep test-only infrastructure out of production configuration.
9. Report exactly what was verified, skipped by design, or blocked by the environment.

## Output Contract
Return:
1. `Behavior Under Test`
2. `Test Scope`
3. `Dependency Strategy`
4. `Profiles / Wiring`
5. `Required Tests`
6. `Verification Notes`

## Guardrails
- Do not add skip/disable annotations merely to bypass missing local prerequisites.
- If a prerequisite such as Docker is unavailable, report the blocked verification honestly.
- Only use skip conditions that are part of the intended long-term test design.
- Do not mix visibility styles in one test class without a concrete reason.
- Do not use `lenient()` merely to silence unused stubs or strict-stubbing failures.
- Do not rely on reflection to reach private business logic when the real issue is missing testable design seams.
- Do not prefer a full Spring context when a unit or slice test can verify the behavior honestly.
- Do not keep duplicated scenarios as separate tests when a parameterized test expresses the behavior more clearly.
- Do not optimize for a green test status if that weakens defect detection.

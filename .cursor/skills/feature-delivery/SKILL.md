---
name: feature-delivery
description: Deliver or modify a feature end-to-end with minimal diff, correct tests, milestone requirement coverage, and explicit verification. Use when implementing behavior, fixing bugs, or wiring a vertical slice across layers.
---

# Feature Delivery

## When to Use
- Add or change user-visible behavior.
- Fix a defect that requires code changes.
- Implement a vertical slice across multiple layers.
- Execute a milestone that includes both feature work and foundational infrastructure/configuration.

## When NOT to Use
- Pure architecture exploration with no implementation request.
- Narrow specialist reviews such as cache design, DB query review, or logging review when no feature delivery is requested.
- Mechanical formatting-only edits.

## Required Input
- User request or milestone/spec.
- Relevant existing code, file tree, or diff.
- Constraints such as framework, module boundaries, performance, rollout, and documentation requirements.

## Workflow
1. Restate the target behavior or milestone scope and list explicit assumptions only if the code/spec does not answer them.
2. Identify the minimal set of files that must change.
3. Do a boundary pass before coding:
   - what is the business responsibility here?
   - what technical or cross-cutting concerns are involved?
   - is any new logic better isolated in a dedicated collaborator?
   - is the mechanism feature-local or a reusable project-level primitive?
   - what will need to be tested, and does the proposed design make that verification straightforward?
4. Implement the narrowest change that satisfies the request.
5. Add or update tests for every changed behavior.
6. Run a boundary pass for:
   - request validation
   - response semantics
   - error mapping
   - configuration and profiles
   - OpenAPI/Swagger requirements
   - persistence/query safety
   - observability
7. Run a cleanup pass for obsolete classes, methods, fields, configs, fixtures, and dependencies after refactoring or responsibility moves.
8. Run a milestone coverage pass: map every explicit milestone requirement to implemented, intentionally deferred, blocked, or not applicable.
9. Finish with exact verification status.

## Output Contract
Return these sections in order:
1. `Plan`
2. `Patch Summary`
3. `Requirement Coverage`
4. `Tests`
5. `Risks / Follow-ups` - only if there are real residual risks
6. `Tuning Knobs` - only if config or runtime behavior changed

## Guardrails
- Do not invent requirements or hidden files.
- Do not create new abstractions unless they remove a real problem in the changed code.
- Prefer deleting or simplifying code over adding layers.
- Do not leave comments that reference the spec, prompt, interview task, generation process, or assumptions history inside production code.
- Do not silently defer explicit milestone requirements just because they are not yet exercised by runtime flow.
- Do not place cross-cutting or low-level technical mechanics inside an application service just because the feature currently has only one use site.
- Do not make a component generic unless the reuse scope and stable responsibility are clear.

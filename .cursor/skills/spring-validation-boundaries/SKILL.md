---
name: spring-validation-boundaries
description: Design or review validation at Spring boundaries using Bean Validation annotations for request DTOs and configuration properties, including custom constraints when needed.
---

# Spring Validation Boundaries

## When to Use
- Add or review validation for controller request DTOs.
- Add or review validation for `@ConfigurationProperties`.
- Replace imperative field checks with declarative constraints.

## When NOT to Use
- Deep business invariants that require orchestration across repositories or external systems.
- Pure persistence constraints with no Spring boundary involvement.

## Required Input
- DTO or config class definitions.
- Validation rules and expected error semantics.
- Whether the rule is field-local, cross-field, or business-process level.

## Workflow
1. Put field-local and declarative rules on DTO/config fields with Bean Validation annotations.
2. Trigger validation at the HTTP boundary with `@Valid` / `@Validated` as appropriate.
3. For `@ConfigurationProperties`, validate required values, bounds, durations, URLs, credentials presence, and nested objects.
4. Use custom constraints for reusable domain-specific validation instead of repeating imperative checks.
5. Leave cross-aggregate or repository-backed business rules in the service layer.
6. Verify error mapping produces stable client-facing errors.

## Output Contract
Return:
1. `Validation Placement`
2. `Annotations / Constraints`
3. `Custom Constraint Need`
4. `Error Behavior`
5. `Tests`

## Guardrails
- Do not hand-write repetitive null/blank/range checks in controllers or services when Bean Validation can express them.
- Do not leave `@ConfigurationProperties` classes unvalidated when bad values can break startup or runtime behavior.
- Do not push true business workflow validation into DTO annotations.

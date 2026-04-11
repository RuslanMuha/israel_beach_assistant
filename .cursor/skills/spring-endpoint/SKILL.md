---
name: spring-endpoint
description: Implement or review a Spring Boot REST endpoint end-to-end, including request/response DTOs, validation, documentation, service flow, persistence touchpoints, error mapping, and tests.
---

# Spring Endpoint

## When to Use
- Add a new REST endpoint.
- Modify endpoint behavior, validation, request/response shape, or error handling.
- Review whether a Spring endpoint is wired correctly end-to-end.

## When NOT to Use
- Outbound HTTP client integration work.
- Pure repository/query tuning with no endpoint scope.
- Non-HTTP async/message-driven workflows.

## Required Input
- Endpoint intent and HTTP contract if available.
- Existing controller/service/repository code or desired vertical slice.
- Validation, documentation, error, and persistence constraints.

## Workflow
1. Confirm HTTP method, path, request DTO, response DTO, and status codes.
2. Prefer resource-oriented route naming unless the endpoint is not resource-based.
3. Keep controller limited to mapping, validation trigger, context extraction, and service delegation.
4. For controllers/services with final dependencies and no constructor logic, use Lombok `@RequiredArgsConstructor`.
5. Use explicit DTOs and deterministic mapping.
6. Trigger bean validation at the HTTP boundary when request DTO constraints exist.
7. Use custom constraints when built-in validation annotations are not sufficient.
8. For creation endpoints, decide explicitly whether `Location` header is required.
9. Place business rules and transaction boundaries in the service.
10. Verify repository/query shape is bounded and appropriate for the access pattern.
11. Wire centralized error handling instead of local ad-hoc try/catch.
12. If OpenAPI is required in the project, include endpoint documentation and follow the interface-based documentation convention when that is the repository standard.
13. Add controller/service/integration tests appropriate to the change.

## Output Contract
Return:
1. `HTTP Contract`
2. `Files to Change`
3. `Implementation Notes`
4. `Tests`
5. `Operational Checks`

## Guardrails
- Do not place business rules in the controller.
- Do not expose JPA entities directly in the HTTP contract.
- Do not skip `@Valid` when request DTO validation constraints are expected to apply.
- Do not write manual constructors for Spring beans when `@RequiredArgsConstructor` is sufficient and consistent with project conventions.
- Do not use verb-style routes by default when the endpoint represents a resource.
- Do not return `201 Created` without deciding explicitly whether the API should also return a `Location` header.
- Do not leave OpenAPI documentation incomplete when the repository requires it.
- If a controller implements a documentation interface, keep validation annotations on the controller method/parameters only.
- Do not place runtime validation annotations in the documentation interface.
- Do not duplicate method parameter constraints across interface and implementation.
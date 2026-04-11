---
name: dockerization-runtime
description: Create or review Dockerfile and Docker Compose setup for production-minded local/runtime packaging with security, health checks, env handling, and reliability defaults.
---

# Dockerization Runtime

## When to Use
- Add or refactor a Dockerfile or Docker Compose setup.
- Review container security, env wiring, health checks, restart behavior, image size, or local reliability.
- Prepare a service for local/dev/test runtime packaging.

## When NOT to Use
- Kubernetes-only deployment design with no Docker/Compose artifact scope.
- Pure application code changes with no runtime packaging impact.

## Required Input
- Application runtime and build system.
- Required dependent services.
- Local/dev operational expectations.
- Secret/config handling expectations.

## Workflow
1. Build a minimal runtime image, preferably multi-stage.
2. Run as non-root and keep the image free of secrets and build tooling.
3. Move deploy-specific values and passwords to environment variables or ignored local env files.
4. Commit only sanitized defaults or `.env.example`.
5. Add realistic health checks for app and stateful dependencies.
6. Verify startup ordering does not rely on `depends_on` alone.
7. Add restart policy, named volumes, and explicit ports only where they help local reliability.
8. Verify build, boot, health, and env override behavior.

## Output Contract
Return:
1. `Artifacts`
2. `Security / Secrets`
3. `Health / Reliability`
4. `Env Model`
5. `Verification`

## Guardrails
- Do not commit real secrets, passwords, or personal credentials.
- Do not keep app config split inconsistently across Dockerfile, compose, and properties without a reason.
- Do not use fake healthchecks that always return success.
- Do not run the runtime image as root by default.

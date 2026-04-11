---
name: code-review-java
description: Perform a strict Java/Spring code review focused on correctness, API boundary quality, architecture alignment, testing realism, and repository conventions.
---

# Code Review Java

## When to Use
- Review Java or Spring code for correctness and maintainability.
- Review controller, service, repository, configuration, or test code after Cursor generation.
- Produce review feedback in a senior-engineer format.

## When NOT to Use
- Implementing code changes directly without a review request.
- High-level architecture ideation with no concrete code.
- Language-agnostic review requests where Java/Spring specifics do not matter.

## Required Input
- Code snippet, file, or diff.
- Relevant repository conventions if known.
- Any explicit milestone or task requirements.

## Workflow
1. Review for correctness first.
2. Review boundary quality: validation, docs, error handling, config, profiles, observability.
3. Review architecture alignment: dependency direction, infra leakage, responsibility placement.
4. Review persistence/runtime semantics: transactions, repository contract clarity, retries, time source, config typing.
5. Review tests for realism, scope, and verification honesty.
6. Separate true defects from style inconsistencies and from acceptable trade-offs.

## Output Contract
For each review comment use:
- `Issue/Concern`
- `Impact`
- `Fix`

Then include:
1. `Overall Verdict`
2. `What Is Good`
3. `Main Risks`
4. `Non-Issues / Do Not Over-Fix` - when relevant

## Guardrails
- Do not invent issues from files or behavior that are not shown.
- Do not call something a bug merely because there is another valid style.
- Distinguish correctness issues, contract gaps, consistency issues, and optional improvements.

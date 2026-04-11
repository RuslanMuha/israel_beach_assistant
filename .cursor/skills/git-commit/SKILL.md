---
name: git-commit
description: Review the current diff, decide whether the work should be committed as one commit or split into multiple commits, propose pragmatic commit messages, and create a commit only when the user explicitly asks.
---

# Git Commit

## When to Use
- The user asks to prepare a commit.
- The user asks whether the current diff should be one commit or multiple commits.
- The user asks for commit message options.
- The user explicitly asks to create a git commit.

## When NOT to Use
- The user only asked to change code, review code, or explain a diff.
- The current work is still incomplete, blocked, or not yet reviewed.
- Verification status is unclear and the user did not ask to commit anyway.

## Required Input
- Current git diff or repository status.
- The change intent.
- Verification status:
  - passed
  - skipped by design
  - blocked by environment
  - not verified

## Workflow
1. Inspect the current diff and changed files.
2. Identify the main intent of the change.
3. Decide whether the diff should be:
   - one commit
   - split into multiple commits
4. If multiple concerns are mixed, propose a clean split by intent.
5. Summarize:
   - what changed
   - why it changed
   - what was verified
   - what was not verified
6. Propose 2-5 commit message options using pragmatic commit types:
   - `feat`
   - `fix`
   - `refactor`
   - `test`
   - `docs`
   - `chore`
7. Create a git commit only if the user explicitly asks to do so.

## Output Contract
Return these sections in order:
1. `Commit Assessment`
2. `Recommended Split`
3. `Commit Message Options`
4. `Verification Status`
5. `Commit Command / Action` - only if the user explicitly asked to commit

## Guardrails
- Do not create a git commit unless the user explicitly asks for it.
- Do not treat blocked or skipped verification as fully verified work.
- Do not hide mixed concerns inside one vague commit.
- Do not use weak commit messages such as:
  - `wip`
  - `tmp`
  - `fix2`
  - `final`
  - `misc changes`
- Prefer one commit per clear intent.
- If reverting a proposed commit would leave the repository in a broken state, recommend a better split.
- If the diff mixes unrelated feature, refactor, config, and test changes, call that out explicitly.
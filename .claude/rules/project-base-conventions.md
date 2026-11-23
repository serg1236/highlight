# Project Base Conventions

## Core Principles

- **Simplicity first** — smallest change that solves the problem.
- **Minimal impact** — touch only what is necessary; avoid unrelated refactors.
- **Root causes** — avoid temporary hacks; fix the underlying issue when practical.

## Behavioral Rules

1. For non-trivial work, outline a short plan before coding; adjust if requirements shift.
2. If requirements are unclear, ask before implementing.
3. After substantive changes, note edge cases and suggest tests.
4. Large changes: split into smaller steps instead of one huge diff.
5. Bugs: reproduce with a failing test when practical, then fix.
6. When corrected by the user, adjust approach and avoid repeating the same mistake.

## General Development Expectations
- Keep changes focused and minimal for the requested behavior.
- Prefer clear, maintainable code over clever shortcuts.
- Keep methods small and intention-revealing.
- Remove dead code and commented-out code instead of leaving it in place.
- Do not introduce TODO comments unless a ticket/reference is included.

## Reliability and Error Handling
- Handle failure paths explicitly and avoid swallowing exceptions.
- Add structured log messages for critical flow changes and error cases.
- Keep side effects explicit and local to the relevant layer.
- Make null/empty handling explicit at service and API boundaries.

## Layering and Ownership
- Keep controller classes thin: validation, mapping, and delegation only.
- Keep business rules in service classes.
- Keep persistence logic in repository classes.
- Do not bypass layers unless the existing architecture already requires it.

## Definition Of Done
- Touched modules compile cleanly.
- Tests for touched modules pass locally.
- New or changed business behavior includes tests.
- API/contract changes include updated controller or integration tests.
- Regression scenarios are covered for the changed behavior.
- When a change adds, removes, or renames `@ConfigurationProperties`, environment-variable bindings in `application.yml`, scheduled jobs, build/run steps, or materially alters service behavior, update the module `README.md` using the **java-readme-writer** skill (`.claude/skills/java-readme-writer/`). Skip only when the change is purely internal with no config or operational impact.
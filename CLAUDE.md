# Agent Working Guide

This repository uses a hybrid approach:

- `CLAUDE.md` is the quick entrypoint for people and agents.
- `.claude/rules/*.md` are the canonical, enforceable conventions.

> Core principles and behavioral rules are defined in
> `.claude/rules/project-base-conventions.md` and apply always.

## Skills

Agent skills live under `.claude/skills/`. Each skill has a `SKILL.md` that the agent reads when triggered. Key skills for Java / Spring Boot work:

- **java-readme-writer** (`.claude/skills/java-readme-writer/`) — create or update a module's `README.md` after feature work when tests pass and config, env vars, jobs, or run instructions changed.
- **java-unit-tester** (`.claude/skills/java-unit-tester/`) — write and maintain JUnit 5 + Mockito unit tests with a consistent style.

## Source Of Truth

Follow these rules as the primary policy:

- `.claude/rules/project-base-conventions.md` (global conventions and definition of done)
- `.claude/rules/maven-build-verify.md` (Maven build and verify for Spring Boot modules)
- `.claude/rules/java-style.md` (production Java style for `src/main/java`)
- `.claude/rules/java-testing-requirements.md` (Java testing requirements for `src/test/java`)

If this file and a rule differ, the `.claude/rules/*.md` file content takes precedence.

Add project-specific rules as additional `.claude/rules/*.md` files alongside the shared ones.

## Exceptions And Overrides

- Do not silently deviate from the rules.
- If an exception is necessary, document the rationale in the task or PR.
- Prefer narrow, explicit exceptions over broad rule bypasses.

## Maintenance

- Keep this file short and index-like.
- Put detailed policy changes in `.claude/rules/*.md`.
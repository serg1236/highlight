---
name: java-readme-writer
description: |
    Use after a feature on a Java/Spring Boot service is implemented and the
    module's tests pass to create or update its `README.md`. The README must
    explain what the app does, list every required and optional environment
    variable mapped to its `application.yml` key, and give concrete build and
    run instructions. Also use whenever the change adds, removes, or renames
    `@ConfigurationProperties`, env vars, scheduled jobs, or alters how the
    app is built or run, and whenever the user asks to "write README",
    "update README", "document the app/service", or "add docs for the new
    feature". Be proactive: at the end of feature work, check the README
    and update it before declaring the task done.

---

# Java README Writer

A focused checklist and template for writing or updating `README.md` of a
Java / Spring Boot service in this workspace, so a developer (or the next
agent) can understand the app, configure it, and run it without reading
the source.

## When to apply

Apply this skill once the implementation work for a change is finished and
the module's tests pass. Concretely:

- A new feature, scheduled job, repository, or service has been added.
- A `@ConfigurationProperties` class was added, removed, or renamed.
- An entry in `src/main/resources/application.yml` was added, removed,
  or its env-var binding (`${ENV_VAR:default}`) changed.
- A new external dependency (DB, IBM i program, file system path, queue,
  HTTP endpoint) became required to run the app.
- The build or run command changed (new Maven profile, new sibling
  SNAPSHOT dependency that must be installed first, etc.).
- The user explicitly asks to write or refresh the README.

If none of the above is true, the README likely does not need an update —
say so instead of editing it.

## What "good" looks like

A `README.md` written with this skill should let a new engineer:

1. Understand in one paragraph what the service does and which stack it
   uses (Spring Boot version, Java version, what kind of worker / API).
2. See exactly which env vars are mandatory vs. optional, and what each
   `application.yml` key controls.
3. Build the module with a single copy-pasteable command, including any
   sibling SNAPSHOT install step needed before `mvn verify`.
4. Run the service locally with a minimal, copy-pasteable env-var block.
5. Find the entry point, configuration class, jobs, services, and
   repositories without grepping.

Do not duplicate per-class Javadoc; keep the README at the level of
"how do I configure and run this service".

## Required sections

Use this exact set of top-level sections, in this order. Omit a section
only if it is genuinely irrelevant for the module (and say why in the
PR/commit message, not in the README itself).

1. **Title + intro paragraph**
2. **Behavior**
3. **Configuration (`application.yml`)**
4. **Build and verify**
5. **Runtime**
6. **Project layout**

A ready-to-fill template lives in
[`references/template.md`](references/template.md). Read it and copy the
shape rather than improvising the layout.

### 1. Title + intro paragraph

- Title is the module's artifactId (matches the directory and `pom.xml`).
- The intro is **one paragraph** stating, in this order:
    - Spring Boot version (read from `pom.xml` → `<parent>` or
      `spring-boot.version` property) and Java version
      (`<java.version>`).
    - Whether the app is a worker / scheduled job, a REST API, a CLI
      one-shot exporter, etc.
    - The end-to-end purpose in business terms (what data flows in,
      what flows out, where it lands).
    - Any non-obvious assumption a future maintainer must know
      immediately (e.g. "if no `tenant_settings` row exists, defaults apply").
- If there is a `CLAUDE.md`, end with: "For AI/agent conventions
  (rules, skills), see [`CLAUDE.md`](CLAUDE.md) if present."

### 2. Behavior

A bullet list of the runtime behavior that a maintainer needs to predict
the app's actions. Cover at minimum:

- **Scheduling / entry**: cron expression property and the job class,
  or the controller / CLI runner that drives execution.
- **Startup checks / validators**: directory validators,
  Bean-Validation rules on `@ConfigurationProperties`, fail-fast
  behavior.
- **Per-unit-of-work flow**: for each input (file, request, record),
  what is read, transformed, persisted, and what determines success vs.
  failure (file rename suffixes, HTTP status, DB state).
- **Error handling**: what causes a retry vs. a permanent failure,
  what gets logged, what gets renamed/marked.

Link class names to their source files using relative paths, e.g.
[`FileReaderJob`](src/main/java/.../job/FileReaderJob.java).

### 3. Configuration (`application.yml`)

A Markdown table with two columns: `Key | Purpose`.

Generate rows by reading **both**:

- The module's `@ConfigurationProperties` class(es) (canonical source of
  property names, types, defaults, validation).
- `src/main/resources/application.yml` (canonical source of env-var
  bindings — every `${SOMETHING:default}` placeholder).

For each property:

- `Key` is the dotted YAML path (e.g. `app.input-directory`).
- `Purpose` is one sentence ending with the env-var override in
  parentheses, plus the default if any. Mark required properties with
  **"Required"** in the purpose cell.

Example row format:

```
| `app.input-directory` | **Required** — monitored folder (`APP_INPUT_DIRECTORY`) |
| `app.file-reader-job-cron` | Cron (`APP_FILE_READER_JOB_CRON`, default `0 * * * * *`) |
```

Cross-check after writing: every `${ENV:...}` in `application.yml` must
appear in the table, and every property in the `@ConfigurationProperties`
class must appear too. If a property has no env-var binding, list the
key without an env-var name and note "(no env-var override)".

### 4. Build and verify

- State the canonical build command from
  `.claude/rules/maven-build-verify.md`:

    ```bash
    mvn -B clean verify
    ```

- If the module depends on a sibling internal SNAPSHOT, add a
  one-line note with the exact `mvn -B install -DskipTests -f ../sibling/pom.xml`
  command **before** the verify command.
- Do not invent build flags; only mention profiles or skips that the
  module's `pom.xml` actually defines.

### 5. Runtime

- One short intro sentence: what env vars are needed for normal
  operation (filter to **required** ones from the configuration table,
  plus any password/secret).
- A copy-pasteable POSIX `bash` block that exports those vars and runs
  the **packaged jar**:

    ```bash
    java -jar target/<artifactId>-<version>.jar
    ```

  This is the canonical run command for every module in this
  workspace, including one-shot CLI exporters scheduled by Windows
  Task Scheduler / cron and long-running Spring Boot services. It is
  what real environments invoke, so it must work end-to-end. Read
  `<artifactId>` and `<version>` from `pom.xml` to fill in the exact
  file name.
- `mvn -B spring-boot:run` may be mentioned as an **optional local-dev
  shortcut** in a single follow-up sentence after the code block, but
  never as the primary command and never alone.
- If the runtime has conditional requirements (e.g. "the `prod` profile
  also requires `app.partner-mappings`", "intended to run once per
  day"), put them in a short paragraph after the code block.
- Do **not** include real credentials or hostnames. Use `...` or
  placeholder values like `/path/to/inbox`.

### 6. Project layout

A Markdown table with `Area | Location` mapping each significant area
(Entry point, Config, Domain, Job, Service(s), Repository, XML/serialization,
HTTP layer, etc.) to the primary file(s). Link each location relatively.

Keep this table to the things a maintainer needs to find first; do not
list every class.

## Update workflow (existing README)

When a `README.md` already exists, prefer **surgical edits** over a
rewrite:

1. Read the current README first.
2. Re-derive the configuration table from `application.yml` and the
   `@ConfigurationProperties` class, and reconcile with the table in
   the README. Add new keys, remove deleted ones, fix renamed ones.
3. If the change introduced a new behavior (job, validator, branch in
   the flow), add or update a single bullet under **Behavior**.
4. If the change altered build or run steps, update those sections.
5. Leave wording, ordering, and unrelated sections untouched.

This matches the workspace rule "Minimal impact — touch only what is
necessary; avoid unrelated refactors".

## Verification before declaring "done"

Before considering the README task finished, run through this checklist:

- [ ] Title equals the module's artifactId.
- [ ] Intro names the Spring Boot version and Java version found in
  `pom.xml`.
- [ ] Every `${ENV:...}` in `application.yml` appears in the
  configuration table.
- [ ] Every property field in `@ConfigurationProperties` appears in the
  table (or is documented as deliberately omitted, e.g. internal).
- [ ] Required properties are explicitly marked **Required**.
- [ ] Build section's command runs from the module root and matches
  `.claude/rules/maven-build-verify.md`.
- [ ] Runtime block lists at minimum every property marked **Required**.
- [ ] All file links resolve (relative paths from the README's
  directory).
- [ ] No secrets, no real hostnames, no internal IPs.
- [ ] No TODOs without a ticket reference (project base convention).

If any item fails, fix it before reporting the task complete.

## Style notes

- Use sentence case for headings.
- Prefer short bullets and tables over prose paragraphs for reference
  material (config keys, project layout).
- Use backticks for class names, file paths, env vars, YAML keys, and
  shell commands.
- Use **bold** for emphasized terms the maintainer must not miss
  (e.g. **Required**, key message types, key file suffixes).
- Keep code fences language-tagged (` ```bash `, ` ```yaml `, ` ```java `).
- Do not invent versions or defaults — read them from `pom.xml`,
  `application.yml`, or the property class.

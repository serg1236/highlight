# `<artifact-id>`

Spring Boot **<spring-boot.version>** / **Java <java.version>** <kind:
worker | REST API | one-shot CLI exporter | …> that <one-paragraph
description of what the service does end-to-end: where data comes from,
how it is transformed, where it lands, and any non-obvious assumption a
future maintainer must know immediately>.

For AI/agent conventions (rules, skills), see [`CLAUDE.md`](CLAUDE.md) if present.

## Behavior

- **Scheduling / entry:** `<JobOrController>` runs on
  `<property.name>` (<cron or trigger description>).
- **Startup:** `<ValidatorClass>` ensures `<property.name>` is valid;
  `<PropertiesClass>` is validated at bind time via Bean Validation
  (`@NotBlank`, `@Pattern`, etc.).
- **Per <unit-of-work>:** <describe the main flow: read → transform →
  persist/produce output → mark success/failure>. Include the rules
  that decide success vs. failure (file rename suffixes, HTTP status,
  DB state, …).
- **Error handling:** <what is retried vs. left for next run; what is
  logged; what is moved/renamed/rolled back>.

## Configuration (`application.yml`)

| Key                       | Purpose                                                          |
|---------------------------|------------------------------------------------------------------|
| `spring.application.name` | `<APP_NAME_ENV_VAR>` (default `<artifact-id>`)                   |
| `<group.required-key>`    | **Required** — <what it controls> (`<ENV_VAR_NAME>`)             |
| `<group.optional-key>`    | <what it controls> (`<ENV_VAR_NAME>`, default `<default-value>`) |
| `<group.no-env-key>`      | <what it controls> (no env-var override)                         |

> Generate rows from the module's `@ConfigurationProperties` class **and**
> from every `${ENV_VAR:default}` placeholder in `application.yml`.
> Mark required properties with **Required**. Include the default in
> parentheses when one exists.

## Build and verify

> If this module depends on a sibling SNAPSHOT, install it first, e.g.:
>
> ```bash
> mvn -B install -DskipTests -f ../<sibling-module>/pom.xml
> ```

From this directory:

```bash
mvn -B clean verify
```

## Runtime

Normal operation requires `<list of required env vars>`:

```bash
export <REQUIRED_ENV_VAR_1>=<placeholder>
export <REQUIRED_ENV_VAR_2>=<placeholder>
# add other required vars here
java -jar target/<artifact-id>-<version>.jar
```

<Optional paragraph: any conditional runtime requirements, e.g.
"Production <feature> also requires <property> mappings"; or notes about
profiles, additional secrets, scheduling cadence, or external systems
that must be reachable. Optionally mention `mvn -B spring-boot:run` as a
local-dev shortcut here, never as the primary command.>

## Project layout

| Area        | Location                                                             |
|-------------|----------------------------------------------------------------------|
| Entry       | [`<App>.java`](src/main/java/.../<App>.java)                         |
| Config      | [`<Properties>`](src/main/java/.../config/<Properties>.java)         |
| Domain      | [`<DomainModel>`](src/main/java/.../domain/model/<DomainModel>.java) |
| Job         | [`<Job>`](src/main/java/.../job/<Job>.java)                          |
| Service     | [`<Service>`](src/main/java/.../service/<Service>.java)              |
| Repository  | [`<Repository>`](src/main/java/.../repository/<Repository>.java)     |
| Output / IO | [`<Writer>`](src/main/java/.../xml/<Writer>.java)                    |

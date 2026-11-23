# Java Production Style

## Baseline
- Target Java 21 language features and Spring Boot project idioms.
- Indent with tabs (not spaces) in both production and test sources.
- Keep package structure consistent with layered ownership (controller, service, repository, dto, entity).
- Define **service contracts** as interfaces under `service/`; place implementations under **`service/impl/`**. Do not collapse to a single concrete class for top-level domain services unless the module already does so consistently.

## Class and Dependency Patterns
- Prefer constructor injection and immutable dependencies (`final` fields); Lombok constructor annotations are acceptable.
- Use `@Slf4j` on any class that emits operational or diagnostic logs.
- For Lombok-backed **entities, DTOs, and domain models** with multiple fields: **prefer `@Builder`** for construction (`Type.builder()…build()`); **avoid** long `new Type()` plus many setter calls when a single built instance is enough. Keep **`@Data`** (or explicit accessors) when types are mutated after creation or frameworks need setters or a no-arg constructor; pair **`@Builder`** with **`@NoArgsConstructor`** / **`@AllArgsConstructor`** when both builder and no-arg construction are required. Keep builders explicit for complex validation beyond field assignment.
- Prefer Java `record` for narrow, package-private value carriers internal to a class or service (small batch contexts, scoped key tuples, etc.). Keep full Lombok `@Data` / `@Builder` for JPA entities, request/response DTOs, and other public model types.
- Keep utility classes stateless and side-effect free unless explicitly designed otherwise.

## Configuration
- Prefer **typed configuration** with `@ConfigurationProperties` (or constructor-bound `@ConfigurationProperties` beans) keyed from `src/main/resources/application.yml` (and profile-specific YAML there); avoid injecting raw `Environment` or `Map` blobs for stable settings.
- Validate critical numeric or path settings at startup where wrong values cause silent failure (e.g. empty output directory).

## Scheduled Jobs and Lifecycle
- Use `@Scheduled` on dedicated beans; keep scheduling concerns separate from heavy business logic where possible (delegate to services).
- Document cron meaning in code or Javadoc when non-obvious (six-field Spring cron includes seconds).
- For long-running or I/O-heavy work, consider **graceful shutdown**: respect `InterruptedException`, avoid blocking indefinitely, and clean up resources in `@PreDestroy` or `DisposableBean` when holding open files or locks.
- Be cautious of **overlapping executions** if job duration can exceed the cron interval; use locking, idempotency, or `ScheduledExecutorService` with fixed delay when required.

## Controller, Service, Repository Boundaries
- Controllers should only orchestrate request/response concerns and delegate business logic.
- Apply **`@PreAuthorize`** at class or method level when endpoint access is authority-based; align authority strings with the project's shared security constants or existing module conventions.
- Services own domain/business rules, validation orchestration, and workflow decisions.
- Repositories own data access concerns and should not contain domain decision logic.
- For **JDBC** repositories using `NamedParameterJdbcTemplate`, express multi-line SQL with **Java text blocks** (`""" ... """`) for readability and stable formatting.
- For **complex SQL** (heavy joins, aggregates, vendor-specific result types such as PostgreSQL arrays, or shapes that do not map cleanly to entities): prefer **`NamedParameterJdbcTemplate`** with explicit mapping (`RowMapper`, constructor mapping, or small mappers). Use Spring Data **`JpaRepository` + `@Query(nativeQuery = true)`** with **interface or class projections** only when every selected column binds reliably to the projection (including arrays and enums); do **not** inject or use **`EntityManager`** directly in application repositories for report-style queries.

## Method and Data Handling
- Keep methods focused on one responsibility and split complex branches into private helpers.
- Prefer descriptive names for DTO/entity fields and methods; avoid unclear abbreviations.
- Be explicit about null, empty, and optional behavior at boundaries.
- Use deterministic collections (`List`, `Set`, `Map`) intentionally based on ordering and uniqueness needs.
- Prefer `List<T>` over `T[]` for return types and parameters; use arrays only when a specific API or interop contract requires them.

### Local type inference (`var`)
- Prefer `var` for local variables and enhanced `for` loops (`for (var x : …)`) when the type is clear from the initializer, factory, or method return.
- Do not use `var` for fields, method parameters, or return types (`var` applies to locals only).
- **Generics and diamond**: avoid `var x = new ArrayList<>()` without a target type (it becomes `ArrayList<Object>`). Use an explicit type argument, e.g. `var fallback = new ArrayList<String>()`, or a factory that returns the intended generic type.
- **When to stay explicit**: keep a written interface or supertype on the left (e.g. `List<T> x = new ArrayList<>()`) if documenting the abstraction matters more than brevity; otherwise `var` is fine when the RHS already has that type (e.g. `var lines = loadLineTexts(...)` where the method returns `List<String>`).

## Error Handling and Logging
- Do not swallow exceptions; either handle and log with context or rethrow with clear intent.
- Log key state transitions for async/event-driven operations.
- Ensure log messages include identifiers needed for tracing (user/station/reason/job context).
# Maven Build and Verify

## Prerequisites

- **JDK 21** (match `<java.version>` / compiler settings in the module `pom.xml`).
- **Maven** on `PATH`. Use `./mvnw` if the project ships a wrapper, otherwise `mvn`.

## Where to run

Run commands from the **target module directory**, or use `mvn -f path/to/pom.xml`.

Always match the Spring Boot version and `<java.version>` declared in the **module's own** `pom.xml`.

## Restricted shells

Maven test and verify need access to `~/.m2`, child processes, and often the network. Run `mvn … verify`, `mvn … test`, and `mvn … install` (when tests are not skipped) outside any restricted shell sandbox so dependency resolution and tests can complete.

## Default check after code changes

From the module root:

```bash
mvn -B clean verify
```

## Build without running tests

```bash
mvn -B -DskipTests clean package
```

## Single test class

```bash
mvn -B clean -Dtest=ClassName test
```

Use the simple or fully qualified class name as appropriate.

## Local SNAPSHOT dependencies

If resolution fails for internal SNAPSHOT artifacts, install the missing modules first: from each project directory that owns the artifact, run `mvn -B install -DskipTests`. Use the module's `pom.xml` `<dependencies>` as the list of what may need installing.

## Integration tests and environment

Many services load `src/test/resources/application-test.yml` and require environment variables (for example `TEST_DB_*`). Do not invent or commit credentials. If tests fail for missing configuration, fix local env or align with task instructions; only skip tests when the task explicitly allows it.
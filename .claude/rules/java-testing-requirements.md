# Java Testing Requirements

## Framework and Structure
- Use JUnit 5 for all new tests.
- Prefer `@ExtendWith(MockitoExtension.class)` for unit tests with Mockito.
- Keep tests in **given-when-then** structure; short comments for section boundaries are acceptable.
- Test classes and test methods are **package-private** (no `public` on the class or `@Test` methods unless the test framework requires otherwise).
- Name test classes `<TestSubject>Test` (e.g. `EmployeeServiceImplTest` for `EmployeeServiceImpl`).
- Name test methods with optional segments: `<testSubjectMethod>_<useCase>_<expectedResult>` — use-case and expected-result parts are optional when the name stays clear. Examples: `updateEmployee`, `updateEmployee_notFound`, `updateEmployee_notFound_shouldThrowException`. Legacy `should...` or `test...` names may remain until refactored.
- Use **`org.junit.jupiter.api.Assertions`** (`assertEquals`, `assertThrows`, `assertTrue`, `assertDoesNotThrow`, …) for **all new and touched tests**. Do not add new AssertJ (`assertThat`, `tuple`, …) usages. Existing AssertJ tests are legacy; migrate them to JUnit Jupiter when you touch the file.

## Coverage target
- Aim for **90% line coverage** on each unit under test (the class or cohesive unit you are testing).

## Entities that can be left untested
- REST controllers whose methods are single-line delegates with no business logic.
- Entities, DTOs, and other domain model classes.
- Spring Data JPA repository methods with no custom queries.
- Repositories for databases without a stable test environment (e.g. Oracle, DB2) when integration tests are not practical; if the code builds SQL dynamically, consider unit tests that assert the generated query.
- Legacy code touched only minimally: removals, one-line additions, argument or field renames, and similar trivial edits.

## Spring Boot 3.4 test annotations
- Prefer **`@MockitoBean`** and **`@MockitoSpyBean`** on test fields for replacing beans in `@SpringBootTest` slices (Spring Boot 3.4+; package `org.springframework.test.context.bean.override.mockito`).
- **`@MockBean`** and **`@SpyBean`** are deprecated; migrate new and touched tests to `@MockitoBean` / `@MockitoSpyBean`.

## Unit Test Expectations
- Every new or changed business rule in services/utilities must have unit test coverage.
- Assert observable behavior and outputs, not internal implementation details.
- When asserting a resulting DTO or value object, compare the **whole expected object** to the actual result instead of asserting field-by-field only, so new fields are caught by the test.
- Use Mockito stubbing and verification only for meaningful interactions.
- **Mockito `eq(...)`:** Omit `eq` when every argument in that `when(...)` or `verify(...)` is a concrete value (pass literals and variables directly). Use `eq(...)` only where Mockito requires matchers — i.e. when another argument in the **same** invocation uses a matcher (`any()`, `anyString()`, `anyList()`, `ArgumentCaptor.capture()`, etc.); then every argument in that invocation must be a matcher, so wrap fixed values with `eq(...)`.
- **Prefer precise argument matchers:** Avoid `any()`, `anyString()`, `anyList()`, `anySet()`, and similar wildcard matchers in stubs (`when(...).thenReturn(...)`) and positive `verify(...)` calls whenever the exact argument value is known from the test setup. Use the specific value directly (or via `eq(...)` when matchers are required). Precise matchers ensure a wrong argument passed by production code causes an immediate test failure. Reserve `any*()` for `verify(..., never())` assertions — where the intent is "never called regardless of arguments" — and for genuinely variable values such as parameterized test inputs that cannot be determined at stub time.
- Cover both happy path and at least one failure/edge path per changed behavior.
- **Do not widen method visibility for testability.** Do not make a method `package-private`, `protected`, or `public` solely to allow direct unit tests on it. Test private implementation details through the class's public interface. If a helper is complex enough to seem to need its own tests, consider whether it belongs in a separate class with a public interface. If an agent believes widening visibility for testing is the right call in context, it must ask the user before doing so.
- Use **`@MockitoSettings(strictness = LENIENT)`** or **`lenient()`** only when unavoidable (e.g. shared setup with optional stubs). Prefer fixing strict-mode failures by removing unnecessary stubs or splitting tests first.
- **PowerMock** (or similar) to mock private or static methods is a sign of poor design; prefer refactoring. For static utility methods, exercise real behavior in tests when practical.
- Do **not** mock **`RestTemplate`** or **`WebClient`**; use **Wiremock** (`com.github.tomakehurst.wiremock`) to stub the remote HTTP API instead.

## Parameterized tests
- Use **`@ParameterizedTest`** with **`@MethodSource`**, **`@CsvSource`**, or **`@ArgumentsSource`** for data-driven cases (DTOs, parsers, utilities).
- Keep parameter sets readable; extract providers to named methods when the table grows.

## Controller/API Test Expectations
- Endpoint changes must include HTTP-level tests (`MockMvc` or project standard equivalent).
- Prefer a **narrow slice**: `@SpringBootTest(classes = { YourController.class, YourTestConfig.class })` with only the controller and minimal supporting beans.
- Use **`@AutoConfigureMockMvc`**; add **`@EnableWebMvc`** when the slice needs MVC configuration.
- For secured endpoints, add an inner **`@Configuration`** with **`@EnableMethodSecurity`** and a **`@Primary` `SecurityFilterChain`** that permits all requests and disables CSRF for the test, so tests focus on controller behavior and validation.
- Replace collaborators with **`@MockitoBean`** where the controller depends on services or infrastructure.
- Verify status codes and request validation behavior.
- For secured endpoints, include authorization/permission scenario coverage where behavior differs by role.
- Include both successful and invalid input scenarios when request models are changed.

## Async and Event-Driven Test Expectations
- For asynchronous/event handlers, use deterministic waiting (`Awaitility`) instead of sleeps.
- Verify side effects and interactions once async processing completes.
- Keep timeouts tight but stable to avoid flaky tests.

## Test Data and Determinism
- Use explicit fixtures/builders to keep test intent clear.
- Avoid non-deterministic data and shared mutable state across tests.
- Ensure tests are isolated and can run independently in any order.

## Repository integration tests (PostgreSQL)
- Use the `*Test` class name suffix (not `*IT`) so Maven Surefire discovers them with default includes.
- Require `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD` (see `src/test/resources/application-test.yml`).
- Keep DbUnit Flat XML fixtures beside the test class package under `src/test/resources/.../repository/` when used.
- For **read-only** database operations, define the dataset with XML (Flat XML or project standard) as usual.
- For tests that **insert, update, or delete** data, define the **teardown/removal** dataset **dynamically** (typically via SQL query or `QueryDataSet`), not only a static Flat XML load, so rows inserted in the test are removed safely.
- **Test data uniqueness:** fixture rows must not collide with unrelated data in the shared environment. Prefer synthetic keys: for numeric identifiers, values such as `-(current timestamp ± N)`; for strings or composite keys, use obviously non-production values (e.g. `ZZ_TEST_` prefixes) and, where helpful, prefixes derived from repository or test names. Apply the same care to **unique non-key columns** that must stay distinct.

### JDBC repository tests
- Use **`@SpringBootTest`** with an explicit **`classes`** list (repository under test plus JDBC/DataSource/Jackson auto-configuration as needed).
- Use **`@ActiveProfiles("test")`** when the module uses profile-specific datasource settings.
- Use DbUnit **`DatabaseOperation.REFRESH`** / **`DELETE`** (or equivalent) in **`@BeforeEach`** / **`@AfterEach`** for dataset lifecycle.
- Use a shared DbUnit connection factory if the project provides one; otherwise configure **`DatabaseDataSourceConnection`** with **`PostgresqlDataTypeFactory`** and **`FEATURE_QUALIFIED_TABLE_NAMES = true`** as described under JPA repository tests.

### JPA repository tests
- Prefer **`@TestInstance(Lifecycle.PER_CLASS)`** when expensive setup (shared persistence context / schema) should run once per class.
- Use an inner **plain `static` class** (e.g. `TestConfig`) with **`@AutoConfigureDataJpa`**, **`@AutoConfigureTestEntityManager`**, **`@EnableJpaRepositories`**, **`@EntityScan`** scoped to entities and repositories under test. Do **not** annotate that inner class with **`@SpringBootApplication`**, **`@SpringBootTest`**, or **`@Configuration`** unless an existing module pattern explicitly requires it — `@SpringBootApplication` pulls in broad scanning, scheduling, and unrelated auto-configuration.
- Wire DbUnit lifecycle in **`@BeforeAll`** / **`@AfterAll`** (or **`@BeforeEach`** / **`@AfterEach`**): **`DatabaseOperation.REFRESH`** before tests and **`DELETE`** after, using the same connection settings as below.
- **DbUnit connection (PostgreSQL):** Every **`DatabaseDataSourceConnection`** used in these tests must set **`DatabaseConfig.PROPERTY_DATATYPE_FACTORY`** to **`new PostgresqlDataTypeFactory()`** and **`DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES`** to **`true`**.
- **Flat XML element names must be lowercase** when targeting PostgreSQL with **`FEATURE_QUALIFIED_TABLE_NAMES = true`**. PostgreSQL JDBC metadata returns schema and table names in lowercase; uppercase element names (e.g. `APP.ORDERS`) cause **`NoSuchTableException`** even when the tables exist. Use **`app.orders`** (schema and table both lowercase) in every DbUnit dataset XML file.
- **Single dataset file:** Prefer one Flat XML dataset per test class named **`<TestClass>-dataset.xml`** in the matching package under `src/test/resources`. Load it once with **`DatabaseOperation.REFRESH`** in **`@BeforeAll`**. Do not split baseline fixture data across multiple per-scenario XML files unless a rare case truly requires it.
- **`@BeforeAll` / `@AfterAll`:** Use for one-time DbUnit load and teardown. In **`@AfterAll`**, delete the static baseline with **`DatabaseOperation.DELETE`** on the same Flat XML dataset, then delete rows inserted during tests with a **`QueryDataSet`** built from a scoped **`SELECT … WHERE`** that matches only those inserted keys. Keep **`@Test` methods** to assertions and repository calls only — avoid per-test setup/teardown helpers, try/finally cleanup blocks, or hand-rolled JDBC insert pipelines that duplicate DbUnit.
- Do **not** **`@Autowired`** **`EntityManager`** into JPA repository integration tests to call **`flush()`** / **`clear()`** after mutations inside **`@Transactional`** test methods; the transactional test boundary already provides an isolated persistence context for each method.
- **Insert coverage:** For insert tests, build the expected entity with the builder, call the repository insert, reload the row via the repository (same lookup the feature uses), and assert **`assertEquals(expected, loaded)`** so unmapped or wrong columns fail the test; avoid asserting only a subset of fields field-by-field.

- **Shared test database — deletion safety:** Do not remove data except rows that belong to this test's own fixtures (keys declared in the test's DbUnit XML or inserted under known synthetic identifiers). Prohibited: `TRUNCATE`; `DELETE` without a predicate that limits to those keys; Spring Data `deleteAll()` or other "clear table" helpers on shared tables. Allowed: PK-scoped deletes matching fixture keys—for example, define initial rows in a Flat XML dataset and tear them down with `DatabaseOperation.DELETE` on the same dataset, plus a `QueryDataSet` built from a `SELECT … WHERE` predicate scoped to keys inserted during the test, or an equivalent PK-scoped SQL `DELETE` with the same predicate if DbUnit delete fails—and inserts that roll back via `@Transactional` when nothing is committed destructively. Prefer dedicated synthetic keys where collisions with pre-existing shared DB rows would affect assertions.

## Change-Gated Requirement
- If production code in `src/main/java` changes, update or add tests in `src/test/java` in the same task.
---
name: java-unit-tester
description: Writes and maintains JUnit 5 + Mockito unit tests for Java classes. Use this whenever asked to write, add, update, or fill in unit tests for a Java class or method — including "add tests for X", "test this method", "make sure Y is covered", or when a class was just changed and its tests need to catch up. Always follow this skill's workflow and style rules rather than improvising a different testing style.
---

# Unit Tester

Writes JUnit 5 + Mockito unit tests that follow one consistent, deliberate style. Don't improvise a different structure,
naming scheme, or mocking approach even if it would technically work — the point of this skill is consistency across the
whole test suite, not just passing tests.

## Workflow

For each class you're asked to test, follow this sequence — don't skip straight to writing test bodies:

1. **Detect changed/added methods.** Look at what changed in the class under test (e.g. `git diff` / `git log` against
   that file, or the specific methods the user pointed at) so you know exactly what needs coverage.
2. **Check whether a test class already exists.** Look for `<Class>Test` in the matching test package. If it doesn't
   exist, create it.
3. **Analyze existing coverage for the changed/added methods.** For each one, is it already tested? Are its main
   scenarios covered — the happy path and the meaningful edge/failure cases (e.g. not-found, validation failure, empty
   input)? A scenario is a distinct *observable outcome of the whole method* — a return value, a thrown exception, or a
   downstream interaction that did or didn't happen — not just "this one internal step succeeded," which on its own
   produces no observable outcome yet (see "Assertions and coverage" below).
4. **Stub the missing scenarios.** For every scenario that isn't covered yet, add an empty test method whose name
   already describes exactly what it verifies (see naming convention below) — this makes the coverage gap visible before
   you write a single line of implementation.
5. **Implement each stub**, following the style rules below and the patterns in `references/`.
6. **Run the tests, check the results, and fix them until everything passes** — by editing test code only. If a failure
   points at something actually wrong in the production code rather than the test, stop and tell the user instead of
   changing source (see "Source code boundary" below).

## Style rules

**Naming and structure**

- No shortened/abbreviated variable or field names — write out what the thing is.
- Test classes and test methods are package-private (no `public` modifier).
- Test class is named `<Subject>Test`. Test methods are named `<method>_<useCase>_<expectedResult>` — the use-case and
  expected-result segments are optional once the method name is already clear on its own (e.g. a single obvious
  happy-path test can just be `findOrdersByCustomerId`).
- Every test is structured with `// given` / `// when` / `// then` comments marking the three sections.
- Name the values being compared `expected` and `actual`.

**Mocking**

- Use `@ExtendWith(MockitoExtension.class)` with `@Mock` fields for collaborators and `@InjectMocks` for the class under
  test, instead of constructing it by hand.
- Avoid using `Mockito.lenient()` or `@MockitoSettings(strictness = LENIENT)`. If a stub looks unnecessary under strict
  mode, remove the stub or split the test rather than loosening strictness.
- Avoid PowerMock or reflection-based mocking of private/static methods — if a test seems to need that, it's usually a
  sign the design should change, not the test.
- Omit `eq(...)` when every argument in a `when(...)` or `verify(...)` call is a concrete value — pass the literal or
  variable directly. Use `eq(...)` only when another argument in that *same* call requires a matcher (Mockito requires
  all-or-nothing per call).
- Use `any()` (or other wildcard matchers) **only when matching by equality genuinely isn't possible or known yet** —
  for example: time-related values, objects without a reliable `equals`/`hashCode`, anonymous classes, `RowMapper`/
  `ResultSetExtractor`-style callbacks passed into `NamedParameterJdbcTemplate` calls, or `verify(..., never())` checks
  where the argument doesn't matter. Otherwise, match the concrete expected value so a wrong argument from production
  code fails the test immediately.

**Assertions and coverage**

- Use `org.junit.jupiter.api.Assertions` (`assertEquals`, `assertThrows`, `assertTrue`, `assertDoesNotThrow`, ...) — not
  AssertJ — for anything you write or touch.
- One assertion per test method. When comparing a resulting DTO or value object, assert equality on the whole expected
  object rather than field-by-field, so a newly added field that production code forgets to populate is still caught.
  The exception is a method whose real effect is a side effect (a void call, a write to a collaborator) — there the
  check is a `verify(...)` on that interaction instead of an assertion.
- Assert observable behavior and outputs, not internal implementation details.
- Cover the happy path plus at least one meaningful failure/edge case per changed behavior.
- **Don't give an early step its own test just for succeeding.** Many methods chain steps — validate, then persist, then
  publish an event, say. An early step succeeding isn't an observable outcome by itself: nothing has actually happened
  yet from the caller's point of view, so a test that stops there (e.g. asserting only that no exception was thrown
  after validation) isn't verifying real behavior. Fold that success case into the test(s) that carry the method through
  to an actual outcome, and assert on that outcome instead — the return value and/or a `verify(...)` on the downstream
  collaborator. The flip side still holds: an early step's *failure* is a legitimate scenario on its own whenever it
  changes the method's outcome (throws, short-circuits, skips the later steps) — that's a real, distinct, observable
  result, not the anti-pattern. And when a later step has multiple outcomes of its own (repository save throws vs.
  succeeds, event publish throws vs. succeeds), each of those is its own scenario, with the earlier steps succeeding as
  part of its `given` — there's no need for a separate, redundant test that covers only "earlier steps succeeded, then
  stopped."
- Never widen a method's visibility (to package-private, protected, or public) purely to make it directly testable. Test
  private behavior through the class's public interface instead. If that genuinely seems necessary, ask the user first
  rather than doing it.

**Test data**

- Avoid non-deterministic data and shared mutable state between tests. Tests must be able to run independently, in any
  order.

**Scope — what to test**

- Only write tests for a repository class if it explicitly uses `NamedParameterJdbcTemplate`. Other repository styles (
  e.g. plain Spring Data JPA repositories with no custom query methods) are out of scope for this skill.
- Classes that don't need unit tests from this skill: pure-delegate REST controllers with no business logic,
  DTOs/entities/domain model classes, Spring Data JPA repository methods with no custom queries, non-PostgreSQL
  repositories, and legacy code touched only trivially (removals, one-line additions, renames).

**Source code boundary**

- Never modify existing production/source code just to make a test pass. If a test reveals what looks like an actual bug
  or a change that's genuinely needed in the source, stop and tell the user instead of changing it yourself.

## Reference examples

`references/src/` and `references/test/` hold three source/test pairs that demonstrate the rules above in
practice — read the relevant one(s) before writing new tests, and match their structure rather than inventing a new one:

- **`InvoiceServiceImpl` / `InvoiceServiceImplTest`** — a `@Service` with several collaborators, validation logic
  that throws on bad input, and DTO-building methods. Shows: `@Mock`/`@InjectMocks` with multiple collaborators, and
  exception-path tests (`assertThrows`).
- **`ProductCatalogServiceImpl` / `ProductCatalogServiceImplTest`** — a service merging results from two repositories by
  product code, with precedence and empty-source cases. Shows: multiple scenario coverage (merge, precedence,
  empty source) for the same method, each as its own clearly named test.
- **`OrderRepository` / `OrderRepositoryTest`** — a `@Repository` using `NamedParameterJdbcTemplate` with a
  `@Qualifier`. Shows: mocking `NamedParameterJdbcTemplate` itself, and the `any()` carve-out in action —
  `any(MapSqlParameterSource.class)` and `any(RowMapper.class)` are used because the real parameter source and row
  mapper can't be matched by equality, then `thenAnswer` intercepts the captured `RowMapper` to simulate mapping mocked
  `ResultSet` rows.
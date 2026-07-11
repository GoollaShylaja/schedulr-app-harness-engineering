# Testing — Schedulr (Java Edition)

## Backend (JUnit 5 + Spring Boot Test)

**Framework:** JUnit 5, `spring-boot-starter-test` (includes AssertJ, Mockito, MockMvc)
**Run:** `cd app/backend && ./mvnw test`

### Test Classes (mirror `src/main/java` package layout under `src/test/java`)

| Package | What it tests |
|---|---|
| `com.schedulr.auth` | Login, token validation, 401 cases |
| `com.schedulr.meetings` | CRUD, team isolation, status transitions |
| `com.schedulr.contacts` | Contact CRUD, team scoping |
| `com.schedulr.teams` | Team management |
| `com.schedulr.availability` | Availability rules |
| `com.schedulr.export` | Export renderer unit tests (no DB, no Spring context needed) |
| `com.schedulr.common.timezone` | `TimezoneConverter` rendering |

### Test Types

- **Repository/integration tests**: `@DataJpaTest` or `@SpringBootTest` with Testcontainers
  Postgres (preferred over H2 — Flyway migrations and `timestamptz` columns should run
  against the real engine, not an in-memory substitute).
- **Controller tests**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `MockMvc` or
  `TestRestTemplate`, with a JWT obtained via a real login call or a test-only token
  factory.
- **Pure unit tests**: plain JUnit + Mockito for services/renderers with no Spring context
  — fastest to run, prefer these wherever the class under test has no framework
  dependency.

### Patterns

```java
// Controller test pattern
@Test
void createMeetingReturns201() throws Exception {
    mockMvc.perform(post("/api/meetings")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("..."));
}

// Pure unit test (no HTTP, no DB)
@Test
void renderReturnsNonEmptyBytes() {
    byte[] result = new PdfExportRenderer().render(List.of(), "UTC");
    assertThat(result).isNotEmpty();
}
```

### What Tests Do NOT Cover

- End-to-end browser flows are covered by the frontend's Playwright suite
  (`app/frontend/e2e/`), not backend tests.
- No coverage minimum is enforced — coverage is aspirational, not gated.

## Frontend (Vitest + Playwright)

**Unit tests:** Vitest (`npm run test` in `app/frontend/`); config in `vitest.config.ts`.
**E2E:** Playwright (`app/frontend/e2e/`); config in `playwright.config.ts`.
**Component tests:** in `app/frontend/src/__tests__/`.

## Testing REST API Best Practices

When writing controller tests, assert the contract described in `.claude/context/architecture.md`, not just the happy path:

- **Status codes** — one test per meaningful outcome: `201` create, `200` read/update, `204` delete, `400` validation failure, `401` missing/invalid token, `403` wrong role/team, `404` unknown ID. A resource's test class isn't complete until these are covered, not just the 200 case.
- **Method correctness** — verify `PUT` requires the full payload (missing required fields → `400`) and `PATCH` accepts a partial one; don't let a `PUT` handler silently behave like `PATCH`.
- **DTO boundary** — assert response JSON never contains entity-only fields (password hash, internal audit columns). A `jsonPath("$.password").doesNotExist()`-style assertion belongs in every user/auth-adjacent controller test.
- **Validation** — for every `@Valid` DTO, test at least one invalid payload per constraint (missing `@NotNull` field, malformed `@Email`, out-of-range `@Min`/`@Size`) and assert `400` with a useful error body, not a stack trace.
- **Pagination** — for any `Page<T>` endpoint, test `page`/`size` params produce the expected slice and that omitting them falls back to the documented defaults.
- **Auth enforcement** — every protected endpoint gets a test with no `Authorization` header (expect `401`) and, where roles apply, one with a token for the wrong role (expect `403`).
- **Error envelope shape** — since `GlobalExceptionHandler` produces the consistent `success`/`message`/`data`/`timestamp` body, assert that shape in at least one error-path test per resource rather than only checking the status code.

## Adding Tests for a New Feature

1. Backend: add a test class under `src/test/java/com/schedulr/<resource>/`, mirroring the
   export tests for pure-unit cases or the meetings tests for HTTP+DB cases.
2. For timezone-sensitive code: always test with at least one non-UTC timezone (see
   `TimezoneConverterTest`).
3. For exports: include a formula-injection test (see `.claude/context/export-pattern.md`).
4. Prefer Testcontainers Postgres over H2 for repository tests — Flyway migrations and
   `timestamptz` semantics must be exercised against the real engine.

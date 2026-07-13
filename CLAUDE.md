# Schedulr (Java Edition) — Harness Engineering Project

Schedulr is a B2B meeting-scheduling SaaS for sales teams. Stack: React 19 + TypeScript
(frontend, `app/frontend/`) and Java 21 + Spring Boot 4 + Spring Data JPA + Flyway
(backend, `app/backend/`). Postgres on host port 5435 (container 5432) via
`app/docker-compose.yml`.

This document is a **spec for Claude Code to build the app**. There is no existing codebase yet — Claude Code
should treat every convention below as a rule to apply while generating the project,
not as a description of code that already exists.

---

## Naming Conventions

| Layer | Convention | Example |
|---|---|---|
| Java files/classes | PascalCase | `MeetingController.java`, `ExportService.java` |
| Java packages | lowercase, dot-separated, feature-based | `com.schedulr.meetings`, `com.schedulr.contacts` |
| Java methods/fields | camelCase | `listMeetings`, `viewerTz` |
| JPA entity fields | camelCase in Java, snake_case in DB via `@Column(name = "...")` | `startTime` → `start_time` |
| DTOs | `*CreateRequest`, `*UpdateRequest`, `*Response` naming, implemented as Java records | `MeetingCreateRequest`, `MeetingResponse` |
| TS files/components | kebab-case files, PascalCase components | `meeting-list.tsx`, `MeetingList` |
| API routes | `/api/<resource>`, plural noun | `/api/meetings`, `/api/contacts` |

---

## Core Code Patterns

- **DTOs**: one file per resource under `app/backend/src/main/java/com/schedulr/<resource>/dto/`.
  Use Java `record` types for `*CreateRequest`, `*UpdateRequest`, `*Response`. No mutable
  DTO classes.
- **JPA entities**: under `.../<resource>/entity/`. Use Jakarta Persistence annotations
  (`jakarta.persistence.*`, Spring Boot 4 defaults to this namespace). Prefer Lombok
  `@Getter`/`@Setter`/`@RequiredArgsConstructor` over hand-written boilerplate, but never
  Lombok on entity `equals`/`hashCode` without care for JPA proxy pitfalls.
- **Dependency injection**: constructor injection only. Never `@Autowired` on fields.
  Use `@RequiredArgsConstructor` with `private final` fields on every `@Service`,
  `@RestController`, and `@Component`.
- **Structured errors**: a single `@RestControllerAdvice` (`GlobalExceptionHandler`)
  translates service-layer exceptions into HTTP responses. Services throw typed
  exceptions (e.g. `MeetingNotFoundException`, `InvalidScheduleException`) for
  business-rule violations; controllers never construct `ResponseEntity` error bodies
  by hand.
- **Transactions**: `@Transactional` at the service method level, not the controller.
  Repositories are Spring Data JPA interfaces (`MeetingRepository extends
  JpaRepository<Meeting, UUID>`), no manual session handling.
- **Datetime storage**: always UTC, `OffsetDateTime` columns via
  `@Column(columnDefinition = "timestamptz")`. Render to the viewer's timezone only at
  the DTO-mapping boundary, via a dedicated `TimezoneConverter` component. Never call
  `.toString()` on a raw UTC timestamp and send it straight to the frontend as
  display text.
- **CSV/export escaping**: any user-supplied string written to a CSV cell MUST be
  prefixed with `'` if it starts with `=`, `+`, `-`, or `@`, to prevent formula
  injection. Centralise this in `ExportService`; do not inline escaping logic in
  controllers.
- **IDs**: use UUID (v7 if the `com.fasterxml.uuid:java-uuid-generator` dependency is
  available, v4 otherwise) as the externally exposed primary key. Never expose
  sequential integer IDs in a URL or API response.

---

## Build & Validation Commands

| Step | Command | Working Directory |
|---|---|---|
| Backend lint/format | `mvn spotless:check` (or `checkstyle:check`) | `app/backend` |
| Backend build + compile check | `mvn compile` | `app/backend` |
| Backend tests | `mvn test` | `app/backend` |
| Frontend lint | `npm run lint` | `app/frontend` |
| Frontend type check | `npx tsc --noEmit` | `app/frontend` |
| Frontend unit tests | `npm run test` | `app/frontend` |
| Frontend build | `npm run build` | `app/frontend` |

Run the full gate before any PR. This is what a Stop hook (see below) should enforce
automatically rather than relying on manual invocation.

---

## On-Demand Context

Load these modules only when the task touches the relevant area (mirrors the original
progressive-disclosure structure):

| Module | Load when... |
|---|---|
| `.claude/context/architecture.md` | Adding a new resource, service, or REST controller — also covers REST API best practices (HTTP methods, URL naming, status codes, versioning, DTOs, validation, pagination, error handling) |
| `.claude/context/auth.md` | Any authentication or authorization work — also covers REST API best practices for auth endpoints (login/register/refresh/logout) |
| `.claude/context/export-pattern.md` | Any export feature (CSV, PDF, XLSX, etc.) — also covers REST API best practices for export endpoints |
| `.claude/context/testing.md` | Writing or modifying tests — also covers testing the REST API contract (status codes, DTO leakage, validation, pagination, auth enforcement) |
| `.claude/context/timezones.md` | Any datetime display, serialization, or storage — also covers REST API best practices for timezone-related endpoints |
| `.claude/context/codebase-search.md` | Using the MCP tools to navigate by symbol |

These files don't exist yet either — Claude Code should create them as it builds out
each area, following the same structure as the modules referenced above.

Navigate by symbol using the `codebase-search` MCP server (`.mcp.json`) instead of
`Grep`. The three tools — `find_references`, `where_is`, `outline` — parse the Java AST
(via JavaParser) and return only real definitions and call sites, with no false hits
from comments, javadoc, or string literals. Use them whenever you need to:

- Confirm a method/class is defined where you expect it before reading it (`where_is`).
- Verify a dependency is actually applied in new code, e.g. `find_references("getCurrentUser")`
  to confirm a new controller method uses JWT auth, or see all callers before refactoring.
- Check the public API of a service before adding a method (`outline`).

See `.claude/context/codebase-search.md` for full tool descriptions. The server must be
built once (`cd tooling/mcp-codebase-search && mvn package`) before it's usable — it isn't
committed as a prebuilt jar.

---

## Hard Rules

- Run the full validation gate before opening a PR.
- Never commit secrets, `.env` files, or JWT signing keys to version control. A
  `PreToolUse` hook should hard-block reading/editing any real `.env` (use
  `.env.example` instead) and block recursive directory deletes.
- Flyway migrations must be reversible where the database supports it; if using
  versioned-only migrations, never edit a migration that has already shipped — add a
  new one.
- Escape user-supplied fields before writing them to any CSV cell (formula-injection
  risk — see export pattern context above).
- All new endpoints use Spring Security with JWT bearer auth. Do not introduce a
  second, legacy auth mechanism the way the original brownfield app did — build this
  one clean from the start.

---

## Miscellaneous / Gotchas (to establish, not inherited)

- Postgres runs on host port **5435** (not 5432). Connection string:
  `jdbc:postgresql://localhost:5435/schedulr`. Configure this as the default in
  `application.yml` under the `local` profile, falling back to `DATABASE_URL` env var
  in other profiles.
- `docker-compose.yml` should map `5435:5432` for the Postgres service.
- `mvnw`/`mvnw.cmd` wrappers should be committed so `./mvnw <cmd>` works without a
  local Maven install; frontend `node_modules` should NOT be committed.
- Since this is a from-scratch build rather than a brownfield app, there should be no
  deliberately-inconsistent columns or dual auth systems — treat this as an
  opportunity to avoid the smells the original demo calls out, not replicate them.

---

## What Claude Code should do with this file

1. Scaffold `app/backend` as a Spring Boot 4 (Maven) project and `app/frontend` as a
   Vite + React + TypeScript project, matching the directory layout implied above.
2. Set up `app/docker-compose.yml` for Postgres on port 5435.
3. Build the resource set to match Schedulr: meetings, contacts, CSV export, JWT auth,
   timezone-aware scheduling — one resource at a time, each following the patterns
   above.
4. Create the `.claude/context/*.md` modules as each area is built, so future sessions
   load only what's relevant.
5. Wire up hooks (`PreToolUse` for the `.env`/delete guard, `Stop` for the validation
   gate) once the build commands above actually exist and pass.

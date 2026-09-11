# Schedulr — Harness Engineering Project

Schedulr is a B2B meeting-scheduling SaaS for sales teams. Stack: React 19 + TypeScript
(frontend, `app/frontend/`) and Java 21 + Spring Boot 4 + Spring Data JPA + Flyway
(backend, `app/backend/`). Postgres on host port 5435 (container 5432) via
`app/docker-compose.yml`.

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

- **DTOs**: one file per resource under `.../<resource>/dto/`, as `*CreateRequest`,
  `*UpdateRequest`, `*Response` records. No mutable DTO classes.
- **JPA entities**: under `.../<resource>/entity/`, Jakarta Persistence annotations.
  Prefer Lombok `@Getter`/`@Setter`/`@RequiredArgsConstructor`, but never on entity
  `equals`/`hashCode` without care for JPA proxy pitfalls.
- **DI**: constructor injection only, `@RequiredArgsConstructor` + `private final`
  fields on every `@Service`/`@RestController`/`@Component`. Never `@Autowired` fields.
- **Errors**: a single `@RestControllerAdvice` (`GlobalExceptionHandler`) translates
  typed service exceptions into HTTP responses; controllers never build error bodies.
- **Transactions**: `@Transactional` at the service method level, not the controller.
- **Datetime**: always UTC, `OffsetDateTime` via `@Column(columnDefinition =
  "timestamptz")`. Convert to viewer timezone only at the DTO boundary via
  `TimezoneConverter` — never `.toString()` a raw UTC timestamp to the frontend.
- **CSV escaping**: prefix values starting with `=`, `+`, `-`, `@` with `'` (formula
  injection). Centralise in `ExportService`, never inline in controllers.
- **IDs**: UUID (v7 preferred) as the externally exposed primary key, never sequential
  integers in a URL or response.

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

Run the full gate before any PR. A `Stop` hook enforces this automatically. Backend
integration tests use Testcontainers — Docker must be running locally for `mvn test`
to pass.

---

## On-Demand Context

Load these modules only when the task touches the relevant area:

| Module | Load when... |
|---|---|
| `.claude/context/architecture.md` | Adding a new resource, service, or REST controller |
| `.claude/context/auth.md` | Any authentication or authorization work |
| `.claude/context/export-pattern.md` | Any export feature (CSV, PDF, XLSX, etc.) |
| `.claude/context/testing.md` | Writing or modifying tests |
| `.claude/context/timezones.md` | Any datetime display, serialization, or storage |
| `.claude/context/codebase-search.md` | Using the MCP tools to navigate by symbol |

Navigate Java code by symbol using the `codebase-search` MCP server (`.mcp.json`)
instead of `Grep` — it parses the AST via JavaParser (`find_references`, `where_is`,
`outline`) and returns only real definitions and call sites. Build it once with
`cd tooling/mcp-codebase-search && mvn package` if the jar is missing.

---

## Hard Rules

- Run the full validation gate before opening a PR.
- Never commit secrets, `.env` files, or JWT signing keys. A `PreToolUse` hook
  hard-blocks reading/editing any real `.env` (use `.env.example` instead) and blocks
  recursive directory deletes.
- Flyway migrations must be reversible where the database supports it; never edit a
  migration that has already shipped — add a new one.
- Escape user-supplied fields before writing them to any CSV cell (formula-injection
  risk).
- All endpoints use Spring Security with JWT bearer auth. No second, legacy auth
  mechanism.
- Postgres runs on host port **5435** (container 5432):
  `jdbc:postgresql://localhost:5435/schedulr`, the `local` profile default in
  `application.yml`, falling back to `DATABASE_URL` elsewhere.
- `mvnw`/`mvnw.cmd` are committed so `./mvnw <cmd>` works without a local Maven
  install; frontend `node_modules/` is not committed.

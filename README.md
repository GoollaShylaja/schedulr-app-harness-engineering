# Schedulr

Schedulr is a B2B meeting-scheduling app for sales teams. A rep publishes their
availability, teammates or external contacts book time against it, and every
timestamp is stored in UTC and rendered in the viewer's own timezone. Meetings can be
exported to CSV or PDF for reporting.

## Use case

Sales teams need a shared way to see who's free, book meetings with contacts, and
export a record of scheduled meetings — without juggling spreadsheets or timezone
math by hand. Schedulr covers: team membership, per-user availability windows,
contact management, meeting booking/RSVP, and export.

## Stack

| Layer | Tech |
|---|---|
| Frontend | React 19, TypeScript, Vite, React Router |
| Backend | Java 21, Spring Boot 4, Spring Data JPA, Spring Security (JWT) |
| Database | PostgreSQL 16, versioned with Flyway |
| Testing | Vitest + Testing Library (frontend); JUnit + Testcontainers (backend) |

## Project layout

```
app/
  backend/    Spring Boot API (Maven, ./mvnw)
  frontend/   React SPA (npm)
  docker-compose.yml   Postgres on host port 5435
tooling/
  mcp-codebase-search/  Java-AST symbol search MCP server for this repo
.claude/
  context/    Per-area convention docs (architecture, auth, export, testing, timezones)
  hooks/      Secret/`.env` guard, lint, and test-gate hooks
```

## Features

- **Auth** — JWT-based login, current-user profile, password change (`/api/v1/auth`)
- **Teams** — team membership and role management (`/api/v1/teams`)
- **Contacts** — CRUD on external contacts to book meetings with (`/api/v1/contacts`)
- **Availability** — per-user availability windows, viewable by any team member
  (`/api/v1/availability`)
- **Meetings** — create/update/cancel meetings, invitee RSVP, list with filters
  (`/api/v1/meetings`)
- **Export** — meetings list as CSV or PDF, with formula-injection-safe CSV escaping
  (`/api/v1/meetings/export`)

All timestamps are stored as UTC (`timestamptz`) and converted to the requesting
user's timezone only at the API response boundary.

## Running locally

**Prerequisites:** Java 21, Node 20+, Docker (for Postgres and for backend
integration tests via Testcontainers).

```bash
# 1. Start Postgres
cd app
docker compose up -d

# 2. Backend (http://localhost:8080)
cd backend
./mvnw spring-boot:run

# 3. Frontend (http://localhost:3000)
cd ../frontend
npm install
npm run dev
```

Backend config lives in `app/backend/src/main/resources/application.yml`. The
`local` profile (default) falls back to `localhost:5435` / `schedulr`/`schedulr` and
a dev-only JWT secret if `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD`/
`JWT_SECRET` aren't set. Any other profile requires those as real environment
variables — never commit a `.env` with real secrets.

## Validation gate

Run before opening a PR (a `Stop` hook enforces this automatically):

```bash
cd app/backend  && mvn spotless:check && mvn compile && mvn test
cd app/frontend && npm run lint && npx tsc --noEmit && npm run test && npm run build
```

## Harness engineering

Beyond the app itself, this repo uses Claude Code's automation surfaces to make the
agent's own workflow safer and more reliable:

- **`CLAUDE.md`** — one top-level conventions doc (naming, DTO/entity patterns,
  transactions, CSV escaping, UUID IDs, hard rules) loaded every session, so
  conventions don't need rediscovering each time.
- **Progressive-disclosure context** — `.claude/context/*.md` (architecture, auth,
  export-pattern, testing, timezones, codebase-search) is loaded only when a task
  touches that area, keeping default context small.
- **Guardrails - Hooks** (`.claude/hooks/`, single-file JDK 21 sources, no build
  step) — hooks that constrain what the agent can do or let it stop, enforced
  automatically rather than relying on the agent remembering the rules:
  - `SecurityGuard.java` (`PreToolUse`) — blocks reading/editing real `.env` files
    and recursive deletes across every tool vector (Read/Edit/Write/Bash/Glob/Grep);
    fails open on internal error.
  - `PostToolUseLint.java` (`PostToolUse`) — runs `spotless:check` on touched Java
    files or `tsc --noEmit` on touched TS files after an edit; advisory, never blocks.
  - `StopValidate.java` (`Stop`) — runs the full backend gate (`spotless:check
    compile test`) before the agent can stop, and blocks the stop with the failure
    output if it fails.
- **MCP server** (`tooling/mcp-codebase-search`) — a JavaParser-based AST search
  server (`find_references`, `where_is`, `outline`) so the agent navigates Java by
  symbol instead of `grep`, avoiding false hits from comments or string literals.
- **Plan → implement → review trail** (`plans/`, `reports/`) — each build phase has a
  written plan, an implementation report, and a review doc, giving an audit trail of
  what was built and why, separate from git history.

## Conventions

See [`CLAUDE.md`](./CLAUDE.md) for naming conventions, code patterns (DTOs as
records, JPA entities with Lombok, UTC-only datetimes, UUID primary keys, CSV
escaping), and hard rules enforced by repo hooks.

# Architecture — Schedulr (Java Edition)

## Overview

```
app/
├── backend/                     Spring Boot 4 + Spring Data JPA + Flyway, Java 21, Maven
│   ├── src/main/java/com/schedulr/
│   │   ├── meetings/             dto/ entity/ controller/ service/ repository/ exception/
│   │   ├── contacts/             (same shape)
│   │   ├── teams/                (same shape)
│   │   ├── availability/         (same shape)
│   │   ├── auth/                 (same shape) — JWT issuance, Spring Security config
│   │   ├── export/               (same shape) — ExportService, CSV/PDF renderers
│   │   └── common/
│   │       ├── config/           CORS, Spring Security, OpenAPI config
│   │       ├── error/             GlobalExceptionHandler (@RestControllerAdvice)
│   │       ├── security/          JWT filter, password hashing
│   │       └── timezone/          TimezoneConverter
│   ├── src/main/resources/
│   │   ├── application.yml       local profile: jdbc:postgresql://localhost:5435/schedulr
│   │   └── db/migration/         Flyway migrations: V1__..., V2__...
│   └── src/test/java/com/schedulr/   mirrors main/ package layout, one test class per resource
├── frontend/                     Vite + React 19 + TypeScript
│   ├── src/pages/                 dashboard, meetings, contacts, team, availability, schedule, settings
│   ├── src/components/           layout/ ui/
│   └── src/lib/                   API client, utils
└── docker-compose.yml             Postgres 16 (host 5435 → container 5432)
```

## Adding a New Resource

Pattern: mirror `meetings` end-to-end.

1. **Entity** (`.../<resource>/entity/<Resource>.java`) — `jakarta.persistence.*` annotations, `OffsetDateTime` for any datetime column, UUID primary key.
2. **DTOs** (`.../<resource>/dto/`) — `<Resource>CreateRequest`, `<Resource>UpdateRequest`, `<Resource>Response` records.
3. **Repository** (`.../<resource>/repository/<Resource>Repository.java`) — `extends JpaRepository<<Resource>, UUID>`.
4. **Service** (`.../<resource>/service/<Resource>Service.java`) — `@Service`, `@RequiredArgsConstructor`, `@Transactional` on mutating methods.
5. **Controller** (`.../<resource>/controller/<Resource>Controller.java`) — `@RestController`, `@RequestMapping("/api/<resource>")`, constructor-injected service.
6. **Exceptions** (`.../<resource>/exception/`) — typed exceptions (e.g. `MeetingNotFoundException`) registered in `GlobalExceptionHandler`.
7. **Migration** — new Flyway file `src/main/resources/db/migration/V<N>__add_<resource>.sql`. Never edit a migration that has already shipped.

## REST API Best Practices

Apply these to every controller in every resource package, not just new ones.

1. **HTTP methods match intent** — `GET` read, `POST` create, `PUT` full replace, `PATCH` partial update, `DELETE` remove. Never tunnel an action through `GET`/`POST` alone.
2. **Noun-based, plural URLs** — `/api/meetings`, `/api/meetings/{id}`, `/api/meetings/{id}/attendees`. No verbs (`/api/getMeetings`, `/api/createMeeting`).
3. **Meaningful status codes** — `200` read/update ok, `201` created (with `Location` header), `204` deleted, `400` bad input, `401` unauthenticated, `403` unauthorized, `404` not found, `500` unexpected. Controllers return `ResponseEntity<T>`; never `200` on failure.
4. **Version the API** — prefix routes `/api/v1/...`. Introduce `/api/v2/...` (separate controller) for breaking changes rather than mutating v1 behavior under existing clients.
5. **DTOs only, never entities** — controllers and services never return JPA entities directly. Map to the `*Response` records described above so internal/sensitive fields (password hashes, audit columns) can't leak.
6. **Centralized error handling** — all exception → HTTP mapping lives in `GlobalExceptionHandler` (`@RestControllerAdvice`). Controllers throw/let typed exceptions propagate; they never build error `ResponseEntity`s inline.
7. **Validate at the boundary** — `@Valid @RequestBody` on every `*CreateRequest`/`*UpdateRequest`, with Bean Validation annotations (`@NotNull`, `@Size`, `@Email`, `@Min`, etc.) on the DTO fields themselves.
8. **Paginate list endpoints** — any endpoint that can return an unbounded collection accepts `page`/`size` query params and returns `Page<*Response>` via `Pageable`, not a raw `List`.
9. **Secure by default** — every endpoint sits behind Spring Security + JWT bearer auth (see `.claude/context/auth.md`); role/ownership checks via `@PreAuthorize` where relevant. No endpoint is unauthenticated unless explicitly designed to be (e.g. login/register).
10. **Consistent response envelope** — success and error bodies follow the same shape (`success`, `message`, `data`, `timestamp`) so frontend API-client code can handle every endpoint uniformly.

## Key Locations

| Area | Path | Purpose |
|---|---|---|
| App entrypoint | `app/backend/src/main/java/com/schedulr/SchedulrApplication.java` | Spring Boot `@SpringBootApplication` main class |
| CORS / security config | `.../common/config/` | `SecurityFilterChain`, CORS beans |
| Error handling | `.../common/error/GlobalExceptionHandler.java` | Maps typed exceptions → HTTP responses |
| Timezone rendering | `.../common/timezone/TimezoneConverter.java` | DTO-mapping-boundary timezone conversion |
| Migrations | `app/backend/src/main/resources/db/migration/` | Flyway versioned SQL |
| Config | `app/backend/src/main/resources/application.yml` | `local` profile port 5435, falls back to `DATABASE_URL` |

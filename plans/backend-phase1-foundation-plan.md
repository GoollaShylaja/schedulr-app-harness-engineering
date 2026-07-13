# Plan: Schedulr Backend — Phase 1 Foundation (Auth + Meetings + Export)

## Ticket
Port the Schedulr backend from the reference FastAPI app
(`/Users/nirnaypolaboina/Documents/harness-engineering-demo-main/app/backend`) to
Spring Boot 4 + Java 21 + Spring Data JPA + Flyway, following the patterns in `CLAUDE.md`
and `.claude/context/*.md`. **Phase 1** delivers the runnable foundation: project scaffold,
Postgres/Flyway schema, JWT auth + user profile, the meetings resource (with invitees, RSVP,
filtering, pagination), PDF + CSV export, timezone rendering, and their tests. Contacts,
teams, and availability CRUD are **Phase 2** (separate `/plan`).

## Decisions (locked before planning)
- **API contract:** full REST best-practices prose from `architecture.md` — routes under
  `/api/v1/...`, every response wrapped in a `success`/`message`/`data`/`timestamp`
  envelope, list endpoints paginated with Spring `Page<>` (`page`/`size`). This **diverges
  from the reference** (`/api/meetings`, bare arrays, `limit`/`offset`) — intentional.
- **Primary keys:** UUID (v7 via `com.fasterxml.uuid:java-uuid-generator` if present, else
  v4) — never the reference's sequential ints. (`CLAUDE.md` hard rule.)
- **Auth:** single JWT system. The reference's dual auth (JWT + `X-Session-Token` legacy on
  contacts) is **dropped** — clean build, one `SecurityFilterChain`. Login takes a JSON body
  `{email, password}` (auth.md), not the reference's OAuth2 form-urlencoded.
- **No inherited smells:** snake_case columns throughout (reference's `users.createdAt`
  camelCase column becomes `created_at`); no hardcoded secret in non-local profiles; Postgres
  host port **5435** (reference used 5433).
- **Deferred to Phase 2:** `contacts`/`teams`/`availability` controllers+services, the
  `contact_meeting_links` M2M table, `availability_slots` table, `/api/v1/auth/register`,
  `/refresh`, `/logout` (reference has none; members are invited by a team admin).

---

## Affected Files

### Read before implementing
- `CLAUDE.md` — naming, DI, DTO/entity/transaction/error rules, hard rules, port 5435.
- `.claude/context/architecture.md` (L31-56) — package layout + the 10 REST best-practice rules.
- `.claude/context/auth.md` (L9-72) — JWT filter shape, `@AuthenticationPrincipal`, envelope, BCrypt.
- `.claude/context/timezones.md` (whole) — `TimezoneConverter` contract, `viewerTz` threading, IANA validation.
- `.claude/context/export-pattern.md` (L10-93) — `ExportRenderer` interface, `csvSafe`, CSV checklist, formula-injection test.
- `.claude/context/testing.md` (whole) — Testcontainers Postgres, controller-test contract assertions.
- Reference (for behavior/field parity only — do **not** copy structure):
  - `app/backend/app/models/{user,team,contact,meeting}.py`
  - `app/backend/app/api/{routes_auth,routes_meetings}.py`
  - `app/backend/app/services/{meeting_service,export_service,auth_jwt,security}.py`
  - `app/backend/app/utils/timezones.py`
  - `app/backend/app/seed.py`
  - `app/frontend/lib/api.ts` (L61-203) — field names/filters the meetings + auth endpoints carry.

### Create — build tooling & scaffold
- `app/backend/pom.xml` — Spring Boot 4 parent, Java 21, deps below, Spotless plugin.
- `app/backend/mvnw`, `app/backend/mvnw.cmd`, `app/backend/.mvn/wrapper/maven-wrapper.properties` — committed wrapper.
- `app/backend/.env.example` — `DATABASE_URL`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`, `CORS_ORIGIN` (never a real `.env`).
- `app/backend/src/main/resources/application.yml` — profiles; `local` → `jdbc:postgresql://localhost:5435/schedulr`, Flyway on, JPA `ddl-auto: validate`.
- `app/docker-compose.yml` — Postgres 16, `5435:5432`, db/user/pass `schedulr`.
- `app/backend/src/main/java/com/schedulr/SchedulrApplication.java` — `@SpringBootApplication`.

### Create — common infrastructure (`com.schedulr.common`)
- `common/api/ApiResponse.java` — record `{ boolean success, String message, T data, OffsetDateTime timestamp }` + static `ok(data)` / `ok(message,data)` / `error(message)` factories.
- `common/api/PageResponse.java` — record `{ List<T> content, int page, int size, long totalElements, int totalPages }` + `from(Page<T>)` (stable DTO instead of serializing raw Spring `Page`).
- `common/error/GlobalExceptionHandler.java` — `@RestControllerAdvice`; maps typed exceptions → `ResponseEntity<ApiResponse<Void>>` with the right status; handles `MethodArgumentNotValidException` (→400 with field errors in `message`), `AccessDeniedException`/auth (→401/403), fallback (→500).
- `common/error/exception/` — `NotFoundException` (404), `InvalidRequestException` (400), `NotAuthorizedException` (403), `ConflictException` (409) base types (resources subclass or throw these).
- `common/security/JwtService.java` — `@Service`; `issue(AuthenticatedUser)` → HS256 token (sub = user UUID string, `exp` from `jwt.expiration-minutes`); `parse(token)` → claims; secret from `jwt.secret`.
- `common/security/JwtAuthenticationFilter.java` — `OncePerRequestFilter`; reads `Authorization: Bearer`, resolves via `JwtService` + `UserRepository`, sets `SecurityContext` with an `AuthenticatedUser` principal.
- `common/security/AuthenticatedUser.java` — record `{ UUID id, UUID teamId, String role, String timezone, String email }` (the principal; carries `viewerTz` + `teamId` for scoping).
- `common/config/SecurityConfig.java` — `SecurityFilterChain` (stateless, JWT filter, `/api/v1/auth/login` + `/health` + OpenAPI public, everything else authenticated, `@EnableMethodSecurity` for `@PreAuthorize`), `PasswordEncoder` `@Bean` (`BCryptPasswordEncoder`), CORS `@Bean` from `cors.origin`.
- `common/timezone/TimezoneConverter.java` — `@Component`; `inZone(OffsetDateTime, tz)` and `render(OffsetDateTime, tz)` → `yyyy-MM-dd HH:mm zzz`; `render` guards unknown zones via IANA check → `InvalidTimezoneException`.
- `common/timezone/InvalidTimezoneException.java` — extends `InvalidRequestException` (→400).

### Create — auth resource (`com.schedulr.auth`)
- `auth/entity/User.java`, `auth/entity/Team.java` — JPA entities (Team here since users FK it; teams CRUD is Phase 2). UUID PK, `OffsetDateTime created_at`, snake_case `@Column`s, Lombok `@Getter/@Setter`, no-arg + `@RequiredArgsConstructor` care.
- `auth/repository/UserRepository.java` (`JpaRepository<User, UUID>` + `findByEmail`), `auth/repository/TeamRepository.java`.
- `auth/dto/LoginRequest.java` (`@Email`, `@NotBlank`), `auth/dto/AuthResponse.java` (`token`, `expiresAt`, nested `UserResponse`), `auth/dto/UserResponse.java`, `auth/dto/ProfileUpdateRequest.java` (`fullName`, `timezone`), `auth/dto/PasswordChangeRequest.java` (`@Size(min=8)` newPassword).
- `auth/service/AuthService.java` — `@Service @Transactional`; `login`, `currentUser`, `updateProfile` (validate IANA tz), `changePassword` (verify current via `PasswordEncoder`).
- `auth/controller/AuthController.java` — `/api/v1/auth`: `POST /login`, `GET /me`, `PATCH /me/profile`, `POST /me/change-password` (204). All return the envelope.
- `auth/exception/InvalidCredentialsException.java` (→401).

### Create — meetings resource (`com.schedulr.meetings`)
- `meetings/entity/Meeting.java`, `meetings/entity/MeetingInvitee.java` — UUID PK; `startTime`/`endTime` as `OffsetDateTime` `@Column(columnDefinition = "timestamptz")`; `@OneToMany` invitees `cascade = ALL, orphanRemoval = true`.
- `meetings/repository/MeetingRepository.java` — `JpaRepository<Meeting, UUID>` + `JpaSpecificationExecutor<Meeting>` (for the optional filters) **or** a `@Query` with nullable params + `Pageable`; `findByIdAndTeamId`. `meetings/repository/MeetingInviteeRepository.java`.
- `meetings/dto/MeetingCreateRequest.java` (`@NotBlank title`, `@NotNull start/end`, `meetingTimezone`, `notes`, `List<UUID> inviteeContactIds`), `MeetingUpdateRequest.java` (all nullable, partial PATCH), `RsvpUpdateRequest.java` (`@NotNull response`), `MeetingResponse.java` (id, title, host, hostId, start, end, timezone, status, notes, inviteeCount, `List<InviteeResponse>`), `InviteeResponse.java`.
- `meetings/service/MeetingService.java` — `@Service @Transactional`; `list(teamId, filters, viewerTz, Pageable)` → `Page<MeetingResponse>`; `create` (validate end>start → `InvalidScheduleException`; validate `meetingTimezone` IANA; resolve invitee contacts within team); `get`, `update` (host-or-admin), `cancel` (→ status `cancelled`), `updateRsvp`. Maps entities → `MeetingResponse` **via `TimezoneConverter.render(startTime, viewerTz)`** — never `.toString()`.
- `meetings/controller/MeetingController.java` — `/api/v1/meetings`: `GET` (paginated + filters), `POST` (201 + `Location`), `GET /export`, `GET /{id}`, `PATCH /{id}`, `DELETE /{id}` (204), `PATCH /{id}/invitees/{inviteeId}/rsvp`. **Register `/export` before `/{id}`** so it isn't shadowed. `viewerTz` = `current.timezone()` resolved once at the boundary.
- `meetings/exception/MeetingNotFoundException.java` (→404), `meetings/exception/InvalidScheduleException.java` (→400).

### Create — contacts entity only (Phase-2 controller deferred) (`com.schedulr.contacts`)
- `contacts/entity/Contact.java` — full entity (all columns, for the Phase-2 CRUD) UUID PK.
- `contacts/repository/ContactRepository.java` — `findByIdInAndTeamId(Collection<UUID>, UUID)` used by `MeetingService` to resolve/scope invitees. No controller/service this phase.

### Create — export (`com.schedulr.export`)
- `export/service/ExportRenderer.java` — interface `{ String contentType(); String fileExtension(); byte[] render(List<MeetingResponse> meetings, String viewerTz); }`.
- `export/service/ExportService.java` — `@Service`; holds `Map<String, ExportRenderer>` (Spring injects all beans keyed by format); `resolve(format)` → renderer or `UnsupportedExportFormatException`; **static `csvSafe(String)`** (the one place formula-injection escaping lives).
- `export/service/PdfExportRenderer.java` — `@Component("pdf")`; renders through the already-mapped `MeetingResponse` (times already viewer-tz).
- `export/service/CsvExportRenderer.java` — `@Component("csv")`; header `ID,Title,Start,End,Timezone,Status,Invitees`; `ExportService.csvSafe` on `title`/`notes`/contact fields; UTF-8 BOM prefix.
- `export/exception/UnsupportedExportFormatException.java` (→400).

### Create — seed (optional, `seed`/`local` profile)
- `common/seed/DataSeeder.java` — `@Component @Profile("seed")` `CommandLineRunner`; mirrors `seed.py` (team "Acme Sales"; 3 users across America/Chicago, Europe/Berlin, Asia/Singapore; 3 contacts; 12 meetings with invitees). Makes the timezone rendering demonstrable.

### Create — migrations
- `app/backend/src/main/resources/db/migration/V1__initial_schema.sql` — tables `teams`, `users`, `contacts`, `meetings`, `meeting_invitees` (UUID PKs, `timestamptz`, FKs, indexes on `team_id`/`host_id`/`start_time`/email). Phase 2 adds `V2__...` for `availability_slots` + `contact_meeting_links`.

### Create — tests (`src/test/java/com/schedulr/...`)
- `common/timezone/TimezoneConverterTest.java` — UTC vs Berlin vs Chicago render to different strings; unknown zone → `InvalidTimezoneException`.
- `export/PdfExportRendererTest.java`, `export/CsvExportRendererTest.java` (incl. formula-injection), `export/ExportServiceTest.java` (`csvSafe` + `resolve` unknown → 400 exception).
- `common/security/JwtServiceTest.java` — round-trip issue/parse; tampered/expired → rejected.
- `auth/AuthControllerTest.java` — Testcontainers; login 200, bad creds 401, `/me` without token 401, response has no `hashedPassword`.
- `meetings/MeetingControllerTest.java` — Testcontainers; create 201, get 200, update 200, delete 204, validation 400, no-token 401, wrong-team/role 403, unknown id 404, pagination slice, envelope shape on an error path, DTO boundary (no entity-only fields).

---

## pom.xml dependencies (Task 1 detail)
`spring-boot-starter-web`, `-data-jpa`, `-validation`, `-security`, `org.flywaydb:flyway-core`,
`flyway-database-postgresql`, `org.postgresql:postgresql`, `org.projectlombok:lombok`,
`io.jsonwebtoken:jjwt-api/impl/jackson` (or `com.auth0:java-jwt`),
`com.fasterxml.uuid:java-uuid-generator` (UUIDv7), test: `spring-boot-starter-test`,
`org.testcontainers:postgresql` + `junit-jupiter`. Plugins: `spring-boot-maven-plugin`,
`com.diffplug.spotless:spotless-maven-plugin` (google-java-format), compiler release 21.

---

## Ordered Tasks

### Task 1 — Scaffold the Maven project + config + docker-compose
- What: `pom.xml`, `mvnw` wrapper, `application.yml` (local profile, port 5435, Flyway on, `ddl-auto: validate`), `.env.example`, `app/docker-compose.yml` (5435:5432), `SchedulrApplication.java`.
- Pattern: `architecture.md` Key Locations table; `CLAUDE.md` "Miscellaneous / Gotchas" (5435, wrapper committed).
- Gotcha: `ddl-auto: validate` (not `update`) so Flyway owns the schema; without a running DB, `compile` still works but `test` needs Docker (Testcontainers).
- Validate: `cd app/backend && ./mvnw -q compile`

### Task 2 — Flyway V1 migration (full Phase-1 schema)
- What: `V1__initial_schema.sql` with teams/users/contacts/meetings/meeting_invitees.
- Pattern: reference models for columns; `CLAUDE.md` datetime rule → `timestamptz`; UUID PKs.
- Gotcha: never edit V1 after it ships — Phase 2 tables go in V2. Include FKs + the indexes the reference marks (`team_id`, `host_id`, `start_time`, `email`).
- Validate: `cd app/backend && ./mvnw -q compile` (SQL validated at test/run time against Testcontainers in later tasks).

### Task 3 — Common infrastructure: envelope, errors, timezone
- What: `ApiResponse`, `PageResponse`, `GlobalExceptionHandler`, base exceptions, `TimezoneConverter` + `InvalidTimezoneException`.
- Pattern: `architecture.md` rules 6 & 10; `timezones.md` `TimezoneConverter` contract.
- Gotcha: `GlobalExceptionHandler` must also wrap validation errors (`MethodArgumentNotValidException`) and Spring Security's 401/403 into the **same envelope** — otherwise error bodies diverge from success bodies (rule 10). `TimezoneConverter.render` must validate the zone before `ZoneId.of` (raw `DateTimeException` → 500 otherwise).
- Validate: `cd app/backend && ./mvnw -q compile` then `./mvnw -q test -Dtest=TimezoneConverterTest`

### Task 4 — Security: JWT service, filter, SecurityConfig, principal
- What: `JwtService`, `JwtAuthenticationFilter`, `AuthenticatedUser`, `SecurityConfig` (+ `PasswordEncoder`, CORS, `@EnableMethodSecurity`).
- Pattern: `auth.md` L13-36 (`OncePerRequestFilter`, `@AuthenticationPrincipal`), rule 9 secure-by-default.
- Gotcha: filter must **not** 500 on a malformed/expired token — catch and let the chain produce 401 via the entry point (routed through `GlobalExceptionHandler`/`AuthenticationEntryPoint`). Only `/api/v1/auth/login`, `/health`, OpenAPI are `permitAll`.
- Validate: `cd app/backend && ./mvnw -q test -Dtest=JwtServiceTest`

### Task 5 — Auth resource (User/Team entities, repos, DTOs, service, controller)
- What: `User`+`Team` entities, repositories, auth DTOs, `AuthService`, `AuthController` (`/api/v1/auth/login|me|me/profile|me/change-password`).
- Pattern: `auth.md` (BCrypt, JSON login body, envelope); `architecture.md` rules 1-3,5,7.
- Gotcha: `UserResponse` must never carry `hashedPassword`. Profile update must IANA-validate `timezone`. Login returns 401 (not 404) on unknown email — don't leak existence.
- Validate: `cd app/backend && ./mvnw -q test -Dtest=AuthControllerTest`

### Task 6 — Contacts entity + repository (no controller)
- What: `Contact` entity, `ContactRepository.findByIdInAndTeamId`.
- Pattern: reference `contact.py`; `architecture.md` entity rules.
- Gotcha: full column set now (so Phase-2 CRUD needs no migration change), but no service/controller yet — keep scope tight.
- Validate: `cd app/backend && ./mvnw -q compile`

### Task 7 — Meetings resource (entities, repo, DTOs, service, controller)
- What: `Meeting`+`MeetingInvitee`, repository (filters + `Pageable`), meeting DTOs, `MeetingService`, `MeetingController` (`/api/v1/meetings` full set incl. RSVP).
- Pattern: reference `routes_meetings.py` + `meeting_service.serialize_meeting` for field parity; `timezones.md` (render via `TimezoneConverter`, thread `viewerTz` from principal); `architecture.md` rules 1-3,5,7,8.
- Gotcha: **route order** — declare `GET /export` before `GET /{id}` or it's shadowed (this is the exact bug the reference's route order avoids). `create` must reject `end <= start` (`InvalidScheduleException`) and scope invitee contact IDs to the caller's team. `update`/`delete` enforce host-or-admin (`@PreAuthorize` or service check → 403). List endpoint paginated (`Page<MeetingResponse>` → `PageResponse` in the envelope).
- Validate: `cd app/backend && ./mvnw -q test -Dtest=MeetingControllerTest`

### Task 8 — Export (interface, service, PDF + CSV renderers)
- What: `ExportRenderer`, `ExportService` (+ `csvSafe`, `resolve`), `PdfExportRenderer`, `CsvExportRenderer`; wire `GET /api/v1/meetings/export?format=` in `MeetingController`.
- Pattern: `export-pattern.md` L10-66 (interface, checklist, `csvSafe`); rules for the export route L71-78.
- Gotcha: `csvSafe` centralized in `ExportService` — no inline escaping in the controller/renderer. Renderers consume `List<MeetingResponse>` (already viewer-tz, already DTO), never entities. Unknown `format` → `UnsupportedExportFormatException` (400), not NPE/500. CSV gets UTF-8 BOM.
- Validate: `cd app/backend && ./mvnw -q test -Dtest=PdfExportRendererTest,CsvExportRendererTest,ExportServiceTest`

### Task 9 — Seed runner (optional, `seed` profile)
- What: `DataSeeder` `CommandLineRunner` mirroring `seed.py`.
- Pattern: `seed.py` (team, 3 TZ-spread users, 3 contacts, 12 meetings).
- Gotcha: gate on `@Profile("seed")` so tests/prod don't auto-seed; idempotent (skip if a team exists).
- Validate: `cd app/backend && ./mvnw -q compile`

### Task 10 — Full validation gate + format
- What: run Spotless apply, then the whole gate.
- Validate: the gate below.

---

## Validation Gate
Backend-only this phase (frontend is empty until its own plan):
```
cd app/backend && ./mvnw spotless:apply
cd app/backend && ./mvnw spotless:check
cd app/backend && ./mvnw compile
cd app/backend && ./mvnw test
```
`./mvnw test` requires Docker running (Testcontainers Postgres). If Docker is unavailable,
note it and run `./mvnw -Dtest='!*ControllerTest' test` for the pure-unit subset, but the
gate is not satisfied until the Testcontainers tests pass.

## Acceptance Criteria
- [ ] `app/backend` is a buildable Spring Boot 4 / Java 21 Maven project; `./mvnw compile` passes.
- [ ] Flyway `V1` creates teams/users/contacts/meetings/meeting_invitees with UUID PKs and `timestamptz` datetimes.
- [ ] `POST /api/v1/auth/login` (JSON `{email,password}`) returns a JWT in the envelope; bad creds → 401; unknown email → 401 (not 404).
- [ ] Every response (success and error) is wrapped in the `success`/`message`/`data`/`timestamp` envelope.
- [ ] `GET /api/v1/meetings` is JWT-protected, team-scoped, paginated (`page`/`size` → `PageResponse`), and supports the reference filters (host, status, search, date range, contact).
- [ ] Meeting create rejects `end <= start` (400) and an invalid IANA `meetingTimezone` (400); update/delete enforce host-or-admin (403).
- [ ] Meeting times render through `TimezoneConverter` in the viewer's timezone — no raw `.toString()`; a UTC vs Berlin render test proves they differ.
- [ ] `GET /api/v1/meetings/export?format=csv|pdf` works, is declared before `/{id}`, and CSV escapes formula-injection (`=`,`+`,`-`,`@`) via `ExportService.csvSafe`; unknown format → 400.
- [ ] No `UserResponse`/DTO leaks `hashedPassword`; no JPA entity is returned from any controller.
- [ ] Only one auth mechanism exists (no legacy session token anywhere).
- [ ] All validation-gate commands pass.

---

## Confirmation
- **Path:** `plans/backend-phase1-foundation-plan.md`
- **Complexity:** High (greenfield Spring Boot scaffold + security + JPA + Flyway + Testcontainers, ~45 new files).
- **Key risks:**
  1. Testcontainers needs Docker at test time — gate can't fully pass without it.
  2. Envelope + pagination (`ApiResponse<PageResponse<T>>`) adds wrapping boilerplate on every handler; a `ResponseBodyAdvice` could reduce it but explicit wrapping is clearer and less magic — plan uses explicit.
  3. Spring Boot 4 / Spring Security 6/7 API drift (e.g. `SecurityFilterChain` lambda DSL, `@EnableMethodSecurity`) — verify against the resolved version, not older tutorials.
  4. UUIDv7 dependency (`java-uuid-generator`) availability — fall back to v4 generation if absent (hard rule allows it).
  5. Route shadowing (`/export` vs `/{id}`) — explicitly ordered in Task 7.
- **Confidence:** 7/10 that `/implement` completes Phase 1 first-pass, assuming Docker is available for the Testcontainers suite. Without Docker, unit-level tasks (3,4,8) still pass but the controller acceptance criteria can't be verified.

**Handoff:** `/implement plans/backend-phase1-foundation-plan.md`

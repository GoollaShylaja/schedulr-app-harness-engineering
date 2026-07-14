# Plan: Schedulr Backend — Phase 2 (Contacts, Teams, Availability)

## Ticket
Add the three deferred CRUD resources from the reference FastAPI app, following the
Phase 1 patterns (JWT auth, `/api/v1`, `ApiResponse` envelope, `Page<>` for unbounded
lists, UUID PKs, typed exceptions → `GlobalExceptionHandler`, constructor injection,
`@Transactional` on services). All JWT-only — the reference's legacy session auth on
contacts is dropped.

## Decisions
- **Contacts list** is paginated (`Page<ContactResponse>`, default sort by name).
- **Availability** and **team members** return plain envelope-wrapped lists (bounded per
  user/team), not `Page<>`.
- **Teams mutations** are admin-only via `@PreAuthorize("hasRole('ADMIN')")` (filter sets
  `ROLE_<role.upper()>`).
- **Skip `contact_meeting_links`** — orphaned M2M in the reference (no route uses it);
  adding it would replicate a dead-table smell. `MeetingInvitee` already links the two.
- **No `/auth/register|refresh|logout`** — invite-member is the registration path.

## Affected Files
### Modify
- `auth/repository/UserRepository.java` — add `List<User> findAllByTeamIdOrderByFullName(UUID)`, `Optional<User> findByIdAndTeamId(UUID,UUID)`.
- `contacts/repository/ContactRepository.java` — add `Page<Contact> findByTeamId(UUID, Pageable)`, `Optional<Contact> findByIdAndTeamId(UUID,UUID)`.
### Create — migration
- `resources/db/migration/V2__add_availability_slots.sql` — `availability_slots` table (UUID PK, user_id FK, weekday int, start/end varchar(5), index on user_id).
### Create — contacts (`com.schedulr.contacts`)
- `dto/{ContactCreateRequest,ContactUpdateRequest,ContactResponse}.java`, `service/ContactService.java`, `controller/ContactController.java`, `exception/ContactNotFoundException.java`.
### Create — teams (`com.schedulr.teams`)
- `dto/{TeamResponse,TeamMemberResponse,InviteMemberRequest,UpdateMemberRoleRequest}.java`, `service/TeamService.java`, `controller/TeamController.java`, `exception/MemberNotFoundException.java`.
### Create — availability (`com.schedulr.availability`)
- `entity/AvailabilitySlot.java`, `repository/AvailabilitySlotRepository.java`, `dto/{AvailabilitySlotCreateRequest,AvailabilitySlotResponse,AvailabilityBulkSetRequest}.java`, `service/AvailabilityService.java`, `controller/AvailabilityController.java`, `exception/SlotNotFoundException.java`.
### Create — tests
- `contacts/ContactControllerTest.java`, `teams/TeamControllerTest.java`, `availability/AvailabilityControllerTest.java` (all extend `support/AbstractIntegrationTest`).

## Ordered Tasks

### Task 1 — V2 migration + AvailabilitySlot entity/repo
- What: `V2__add_availability_slots.sql`; `AvailabilitySlot` entity (mirror `Contact.java` style); repo with `findByUserIdOrderByWeekday`, `findByIdAndUserId`, `deleteByUserId`.
- Gotcha: never edit V1; contacts table already exists there.
- Validate: `cd app/backend && ./mvnw compile`

### Task 2 — Contacts CRUD
- What: DTOs (records; `@NotBlank name`, `@Email email` on create), `ContactService` (`@Transactional`, team-scoped, `ContactNotFoundException` on miss), `ContactController` `/api/v1/contacts` (GET paginated, POST 201+Location, GET/PATCH/DELETE `/{id}`).
- Pattern: mirror `MeetingController.java` + `MeetingService.java` (envelope, `PageResponse.from`, `@AuthenticationPrincipal`).
- Validate: `cd app/backend && ./mvnw -q test -Dtest=ContactControllerTest`

### Task 3 — Teams
- What: `TeamService` (currentTeam → members; invite creates `User` w/ encoded temp password, dup email → `ConflictException` 409; role update validates {admin,member}→400; remove-self→400 `InvalidRequestException`), `TeamController` `/api/v1/teams/me` (GET), `/me/members` (POST 201), `/me/members/{userId}/role` (PATCH), `/me/members/{userId}` (DELETE 204).
- Pattern: `AuthService.java` (PasswordEncoder, IdGenerator); `@PreAuthorize("hasRole('ADMIN')")` on the three mutations.
- Gotcha: `UserResponse`/member DTOs never expose `hashedPassword`.
- Validate: `cd app/backend && ./mvnw -q test -Dtest=TeamControllerTest`

### Task 4 — Availability
- What: DTOs (`@Min(0)@Max(6) weekday`, `@Pattern("\\d{2}:\\d{2}") start/end`), `AvailabilityService` (list own, list `/user/{userId}` scoped to team→404, add, bulk PUT replaces all own slots, delete own→`SlotNotFoundException`), `AvailabilityController` `/api/v1/availability` (GET, `/user/{userId}` GET, POST 201, PUT, `/{slotId}` DELETE 204).
- Gotcha: bulk PUT deletes existing then inserts — do inside one `@Transactional`.
- Validate: `cd app/backend && ./mvnw -q test -Dtest=AvailabilityControllerTest`

## Validation Gate
```
cd app/backend && ./mvnw spotless:apply
cd app/backend && ./mvnw spotless:check
cd app/backend && ./mvnw compile
cd app/backend && ./mvnw test
```
Backend-only (frontend still unscaffolded, out of scope). Docker required for Testcontainers.

## Acceptance Criteria
- [ ] V2 migration adds `availability_slots`; V1 untouched.
- [ ] Contacts: full CRUD, JWT-protected, team-scoped, list paginated; unknown id → 404.
- [ ] Teams: GET /me returns team + members; invite/role/remove are admin-only (403 for members); dup email → 409; remove-self → 400; no password hash leaks.
- [ ] Availability: own + per-team-member GET, add, bulk-replace PUT, delete; cross-team user → 404; invalid weekday/time → 400.
- [ ] All three controllers enforce 401 without a token; no JPA entity returned from any controller.
- [ ] All validation gate commands pass.

# Auth — Schedulr

## One Auth System (Clean Build)

Schedulr has one authentication pattern. All **new routes** must use JWT.

### JWT (the only pattern)

**Package:** `com.schedulr.auth`
**Security config:** `com.schedulr.common.security`

- Token issued at `POST /api/auth/login` (JSON body: `{ "email": ..., "password": ... }`).
- Bearer token via `Authorization: Bearer <token>` header.
- A `OncePerRequestFilter` (e.g. `JwtAuthenticationFilter`) resolves the token into a
  Spring Security `Authentication`, populating the `SecurityContext` with the
  authenticated user.
- Token payload: subject = user ID (UUID, as string), `exp` claim for expiry.
- Algorithm: `HS256`; secret from `application.yml` (`jwt.secret`, env `JWT_SECRET`,
  never a hardcoded default in production profiles — a `local`-only fallback is fine).
- Expiry: `jwt.expiration-minutes` (default 1440 min = 24h), configurable per profile.

```java
// Canonical usage in a controller — the authenticated principal is injected,
// never resolved manually inside the handler body.
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @GetMapping
    public List<MeetingResponse> listMeetings(@AuthenticationPrincipal AuthenticatedUser current) {
        return meetingService.listMeetings(current.teamId());
    }
}
```

## Authorization

- Team-scoped: every authenticated principal carries `teamId`; repository queries always
  filter by it (either via a `@Query` clause or a Spring Data derived method like
  `findAllByTeamId`).
- Role checks for mutations: prefer method-level `@PreAuthorize("hasRole('ADMIN')")` over
  inline `if` checks in the controller, so the rule is declarative and testable in
  isolation.
- No dual authorization path — every new endpoint goes through the same
  `SecurityFilterChain`.

## REST API Best Practices for Auth Endpoints

Auth routes follow the same rules as `.claude/context/architecture.md`, applied specifically here:

- **Methods & nouns** — `POST /api/v1/auth/login`, `POST /api/v1/auth/register`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`. All state-changing, so `POST`; no verbs beyond the resource name itself (`auth`), no `GET` with side effects.
- **Versioned** — auth routes live under `/api/v1/auth/...` like every other resource, so a future breaking change (e.g. new token format) ships as `/api/v2/auth/...` without touching v1 clients.
- **Status codes** — `200` on successful login/refresh, `201` on register, `204` on logout, `400` malformed request body, `401` bad credentials or expired/invalid token, `403` valid token but insufficient role, `404` never used for login failures (don't leak whether an email exists).
- **DTOs only** — `LoginRequest`, `RegisterRequest`, `AuthResponse` (token + expiry + minimal user info) as records under `.../auth/dto/`. Never return the `User`/`AuthenticatedUser` entity, and never include the password hash in any response.
- **Validation** — `@Valid @RequestBody` on `LoginRequest`/`RegisterRequest` with `@Email`, `@NotNull`, `@Size(min = ...)` on password, so malformed input is rejected before it reaches the service layer.
- **Centralized errors** — bad credentials, expired tokens, and duplicate-email registration all throw typed exceptions (e.g. `InvalidCredentialsException`, `EmailAlreadyRegisteredException`) handled in `GlobalExceptionHandler`, never inline `ResponseEntity` error construction in `AuthController`.
- **Consistent envelope** — auth responses use the same `success`/`message`/`data`/`timestamp` wrapper as every other endpoint, with `data` holding the token payload.

## Password Hashing

Use Spring Security's `PasswordEncoder` bean (`BCryptPasswordEncoder`). Never hand-roll
hashing or store plaintext passwords.

## Frontend Auth Flow

- Login → stores JWT in memory / `localStorage` (see `app/frontend/src/lib/`).
- API client attaches `Authorization: Bearer` to every request.
- On 401, the client clears the stored token and redirects to login.

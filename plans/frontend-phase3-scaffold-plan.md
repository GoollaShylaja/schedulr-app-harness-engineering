# Plan: Frontend Phase 3 — Scaffold + Core Screens

## Ticket
Phase 3: build out `app/frontend` (Vite + React 19 + TypeScript), currently an empty
directory skeleton (`src/{pages,components,lib,__tests__}` exist with no files), against
the Phase 1/2 backend API (`/api/v1/...`, JWT auth, envelope-wrapped responses).

## Backend API Surface (verified by reading controllers/DTOs directly)

- Base URL: `http://localhost:8080/api/v1`. CORS allows `http://localhost:3000` (default
  `cors.origin`) and `http://localhost:3001` — **dev server must run on port 3000**.
- Success envelope: `{ success, message, data, timestamp }`. List endpoints (except
  availability) wrap `data` in `{ content, page, size, totalElements, totalPages }`.
- Error envelope: same shape, `success=false`, `data=null`. Status codes: 400 validation/bad
  input, 401 unauthenticated, 403 forbidden, 404 not found, 409 conflict, 500 unexpected.
- Auth: `POST /auth/login` (public) → `{ token, expiresAt, user }`. All other routes need
  `Authorization: Bearer <token>`. On 401, client must clear token and redirect to `/login`.
- Resources: `auth` (`/auth/me`, `/auth/me/profile`, `/auth/me/change-password`), `meetings`
  (CRUD + `/export?format=csv|pdf` + `/{id}/invitees/{inviteeId}/rsvp`), `contacts` (CRUD),
  `teams` (`/teams/me`, admin-only member invite/role/remove), `availability` (own +
  `/user/{userId}`, add, bulk `PUT`, delete — plain `List`, not paginated).
- Full field-level DTO shapes captured in the Explore-agent findings above; DTOs below
  mirror those verbatim.

## Stack Decisions

- **Routing**: `react-router-dom` v6 (data-agnostic, standard for Vite+React SPAs).
- **Data fetching**: plain `fetch` wrapped in a small typed client + per-resource hook
  functions (`useMeetings`, `useContacts`, ...) using React state, no React Query — the
  CRUD surface is small enough that a query-caching library is unneeded abstraction per
  CLAUDE.md's "don't add abstractions beyond what the task requires."
- **Styling**: plain CSS files per component (kebab-case, colocated), no UI/component
  library pulled in — nothing in CLAUDE.md mandates one.
- **Testing**: Vitest + React Testing Library (`src/__tests__/`), matching the `npm run
  test` command already specified in CLAUDE.md's build table.
- **Out of scope**: `app/frontend/e2e/` (Playwright or similar) is left empty — CLAUDE.md's
  validation gate only requires unit tests (`npm run test`), not e2e.

## Affected Files

### Read before implementing
- `CLAUDE.md` — naming conventions (kebab-case files, PascalCase components).
- `.claude/context/auth.md` — frontend auth flow section (store JWT, attach bearer header, 401 → clear + redirect).
- `.claude/context/timezones.md` — meeting `start`/`end`/`timezone` fields already arrive as pre-rendered display strings from the backend; frontend must NOT re-parse/re-format them as dates.
- `app/backend/src/main/java/com/schedulr/*/dto/*.java` — DTO field names/types (already captured above; re-read specific file only if a mismatch is suspected during implementation).

### Create
- `app/frontend/package.json`, `tsconfig.json`, `tsconfig.node.json`, `vite.config.ts`, `index.html`, `.env.example`, `.eslintrc.cjs` (or flat config), `.gitignore`.
- `app/frontend/src/main.tsx`, `src/App.tsx`, `src/vite-env.d.ts`.
- `app/frontend/src/lib/api-client.ts`, `src/lib/types.ts`, `src/lib/api/{auth,meetings,contacts,teams,availability}.ts`, `src/lib/auth-context.tsx`.
- `app/frontend/src/components/layout/{app-layout.tsx,nav-bar.tsx,protected-route.tsx}`.
- `app/frontend/src/components/ui/{button.tsx,input.tsx,select.tsx,modal.tsx,pagination.tsx}` (minimal shared primitives, only what pages actually need).
- `app/frontend/src/pages/login/login-page.tsx`.
- `app/frontend/src/pages/dashboard/dashboard-page.tsx`.
- `app/frontend/src/pages/meetings/{meetings-list-page.tsx,meeting-form.tsx,meeting-detail-page.tsx}`.
- `app/frontend/src/pages/contacts/{contacts-list-page.tsx,contact-form.tsx}`.
- `app/frontend/src/pages/team/team-page.tsx`.
- `app/frontend/src/pages/availability/availability-page.tsx`.
- `app/frontend/src/pages/settings/settings-page.tsx`.
- `app/frontend/src/__tests__/{api-client.test.ts,auth-context.test.tsx,meetings-list-page.test.tsx,contacts-list-page.test.tsx}`.

## Ordered Tasks

### Task 1 — Vite + TS scaffold
- What: `package.json` (react, react-dom, react-router-dom, dev deps: vite, @vitejs/plugin-react, typescript, vitest, @testing-library/react, @testing-library/jest-dom, jsdom, eslint + typescript-eslint); `vite.config.ts` with `server.port: 3000` and a `test` block for Vitest (`environment: 'jsdom'`); `tsconfig.json` (strict mode); `index.html` mounting `#root`; `.env.example` with `VITE_API_BASE_URL=http://localhost:8080/api/v1`.
- Gotcha: dev server MUST bind port 3000 (or 3001) — CORS on the backend only allows those two origins.
- Validate: `cd app/frontend && npm install && npx tsc --noEmit`

### Task 2 — API client + types
- What: `lib/types.ts` — TS interfaces mirroring every backend DTO verbatim (field names/types from the API surface above: `MeetingResponse.start`/`end` are `string`, not `Date`). `lib/api-client.ts` — a `request<T>(path, opts)` wrapper that reads `VITE_API_BASE_URL`, attaches `Authorization: Bearer <token>` from `lib/auth-context`'s stored token, parses the `{success,message,data,timestamp}` envelope, throws a typed `ApiError` (status + message) on `success:false` or non-2xx, and on `401` clears the stored token (`localStorage.removeItem('schedulr_token')`) and redirects to `/login`. Per-resource files (`lib/api/meetings.ts` etc.) export typed functions (`listMeetings(params)`, `createMeeting(body)`, ...) built on `request`.
- Pattern: envelope shape confirmed via `ApiResponse.java`/`PageResponse.java` (verbatim in API Surface section above).
- Gotcha: the export endpoint (`GET /meetings/export`) returns a raw file, not the envelope — needs a separate `requestBlob` path that does NOT try to parse JSON.
- Validate: `cd app/frontend && npx tsc --noEmit`

### Task 3 — Auth context + login page + protected routing
- What: `lib/auth-context.tsx` — React context holding `{ user, token, login(email,pw), logout() }`, persists token to `localStorage`, calls `POST /auth/login`, hydrates `user` from the response. `components/layout/protected-route.tsx` — redirects to `/login` if no token. `pages/login/login-page.tsx` — email/password form, calls `login()`, redirects to `/dashboard` on success, surfaces `ApiError.message` on failure (e.g. bad credentials → 401).
- Pattern: `.claude/context/auth.md` "Frontend Auth Flow" section — store JWT, attach bearer header, 401 → clear + redirect.
- Validate: `cd app/frontend && npx tsc --noEmit`

### Task 4 — App shell, router, layout
- What: `App.tsx` wires `react-router-dom` routes: `/login` (public), `/dashboard`, `/meetings`, `/meetings/:id`, `/contacts`, `/team`, `/availability`, `/settings` (all wrapped in `ProtectedRoute` + `AppLayout`). `components/layout/app-layout.tsx` + `nav-bar.tsx` — sidebar/topbar nav linking the six pages, shows current user's name/role from `auth-context`, logout button.
- Validate: `cd app/frontend && npm run dev` (manual smoke: loads, redirects to `/login` when unauthenticated).

### Task 5 — Meetings pages
- What: `meetings-list-page.tsx` — paginated table (uses `PageResponse.page/size/totalPages`), filter controls (`hostId`, `startAfter/Before`, `contactId`, `status`, `search`), export buttons (`GET /meetings/export?format=csv|pdf` via `requestBlob`, triggers a browser download). `meeting-form.tsx` — shared create/edit form (`title`, `startTime`, `endTime` as native datetime-local inputs converted to `OffsetDateTime` ISO strings, `meetingTimezone`, `notes`, invitee contact multi-select sourced from `listContacts`). `meeting-detail-page.tsx` — shows invitees with RSVP status, RSVP action per invitee (`PATCH /meetings/{id}/invitees/{inviteeId}/rsvp`).
- Gotcha: `MeetingResponse.start`/`end` are already-formatted display strings (per `TimezoneConverter.render`) — render as-is, never `new Date(response.start)`.
- Validate: `cd app/frontend && npx tsc --noEmit`

### Task 6 — Contacts pages
- What: `contacts-list-page.tsx` — paginated table, search/filter. `contact-form.tsx` — create/edit (`name`, `email`, `company`, `phone`, `title`, `notes`, `stage` dropdown).
- Pattern: mirror Task 5's list/form split.
- Validate: `cd app/frontend && npx tsc --noEmit`

### Task 7 — Team page
- What: `team-page.tsx` — `GET /teams/me` renders team name + member table; invite-member form and role-change/remove actions rendered only when `auth-context.user.role === 'admin'` (backend still enforces via `@PreAuthorize`, this is UX-only gating). Handle 409 (duplicate email) and 400 (invalid role) errors inline on the invite form.
- Gotcha: never show admin controls as the sole authorization boundary — a 403 from the backend must still be handled gracefully (toast/error banner) since the UI check can't be trusted as the only gate.
- Validate: `cd app/frontend && npx tsc --noEmit`

### Task 8 — Availability page
- What: `availability-page.tsx` — 7-day x time-slot grid reflecting `GET /availability` (own slots); editing recomputes the full slot list client-side and submits via bulk `PUT /availability` (`AvailabilityBulkSetRequest`); per-slot delete via `DELETE /availability/{slotId}`. Weekday `0-6`, `start`/`end` as `HH:mm` strings validated client-side against the same `\d{2}:\d{2}` pattern the backend enforces.
- Validate: `cd app/frontend && npx tsc --noEmit`

### Task 9 — Dashboard + Settings pages
- What: `dashboard-page.tsx` — lightweight summary (upcoming meetings count/list via `listMeetings({ startAfter: now })`, contact count). `settings-page.tsx` — profile form (`PATCH /auth/me/profile`: `fullName`, `timezone` — validate against a fixed IANA zone list or a simple dropdown), change-password form (`POST /auth/me/change-password`, `currentPassword`/`newPassword` min 8 chars, 204 on success, 401 on wrong current password).
- Validate: `cd app/frontend && npx tsc --noEmit`

### Task 10 — Unit tests
- What: `api-client.test.ts` (envelope parsing, 401 → clears token, `ApiError` thrown on `success:false`), `auth-context.test.tsx` (login persists token, logout clears it), `meetings-list-page.test.tsx` + `contacts-list-page.test.tsx` (render with mocked `fetch`, assert list rendering + pagination controls), using `msw` or a simple `vi.fn()` fetch mock — prefer the latter to avoid adding a new dependency.
- Validate: `cd app/frontend && npm run test`

### Task 11 — Lint + build
- What: run lint and production build to catch anything the incremental `tsc --noEmit` checks missed (unused vars, build-time-only errors).
- Validate: `cd app/frontend && npm run lint && npm run build`

## Validation Gate
Run these in order after all tasks are done:
```
cd app/frontend && npx tsc --noEmit
cd app/frontend && npm run lint
cd app/frontend && npm run test
cd app/frontend && npm run build
```
Frontend-only; backend gate (`./mvnw spotless:check/compile/test`) not re-run since no
backend files change in this phase.

## Acceptance Criteria
- [ ] `npm run dev` serves on port 3000 and the app loads against the running backend (`docker-compose up` + `./mvnw spring-boot:run`) without CORS errors.
- [ ] Unauthenticated visits to any page except `/login` redirect to `/login`; login persists a session across a page refresh.
- [ ] Meetings: list (paginated, filterable), create, edit, RSVP, and both CSV and PDF export all work end-to-end against the live backend.
- [ ] Contacts and Availability: full CRUD/bulk-set against the live backend.
- [ ] Team: member list renders for all roles; invite/role-change/remove controls are hidden for non-admins and produce a graceful error if a 403 is ever returned.
- [ ] Settings: profile update and password change both work; wrong current password surfaces the 401 message.
- [ ] No raw UTC/ISO timestamp is ever re-parsed or reformatted client-side — meeting times render exactly as returned by the backend.
- [ ] All validation gate commands pass.

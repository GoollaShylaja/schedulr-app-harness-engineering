# Timezones — Schedulr (Java Edition)

## The Golden Rule

**All meeting datetimes are stored as UTC** (`OffsetDateTime` columns via
`@Column(columnDefinition = "timestamptz")`).
**All rendering happens through `TimezoneConverter`** — never call `.toString()` on a raw
`OffsetDateTime` and send it straight to the frontend as display text.

## `TimezoneConverter` (`com.schedulr.common.timezone.TimezoneConverter`)

```java
@Component
public class TimezoneConverter {
    public OffsetDateTime inZone(OffsetDateTime value, String tzName) { ... }
    public String render(OffsetDateTime value, String tzName) { ... } // "2026-07-20 16:00 CEST"
}
```

Display format mirrors `yyyy-MM-dd HH:mm zzz`.

### Correct usage

```java
// In a service or DTO mapper — never in the entity itself:
String displayed = timezoneConverter.render(meeting.getStartTime(), viewerTz);
```

### Wrong (the bug pattern)

```java
// DO NOT DO THIS — ignores viewer timezone, always shows UTC (or system default)
String displayed = meeting.getStartTime().toString();
```

## Where `viewerTz` Comes From

- The authenticated user's IANA timezone string (e.g. `"America/Chicago"`), stored on the
  `User` entity as `timezone`.
- Always passed into service-layer methods as a `String viewerTz` parameter — resolved
  once from the authenticated principal at the controller boundary, then threaded through
  to whatever DTO mapper needs it. Never re-resolved deep inside a mapper.

## `meetingTimezone`

`Meeting.meetingTimezone` stores the timezone in which the meeting was **created**. This is
distinct from the viewer's timezone. It is persisted on the entity and surfaced in
`MeetingResponse` as `timezone`. Do not confuse the two — a meeting created in Berlin time
and viewed by someone in Chicago should render in Chicago time, but the `timezone` field
in the response still reports Berlin.

## Validating Timezone Correctness

When adding any new datetime-rendering path, add a test that asserts a UTC time and a
Berlin/Chicago time produce **different** rendered strings (see
`.claude/context/testing.md`).

## REST API Best Practices for Timezone-Related Endpoints

Same rules as `.claude/context/architecture.md`, applied to any endpoint that accepts or renders a timezone (user settings, meeting create/update):

- **Method matches intent** — updating a user's `timezone` is a partial update, so `PATCH /api/v1/users/{id}` (or `/api/v1/users/me`), not a full `PUT` unless the whole user resource is being replaced.
- **Status codes** — `200` on successful timezone update, `400` for an invalid IANA zone string (via `InvalidTimezoneException`), `401`/`403` per normal auth rules. Never let a bad zone string reach `ZoneId.of(...)` unguarded and surface as an uncaught `500`.
- **DTOs only** — `viewerTz` and `meetingTimezone` are always read from/written to `*Request`/`*Response` records (`UserUpdateRequest.timezone`, `MeetingResponse.timezone`), never the entity directly, so rendering stays isolated at the DTO-mapping boundary described above.
- **Validate at the boundary** — add a `@Pattern`/custom validator or explicit `ZoneId.getAvailableZoneIds()` check on any incoming timezone field in the request DTO (e.g. `MeetingCreateRequest.meetingTimezone`, `UserUpdateRequest.timezone`), so invalid values are rejected by `@Valid` before reaching the service layer.
- **Centralized errors** — `InvalidTimezoneException` is mapped once in `GlobalExceptionHandler`, not caught/handled ad hoc in each controller that happens to accept a timezone param.
- **Consistent envelope** — timezone-bearing responses (`MeetingResponse`, `UserResponse`) still return through the standard `success`/`message`/`data`/`timestamp` wrapper; the rendered display string (via `TimezoneConverter.render(...)`) lives inside `data`, never bolted on as a separate top-level field.
- **No verbs** — resolve `viewerTz` from the authenticated principal or a query param on the existing resource endpoint (e.g. `GET /api/v1/meetings?viewerTz=...` if ever needed), not a bespoke endpoint like `/api/renderMeetingTime`.

## IANA Timezone Validation

When accepting a timezone name from user input (e.g. on user settings or meeting create),
validate it against `ZoneId.getAvailableZoneIds()` and throw a typed
`InvalidTimezoneException` (mapped to `400 Bad Request` by `GlobalExceptionHandler`) on an
unknown value. Never let an invalid zone string reach `ZoneId.of(...)` unguarded — it
throws an unchecked `DateTimeException` that would otherwise surface as a raw 500.

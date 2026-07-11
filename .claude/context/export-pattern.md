# Export Pattern — Schedulr

## Interface

**Package:** `com.schedulr.export`

`ExportRenderer` is a small interface — no protocol/duck-typing needed in Java, just
implement it:

```java
public interface ExportRenderer {
    String contentType();
    String fileExtension();
    byte[] render(List<MeetingResponse> meetings, String viewerTz);
}
```

To add a new format, create a `@Component` class that implements `ExportRenderer` — Spring
picks it up for DI without any registry boilerplate, as long as the export endpoint
resolves the right bean by format (e.g. a `Map<String, ExportRenderer>` keyed by format
name, injected via constructor).

## Existing Exporter: PdfExportRenderer

`PdfExportRenderer` renders meeting times through `TimezoneConverter.render(...)`. This is
the **correct** pattern — times always arrive in the viewer's timezone, never in raw UTC.

## Adding CSV Export

**Route:** `GET /api/meetings/export?format=pdf` (`MeetingController`). Add a `format=csv`
branch that resolves `CsvExportRenderer` instead.

### Formula Injection (Critical Security Rule)

CSV cells that start with `=`, `+`, `-`, or `@` are interpreted as formulas by Excel/Google
Sheets. Any user-supplied string (title, notes, contact name, email) must be sanitized
before writing. Centralize this in `ExportService` — never inline the escape in a
controller:

```java
private static final Set<Character> FORMULA_PREFIXES = Set.of('=', '+', '-', '@');

public static String csvSafe(String value) {
    if (value != null && !value.isEmpty() && FORMULA_PREFIXES.contains(value.charAt(0))) {
        return "'" + value; // prefix with single-quote: Excel treats as literal text
    }
    return value;
}
```

Apply `csvSafe()` to every cell derived from user input. Fields at risk:
- `meeting.title` (user-controlled)
- `meeting.notes` (user-controlled)
- contact name/email surfaced through meeting invitees

### Implementation Checklist for CsvExportRenderer

1. Add `CsvExportRenderer` (`@Component`) implementing `ExportRenderer` in `com.schedulr.export.service`.
2. Use Apache Commons CSV or plain `StringWriter` + manual quoting — no heavyweight dependency needed for a flat CSV.
3. Header row: `["ID", "Title", "Start", "End", "Timezone", "Status", "Invitees"]`.
4. Render times through `TimezoneConverter.render(startTime, viewerTz)` (same as the PDF renderer).
5. Apply `ExportService.csvSafe()` to `title`, `notes`, and all contact fields.
6. Return `byte[]` with UTF-8 BOM prefix (`﻿`) for Excel compatibility.
7. Wire into the export endpoint: resolve renderer by `format` query param, default to `pdf`.
8. Set response header `Content-Disposition: attachment; filename=meetings.csv`.

## REST API Best Practices for Export Endpoints

Same rules as `.claude/context/architecture.md`, applied to the export route:

- **Method & noun** — `GET /api/v1/meetings/export?format=csv|pdf`, not `/api/exportMeetings`. Export is a read (produces a representation of existing data), so `GET`, not `POST`.
- **Versioned** — lives under `/api/v1/meetings/...` alongside the rest of the meetings resource.
- **Status codes** — `200` with the file body on success, `400` for an unsupported `format` value, `401`/`403` per the normal auth rules, `404` only if the underlying resource (e.g. a specific meeting) doesn't exist — never `500` for a bad `format` param, that's a client error.
- **DTOs only** — renderers consume `List<MeetingResponse>` (already-mapped DTOs), never raw `Meeting` entities, so no internal/team-scoping fields leak into the exported file.
- **Validate input** — the `format` query param is validated against the known renderer keys before resolving a bean; reject unknown formats with `400`, don't let a missing renderer surface as a `NullPointerException`/`500`.
- **Centralized errors** — an unsupported format throws a typed exception (e.g. `UnsupportedExportFormatException`) handled by `GlobalExceptionHandler`, not an inline `ResponseEntity.badRequest()` in `MeetingController`.
- **Secure by default** — export endpoints sit behind the same JWT + team-scoping rules as every other meetings endpoint; exporting must never bypass `findAllByTeamId` filtering.
- **Not paginated** — exports intentionally return the full filtered set as a file rather than a `Page<T>`; pagination applies to JSON list endpoints, not file downloads.

### Test Pattern

Mirror the PDF renderer's test class for `CsvExportRenderer`: assert `render(List.of(), "UTC")`
returns non-null bytes, verify a non-default `viewerTz` produces a different time string, and
add a formula-injection test:

```java
@Test
void csvFormulaInjectionIsEscaped() {
    var meeting = meetingResponseWithTitle("=HYPERLINK(\"http://evil.com\",\"Click\")");
    String csv = new String(csvExportRenderer.render(List.of(meeting), "UTC"), StandardCharsets.UTF_8);
    assertThat(csv.lines().skip(1).findFirst().orElseThrow()).doesNotStartWith("=HYPERLINK");
}
```

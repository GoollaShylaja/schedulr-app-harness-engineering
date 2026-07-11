# Codebase-Search MCP Tools

The repo ships a Java MCP server at `tooling/mcp-codebase-search/`, wired in via `.mcp.json` as `codebase-search`. It parses the project's Java source with JavaParser into a real AST and exposes three structured-search tools. Use these instead of `Grep` when you need to navigate by symbol.

---

## Tools

### `where_is(name)`

Find every definition of `name` — a class, interface, enum, record, method, constructor, or field.

Returns the file path, line number, kind, and fully-qualified name. For methods and constructors it also returns the full signature.

**Use when:** you need to locate a method before reading it, or confirm a symbol is defined where you expect.

```
where_is("listMeetings")
→  app/backend/src/main/java/com/schedulr/meetings/controller/MeetingController.java:42  [method]  com.schedulr.meetings.controller.MeetingController.listMeetings
       public List<MeetingResponse> listMeetings(...)
```

### `find_references(name)`

Find every place `name` is used — method calls, constructor calls (`new Foo(...)`), field access, and identifier loads — across all Java source in the project.

Deduplicated to one result per line (call > attribute > name), so a `foo(...)` call site appears once as `[call]`, not as both `[call]` and `[name]`.

**Use when:** you want to verify a dependency is actually applied in new code (e.g., `find_references("getCurrentUser")` to confirm a new controller method uses JWT auth), or to see all callers before refactoring a method.

```
find_references("meetingService")
→  app/backend/src/main/java/com/schedulr/meetings/controller/MeetingController.java:33  [attribute]  .meetingService
```

### `outline(module)`

Show the structured public API of one Java file — its types, methods, constructors, and fields with full signatures, in source order.

`module` accepts a file path (`app/backend/src/main/java/com/schedulr/export/service/ExportService.java`) or a dotted class name (`com.schedulr.export.service.ExportService`, or just `ExportService`).

**Use when:** you need to understand what a service exposes before adding a method, or verify the shape of a class before reading its full source.

```
outline("ExportService")
→  outline of app/backend/src/main/java/com/schedulr/export/service/ExportService.java:
     22: [class] class ExportService
         28: [method] public byte[] exportMeetingsCsv(List<MeetingResponse> meetings)
         48: [method] private String csvSafe(String value)
```

---

## Scope

The server walks the project from `CLAUDE_PROJECT_DIR` (or cwd if unset), skipping:

`.git`, `target`, `build`, `dist`, `node_modules`, `.mvn`, `.idea`, `.claude`, `.vscode`, `out`

It covers **Java source only** — TypeScript/frontend files are not indexed. Definitions/references are matched structurally via the AST (records, modern `switch` expressions, and all Java 21 syntax are supported), never by substring — no false hits from comments, javadoc, or string literals, unlike `Grep`.

---

## MCP server wiring

`.mcp.json` at the repo root tells Claude Code to start the server with:

```
java -jar tooling/mcp-codebase-search/target/codebase-search.jar
```

The jar must be built once before first use (and after any change to the server itself):

```
cd tooling/mcp-codebase-search && mvn package
```

`tooling/mcp-codebase-search/pom.xml` declares the `mcp` (official Java MCP SDK) and `javaparser-core` dependencies and produces a self-contained shaded jar via `maven-shade-plugin`, so no classpath setup is needed at runtime — just `java -jar`. Requires Java 21 on `PATH` (the same version the backend targets).

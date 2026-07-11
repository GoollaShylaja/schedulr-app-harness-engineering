package com.schedulr.tooling.codebasesearch;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Codebase-search MCP server for Schedulr —Abstract Syntax Tree (AST)-based structured search.
 * <p>
 * Parses every module into a real JavaParser AST and answers questions about code
 * <em>structure</em> — definitions, references, and file shape — never substring-matching:
 * {@code Grep} already does that. {@code where_is} returns only real
 * class/interface/enum/record/method/constructor/field declarations;
 * {@code find_references} returns only real call/attribute/name uses — no false hits from
 * comments, javadoc, or string literals.
 * <p>
 * Runs over stdio; wired into the repo via {@code .mcp.json}.
 * <p>
 * Tools:
 * <ul>
 *   <li>{@code where_is(name)} — every definition of {@code name}, with kind + qualified name.</li>
 *   <li>{@code find_references(name)} — every use of {@code name} across the project's Java source.</li>
 *   <li>{@code outline(module)} — the structured API of one file — types, methods, fields, in source order.</li>
 * </ul>
 * Run directly: {@code java -jar codebase-search.jar}
 */
public final class CodebaseSearchServer {

    private CodebaseSearchServer() {
    }

    public static void main(String[] args) {
        Path root = Path.of(System.getenv().getOrDefault("CLAUDE_PROJECT_DIR", System.getProperty("user.dir")));
        JavaSourceIndex index = new JavaSourceIndex(root);

        McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);

        Map<String, Object> nameSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string",
                                "description", "The exact identifier to look up (function, method, class, field, or constant name).")),
                "required", List.of("name"));

        Map<String, Object> moduleSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "module", Map.of("type", "string",
                                "description", "A file path (app/backend/src/main/java/com/schedulr/export/service/ExportService.java) "
                                        + "or a dotted class name (com.schedulr.export.service.ExportService, or just ExportService).")),
                "required", List.of("module"));

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("schedulr-codebase-search", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .toolCall(
                        McpSchema.Tool.builder("where_is", nameSchema)
                                .description("Find every definition of `name` — a class, interface, enum, record, "
                                        + "method, constructor, or field — by parsing the AST of the project's Java "
                                        + "source. Unlike a text search, this matches only real declarations: no hits "
                                        + "from comments, javadoc, string literals, or identically-spelled locals.")
                                .build(),
                        (exchange, request) -> textResult(index.whereIs(requireString(request, "name"))))
                .toolCall(
                        McpSchema.Tool.builder("find_references", nameSchema)
                                .description("Find every place `name` is used — method calls, constructor calls, "
                                        + "field access, and identifier loads — across the project's Java source, "
                                        + "parsed from the AST. Returns only genuine references, not every textual mention.")
                                .build(),
                        (exchange, request) -> textResult(index.findReferences(requireString(request, "name"))))
                .toolCall(
                        McpSchema.Tool.builder("outline", moduleSchema)
                                .description("Show the structured API of one Java file — its types, methods, "
                                        + "constructors, and fields with full signatures, in source order.")
                                .build(),
                        (exchange, request) -> textResult(index.outline(requireString(request, "module"))))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }

    private static String requireString(McpSchema.CallToolRequest request, String key) {
        Object value = request.arguments() == null ? null : request.arguments().get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing required argument: " + key);
        }
        return String.valueOf(value);
    }

    private static McpSchema.CallToolResult textResult(String text) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(text)))
                .build();
    }
}

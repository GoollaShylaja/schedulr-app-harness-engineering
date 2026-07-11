// Stop hook — validation gate.
//
// Reads Claude Code's Stop hook JSON from stdin. If stop_hook_active is true
// (meaning this hook already triggered a block and Claude is being asked whether
// to continue), exits 0 immediately to prevent an infinite loop.
//
// Otherwise runs the backend validation gate (`./mvnw spotless:check compile test`).
// On failure, prints a JSON block decision that blocks the stop and tells Claude to
// fix the issues. On pass, exits 0 silently.
//
// Wired to: Stop
//
// Run with: java StopValidate.java   (JDK 21 single-file source launch, no compile step)

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class StopValidate {

    public static void main(String[] args) {
        try {
            Object parsed = Json.read(new String(System.in.readAllBytes(), "UTF-8"));
            run(parsed);
        } catch (Exception e) {
            System.err.println("[stop-hook] Could not parse hook JSON: " + e);
        }
        System.exit(0);
    }

    @SuppressWarnings("unchecked")
    private static void run(Object parsed) throws IOException, InterruptedException {
        if (parsed instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) parsed;
            if (Boolean.TRUE.equals(data.get("stop_hook_active"))) {
                return;
            }
        }

        String projectDirEnv = System.getenv("CLAUDE_PROJECT_DIR");
        Path projectDir = Paths.get(projectDirEnv != null ? projectDirEnv : ".").toAbsolutePath().normalize();
        Path backendDir = projectDir.resolve("app").resolve("backend");

        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
        String mvnw = isWindows ? "mvnw.cmd" : "./mvnw";
        Path mvnwPath = backendDir.resolve(mvnw);

        if (!Files.exists(mvnwPath)) {
            // Backend not scaffolded yet (no mvnw wrapper committed) — nothing to validate.
            return;
        }

        List<String> failures = new ArrayList<>();

        Result spotless = exec(backendDir, mvnwPath.toString(), "spotless:check", "-q");
        if (spotless.exitCode != 0) {
            failures.add("spotless:check failed:\n" + spotless.summary());
        }

        Result compile = exec(backendDir, mvnwPath.toString(), "compile", "-q");
        if (compile.exitCode != 0) {
            failures.add("mvn compile failed:\n" + compile.summary());
        }

        Result test = exec(backendDir, mvnwPath.toString(), "test", "-q");
        if (test.exitCode != 0) {
            failures.add("mvn test failed:\n" + test.summary());
        }

        if (!failures.isEmpty()) {
            StringBuilder reason = new StringBuilder("Validation failed: ");
            for (int i = 0; i < failures.size(); i++) {
                if (i > 0) reason.append(" | ");
                reason.append(failures.get(i).replace("\n", " "));
            }
            reason.append(". Fix the issues and re-run.");

            Map<String, Object> block = new LinkedHashMap<>();
            block.put("decision", "block");
            block.put("reason", reason.toString());
            System.out.println(Json.write(block));
        }
    }

    private static Result exec(Path cwd, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), "UTF-8");
        String stderr = new String(p.getErrorStream().readAllBytes(), "UTF-8");
        int exit = p.waitFor();
        return new Result(exit, stdout, stderr);
    }

    private record Result(int exitCode, String stdout, String stderr) {
        String summary() {
            String combined = (stdout + stderr).strip();
            if (combined.isEmpty()) return "(no output)";
            String[] lines = combined.split("\\R");
            int limit = Math.min(20, lines.length);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                if (lines[i].isBlank()) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(lines[i]);
            }
            return sb.length() > 0 ? sb.toString() : "(no output)";
        }
    }

    /** Minimal JSON reader/writer — object/array/string/number/boolean/null only. */
    static final class Json {
        static Object read(String s) {
            return new Parser(s).parseValue();
        }

        static String write(Object o) {
            StringBuilder sb = new StringBuilder();
            writeValue(o, sb);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void writeValue(Object o, StringBuilder sb) {
            if (o == null) {
                sb.append("null");
            } else if (o instanceof String s) {
                writeString(s, sb);
            } else if (o instanceof Map<?, ?> m) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    writeString(String.valueOf(e.getKey()), sb);
                    sb.append(':');
                    writeValue(e.getValue(), sb);
                }
                sb.append('}');
            } else if (o instanceof List<?> l) {
                sb.append('[');
                boolean first = true;
                for (Object v : l) {
                    if (!first) sb.append(',');
                    first = false;
                    writeValue(v, sb);
                }
                sb.append(']');
            } else {
                sb.append(o);
            }
        }

        private static void writeString(String s, StringBuilder sb) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                    }
                }
            }
            sb.append('"');
        }

        static final class Parser {
            private final String s;
            private int i;

            Parser(String s) {
                this.s = s;
            }

            Object parseValue() {
                skipWs();
                if (i >= s.length()) return null;
                char c = s.charAt(i);
                return switch (c) {
                    case '{' -> parseObject();
                    case '[' -> parseArray();
                    case '"' -> parseString();
                    case 't' -> { i += 4; yield Boolean.TRUE; }
                    case 'f' -> { i += 5; yield Boolean.FALSE; }
                    case 'n' -> { i += 4; yield null; }
                    default -> parseNumber();
                };
            }

            private Map<String, Object> parseObject() {
                Map<String, Object> map = new LinkedHashMap<>();
                i++;
                skipWs();
                if (peek() == '}') { i++; return map; }
                while (true) {
                    skipWs();
                    String key = parseString();
                    skipWs();
                    i++; // :
                    Object val = parseValue();
                    map.put(key, val);
                    skipWs();
                    if (peek() == ',') { i++; continue; }
                    if (peek() == '}') { i++; break; }
                    break;
                }
                return map;
            }

            private List<Object> parseArray() {
                List<Object> list = new ArrayList<>();
                i++;
                skipWs();
                if (peek() == ']') { i++; return list; }
                while (true) {
                    Object val = parseValue();
                    list.add(val);
                    skipWs();
                    if (peek() == ',') { i++; continue; }
                    if (peek() == ']') { i++; break; }
                    break;
                }
                return list;
            }

            private String parseString() {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < s.length() && s.charAt(i) != '"') {
                    char c = s.charAt(i);
                    if (c == '\\' && i + 1 < s.length()) {
                        char n = s.charAt(i + 1);
                        switch (n) {
                            case '"' -> { sb.append('"'); i += 2; }
                            case '\\' -> { sb.append('\\'); i += 2; }
                            case '/' -> { sb.append('/'); i += 2; }
                            case 'n' -> { sb.append('\n'); i += 2; }
                            case 'r' -> { sb.append('\r'); i += 2; }
                            case 't' -> { sb.append('\t'); i += 2; }
                            case 'b' -> { sb.append('\b'); i += 2; }
                            case 'f' -> { sb.append('\f'); i += 2; }
                            case 'u' -> {
                                String hex = s.substring(i + 2, i + 6);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 6;
                            }
                            default -> { sb.append(n); i += 2; }
                        }
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                i++;
                return sb.toString();
            }

            private Object parseNumber() {
                int start = i;
                while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
                String num = s.substring(start, i);
                if (num.contains(".") || num.contains("e") || num.contains("E")) {
                    return Double.parseDouble(num);
                }
                try {
                    return Long.parseLong(num);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(num);
                }
            }

            private char peek() {
                return i < s.length() ? s.charAt(i) : '\0';
            }

            private void skipWs() {
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            }
        }
    }
}

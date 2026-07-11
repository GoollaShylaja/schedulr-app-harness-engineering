// Post-tool-use static-check hook.
//
// Reads the Claude Code hook JSON from stdin. After a file edit/write:
//   - Java files under app/backend  -> `./mvnw spotless:check` (format/lint)
//   - TS/TSX files under app/frontend -> `npx tsc --noEmit` (typecheck)
// Prints the result and ALWAYS exits 0 (advisory — surfaces issues, never blocks).
//
// `mvnw`/`mvnw.cmd` is invoked directly from the backend directory so no local
// Maven install is required. `npx` is resolved via PATH so it works whether
// invoked as `npx` (POSIX) or `npx.cmd` (Windows).
//
// Wired to: PostToolUse / Edit|Write|MultiEdit
//
// Run with: java PostToolUseLint.java   (JDK 21 single-file source launch, no compile step)

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public class PostToolUseLint {

    public static void main(String[] args) {
        try {
            Object parsed = Json.read(new String(System.in.readAllBytes(), "UTF-8"));
            run(parsed);
        } catch (Exception e) {
            System.err.println("[lint-hook] Could not parse hook JSON: " + e);
        }
        System.exit(0); // advisory hook — never blocks
    }

    @SuppressWarnings("unchecked")
    private static void run(Object parsed) throws IOException, InterruptedException {
        if (!(parsed instanceof Map)) return;
        Map<String, Object> data = (Map<String, Object>) parsed;
        Object tiObj = data.get("tool_input");
        Map<String, Object> ti = (tiObj instanceof Map) ? (Map<String, Object>) tiObj : Map.of();

        Object fpRaw = ti.get("file_path");
        if (fpRaw == null) return;

        Path filePath = Paths.get(String.valueOf(fpRaw)).toAbsolutePath().normalize();
        String projectDirEnv = System.getenv("CLAUDE_PROJECT_DIR");
        Path projectDir = Paths.get(projectDirEnv != null ? projectDirEnv : ".").toAbsolutePath().normalize();
        Path appBackend = projectDir.resolve("app").resolve("backend");
        Path appFrontend = projectDir.resolve("app").resolve("frontend");

        Path relPath;
        try {
            relPath = projectDir.relativize(filePath);
        } catch (IllegalArgumentException e) {
            return;
        }
        String rel = relPath.toString().replace('\\', '/');

        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");

        if (rel.startsWith("app/backend/") && rel.endsWith(".java")) {
            String mvnw = isWindows ? "mvnw.cmd" : "./mvnw";
            Path mvnwPath = appBackend.resolve(mvnw);
            if (!Files.exists(mvnwPath)) {
                System.out.println("[lint-hook] " + mvnw + " not found in app/backend; skipping backend check");
                return;
            }
            System.out.println("[lint-hook] mvnw spotless:check on " + rel);
            ProcessResult result = exec(appBackend, mvnwPath.toString(), "spotless:check", "-q");
            emit(result, "spotless: OK", "spotless: formatting issues found (see above) — run `./mvnw spotless:apply`");
        } else if (rel.startsWith("app/frontend/") && (rel.endsWith(".ts") || rel.endsWith(".tsx"))) {
            String npx = isWindows ? "npx.cmd" : "npx";
            if (!onPath(npx)) {
                System.out.println("[lint-hook] npx not found on PATH; skipping frontend check");
                return;
            }
            System.out.println("[lint-hook] tsc --noEmit (typecheck) triggered by " + rel);
            ProcessResult result = exec(appFrontend, npx, "tsc", "--noEmit");
            emit(result, "tsc: OK", "tsc: type errors found (see above) — fix before committing");
        }
    }

    private static boolean onPath(String bin) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        String separator = System.getProperty("path.separator", ":");
        for (String dir : path.split(Pattern.quote(separator))) {
            if (Files.isExecutable(Paths.get(dir, bin)) || Files.exists(Paths.get(dir, bin))) return true;
        }
        return false;
    }

    private static void emit(ProcessResult result, String ok, String bad) {
        if (!result.stdout.isBlank()) System.out.println(result.stdout);
        if (!result.stderr.isBlank()) System.err.println(result.stderr);
        System.out.println(result.exitCode == 0 ? "[lint-hook] " + ok : "[lint-hook] " + bad);
    }

    private static ProcessResult exec(Path cwd, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), "UTF-8");
        String stderr = new String(p.getErrorStream().readAllBytes(), "UTF-8");
        int exit = p.waitFor();
        return new ProcessResult(exit, stdout, stderr);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }

    /** Minimal JSON reader — object/array/string/number/boolean/null only. */
    static final class Json {
        static Object read(String s) {
            return new Parser(s).parseValue();
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

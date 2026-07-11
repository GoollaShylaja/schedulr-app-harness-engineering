// PreToolUse security guard.
//
// Denies two classes of dangerous operations BEFORE any tool runs:
//
//   1. Reading, editing, or writing a real `.env` file (it holds secrets),
//      across every vector a coding agent might try:
//        - the Read / Edit / Write / MultiEdit / NotebookEdit tools (file_path),
//        - Bash commands (cat, grep, sed, awk, less, xxd, base64, strings,
//          `python -c "open('.env')"`, `source .env`, `cp .env ...`,
//          `find ... -exec cat`, obfuscated globs like `.e*` / `.??v`, etc.),
//        - Glob / Grep file targeting (pattern / path / glob).
//      Template files (.env.example/.sample/.template/.dist/.defaults) stay
//      ALLOWED so the agent can still scaffold non-secret config.
//
//   2. Recursive directory deletion via Bash: `rm -r`/`-rf`/`-fr`/`-Rf`,
//      `rmdir`, `find ... -delete`, `find ... -exec rm`, `git clean -d`.
//
// Blocking uses the PreToolUse decision schema (permissionDecision: "deny")
// printed to stdout with exit 0, so Claude gets the reason and can adapt.
// On any parse/internal error the hook fails OPEN (exit 0) so a malformed
// event can never brick the session.
//
// Wired to (PreToolUse): Bash|Read|Edit|Write|MultiEdit|NotebookEdit|Glob|Grep
//
// Run with: java SecurityGuard.java   (JDK 21 single-file source launch, no compile step)

import java.util.*;
import java.util.regex.*;

public class SecurityGuard {

    private static final Set<String> ENV_ALLOWED = Set.of(
            ".env.example", ".env.sample", ".env.template", ".env.dist", ".env.defaults");

    private static final Pattern ENV_TOKEN_RE =
            Pattern.compile("\\.env(?:\\.[\\w-]+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENV_GLOB_RE =
            Pattern.compile("\\.e(?:nv)?[*?]|\\.\\?\\?v|\\*\\.env\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern RMDIR_RE = Pattern.compile("\\brmdir\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIND_DELETE_RE =
            Pattern.compile("\\bfind\\b[^|;&\\n]*?(?:-delete\\b|-exec\\s+rm\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIT_CLEAN_DIR_RE =
            Pattern.compile("\\bgit\\s+clean\\b[^|;&\\n]*-[a-z]*d", Pattern.CASE_INSENSITIVE);
    private static final Pattern RM_RE = Pattern.compile("\\brm\\b([^|;&\\n]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_FLAG_RE = Pattern.compile("(?<!\\w)-[a-z]+");

    private static final String ENV_HINT =
            "Reading/editing/copying/sourcing a .env is blocked by the project security hook. "
                    + "Use a .env.example template for non-secret config.";

    private static String clean(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String basename(String path) {
        String p = clean(path).replace('\\', '/');
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        int idx = p.lastIndexOf('/');
        return idx >= 0 ? p.substring(idx + 1) : p;
    }

    private static boolean isProtectedEnvName(String name) {
        name = name.toLowerCase(Locale.ROOT);
        if (ENV_ALLOWED.contains(name)) return false;
        return name.equals(".env") || name.startsWith(".env.");
    }

    private static boolean pathIsProtectedEnv(String path) {
        return path != null && !path.isEmpty() && isProtectedEnvName(basename(path));
    }

    private static boolean textTouchesProtectedEnv(String text) {
        if (text == null || text.isEmpty()) return false;
        if (ENV_GLOB_RE.matcher(text).find()) return true;
        Matcher m = ENV_TOKEN_RE.matcher(text);
        while (m.find()) {
            if (!ENV_ALLOWED.contains(m.group().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean commandDeletesDir(String cmd) {
        String norm = cmd.replaceAll("\\s+", " ");
        if (RMDIR_RE.matcher(norm).find() || FIND_DELETE_RE.matcher(norm).find()
                || GIT_CLEAN_DIR_RE.matcher(norm).find()) {
            return true;
        }
        Matcher m = RM_RE.matcher(norm);
        while (m.find()) {
            String args = m.group(1).toLowerCase(Locale.ROOT);
            if (args.contains("--recursive")) return true;
            Matcher fm = SHORT_FLAG_RE.matcher(args);
            while (fm.find()) {
                String flag = fm.group();
                if (!flag.startsWith("--") && flag.contains("r")) return true;
            }
        }
        return false;
    }

    private static void deny(String reason) {
        Map<String, Object> hookOutput = new LinkedHashMap<>();
        hookOutput.put("hookEventName", "PreToolUse");
        hookOutput.put("permissionDecision", "deny");
        hookOutput.put("permissionDecisionReason", reason);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hookSpecificOutput", hookOutput);
        System.out.println(Json.write(root));
        System.exit(0);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        try {
            Object parsed = Json.read(new String(System.in.readAllBytes(), "UTF-8"));
            if (!(parsed instanceof Map)) {
                System.exit(0);
                return;
            }
            Map<String, Object> data = (Map<String, Object>) parsed;

            String tool = String.valueOf(data.getOrDefault("tool_name", ""));
            Object tiObj = data.get("tool_input");
            Map<String, Object> ti = (tiObj instanceof Map) ? (Map<String, Object>) tiObj : Map.of();

            switch (tool) {
                case "Read", "Edit", "Write" -> {
                    String fp = str(ti.get("file_path"));
                    if (pathIsProtectedEnv(fp)) {
                        deny("Blocked: '" + fp + "' is a protected .env file. " + ENV_HINT);
                    }
                }
                case "MultiEdit" -> {
                    List<String> paths = new ArrayList<>();
                    paths.add(str(ti.get("file_path")));
                    Object edits = ti.get("edits");
                    if (edits instanceof List<?> list) {
                        for (Object e : list) {
                            if (e instanceof Map<?, ?> em) {
                                paths.add(str(em.get("file_path")));
                            }
                        }
                    }
                    for (String p : paths) {
                        if (pathIsProtectedEnv(p)) {
                            deny("Blocked: '" + p + "' is a protected .env file. " + ENV_HINT);
                        }
                    }
                }
                case "NotebookEdit" -> {
                    if (pathIsProtectedEnv(str(ti.get("notebook_path")))) {
                        deny("Blocked: editing a protected .env file. " + ENV_HINT);
                    }
                }
                case "Glob" -> {
                    for (String c : new String[]{str(ti.get("pattern")), str(ti.get("path"))}) {
                        if (textTouchesProtectedEnv(c) || pathIsProtectedEnv(c)) {
                            deny("Blocked: globbing a protected .env file. " + ENV_HINT);
                        }
                    }
                }
                case "Grep" -> {
                    for (String c : new String[]{str(ti.get("path")), str(ti.get("glob"))}) {
                        if (textTouchesProtectedEnv(c) || pathIsProtectedEnv(c)) {
                            deny("Blocked: grepping a protected .env file leaks its contents. " + ENV_HINT);
                        }
                    }
                }
                case "Bash" -> {
                    String cmd = str(ti.get("command"));
                    if (commandDeletesDir(cmd)) {
                        deny("Blocked: recursive directory deletion (rm -r/-rf, rmdir, "
                                + "find -delete, find -exec rm, git clean -d) is denied by the "
                                + "project security hook. Delete specific files explicitly instead.");
                    }
                    if (textTouchesProtectedEnv(cmd)) {
                        deny("Blocked: this command references a protected .env file. " + ENV_HINT);
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            System.exit(0); // fail open
        }
        System.exit(0);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
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
                i++; // {
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
                i++; // [
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
                i++; // opening quote
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
                i++; // closing quote
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

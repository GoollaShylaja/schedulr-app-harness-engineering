package com.schedulr.tooling.codebasesearch;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AST-based structural search over the project's Java source, mirroring the guarantees of
 * the Python-AST codebase-search server from the original brownfield demo: {@link #whereIs}
 * and {@link #findReferences} match only real definitions/uses (via JavaParser's parse
 * tree), never substrings inside comments, string literals, or javadoc — that's what
 * {@code grep} already does.
 */
final class JavaSourceIndex {

    /** Dependency/build/tooling output — never "the codebase" being indexed. */
    private static final Set<String> EXCLUDE_DIRS = Set.of(
            ".git", "target", "build", "dist", "node_modules", ".mvn", ".idea", ".claude",
            ".vscode", "out");

    private final Path root;
    private final JavaParser parser;

    JavaSourceIndex(Path root) {
        this.root = root;
        // StaticJavaParser keeps its ParserConfiguration in a ThreadLocal, so configuring
        // it here (constructor runs on the startup thread) would silently not apply on
        // whatever thread the MCP SDK dispatches tool calls on. An explicit JavaParser
        // instance, configured once and reused, avoids that pitfall entirely. JAVA_21 is
        // required — the default level rejects records and modern switch expressions,
        // both used throughout this codebase (and this class).
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(config);
    }

    record Definition(String path, int line, String kind, String qualname, String signature) {
    }

    record Reference(String path, int line, String kind, String text) {
    }

    // --- file discovery & parsing -------------------------------------------------

    private List<Path> javaFiles() {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .filter(this::notExcluded)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    private boolean notExcluded(Path path) {
        for (Path part : root.relativize(path)) {
            if (EXCLUDE_DIRS.contains(part.toString())) return false;
        }
        return true;
    }

    // JavaParser instances are not documented as thread-safe for concurrent parse() calls;
    // the MCP SDK dispatches tool-call requests on separate threads even via the "sync"
    // server facade, so concurrent tool calls can otherwise corrupt/short-circuit parsing.
    private synchronized CompilationUnit parse(Path path) {
        try {
            return parser.parse(path).getResult().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private String rel(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    // --- definition collection ------------------------------------------------------

    /**
     * Walks one compilation unit collecting every class/interface/enum/record/annotation,
     * method, constructor, and field — in source order, tracking the enclosing-type stack
     * so qualified names are accurate (mirrors the Python collector's class/func stack).
     */
    private static final class DefCollector extends VoidVisitorAdapter<Void> {
        private final String relpath;
        private final String packagePrefix;
        private final Deque<String> stack = new ArrayDeque<>();
        final List<Definition> defs = new ArrayList<>();

        DefCollector(String relpath, String packagePrefix) {
            this.relpath = relpath;
            this.packagePrefix = packagePrefix;
        }

        private String qual(String name) {
            String prefix = packagePrefix.isEmpty() ? "" : packagePrefix + ".";
            String stacked = stack.isEmpty() ? "" : String.join(".", stack) + ".";
            return prefix + stacked + name;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            String kind = n.isInterface() ? "interface" : "class";
            defs.add(new Definition(relpath, n.getBegin().map(b -> b.line).orElse(-1), kind,
                    qual(n.getNameAsString()), kind + " " + n.getNameAsString()));
            stack.push(n.getNameAsString());
            super.visit(n, arg);
            stack.pop();
        }

        @Override
        public void visit(EnumDeclaration n, Void arg) {
            defs.add(new Definition(relpath, n.getBegin().map(b -> b.line).orElse(-1), "enum",
                    qual(n.getNameAsString()), "enum " + n.getNameAsString()));
            stack.push(n.getNameAsString());
            super.visit(n, arg);
            stack.pop();
        }

        @Override
        public void visit(RecordDeclaration n, Void arg) {
            String params = n.getParameters().stream()
                    .map(p -> p.getTypeAsString() + " " + p.getNameAsString())
                    .collect(Collectors.joining(", "));
            defs.add(new Definition(relpath, n.getBegin().map(b -> b.line).orElse(-1), "record",
                    qual(n.getNameAsString()), "record " + n.getNameAsString() + "(" + params + ")"));
            stack.push(n.getNameAsString());
            super.visit(n, arg);
            stack.pop();
        }

        @Override
        public void visit(AnnotationDeclaration n, Void arg) {
            defs.add(new Definition(relpath, n.getBegin().map(b -> b.line).orElse(-1), "annotation",
                    qual(n.getNameAsString()), "@interface " + n.getNameAsString()));
            stack.push(n.getNameAsString());
            super.visit(n, arg);
            stack.pop();
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            defs.add(new Definition(relpath, n.getBegin().map(b -> b.line).orElse(-1), "method",
                    qual(n.getNameAsString()), n.getDeclarationAsString(true, false, true)));
            super.visit(n, arg);
        }

        @Override
        public void visit(ConstructorDeclaration n, Void arg) {
            defs.add(new Definition(relpath, n.getBegin().map(b -> b.line).orElse(-1), "constructor",
                    qual(n.getNameAsString()), n.getDeclarationAsString(true, false, true)));
            super.visit(n, arg);
        }

        @Override
        public void visit(FieldDeclaration n, Void arg) {
            for (VariableDeclarator v : n.getVariables()) {
                defs.add(new Definition(relpath, n.getBegin().map(b -> b.line).orElse(-1), "field",
                        qual(v.getNameAsString()), v.getTypeAsString() + " " + v.getNameAsString()));
            }
            super.visit(n, arg);
        }
    }

    private static String packagePrefix(CompilationUnit cu) {
        return cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
    }

    private List<Definition> definitionsIn(Path path) {
        CompilationUnit cu = parse(path);
        if (cu == null) return List.of();
        DefCollector collector = new DefCollector(rel(path), packagePrefix(cu));
        collector.visit(cu, null);
        return collector.defs;
    }

    private List<Definition> allDefinitions() {
        List<Definition> all = new ArrayList<>();
        for (Path path : javaFiles()) {
            all.addAll(definitionsIn(path));
        }
        return all;
    }

    // --- reference collection --------------------------------------------------------

    private static final class RefCollector extends VoidVisitorAdapter<Void> {
        private final String relpath;
        private final String name;
        final List<Reference> refs = new ArrayList<>();

        RefCollector(String relpath, String name) {
            this.relpath = relpath;
            this.name = name;
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            if (n.getNameAsString().equals(name)) {
                refs.add(new Reference(relpath, n.getBegin().map(b -> b.line).orElse(-1), "call", name + "(...)"));
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            if (n.getTypeAsString().equals(name)) {
                refs.add(new Reference(relpath, n.getBegin().map(b -> b.line).orElse(-1), "call", "new " + name + "(...)"));
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            if (n.getNameAsString().equals(name)) {
                refs.add(new Reference(relpath, n.getBegin().map(b -> b.line).orElse(-1), "attribute", "." + name));
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(NameExpr n, Void arg) {
            if (n.getNameAsString().equals(name)) {
                refs.add(new Reference(relpath, n.getBegin().map(b -> b.line).orElse(-1), "name", name));
            }
            super.visit(n, arg);
        }
    }

    private List<Reference> referencesTo(String name) {
        // priority: call > attribute > name — a call site is also a name load; keep the
        // most specific kind, deduplicated to one reference per line (mirrors Python).
        Map<String, Integer> priority = Map.of("call", 0, "attribute", 1, "name", 2);
        Map<String, Reference> found = new LinkedHashMap<>();
        for (Path path : javaFiles()) {
            CompilationUnit cu = parse(path);
            if (cu == null) continue;
            RefCollector collector = new RefCollector(rel(path), name);
            collector.visit(cu, null);
            for (Reference ref : collector.refs) {
                String key = ref.path() + ":" + ref.line();
                Reference existing = found.get(key);
                if (existing == null || priority.get(ref.kind()) < priority.get(existing.kind())) {
                    found.put(key, ref);
                }
            }
        }
        return found.values().stream()
                .sorted(Comparator.comparing(Reference::path).thenComparingInt(Reference::line))
                .collect(Collectors.toList());
    }

    // --- module resolution -------------------------------------------------------------

    private Path resolveFile(String query) {
        String norm = query.strip();
        if (norm.endsWith(".java")) norm = norm.substring(0, norm.length() - 5);
        norm = norm.replace('\\', '/');
        String dotted = norm.replace('/', '.');
        for (Path path : javaFiles()) {
            String relNoExt = rel(path).substring(0, rel(path).length() - ".java".length());
            String fqcn = relNoExt.replace('/', '.');
            // strip a leading src/main/java or src/test/java prefix for the dotted form
            String fqcnFromSrc = stripSourceRoot(relNoExt).replace('/', '.');
            if (relNoExt.equals(norm) || fqcn.equals(dotted) || fqcnFromSrc.equals(dotted)) {
                return path;
            }
            if (relNoExt.endsWith("/" + norm) || fqcnFromSrc.endsWith("." + dotted)
                    || fqcnFromSrc.equals(dotted)) {
                return path;
            }
        }
        return null;
    }

    private static String stripSourceRoot(String relNoExt) {
        for (String marker : new String[]{"src/main/java/", "src/test/java/"}) {
            int idx = relNoExt.indexOf(marker);
            if (idx >= 0) return relNoExt.substring(idx + marker.length());
        }
        return relNoExt;
    }

    // --- public API used by the MCP tools ----------------------------------------------

    String whereIs(String name) {
        List<Definition> hits = allDefinitions().stream()
                .filter(d -> lastSegment(d.qualname()).equals(name))
                .sorted(Comparator.comparing(Definition::path).thenComparingInt(Definition::line))
                .collect(Collectors.toList());
        if (hits.isEmpty()) {
            return "no definition of '" + name + "' found in the project's Java source";
        }
        StringBuilder sb = new StringBuilder(hits.size() + " definition(s) of '" + name + "':\n");
        for (Definition d : hits) {
            sb.append("  ").append(d.path()).append(':').append(d.line())
                    .append("  [").append(d.kind()).append("] ").append(d.qualname()).append('\n');
            if (d.kind().equals("method") || d.kind().equals("constructor")) {
                sb.append("      ").append(d.signature()).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    String findReferences(String name) {
        List<Reference> refs = referencesTo(name);
        if (refs.isEmpty()) {
            return "no references to '" + name + "' found in the project's Java source";
        }
        StringBuilder sb = new StringBuilder(refs.size() + " reference(s) to '" + name + "':\n");
        for (Reference r : refs) {
            sb.append("  ").append(r.path()).append(':').append(r.line())
                    .append("  [").append(r.kind()).append("] ").append(r.text()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    String outline(String module) {
        Path target = resolveFile(module);
        if (target == null) {
            return "no file matching '" + module + "' in the project's Java source";
        }
        List<Definition> defs = definitionsIn(target);
        if (defs.isEmpty()) {
            return rel(target) + " has no top-level definitions";
        }
        StringBuilder sb = new StringBuilder("outline of " + rel(target) + ":\n");
        for (Definition d : defs) {
            String indent = switch (d.kind()) {
                case "method", "constructor", "field" -> "    ";
                default -> "  ";
            };
            sb.append(indent).append(d.line()).append(": [").append(d.kind()).append("] ")
                    .append(d.signature()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String lastSegment(String qualname) {
        int idx = qualname.lastIndexOf('.');
        return idx >= 0 ? qualname.substring(idx + 1) : qualname;
    }
}

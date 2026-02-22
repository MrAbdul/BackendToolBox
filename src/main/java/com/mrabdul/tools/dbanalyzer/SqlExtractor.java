package com.mrabdul.tools.dbanalyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.Node;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class SqlExtractor {

    private final SqlHeuristicParser parser = new SqlHeuristicParser();

    public List<SqlArtifact> extractAll(Path root, List<String> includePackages, boolean includeDynamic) throws IOException {
        if (root == null || !Files.exists(root)) {
            throw new IllegalArgumentException("Root path does not exist: " + root);
        }

        List<Path> javaFiles = new ArrayList<Path>();
        Stream<Path> walk = null;
        try {
            walk = Files.walk(root);
            walk.filter(new java.util.function.Predicate<Path>() {
                @Override
                public boolean test(Path p) {
                    return p.toString().endsWith(".java");
                }
            }).forEach(new java.util.function.Consumer<Path>() {
                @Override
                public void accept(Path p) {
                    javaFiles.add(p);
                }
            });
        } finally {
            if (walk != null) {
                walk.close();
            }
        }

        List<SqlArtifact> out = new ArrayList<SqlArtifact>();
        for (Path file : javaFiles) {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

            CompilationUnit cu;
            try {
                cu = StaticJavaParser.parse(content);
            } catch (Exception parseErr) {
                // v0.1: skip files that don't parse cleanly
                continue;
            }

            String pkg = cu.getPackageDeclaration().isPresent()
                    ? cu.getPackageDeclaration().get().getNameAsString()
                    : "";

            if (includePackages != null && !includePackages.isEmpty()) {
                boolean ok = false;
                for (String pref : includePackages) {
                    if (pkg.startsWith(pref)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) continue;
            }

            String rel = root.relativize(file).toString().replace('\\', '/');
            out.addAll(extractFromCompilationUnit(rel, cu, includeDynamic));
        }

        return out;
    }

    private List<SqlArtifact> extractFromCompilationUnit(String relativeFile, CompilationUnit cu, boolean includeDynamic) {
        List<SqlArtifact> artifacts = new ArrayList<>();

        cu.accept(new VoidVisitorAdapter<Void>() {
            int callOrdinal=0;
            String currentClass = "";
            String currentMethod = "";

            // Method-local builder tracking (very v0.1)
            Map<String, StringBuilder> builderContent = new HashMap<>();
            Set<String> builderDynamic = new HashSet<>();

            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                String prev = currentClass;
                currentClass = n.getNameAsString();
                super.visit(n, arg);
                currentClass = prev;
            }

            @Override
            public void visit(MethodDeclaration n, Void arg) {
                String prev = currentMethod;
                currentMethod = n.getNameAsString();

                builderContent.clear();
                builderDynamic.clear();
                callOrdinal =0;
                super.visit(n, arg);

                currentMethod = prev;
            }

            @Override
            public void visit(FieldDeclaration n, Void arg) {
                for (VariableDeclarator vd : n.getVariables()) {
                    if (!vd.getTypeAsString().equals("String")) continue;

                    Optional<String> lit = evalLiteralString(vd.getInitializer().orElse(null));
                    if (lit.isPresent()) {
                        String raw = lit.get();
                        if (SqlNormalizer.looksLikeSql(raw)) {
                            addArtifactFrom(relativeFile, currentClass, "FIELD:" + vd.getNameAsString(), vd, raw, false);
                        }
                    }
                }
                super.visit(n, arg);
            }

            @Override
            public void visit(VariableDeclarator n, Void arg) {
                // String sql = "select ..."
                if (n.getTypeAsString().equals("String")) {
                    Optional<String> lit = evalLiteralString(n.getInitializer().orElse(null));
                    if (lit.isPresent()) {
                        String raw = lit.get();
                        if (SqlNormalizer.looksLikeSql(raw)) {
                            addArtifactFrom(relativeFile, currentClass, currentMethod + ":" + n.getNameAsString(), n, raw, false);
                        }
                    }
                }

                // StringBuilder sb = new StringBuilder();
                String t = n.getTypeAsString();
                if (t.equals("StringBuilder") || t.equals("StringBuffer")) {
                    if (n.getInitializer().isPresent() && n.getInitializer().get() instanceof ObjectCreationExpr) {
                        builderContent.put(n.getNameAsString(), new StringBuilder());
                    }
                }

                super.visit(n, arg);
            }

            @Override
            public void visit(MethodCallExpr n, Void arg) {
                // detect sb.append("...")
                n.getScope().ifPresent(scope -> {
                    if (scope instanceof NameExpr) {
                        String var = ((NameExpr) scope).getNameAsString();
                        if (builderContent.containsKey(var) && n.getNameAsString().equals("append") && n.getArguments().size() >= 1) {
                            Expression a0 = n.getArgument(0);
                            Optional<String> lit = evalLiteralString(a0);
                            if (lit.isPresent()) {
                                builderContent.get(var).append(lit.get());
                            } else {
                                builderDynamic.add(var);
                                builderContent.get(var).append(" /*DYNAMIC*/ ");
                            }
                        }

                        // detect sb.toString()
                        if (builderContent.containsKey(var) && n.getNameAsString().equals("toString") && n.getArguments().isEmpty()) {
                            boolean dyn = builderDynamic.contains(var);
                            if (dyn && !includeDynamic) return;

                            String raw = builderContent.get(var).toString();
                            if (SqlNormalizer.looksLikeSql(raw)) {
                                addArtifactFrom(relativeFile, currentClass, currentMethod + ":" + var + ".toString()", n, raw, dyn);
                            }
                        }
                    }
                });

                // detect direct calls like query("SELECT ...")
                for (Expression e : n.getArguments()) {
                    if (e instanceof StringLiteralExpr) {
                        String raw = ((StringLiteralExpr) e).asString();
                        if (SqlNormalizer.looksLikeSql(raw)) {
                            String owner=currentMethod +":call#"+(++callOrdinal);
                            addArtifactFrom(relativeFile, currentClass, owner, n, raw, false);
                        }
                    } else {
                        Optional<String> lit = evalLiteralString(e);
                        if (lit.isPresent()) {
                            String raw = lit.get();
                            if (SqlNormalizer.looksLikeSql(raw)) {
                                String owner=currentMethod +":call#"+(++callOrdinal);
                                addArtifactFrom(relativeFile, currentClass, owner, n, raw, false);
                            }
                        }
                    }
                }

                super.visit(n, arg);
            }

            private void addArtifactFrom(String relativeFile, String cls, String owner, Node node, String raw, boolean dynamic) {
                int line = -1;
                if (node != null && node.getRange().isPresent()) {
                    line = node.getRange().get().begin.line;
                }

                String normalized = SqlNormalizer.normalize(raw);
                SqlMeta meta = parser.parse(normalized);

                String key = relativeFile + "#" + cls + "#" + owner;
                artifacts.add(new SqlArtifact(key, relativeFile, cls, owner, line, raw, normalized, dynamic, meta));
            }

            private int lineOf(MethodCallExpr n) {
                if (n != null && n.getRange().isPresent()) {
                    return n.getRange().get().begin.line;
                }
                return -1;
            }

            private Optional<String> evalLiteralString(Expression e) {
                if (e == null) return Optional.empty();

                if (e instanceof StringLiteralExpr) {
                    return Optional.of(((StringLiteralExpr) e).asString());
                }

                if (e instanceof BinaryExpr) {
                    BinaryExpr b = (BinaryExpr) e;
                    if (b.getOperator() != BinaryExpr.Operator.PLUS) return Optional.empty();
                    Optional<String> left = evalLiteralString(b.getLeft());
                    Optional<String> right = evalLiteralString(b.getRight());
                    if (left.isPresent() && right.isPresent()) {
                        return Optional.of(left.get() + right.get());
                    }
                }

                // v0.1: we don't resolve NameExpr variables/constants across scope
                return Optional.empty();
            }
        }, null);

        return artifacts;
    }
}

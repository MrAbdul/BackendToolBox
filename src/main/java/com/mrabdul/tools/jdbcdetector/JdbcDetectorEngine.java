package com.mrabdul.tools.jdbcdetector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class JdbcDetectorEngine {

    private static final Set<String> JDBC_TYPES = new HashSet<String>(Arrays.asList(
            "java.sql.Connection",
            "java.sql.Statement",
            "java.sql.PreparedStatement",
            "java.sql.ResultSet"
    ));

    private static final Set<String> CLOSE_HELPERS = new HashSet<String>(Arrays.asList(
            "closeQuietly",
            "closeSilently",
            "safeClose"
    ));

    public JdbcDetectorResult run(JdbcDetectorRequest req) throws Exception {
        Path root = Paths.get(req.getSourceRootPath()).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Path does not exist: " + root);
        }

        // Symbol solver setup: reflection + parse types from the target project
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(root.toFile()));

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);

        List<Path> javaFiles = Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains(File.separator + "target" + File.separator))
                .collect(Collectors.toList());

        List<Finding> allFindings = new ArrayList<Finding>();

        for (Path file : javaFiles) {
            try {
                String content = new String(Files.readAllBytes(file), "UTF-8");

                // Optional fast cut: if filter exists and file doesn't even mention any base name, skip parsing
                if (req.hasDaoFilter() && !contentMentionsAnyBase(content, req.getDaoBaseTypes())) {
                    continue;
                }

                CompilationUnit cu = StaticJavaParser.parse(content);
                allFindings.addAll(analyzeCompilationUnit(file, cu, req.getDaoBaseTypes()));

            } catch (Exception parseOrSolveIssue) {
                allFindings.add(Finding.parseError(
                        file.toString(),
                        parseOrSolveIssue.getClass().getSimpleName() + ": " + parseOrSolveIssue.getMessage()
                ));
            }
        }

        // Apply include toggles
        List<Finding> filtered = allFindings.stream().filter(f -> {
            if ("ISSUE".equals(f.kind)) return true;
            if ("WARN".equals(f.kind)) return req.isIncludeWarnings();
            if ("PARSE_ERROR".equals(f.kind)) return req.isIncludeParseErrors();
            return false;
        }).collect(Collectors.toList());

        long issues = filtered.stream().filter(f -> "ISSUE".equals(f.kind)).count();
        long warns = filtered.stream().filter(f -> "WARN".equals(f.kind)).count();
        long parseErrors = filtered.stream().filter(f -> "PARSE_ERROR".equals(f.kind)).count();

        String report = buildReportText(filtered, issues, warns, parseErrors);

        // Optional JSON output
        String jsonPath = req.getJsonOutputPath();
        if (jsonPath != null && !jsonPath.trim().isEmpty()) {
            Path jsonOut = Paths.get(jsonPath.trim()).toAbsolutePath().normalize();
            ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            if (jsonOut.getParent() != null) Files.createDirectories(jsonOut.getParent());
            om.writeValue(jsonOut.toFile(), filtered);
        }

        return new JdbcDetectorResult(filtered, issues, warns, parseErrors, report);
    }

    private boolean contentMentionsAnyBase(String content, List<String> baseTypes) {
        if (baseTypes == null || baseTypes.isEmpty()) return true;
        for (String base : baseTypes) {
            if (base == null) continue;
            String simple = DaoFilterParser.simpleName(base);
            if (!simple.isEmpty() && content.contains(simple)) return true;
            if (base.contains(".") && content.contains(base)) return true;
        }
        return false;
    }

    private List<Finding> analyzeCompilationUnit(Path file, CompilationUnit cu, List<String> daoBaseTypes) {
        // If no filter: analyze everything in file
        if (daoBaseTypes == null || daoBaseTypes.isEmpty()) {
            List<Finding> out = new ArrayList<Finding>();
            for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
                out.addAll(analyzeMethod(file, m));
            }
            return out;
        }

        // With filter: only analyze classes that extend any base
        List<ClassOrInterfaceDeclaration> targets = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(ClassOrInterfaceDeclaration::isClassOrInterfaceDeclaration)
                .filter(c -> extendsAnyConfiguredBase(c, daoBaseTypes))
                .collect(Collectors.toList());

        if (targets.isEmpty()) return Collections.emptyList();

        List<Finding> out = new ArrayList<Finding>();
        for (ClassOrInterfaceDeclaration cls : targets) {
            cls.getMethods().forEach(m -> out.addAll(analyzeMethod(file, m)));
        }
        return out;
    }

    private boolean extendsAnyConfiguredBase(ClassOrInterfaceDeclaration c, List<String> daoBaseTypes) {
        if (daoBaseTypes == null || daoBaseTypes.isEmpty()) return true;

        return c.getExtendedTypes().stream().anyMatch(t -> {
            String extendedSimple = t.getNameAsString();
            String extendedAsString = t.toString(); // might include FQN, generics

            for (String base : daoBaseTypes) {
                if (base == null) continue;
                String baseSimple = DaoFilterParser.simpleName(base);

                // extends CorpDAO
                if (baseSimple.equals(extendedSimple)) return true;

                // extends com.foo.CorpDAO (exact suffix match)
                if (extendedAsString.endsWith("." + baseSimple)) return true;

                // extends com.foo.CorpDAO (provided as FQN)
                if (base.contains(".") && extendedAsString.endsWith(base)) return true;
            }
            return false;
        });
    }

    private List<Finding> analyzeMethod(Path file, MethodDeclaration method) {
        List<Finding> out = new ArrayList<Finding>();

        method.getBody().ifPresent(body -> {
            final Map<String, String> localVarTypes = new HashMap<String, String>();
            final Map<String, ResourceVar> opened = new LinkedHashMap<String, ResourceVar>();

            // 1) Variable declarations with initializer
            body.findAll(VariableDeclarator.class).forEach(vd -> {
                Optional<String> fqnTypeOpt = resolveFqnTypeSafe(vd.getType());
                if (!fqnTypeOpt.isPresent()) return;

                String var = vd.getNameAsString();
                String fqnType = fqnTypeOpt.get();
                localVarTypes.put(var, fqnType);

                if (!JDBC_TYPES.contains(fqnType)) return;

                Optional<Expression> init = vd.getInitializer();
                if (init.isPresent() && looksLikeOpen(init.get())) {
                    int line = vd.getBegin().map(p -> p.line).orElse(-1);
                    opened.put(var, new ResourceVar(var, fqnType, line, vd));
                }
            });

            // 1b) Assignments like rs = stmt.executeQuery()
            body.findAll(AssignExpr.class).forEach(ae -> {
                if (!ae.getTarget().isNameExpr()) return;

                String var = ae.getTarget().asNameExpr().getNameAsString();
                String type = localVarTypes.get(var);
                if (type == null) return;
                if (!JDBC_TYPES.contains(type)) return;

                Expression value = ae.getValue();
                if (!looksLikeOpen(value)) return;

                if (!opened.containsKey(var)) {
                    int line = ae.getBegin().map(p -> p.line).orElse(-1);
                    opened.put(var, new ResourceVar(var, type, line, ae));
                }
            });

            // 2) Remove anything declared in try-with-resources
            Set<String> declaredInTwr = new HashSet<String>();
            body.findAll(TryStmt.class).forEach(ts -> {
                ts.getResources().forEach(res -> {
                    if (res.isVariableDeclarationExpr()) {
                        res.asVariableDeclarationExpr().getVariables().forEach(vd -> declaredInTwr.add(vd.getNameAsString()));
                    }
                });
            });

            // 3) Find closes in finally blocks
            Set<String> closedInFinally = new HashSet<String>();
            body.findAll(TryStmt.class).forEach(ts -> ts.getFinallyBlock().ifPresent(finallyBlock -> {
                closedInFinally.addAll(findVarsClosedIn(finallyBlock));
            }));

            // 3b) Find closes anywhere (for WARN)
            Set<String> closedAnywhere = findVarsClosedIn(body);

            String methodName = method.getDeclarationAsString(false, false, false);
            String filePath = file.toString();

            for (ResourceVar rv : opened.values()) {
                if (declaredInTwr.contains(rv.name)) continue;

                boolean safe = closedInFinally.contains(rv.name);
                boolean closedSomewhere = closedAnywhere.contains(rv.name);

                if (safe) continue;

                String kind;
                String msg;

                if (closedSomewhere) {
                    kind = "WARN";
                    msg = "Closed somewhere in method but NOT in finally/TWR; exception paths may leak";
                } else {
                    kind = "ISSUE";
                    msg = "Opened but NOT in try-with-resources and no close detected";
                }

                out.add(new Finding(
                        kind,
                        filePath,
                        rv.line,
                        methodName,
                        rv.typeFqn,
                        rv.name,
                        msg
                ));
            }
        });

        return out;
    }

    private boolean looksLikeOpen(Expression expr) {
        if (expr.isMethodCallExpr()) {
            MethodCallExpr mc = expr.asMethodCallExpr();
            String name = mc.getNameAsString();

            if ("getConnection".equals(name)) return true;
            if ("prepareStatement".equals(name)) return true;
            if ("createStatement".equals(name)) return true;
            if ("executeQuery".equals(name)) return true;
        }

        if (expr.isCastExpr()) return looksLikeOpen(expr.asCastExpr().getExpression());
        if (expr.isEnclosedExpr()) return looksLikeOpen(expr.asEnclosedExpr().getInner());

        return false;
    }

    private Set<String> findVarsClosedIn(BlockStmt block) {
        Set<String> closed = new HashSet<String>();

        block.findAll(MethodCallExpr.class).forEach(mc -> {
            String methodName = mc.getNameAsString();

            // x.close()
            if ("close".equals(methodName)) {
                mc.getScope().ifPresent(scope -> {
                    if (scope.isNameExpr()) closed.add(scope.asNameExpr().getNameAsString());
                });
            }

            // closeQuietly(x) / safeClose(x)
            if (CLOSE_HELPERS.contains(methodName)) {
                mc.getArguments().forEach(arg -> {
                    if (arg.isNameExpr()) closed.add(arg.asNameExpr().getNameAsString());
                });
            }
        });

        return closed;
    }

    private Optional<String> resolveFqnTypeSafe(com.github.javaparser.ast.type.Type t) {
        try {
            ResolvedType rt = t.resolve();
            if (rt.isReferenceType()) {
                return Optional.of(rt.asReferenceType().getQualifiedName());
            }
            return Optional.empty();
        } catch (Exception ignored) {
            // fallback to simple name
            String s = t.asString();
            if ("Connection".equals(s)) return Optional.of("java.sql.Connection");
            if ("Statement".equals(s)) return Optional.of("java.sql.Statement");
            if ("PreparedStatement".equals(s)) return Optional.of("java.sql.PreparedStatement");
            if ("ResultSet".equals(s)) return Optional.of("java.sql.ResultSet");
            return Optional.empty();
        }
    }

    private String buildReportText(List<Finding> findings, long issues, long warns, long parseErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== jdbcdetector ===\n");
        sb.append("Issues: ").append(issues).append("\n");
        sb.append("Warnings: ").append(warns).append("\n");
        sb.append("Parse/analysis errors: ").append(parseErrors).append("\n");

        List<Finding> topIssues = findings.stream()
                .filter(f -> "ISSUE".equals(f.kind))
                .limit(50)
                .collect(Collectors.toList());

        if (!topIssues.isEmpty()) {
            sb.append("\nTop issues:\n");
            for (Finding f : topIssues) {
                sb.append("- ").append(f.file).append(":").append(f.line).append(" | ").append(f.method).append("\n");
                sb.append("  ").append(f.resourceType).append(" ").append(f.variable).append(" -> ").append(f.message).append("\n");
            }
        }

        List<Finding> topWarns = findings.stream()
                .filter(f -> "WARN".equals(f.kind))
                .limit(30)
                .collect(Collectors.toList());

        if (!topWarns.isEmpty()) {
            sb.append("\nWarnings:\n");
            for (Finding f : topWarns) {
                sb.append("- ").append(f.file).append(":").append(f.line).append(" | ").append(f.method).append("\n");
                sb.append("  ").append(f.resourceType).append(" ").append(f.variable).append(" -> ").append(f.message).append("\n");
            }
        }

        List<Finding> topParse = findings.stream()
                .filter(f -> "PARSE_ERROR".equals(f.kind))
                .limit(10)
                .collect(Collectors.toList());

        if (!topParse.isEmpty()) {
            sb.append("\nParse/analysis errors (top 10):\n");
            for (Finding f : topParse) {
                sb.append("- ").append(f.file).append("\n");
                sb.append("  ").append(f.message).append("\n");
            }
        }

        return sb.toString();
    }

    static class ResourceVar {
        final String name;
        final String typeFqn;
        final int line;
        final Node node;

        ResourceVar(String name, String typeFqn, int line, Node node) {
            this.name = name;
            this.typeFqn = typeFqn;
            this.line = line;
            this.node = node;
        }
    }
}

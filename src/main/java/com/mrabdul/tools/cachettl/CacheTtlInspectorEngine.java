package com.mrabdul.tools.cachettl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class CacheTtlInspectorEngine {
    private static final String DEFAULT_CONFIG_RESOURCE = "cachettl-default-config.json";

    private final ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public CacheTtlInspectorResult run(CacheTtlInspectorRequest req) throws Exception {
        Path root = Paths.get(req.getSourceRoot()).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Path does not exist: " + root);
        }

        CacheTtlConfig cfg = loadConfigOrDefault(req.getConfigPath());
        // Setup symbol solver (best-effort). If it breaks on some repos, we still run on method-name-only.
        setupSymbolSolverBestEffort(root);

        List<Path> javaFiles = Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !shouldExclude(cfg, p))
                .collect(Collectors.toList());

        List<CacheOperation> operations = new ArrayList<CacheOperation>();

        for (Path file : javaFiles) {
            String content;
            try {
                content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            } catch (Exception ignore) {
                continue;
            }

            CompilationUnit cu;
            try {
                cu = StaticJavaParser.parse(content);
            } catch (Exception parseErr) {
                // v0.1: skip files that don't parse
                continue;
            }

            String rel = root.relativize(file).toString().replace('\\', '/');
            operations.addAll(extractOperations(rel, cu, cfg));
        }

        // Summarize namespaces
        Map<String, CacheNamespaceSummary> ns = summarizeNamespaces(operations);

        // Findings
        List<CacheTtlFinding> findings = applyRules(cfg, ns, operations);

        CacheTtlInspectorJsonReport report = new CacheTtlInspectorJsonReport();
        report.sourceRoot = root.toString();
        report.configPath = configUsedLabel(req.getConfigPath());
        report.scannedFileCount = javaFiles.size();
        report.operationsCount = operations.size();
        report.namespacesCount = ns.size();
        report.operations = operations;
        report.namespaces = new ArrayList<CacheNamespaceSummary>(ns.values());

        // Optional JSON write
        if (req.getJsonOutPath() != null && !req.getJsonOutPath().trim().isEmpty()) {
            Path out = Paths.get(req.getJsonOutPath()).toAbsolutePath().normalize();
            if (out.getParent() != null) Files.createDirectories(out.getParent());

            // wrap result in a single object for easier downstream use
            Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
            wrapper.put("report", report);
            wrapper.put("findings", findings);

            om.writeValue(out.toFile(), wrapper);
        }

        return new CacheTtlInspectorResult(report, findings);
    }


    private CacheTtlConfig loadConfigOrDefault(String configPath) throws Exception {

        // If user provided a path, load from file
        if (configPath != null && !configPath.trim().isEmpty()) {
            Path p = Paths.get(configPath.trim()).toAbsolutePath().normalize();
            if (!Files.exists(p)) {
                throw new IllegalArgumentException("Config file does not exist: " + p);
            }
            return om.readValue(p.toFile(), CacheTtlConfig.class);
        }

        // Otherwise load from classpath resource
        InputStream in = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_RESOURCE);
        if (in == null) {
            throw new IllegalStateException(
                    "No --config provided and default config not found in classpath: " + DEFAULT_CONFIG_RESOURCE
            );
        }

        try {
            return om.readValue(in, CacheTtlConfig.class);
        } finally {
            try { in.close(); } catch (Exception ignore) {}
        }
    }

    private String configUsedLabel(String configPath) {
        if (configPath == null || configPath.trim().isEmpty()) {
            return "(built-in default: " + DEFAULT_CONFIG_RESOURCE + ")";
        }
        return Paths.get(configPath.trim()).toAbsolutePath().normalize().toString();
    }

    private boolean shouldExclude(CacheTtlConfig cfg, Path p) {
        if (cfg == null || cfg.scan == null || cfg.scan.excludePathContains == null) return false;
        String s = p.toString().replace('\\', '/');
        for (String x : cfg.scan.excludePathContains) {
            if (x == null) continue;
            String t = x.replace('\\', '/');
            if (!t.isEmpty() && s.contains(t)) return true;
        }
        return false;
    }

    private void setupSymbolSolverBestEffort(Path root) {
        try {
            CombinedTypeSolver typeSolver = new CombinedTypeSolver();
            typeSolver.add(new ReflectionTypeSolver());
            typeSolver.add(new JavaParserTypeSolver(root.toFile()));
            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
            StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
        } catch (Exception ignore) {
            // run without symbol solving
        }
    }

    private List<CacheOperation> extractOperations(final String relativeFile, CompilationUnit cu, CacheTtlConfig cfg) {
        final List<CacheOperation> out = new ArrayList<CacheOperation>();

        cu.accept(new VoidVisitorAdapter<Void>() {

            String currentClass = "";
            String currentMethod = "";

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
                super.visit(n, arg);
                currentMethod = prev;
            }

            @Override
            public void visit(MethodCallExpr n, Void arg) {
                tryMatchMethodCall(n);
                super.visit(n, arg);
            }

            private void tryMatchMethodCall(MethodCallExpr call) {
                if (cfg == null || cfg.detectors == null || cfg.detectors.isEmpty()) return;

                String calledName = call.getNameAsString();
                String resolvedOwnerFqn = null;
                String resolvedSig = null;
                String scopeText = call.getScope().isPresent() ? call.getScope().get().toString() : null;
                // best-effort resolve
                try {
                    ResolvedMethodDeclaration r = call.resolve();
                    resolvedOwnerFqn = r.declaringType().getQualifiedName();
                    resolvedSig = resolvedOwnerFqn + "." + r.getName();
                } catch (Exception ignore) {
                    // ignore
                }

                for (CacheTtlConfig.Detector d : cfg.detectors) {
                    if (d == null || d.methodRegex == null) continue;

                    if (!calledName.matches(d.methodRegex)) {
                        continue;
                    }
                    int actualArgs = call.getArguments() == null ? 0 : call.getArguments().size();
                    if (d.argCount != null && actualArgs != d.argCount.intValue()) continue;
                    if (d.minArgs != null && actualArgs < d.minArgs.intValue()) continue;
                    if (d.maxArgs != null && actualArgs > d.maxArgs.intValue()) continue;
                    if (d.scopeNameRegex != null && !d.scopeNameRegex.trim().isEmpty()) {
                        if (scopeText == null || !scopeText.matches(d.scopeNameRegex)) continue;
                    }
                    // If ownerTypeRegex configured, require match when we have a resolved owner
                    if (d.ownerTypeRegex != null && !d.ownerTypeRegex.trim().isEmpty()) {
                        if (resolvedOwnerFqn != null) {
                            if (!resolvedOwnerFqn.matches(d.ownerTypeRegex)) continue;
                        } else {
                            // no owner info; we keep a soft-match (still accept)
                            // v0.1 choice: accept, but mark methodCalled without owner
                        }
                    }

                    CacheOperation op = new CacheOperation();
                    op.opType = safe(d.operation, "UNKNOWN");
                    op.cacheLayer = safe(d.cacheLayer, "UNKNOWN");
                    op.file = relativeFile;
                    op.className = currentClass;
                    op.methodName = currentMethod;
                    op.line = call.getRange().isPresent() ? call.getRange().get().begin.line : -1;

                    op.methodCalled = resolvedSig != null ? resolvedSig : (calledName + "/" + actualArgs);

                    // key extraction
                    if (call.getArguments() != null && call.getArguments().size() > d.keyArgIndex) {
                        Expression k = call.getArgument(d.keyArgIndex);
                        op.keyExpr = k == null ? null : k.toString();
                        op.namespaceKey = KeyFingerprint.computeNamespace(k, cfg.keyNormalization);
                    } else {
                        op.keyExpr = null;
                        op.namespaceKey = "(unknown-key)";
                    }

                    // ttl extraction
                    if (d.ttlArgIndex != null) {
                        int idx = d.ttlArgIndex.intValue();
                        if (call.getArguments() != null && call.getArguments().size() > idx) {
                            Expression ttlExpr = call.getArgument(idx);
                            op.ttlExpr = ttlExpr == null ? null : ttlExpr.toString();
                            TtlValue tv = TtlResolver.resolve(ttlExpr, d.ttlUnitDefault, cfg.ttlResolution);
                            op.ttlSeconds = tv.seconds;
                        }
                    }

                    out.add(op);

                    // v0.1: first matching detector wins
                    break;
                }
            }

            private String safe(String s, String def) {
                if (s == null) return def;
                String t = s.trim();
                return t.isEmpty() ? def : t;
            }
        }, null);

        return out;
    }

    private Map<String, CacheNamespaceSummary> summarizeNamespaces(List<CacheOperation> ops) {
        Map<String, CacheNamespaceSummary> map = new LinkedHashMap<String, CacheNamespaceSummary>();

        for (CacheOperation op : ops) {
            String nsKey = (op.namespaceKey == null || op.namespaceKey.trim().isEmpty()) ? "(unknown)" : op.namespaceKey;
            String layer = (op.cacheLayer == null || op.cacheLayer.trim().isEmpty()) ? "UNKNOWN" : op.cacheLayer;

            String key = nsKey + "||" + layer;

            CacheNamespaceSummary s = map.get(key);
            if (s == null) {
                s = new CacheNamespaceSummary();
                s.namespaceKey = nsKey;
                s.cacheLayer = layer;
                map.put(key, s);
            }

            if ("PUT".equalsIgnoreCase(op.opType)) {
                s.putCount++;
                if (op.ttlSeconds != null) s.putWithTtlCount++;
                else s.putWithoutTtlCount++;
            } else if ("DELETE".equalsIgnoreCase(op.opType)) {
                s.deleteCount++;
            } else if ("CLEAR".equalsIgnoreCase(op.opType)) {
                s.clearCount++;
            }
        }

        for (CacheNamespaceSummary s : map.values()) {
            s.lifecycleManaged = (s.deleteCount > 0 || s.clearCount > 0);
        }

        return map;
    }

    private List<CacheTtlFinding> applyRules(CacheTtlConfig cfg,
                                             Map<String, CacheNamespaceSummary> ns,
                                             List<CacheOperation> ops) {
        List<CacheTtlFinding> out = new ArrayList<CacheTtlFinding>();
        if (cfg == null || cfg.rules == null) return out;

        // Rule: no TTL and no lifecycle deletes
        if (cfg.rules.flagNoTtlWithoutDelete) {
            for (CacheNamespaceSummary s : ns.values()) {
                if (s.putWithoutTtlCount > 0 && !s.lifecycleManaged) {
                    out.add(new CacheTtlFinding(
                            "HIGH",
                            "NO_TTL_WITHOUT_DELETE",
                            s.namespaceKey,
                            s.cacheLayer,
                            "(summary)",
                            -1,
                            "Namespace has PUT without TTL and no detected DELETE/CLEAR. Potential unbounded cache growth."
                    ));
                }
            }
        }

        // Operation-level rules (ttl too long/short/dynamic)
        for (CacheOperation op : ops) {
            if (!"PUT".equalsIgnoreCase(op.opType)) continue;

            if (cfg.rules.flagDynamicTtl) {
                if (op.ttlExpr != null && op.ttlSeconds == null) {
                    out.add(new CacheTtlFinding(
                            "MED",
                            "DYNAMIC_TTL",
                            safeNs(op),
                            safeLayer(op),
                            op.file,
                            op.line,
                            "TTL expression could not be resolved to seconds: " + op.ttlExpr
                    ));
                }
            }

            if (cfg.rules.flagVeryLongTtl && op.ttlSeconds != null && op.ttlSeconds.longValue() > cfg.rules.veryLongTtlSeconds) {
                out.add(new CacheTtlFinding(
                        "MED",
                        "VERY_LONG_TTL",
                        safeNs(op),
                        safeLayer(op),
                        op.file,
                        op.line,
                        "TTL is very long (" + op.ttlSeconds + "s). Threshold=" + cfg.rules.veryLongTtlSeconds + "s"
                ));
            }

            if (cfg.rules.flagVeryShortTtl && op.ttlSeconds != null && op.ttlSeconds.longValue() < cfg.rules.veryShortTtlSeconds) {
                out.add(new CacheTtlFinding(
                        "LOW",
                        "VERY_SHORT_TTL",
                        safeNs(op),
                        safeLayer(op),
                        op.file,
                        op.line,
                        "TTL is very short (" + op.ttlSeconds + "s). Threshold=" + cfg.rules.veryShortTtlSeconds + "s"
                ));
            }
        }

        return out;
    }

    private String safeNs(CacheOperation op) {
        return (op.namespaceKey == null || op.namespaceKey.trim().isEmpty()) ? "(unknown)" : op.namespaceKey;
    }

    private String safeLayer(CacheOperation op) {
        return (op.cacheLayer == null || op.cacheLayer.trim().isEmpty()) ? "UNKNOWN" : op.cacheLayer;
    }
}
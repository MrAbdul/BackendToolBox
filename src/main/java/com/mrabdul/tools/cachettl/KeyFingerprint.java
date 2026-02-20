package com.mrabdul.tools.cachettl;

import com.github.javaparser.ast.expr.*;

public final class KeyFingerprint {

    private KeyFingerprint() {}

    public static String computeNamespace(Expression keyExpr, CacheTtlConfig.KeyNormalization cfg) {
        if (keyExpr == null) return "(unknown-key)";

        if (cfg == null) {
            return keyExpr.toString();
        }

        // If it's "A + B + C", grab first term as namespace
        if (cfg.useFirstConcatTermAsNamespace) {
            Expression first = firstConcatTerm(keyExpr);
            if (first != null) {
                String ns = normalizeTerm(first, cfg);
                if (ns != null && !ns.trim().isEmpty()) return ns;
            }
        }

        // fallback
        return keyExpr.toString();
    }

    private static Expression firstConcatTerm(Expression e) {
        // flatten left-most of PLUS binary chain
        if (e instanceof EnclosedExpr) return firstConcatTerm(((EnclosedExpr) e).getInner());
        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            if (b.getOperator() == BinaryExpr.Operator.PLUS) {
                return firstConcatTerm(b.getLeft());
            }
        }
        return e;
    }

    private static String normalizeTerm(Expression t, CacheTtlConfig.KeyNormalization cfg) {
        if (t == null) return null;

        if (t instanceof StringLiteralExpr) {
            String lit = ((StringLiteralExpr) t).asString();
            String re = cfg.literalPrefixRegex;
            if (re == null || re.trim().isEmpty()) return lit;
            return lit.matches(re) ? lit : lit; // v0.1: keep it anyway
        }

        // NAME or CONST: KEY_PROFILE or CacheKeys.KEY_PROFILE
        if (t instanceof NameExpr) {
            String name = ((NameExpr) t).getNameAsString();
            if (name.matches(cfg.constantNameRegex)) return name;
            return name;
        }
        if (t instanceof FieldAccessExpr) {
            FieldAccessExpr fa = (FieldAccessExpr) t;
            String name = fa.toString(); // e.g. CacheKeys.PROFILE_KEY
            // if the last part matches constantNameRegex, accept
            String last = fa.getNameAsString();
            if (last.matches(cfg.constantNameRegex)) return name;
            return name;
        }

        return t.toString();
    }
}
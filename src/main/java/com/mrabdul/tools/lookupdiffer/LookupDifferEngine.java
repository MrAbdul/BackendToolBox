package com.mrabdul.tools.lookupdiffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

import static com.mrabdul.tools.lookupdiffer.SqlParsers.*;

public class LookupDifferEngine {

    // HARD-CODED encoding as requested
    private static final Charset FILE_CHARSET = StandardCharsets.UTF_8;

    public LookupDifferResult run(LookupDifferRequest req) throws Exception {
        Path src = Paths.get(req.getSourceDir()).toAbsolutePath().normalize();
        Path tgt = Paths.get(req.getTargetDir()).toAbsolutePath().normalize();

        if (!Files.isDirectory(src)) throw new IllegalArgumentException("Source dir not found: " + src);
        if (!Files.isDirectory(tgt)) throw new IllegalArgumentException("Target dir not found: " + tgt);

        SqlExportIndex sourceIndex = indexDirectory(src, req);
        SqlExportIndex targetIndex = indexDirectory(tgt, req);

        // After parsing PK + inserts, build pk-keyed row maps
        buildPkKeyedRows(sourceIndex, req.isCaseInsensitive());
        buildPkKeyedRows(targetIndex, req.isCaseInsensitive());

        List<DiffFinding> findings = new ArrayList<DiffFinding>();
        findings.addAll(sourceIndex.parseErrors);
        findings.addAll(targetIndex.parseErrors);

        // 1) Missing tables: based on DDL presence in source but not in target
        for (String tableKey : sourceIndex.ddlsByTableKey.keySet()) {
            if (!targetIndex.ddlsByTableKey.containsKey(tableKey)) {
                SqlExportIndex.TableDdl ddl = sourceIndex.ddlsByTableKey.get(tableKey);
                findings.add(new DiffFinding(
                        "TABLE_MISSING",
                        ddl.tableName,
                        ddl.file,
                        ddl.line,
                        "Table exists in SOURCE but not in TARGET."
                ));
            }
        }

        // 2) Missing columns: for tables that exist in both (DDL diff)
        List<String> commonTables = sourceIndex.ddlsByTableKey.keySet().stream()
                .filter(targetIndex.ddlsByTableKey::containsKey)
                .collect(Collectors.toList());

        List<String> schemaAlters = new ArrayList<String>();

        for (String tableKey : commonTables) {
            SqlExportIndex.TableDdl sddl = sourceIndex.ddlsByTableKey.get(tableKey);
            SqlExportIndex.TableDdl tddl = targetIndex.ddlsByTableKey.get(tableKey);

            for (Map.Entry<String, SqlExportIndex.ColumnDef> e : sddl.columnsByKey.entrySet()) {
                String colKey = e.getKey();
                if (!tddl.columnsByKey.containsKey(colKey)) {
                    SqlExportIndex.ColumnDef col = e.getValue();
                    String alter = "ALTER TABLE " + sddl.tableName + " ADD (" + col.columnSqlDef + ");";
                    schemaAlters.add(alter);

                    DiffFinding f = new DiffFinding(
                            "COLUMN_MISSING",
                            sddl.tableName,
                            sddl.file,
                            col.line,
                            "Column exists in SOURCE but not in TARGET: " + col.columnName
                    );
                    f.ddl = alter;
                    findings.add(f);
                }
            }
        }

        // 3) Data diff by PK:
        // - Missing key in target => INSERT
        // - Key exists but values differ => UPDATE
        List<String> insertPatch = new ArrayList<String>();
        List<String> updatePatch = new ArrayList<String>();

        // Union tables that have rows in source (even if no DDL parsed)
        Set<String> tablesWithRows = new LinkedHashSet<String>();
        tablesWithRows.addAll(sourceIndex.rowsByPkKeyByTableKey.keySet());

        for (String tableKey : tablesWithRows) {
            Map<String, SqlExportIndex.Row> srcRows = sourceIndex.rowsByPkKeyByTableKey.get(tableKey);
            if (srcRows == null) srcRows = Collections.emptyMap();

            Map<String, SqlExportIndex.Row> tgtRows = targetIndex.rowsByPkKeyByTableKey.get(tableKey);
            if (tgtRows == null) tgtRows = Collections.emptyMap();

            List<String> pkCols = sourceIndex.pkColumnsByTableKey.get(tableKey);
            boolean hasPk = pkCols != null && !pkCols.isEmpty();

            if (!hasPk && !srcRows.isEmpty()) {
                // If no PK, we cannot safely create UPDATE statements.
                // We'll still generate INSERT for missing (by raw key), but "different" detection is skipped.
                SqlExportIndex.Row any = srcRows.values().iterator().next();
                findings.add(new DiffFinding(
                        "WARN_NO_PK",
                        any.tableName,
                        any.file,
                        any.line,
                        "No PK detected for table; updates are skipped for this table in v0.1."
                ));
            }

            for (Map.Entry<String, SqlExportIndex.Row> e : srcRows.entrySet()) {
                String pkKey = e.getKey();
                SqlExportIndex.Row sRow = e.getValue();
                SqlExportIndex.Row tRow = tgtRows.get(pkKey);

                if (tRow == null) {
                    String ins = ensureSemicolon(sRow.originalInsertSql);
                    insertPatch.add(ins);

                    DiffFinding f = new DiffFinding(
                            "ROW_MISSING",
                            sRow.tableName,
                            sRow.file,
                            sRow.line,
                            "Row exists in SOURCE but not in TARGET."
                    );
                    f.insertSql = ins;
                    findings.add(f);
                } else if (hasPk) {
                    String upd = buildUpdateSql(sRow.tableName, pkCols, sRow.colToVal, tRow.colToVal);
                    if (upd != null) {
                        updatePatch.add(upd);

                        DiffFinding f = new DiffFinding(
                                "ROW_DIFFERENT",
                                sRow.tableName,
                                sRow.file,
                                sRow.line,
                                "Row exists in both but values differ; UPDATE generated."
                        );
                        f.ddl = upd;
                        findings.add(f);
                    }
                }
            }
        }

        long missingTables = count(findings, "TABLE_MISSING");
        long missingColumns = count(findings, "COLUMN_MISSING");
        long missingRows = count(findings, "ROW_MISSING");
        long differingRows = count(findings, "ROW_DIFFERENT");
        long warnings = count(findings, "WARN_NO_PK");
        long parseErrors = count(findings, "PARSE_ERROR");

        String report = buildReport(findings, missingTables, missingColumns, missingRows, differingRows, warnings, parseErrors);

        // Optional outputs
        if (req.getOutDir() != null && !req.getOutDir().trim().isEmpty()) {
            Path out = Paths.get(req.getOutDir().trim()).toAbsolutePath().normalize();
            Files.createDirectories(out);

            writeUtf8(out.resolve("missing_tables.sql"), buildMissingTablesSql(findings));
            writeUtf8(out.resolve("schema_patch.sql"), buildSchemaPatchSql(schemaAlters));
            writeUtf8(out.resolve("insert_patch.sql"), buildInsertPatchSql(insertPatch));
            writeUtf8(out.resolve("update_patch.sql"), buildUpdatePatchSql(updatePatch));
        }

        if (req.getJsonOut() != null && !req.getJsonOut().trim().isEmpty()) {
            Path jsonOut = Paths.get(req.getJsonOut().trim()).toAbsolutePath().normalize();
            if (jsonOut.getParent() != null) Files.createDirectories(jsonOut.getParent());
            ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            om.writeValue(jsonOut.toFile(), findings);
        }

        return new LookupDifferResult(findings, missingTables, missingColumns, missingRows, differingRows, warnings, parseErrors, report);
    }

    // ---------------- Indexing ----------------

    private SqlExportIndex indexDirectory(Path dir, LookupDifferRequest req) throws Exception {
        SqlExportIndex idx = new SqlExportIndex();

        List<Path> files = Files.walk(dir)
                .filter(p -> p.toString().toLowerCase().endsWith(".sql"))
                .collect(Collectors.toList());

        for (Path p : files) {
            try {
                List<String> lines = Files.readAllLines(p, FILE_CHARSET);
                parseFile(p, lines, idx, req);
            } catch (Exception ex) {
                idx.parseErrors.add(DiffFinding.parseError(
                        p.toString(),
                        ex.getClass().getSimpleName() + ": " + ex.getMessage()
                ));
            }
        }
        return idx;
    }

    private void parseFile(Path file, List<String> lines, SqlExportIndex idx, LookupDifferRequest req) {
        String filePath = file.toString();
        String tableFilter = req.getTableNameContains();
        boolean ci = req.isCaseInsensitive();

        StringBuilder stmt = new StringBuilder();
        int stmtStartLine = 1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) continue;

            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // ignore Oracle "PROMPT ..."
            if (trimmed.startsWith("PROMPT ")) continue;

            if (stmt.length() == 0) stmtStartLine = i + 1;
            stmt.append(line).append("\n");

            // Oracle terminators: ";" or "/" on its own line
            boolean endBySemicolon = trimmed.endsWith(";");
            boolean endBySlash = "/".equals(trimmed);

            if (endBySemicolon || endBySlash) {
                String sql = flattenSql(stmt.toString());
                stmt.setLength(0);

                if (startsWithIgnoreCase(sql, "CREATE TABLE")) {
                    parseCreateTable(sql, filePath, stmtStartLine, idx, ci, tableFilter);
                    continue;
                }
                if (startsWithIgnoreCase(sql, "INSERT INTO")) {
                    parseInsert(sql, filePath, stmtStartLine, idx, ci, tableFilter);
                    continue;
                }

                // PK statements usually appear as:
                // - CREATE UNIQUE INDEX ... ON "SCHEMA"."TABLE" ("COL")
                // - ALTER TABLE ... PRIMARY KEY (...)
                if (containsIgnoreCase(sql, "PRIMARY KEY")
                        || startsWithIgnoreCase(sql, "CREATE UNIQUE INDEX")
                        || startsWithIgnoreCase(sql, "ALTER TABLE")) {
                    parsePk(sql, filePath, stmtStartLine, idx, ci, tableFilter);
                }
            }
        }

        // flush tail
        if (stmt.length() > 0) {
            String sql = flattenSql(stmt.toString());
            if (startsWithIgnoreCase(sql, "CREATE TABLE")) {
                parseCreateTable(sql, filePath, stmtStartLine, idx, req.isCaseInsensitive(), req.getTableNameContains());
            } else if (startsWithIgnoreCase(sql, "INSERT INTO")) {
                parseInsert(sql, filePath, stmtStartLine, idx, req.isCaseInsensitive(), req.getTableNameContains());
            } else if (containsIgnoreCase(sql, "PRIMARY KEY")
                    || startsWithIgnoreCase(sql, "CREATE UNIQUE INDEX")
                    || startsWithIgnoreCase(sql, "ALTER TABLE")) {
                parsePk(sql, filePath, stmtStartLine, idx, req.isCaseInsensitive(), req.getTableNameContains());
            }
        }
    }

    private void parseCreateTable(String sql,
                                  String file,
                                  int line,
                                  SqlExportIndex idx,
                                  boolean ci,
                                  String tableFilter) {

        // CREATE TABLE <name> ( ... )
        String after = sql.substring("CREATE TABLE".length()).trim();
        int paren = after.indexOf('(');
        if (paren < 0) return;

        String tableName = stripSchema(stripQuotes(after.substring(0, paren).trim()));
        if (!tableFilterOk(tableName, tableFilter, ci)) return;

        String colsRaw = firstBalanced(after, '(', ')');
        if (colsRaw == null) return;

        List<String> colDefs = splitTopLevelComma(colsRaw);

        Map<String, SqlExportIndex.ColumnDef> colsByKey = new LinkedHashMap<String, SqlExportIndex.ColumnDef>();

        for (String def : colDefs) {
            String colDef = def.trim();
            if (colDef.isEmpty()) continue;

            String up = ci ? colDef.toUpperCase() : colDef;

            // ignore constraints/PK/unique in v0.1 schema diff
            if (up.startsWith("CONSTRAINT")
                    || up.startsWith("PRIMARY KEY")
                    || up.startsWith("UNIQUE")
                    || up.startsWith("FOREIGN KEY")) {
                continue;
            }

            String colName = firstToken(colDef);
            colName = stripSchema(stripQuotes(colName));
            if (colName.isEmpty()) continue;

            String colKey = ci ? colName.toUpperCase() : colName;
            colsByKey.put(colKey, new SqlExportIndex.ColumnDef(colName, colDef, line));
        }

        String tableKey = ci ? tableName.toUpperCase() : tableName;
        if (!idx.ddlsByTableKey.containsKey(tableKey)) {
            idx.ddlsByTableKey.put(tableKey, new SqlExportIndex.TableDdl(tableName, file, line, colsByKey));
        }
    }

    private void parsePk(String sql,
                         String file,
                         int line,
                         SqlExportIndex idx,
                         boolean ci,
                         String tableFilter) {

        String flat = flattenSql(sql);

        // First try: CREATE UNIQUE INDEX ... ON "SCHEMA"."TABLE" ("COL1","COL2")
        if (startsWithIgnoreCase(flat, "CREATE UNIQUE INDEX")) {
            int onIdx = indexOfIgnoreCase(flat, " ON ");
            if (onIdx > 0) {
                String afterOn = flat.substring(onIdx + 4).trim();
                int paren = afterOn.indexOf('(');
                if (paren > 0) {
                    String tableNameRaw = afterOn.substring(0, paren).trim();
                    String tableName = stripSchema(stripQuotes(tableNameRaw));

                    if (!tableFilterOk(tableName, tableFilter, ci)) return;

                    String colsRaw = firstBalanced(afterOn, '(', ')');
                    if (colsRaw == null) return;

                    List<String> cols = splitTopLevelComma(colsRaw);
                    List<String> pk = new ArrayList<String>();
                    for (String c : cols) {
                        String col = stripSchema(stripQuotes(normToken(c)));
                        if (col.isEmpty()) continue;
                        pk.add(ci ? col.toUpperCase() : col);
                    }

                    if (!pk.isEmpty()) {
                        String tableKey = ci ? tableName.toUpperCase() : tableName;
                        if (!idx.pkColumnsByTableKey.containsKey(tableKey)) {
                            idx.pkColumnsByTableKey.put(tableKey, pk);
                        }
                    }
                }
            }
            return;
        }

        // Second try: ALTER TABLE ... PRIMARY KEY (...)
        if (startsWithIgnoreCase(flat, "ALTER TABLE") && containsIgnoreCase(flat, "PRIMARY KEY")) {
            String tableName = extractTableFromAlter(flat);
            if (tableName == null || tableName.trim().isEmpty()) return;

            tableName = stripSchema(stripQuotes(tableName.trim()));
            if (!tableFilterOk(tableName, tableFilter, ci)) return;

            int pkIdx = indexOfIgnoreCase(flat, "PRIMARY KEY");
            String afterPk = flat.substring(pkIdx);
            String colsRaw = firstBalanced(afterPk, '(', ')');
            if (colsRaw == null) return;

            List<String> cols = splitTopLevelComma(colsRaw);
            List<String> pk = new ArrayList<String>();
            for (String c : cols) {
                String col = stripSchema(stripQuotes(normToken(c)));
                if (col.isEmpty()) continue;
                pk.add(ci ? col.toUpperCase() : col);
            }

            if (!pk.isEmpty()) {
                String tableKey = ci ? tableName.toUpperCase() : tableName;
                if (!idx.pkColumnsByTableKey.containsKey(tableKey)) {
                    idx.pkColumnsByTableKey.put(tableKey, pk);
                }
            }
        }
    }

    private void parseInsert(String sql,
                             String file,
                             int line,
                             SqlExportIndex idx,
                             boolean ci,
                             String tableFilter) {

        // INSERT INTO schema.table (a,b) VALUES (x,y)
        String s = sql.trim();

        String after = s.substring("INSERT INTO".length()).trim();
        int parenCols = after.indexOf('(');
        if (parenCols < 0) return;

        String tableName = stripSchema(stripQuotes(after.substring(0, parenCols).trim()));
        if (!tableFilterOk(tableName, tableFilter, ci)) return;

        String colsRaw = firstBalanced(after, '(', ')');
        if (colsRaw == null) return;

        int valuesIdx = indexOfIgnoreCase(after, "VALUES");
        if (valuesIdx < 0) return;

        String afterValues = after.substring(valuesIdx + "VALUES".length()).trim();
        String valsRaw = firstBalanced(afterValues, '(', ')');
        if (valsRaw == null) return;

        List<String> cols = splitTopLevelComma(colsRaw);
        List<String> vals = splitTopLevelComma(valsRaw);
        if (cols.size() != vals.size()) return;

        Map<String, String> colToVal = new LinkedHashMap<String, String>();
        for (int i = 0; i < cols.size(); i++) {
            String col = stripSchema(stripQuotes(normToken(cols.get(i))));
            String val = normalizeValue(normToken(vals.get(i)));

            String colKey = ci ? col.toUpperCase() : col;
            colToVal.put(colKey, val);
        }

        String tableKey = ci ? tableName.toUpperCase() : tableName;

        SqlExportIndex.Row row = new SqlExportIndex.Row(
                tableName,
                file,
                line,
                colToVal,
                ensureSemicolon(sql)
        );

        List<SqlExportIndex.Row> list = idx.rowsListByTableKey.get(tableKey);
        if (list == null) {
            list = new ArrayList<SqlExportIndex.Row>();
            idx.rowsListByTableKey.put(tableKey, list);
        }
        list.add(row);
    }

    private void buildPkKeyedRows(SqlExportIndex idx, boolean ci) {
        for (Map.Entry<String, List<SqlExportIndex.Row>> e : idx.rowsListByTableKey.entrySet()) {
            String tableKey = e.getKey();
            List<SqlExportIndex.Row> rows = e.getValue();
            if (rows == null) rows = Collections.emptyList();

            List<String> pkCols = idx.pkColumnsByTableKey.get(tableKey);

            Map<String, SqlExportIndex.Row> map = new LinkedHashMap<String, SqlExportIndex.Row>();

            for (SqlExportIndex.Row r : rows) {
                String key;
                if (pkCols != null && !pkCols.isEmpty()) {
                    key = buildPkKey(tableKey, pkCols, r.colToVal);
                    if (key == null) {
                        // can't build pk key, fallback to raw identity
                        key = buildRawKey(tableKey, r.colToVal);
                    }
                } else {
                    // no pk => raw identity (still works for "missing" detection; updates skipped)
                    key = buildRawKey(tableKey, r.colToVal);
                }
                map.put(key, r); // last wins if duplicates
            }

            idx.rowsByPkKeyByTableKey.put(tableKey, map);
        }
    }

    // ---------------- Diff helpers ----------------

    private String buildUpdateSql(String tableName,
                                  List<String> pkCols,
                                  Map<String, String> src,
                                  Map<String, String> tgt) {

        // changed columns (excluding PK)
        List<String> sets = new ArrayList<String>();

        for (Map.Entry<String, String> e : src.entrySet()) {
            String col = e.getKey();
            if (pkCols.contains(col)) continue;

            String sVal = e.getValue();
            String tVal = tgt.get(col);

            if (!safeEq(sVal, tVal)) {
                sets.add(col + " = " + sVal);
            }
        }

        if (sets.isEmpty()) return null;

        // WHERE PK
        List<String> wh = new ArrayList<String>();
        for (String pk : pkCols) {
            String v = src.get(pk);
            if (v == null) return null;
            wh.add(pk + " = " + v);
        }

        return "UPDATE " + tableName + " SET " + join(", ", sets) + " WHERE " + join(" AND ", wh) + ";";
    }

    private String buildPkKey(String tableKey, List<String> pkCols, Map<String, String> colToVal) {
        StringBuilder sb = new StringBuilder();
        sb.append(tableKey).append("|");
        for (int i = 0; i < pkCols.size(); i++) {
            String pk = pkCols.get(i);
            String v = colToVal.get(pk);
            if (v == null) return null;
            if (i > 0) sb.append("|");
            sb.append(pk).append("=").append(v);
        }
        return sb.toString();
    }

    private String buildRawKey(String tableKey, Map<String, String> colToVal) {
        TreeMap<String, String> sorted = new TreeMap<String, String>(colToVal);
        StringBuilder sb = new StringBuilder();
        sb.append(tableKey).append("|");
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    // ---------------- Text/report/output ----------------

    private String buildReport(List<DiffFinding> findings,
                               long missingTables,
                               long missingColumns,
                               long missingRows,
                               long differingRows,
                               long warnings,
                               long parseErrors) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== lookupdiffer ===\n");
        sb.append("Missing tables: ").append(missingTables).append("\n");
        sb.append("Missing columns: ").append(missingColumns).append("\n");
        sb.append("Missing rows (INSERT): ").append(missingRows).append("\n");
        sb.append("Differing rows (UPDATE): ").append(differingRows).append("\n");
        sb.append("Warnings: ").append(warnings).append("\n");
        sb.append("Parse errors: ").append(parseErrors).append("\n");

        List<DiffFinding> top = findings.stream()
                .filter(f -> !"PARSE_ERROR".equals(f.kind))
                .limit(80)
                .collect(Collectors.toList());

        if (!top.isEmpty()) {
            sb.append("\nTop findings:\n");
            for (DiffFinding f : top) {
                sb.append("- ").append(f.kind).append(" | ").append(f.table)
                        .append(" | ").append(f.file).append(":").append(f.line).append("\n");
                sb.append("  ").append(f.message).append("\n");
                if (f.ddl != null && !f.ddl.trim().isEmpty()) sb.append("  ").append(f.ddl).append("\n");
            }
        }

        List<DiffFinding> pe = findings.stream().filter(f -> "PARSE_ERROR".equals(f.kind)).limit(10).collect(Collectors.toList());
        if (!pe.isEmpty()) {
            sb.append("\nParse errors (top 10):\n");
            for (DiffFinding f : pe) {
                sb.append("- ").append(f.file).append("\n");
                sb.append("  ").append(f.message).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildMissingTablesSql(List<DiffFinding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Tables present in SOURCE but missing in TARGET\n");
        for (DiffFinding f : findings) {
            if ("TABLE_MISSING".equals(f.kind)) {
                sb.append("-- ").append(f.table).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildSchemaPatchSql(List<String> alters) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Schema patch: add missing columns (SOURCE -> TARGET)\n");
        for (String a : alters) sb.append(a).append("\n");
        return sb.toString();
    }

    private String buildInsertPatchSql(List<String> inserts) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Data patch: insert missing rows (SOURCE -> TARGET)\n");
        for (String ins : inserts) sb.append(ensureSemicolon(ins)).append("\n");
        return sb.toString();
    }

    private String buildUpdatePatchSql(List<String> updates) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Data patch: update differing rows (SOURCE -> TARGET)\n");
        for (String upd : updates) sb.append(upd).append("\n");
        return sb.toString();
    }

    private void writeUtf8(Path path, String content) throws Exception {
        Files.write(path, content.getBytes(Charset.forName("UTF-8")));
    }

    // ---------------- Small utilities ----------------

    private long count(List<DiffFinding> findings, String kind) {
        return findings.stream().filter(f -> kind.equals(f.kind)).count();
    }

    private boolean startsWithIgnoreCase(String s, String prefix) {
        if (s == null || prefix == null) return false;
        if (s.length() < prefix.length()) return false;
        return s.substring(0, prefix.length()).equalsIgnoreCase(prefix);
    }

    private boolean containsIgnoreCase(String s, String token) {
        if (s == null || token == null) return false;
        return s.toUpperCase().contains(token.toUpperCase());
    }

    private int indexOfIgnoreCase(String s, String token) {
        if (s == null || token == null) return -1;
        return s.toUpperCase().indexOf(token.toUpperCase());
    }

    private String firstToken(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return "";
        int sp = t.indexOf(' ');
        if (sp < 0) return t;
        return t.substring(0, sp).trim();
    }

    private String ensureSemicolon(String sql) {
        if (sql == null) return "";
        String t = sql.trim();
        if (t.endsWith(";")) return t;
        return t + ";";
    }

    private String stripQuotes(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private String stripSchema(String s) {
        if (s == null) return "";
        String t = s.trim();
        int dot = t.lastIndexOf('.');
        if (dot >= 0) return t.substring(dot + 1);
        return t;
    }

    private boolean tableFilterOk(String tableName, String filter, boolean ci) {
        if (filter == null || filter.trim().isEmpty()) return true;
        String tf = filter.trim();
        String t = ci ? tableName.toLowerCase() : tableName;
        String f = ci ? tf.toLowerCase() : tf;
        return t.contains(f);
    }

    private String extractTableFromAlter(String flat) {
        // ALTER TABLE <table> ...
        int idx = indexOfIgnoreCase(flat, "ALTER TABLE");
        if (idx < 0) return null;
        String after = flat.substring(idx + "ALTER TABLE".length()).trim();
        int sp = after.indexOf(' ');
        if (sp < 0) return after;
        return after.substring(0, sp).trim();
    }

    private String normalizeValue(String v) {
        if (v == null) return "";
        String s = v.trim();
        s = s.replace('\u00A0', ' ');
        s = s.replaceAll("\\s+", " ");
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        if ("NULL".equalsIgnoreCase(s)) return "NULL";
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    private boolean safeEq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private String join(String sep, List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
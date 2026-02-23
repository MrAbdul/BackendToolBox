package com.mrabdul.tools.lookupdiffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.mrabdul.tools.lookupdiffer.SqlParsers.*;

public class LookupDifferEngine {

    public LookupDifferResult run(LookupDifferRequest req) throws Exception {
        Path src = Paths.get(req.getSourceDir()).toAbsolutePath().normalize();
        Path tgt = Paths.get(req.getTargetDir()).toAbsolutePath().normalize();

        if (!Files.isDirectory(src)) throw new IllegalArgumentException("Source dir not found: " + src);
        if (!Files.isDirectory(tgt)) throw new IllegalArgumentException("Target dir not found: " + tgt);

        SqlExportIndex sourceIndex = indexDirectory(src, req);
        SqlExportIndex targetIndex = indexDirectory(tgt, req);

        List<DiffFinding> findings = new ArrayList<DiffFinding>();

        // 1) tables missing
        for (String tableKey : sourceIndex.ddlsByTableKey.keySet()) {
            if (!targetIndex.ddlsByTableKey.containsKey(tableKey)) {
                SqlExportIndex.TableDdl ddl = sourceIndex.ddlsByTableKey.get(tableKey);
                DiffFinding f = new DiffFinding("TABLE_MISSING", ddl.tableName, ddl.file, ddl.line,
                        "Table exists in SOURCE but not in TARGET.");
                findings.add(f);
            }
        }

        // 2) columns missing
        List<String> commonTables = sourceIndex.ddlsByTableKey.keySet().stream()
                .filter(targetIndex.ddlsByTableKey::containsKey)
                .collect(Collectors.toList());

        List<String> alterStatements = new ArrayList<String>();

        for (String tableKey : commonTables) {
            SqlExportIndex.TableDdl sddl = sourceIndex.ddlsByTableKey.get(tableKey);
            SqlExportIndex.TableDdl tddl = targetIndex.ddlsByTableKey.get(tableKey);

            for (Map.Entry<String, SqlExportIndex.ColumnDef> e : sddl.columnsByKey.entrySet()) {
                String colKey = e.getKey();
                if (!tddl.columnsByKey.containsKey(colKey)) {
                    SqlExportIndex.ColumnDef col = e.getValue();
                    String alter = "ALTER TABLE " + sddl.tableName + " ADD (" + col.columnSqlDef + ");";
                    alterStatements.add(alter);

                    DiffFinding f = new DiffFinding("COLUMN_MISSING", sddl.tableName, colKeyFile(sddl, col), col.line,
                            "Column exists in SOURCE but not in TARGET: " + col.columnName);
                    f.ddl = alter;
                    findings.add(f);
                }
            }
        }

        // 3) missing data rows & updates
        List<String> insertStatements = new ArrayList<String>();
        List<String> updateStatements = new ArrayList<String>();

        for (Map.Entry<String, List<SqlExportIndex.InsertRow>> e : sourceIndex.rowsByTableKey.entrySet()) {
            String tableKey = e.getKey();
            List<SqlExportIndex.InsertRow> srcRows = e.getValue();
            List<SqlExportIndex.InsertRow> tgtRows = targetIndex.rowsByTableKey.get(tableKey);

            List<String> pkCols = sourceIndex.pksByTableKey.get(tableKey);
            if (pkCols == null) pkCols = targetIndex.pksByTableKey.get(tableKey);

            if (pkCols == null || pkCols.isEmpty()) {
                // Warning logic from v0.1: Fall back to exact row match if PK isn't found
                SqlExportIndex.TableDdl ddl = sourceIndex.ddlsByTableKey.get(tableKey);
                String tName = ddl != null ? ddl.tableName : tableKey;
                String fName = ddl != null ? ddl.file : (srcRows.isEmpty() ? "" : srcRows.get(0).file);
                int lNum = ddl != null ? ddl.line : (srcRows.isEmpty() ? -1 : srcRows.get(0).line);

                findings.add(new DiffFinding("WARN_NO_PK", tName, fName, lNum,
                        "No PK detected for table; updates are skipped for this table in v0.1."));

                Set<String> tgtKeys = new HashSet<String>();
                if (tgtRows != null) {
                    for (SqlExportIndex.InsertRow r : tgtRows) tgtKeys.add(r.normalizedKey);
                }
                for (SqlExportIndex.InsertRow r : srcRows) {
                    if (!tgtKeys.contains(r.normalizedKey)) {
                        DiffFinding f = new DiffFinding("ROW_MISSING", r.tableName, r.file, r.line,
                                "Row exists in SOURCE but not in TARGET.");
                        f.insertSql = r.originalSql.endsWith(";") ? r.originalSql : (r.originalSql + ";");
                        findings.add(f);
                        insertStatements.add(f.insertSql);
                    }
                }
            } else {
                // PK Found! Match rows correctly.
                Map<String, SqlExportIndex.InsertRow> tgtByPk = new HashMap<>();
                if (tgtRows != null) {
                    for (SqlExportIndex.InsertRow r : tgtRows) {
                        tgtByPk.put(r.getPkKey(pkCols, req.isCaseInsensitive()), r);
                    }
                }

                for (SqlExportIndex.InsertRow sRow : srcRows) {
                    String pkKey = sRow.getPkKey(pkCols, req.isCaseInsensitive());
                    SqlExportIndex.InsertRow tRow = tgtByPk.get(pkKey);

                    if (tRow == null) {
                        DiffFinding f = new DiffFinding("ROW_MISSING", sRow.tableName, sRow.file, sRow.line,
                                "Row exists in SOURCE but not in TARGET.");
                        f.insertSql = sRow.originalSql.endsWith(";") ? sRow.originalSql : (sRow.originalSql + ";");
                        findings.add(f);
                        insertStatements.add(f.insertSql);
                    } else if (!sRow.normalizedKey.equals(tRow.normalizedKey)) {
                        DiffFinding f = new DiffFinding("ROW_MISMATCH", sRow.tableName, sRow.file, sRow.line,
                                "Row exists in both but values differ (UPDATE needed).");
                        f.insertSql = buildUpdateSql(sRow, pkCols, req.isCaseInsensitive());
                        findings.add(f);
                        updateStatements.add(f.insertSql);
                    }
                }
            }
        }

        long missingTables = findings.stream().filter(x -> "TABLE_MISSING".equals(x.kind)).count();
        long missingColumns = findings.stream().filter(x -> "COLUMN_MISSING".equals(x.kind)).count();
        long missingRows = findings.stream().filter(x -> "ROW_MISSING".equals(x.kind)).count();
        long mismatchedRows = findings.stream().filter(x -> "ROW_MISMATCH".equals(x.kind)).count();
        long warnings = findings.stream().filter(x -> "WARN_NO_PK".equals(x.kind)).count();
        long parseErrors = findings.stream().filter(x -> "PARSE_ERROR".equals(x.kind)).count();

        String report = buildReport(findings, missingTables, missingColumns, missingRows, mismatchedRows, warnings, parseErrors);

        if (req.getOutDir() != null && !req.getOutDir().trim().isEmpty()) {
            Path out = Paths.get(req.getOutDir().trim()).toAbsolutePath().normalize();
            Files.createDirectories(out);

            writeSql(out.resolve("missing_tables.sql"), buildMissingTablesSql(findings));
            writeSql(out.resolve("schema_patch.sql"), buildSchemaPatchSql(alterStatements));
            writeSql(out.resolve("data_patch.sql"), buildDataPatchSql(insertStatements, updateStatements));
        }

        if (req.getJsonOut() != null && !req.getJsonOut().trim().isEmpty()) {
            Path jsonOut = Paths.get(req.getJsonOut().trim()).toAbsolutePath().normalize();
            if (jsonOut.getParent() != null) Files.createDirectories(jsonOut.getParent());
            ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            om.writeValue(jsonOut.toFile(), findings);
        }

        // Now correctly passes all 6 longs!
        return new LookupDifferResult(findings, missingTables, missingColumns, missingRows, mismatchedRows, warnings, parseErrors, report);
    }

    private String colKeyFile(SqlExportIndex.TableDdl ddl, SqlExportIndex.ColumnDef col) {
        return ddl.file != null ? ddl.file : "";
    }

    private void writeSql(Path path, String content) throws Exception {
        Files.write(path, content.getBytes(Charset.forName("UTF-8")));
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
        for (String a : alters) {
            sb.append(a).append("\n");
        }
        return sb.toString();
    }

    private String buildDataPatchSql(List<String> inserts, List<String> updates) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Data patch: insert missing rows (SOURCE -> TARGET)\n");
        for (String ins : inserts) {
            sb.append(ins);
            if (!ins.endsWith("\n")) sb.append("\n");
        }
        if (!updates.isEmpty()) {
            sb.append("\n-- Data patch: update mismatched rows (SOURCE -> TARGET)\n");
            for (String upd : updates) {
                sb.append(upd);
                if (!upd.endsWith("\n")) sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String buildUpdateSql(SqlExportIndex.InsertRow r, List<String> pkCols, boolean ci) {
        StringBuilder sb = new StringBuilder("UPDATE ").append(r.tableName).append(" SET ");
        List<String> sets = new ArrayList<>();
        List<String> wheres = new ArrayList<>();

        for (int i = 0; i < r.columns.size(); i++) {
            String col = r.columns.get(i);
            String val = r.values.get(i);
            boolean isPk = false;
            for (String pk : pkCols) {
                if ((ci && col.equalsIgnoreCase(pk)) || (!ci && col.equals(pk))) {
                    isPk = true; break;
                }
            }
            if (isPk) {
                wheres.add(col + " = " + val);
            } else {
                sets.add(col + " = " + val);
            }
        }

        if (sets.isEmpty()) return "-- UPDATE not needed: " + r.originalSql;

        sb.append(String.join(", ", sets));
        sb.append(" WHERE ").append(String.join(" AND ", wheres)).append(";");
        return sb.toString();
    }

    private String buildReport(List<DiffFinding> findings,
                               long missingTables,
                               long missingColumns,
                               long missingRows,
                               long mismatchedRows,
                               long warnings,
                               long parseErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== lookupdiffer ===\n");
        sb.append("Missing tables: ").append(missingTables).append("\n");
        sb.append("Missing columns: ").append(missingColumns).append("\n");
        sb.append("Missing rows: ").append(missingRows).append("\n");
        sb.append("Mismatched rows (Updates): ").append(mismatchedRows).append("\n");
        sb.append("Warnings (No PK found): ").append(warnings).append("\n");
        sb.append("Parse/analysis errors: ").append(parseErrors).append("\n");

        List<DiffFinding> topTables = findings.stream().filter(f -> "TABLE_MISSING".equals(f.kind)).limit(50).collect(Collectors.toList());
        if (!topTables.isEmpty()) {
            sb.append("\nTables missing in target:\n");
            for (DiffFinding f : topTables) {
                sb.append("- ").append(f.table).append("\n");
            }
        }

        List<DiffFinding> topCols = findings.stream().filter(f -> "COLUMN_MISSING".equals(f.kind)).limit(50).collect(Collectors.toList());
        if (!topCols.isEmpty()) {
            sb.append("\nColumns missing in target (top 50):\n");
            for (DiffFinding f : topCols) {
                sb.append("- ").append(f.table).append(" | ").append(f.message).append("\n");
                if (f.ddl != null) sb.append("  ").append(f.ddl).append("\n");
            }
        }

        List<DiffFinding> topRows = findings.stream().filter(f -> "ROW_MISSING".equals(f.kind) || "ROW_MISMATCH".equals(f.kind)).limit(50).collect(Collectors.toList());
        if (!topRows.isEmpty()) {
            sb.append("\nMissing/Mismatched rows (top 50):\n");
            for (DiffFinding f : topRows) {
                sb.append("- ").append(f.table).append(" @ ").append(f.file).append(":").append(f.line).append("\n");
            }
        }

        return sb.toString();
    }

    private SqlExportIndex indexDirectory(Path dir, LookupDifferRequest req) throws Exception {
        SqlExportIndex idx = new SqlExportIndex();
        List<Path> files = Files.walk(dir)
                .filter(p -> p.toString().toLowerCase().endsWith(".sql"))
                .collect(Collectors.toList());

        for (Path p : files) {
            try {
                List<String> lines = Files.readAllLines(p, Charset.forName("UTF-8"));
                parseFile(p, lines, idx, req);
            } catch (Exception ex) {
                // Ignore gracefully like before
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

            if (stmt.length() == 0) stmtStartLine = i + 1;
            stmt.append(line).append("\n");

            if (trimmed.endsWith(";")) {
                String sql = flattenSqlLine(stmt.toString());
                stmt.setLength(0);

                if (startsWithIgnoreCase(sql, "CREATE TABLE")) {
                    parseCreateTable(sql, filePath, stmtStartLine, idx, ci, tableFilter);
                    continue;
                }
                if (startsWithIgnoreCase(sql, "INSERT INTO")) {
                    parseInsert(sql, filePath, stmtStartLine, idx, ci, tableFilter);
                    continue;
                }
                if (startsWithIgnoreCase(sql, "CREATE UNIQUE INDEX")) {
                    parseCreateIndex(sql, filePath, stmtStartLine, idx, ci, tableFilter);
                    continue;
                }
                if (startsWithIgnoreCase(sql, "ALTER TABLE") && indexOfIgnoreCase(sql, "PRIMARY KEY") > 0) {
                    parseAlterTablePk(sql, filePath, stmtStartLine, idx, ci, tableFilter);
                    continue;
                }
            }
        }
    }

    private void parseCreateTable(String sql, String file, int line, SqlExportIndex idx, boolean ci, String tableFilter) {
        String upper = ci ? sql.toUpperCase() : sql;
        int ct = upper.indexOf("CREATE TABLE");
        if (ct < 0) return;

        String after = sql.substring(ct + "CREATE TABLE".length()).trim();
        int paren = after.indexOf('(');
        if (paren < 0) return;

        String tableName = SqlParsers.cleanIdentifier(after.substring(0, paren));

        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            String tf = tableFilter.trim();
            String tcmp = ci ? tableName.toLowerCase() : tableName;
            String fcmp = ci ? tf.toLowerCase() : tf;
            if (!tcmp.contains(fcmp)) return;
        }

        String colsRaw = firstBalanced(after, '(', ')');
        if (colsRaw == null) return;

        List<String> colLines = splitTopLevelComma(colsRaw);
        Map<String, SqlExportIndex.ColumnDef> colsByKey = new LinkedHashMap<String, SqlExportIndex.ColumnDef>();

        for (String c : colLines) {
            String colDef = c.trim();
            if (colDef.isEmpty()) continue;

            String cUp = ci ? colDef.toUpperCase() : colDef;
            if (cUp.startsWith("CONSTRAINT") || cUp.startsWith("PRIMARY KEY") || cUp.startsWith("UNIQUE") || cUp.startsWith("FOREIGN KEY")) {
                continue;
            }

            String colName = firstToken(colDef);
            if (colName.isEmpty()) continue;

            String colKey = ci ? colName.toUpperCase() : colName;
            colsByKey.put(colKey, new SqlExportIndex.ColumnDef(colName, colDef, line));
        }

        String tableKey = ci ? tableName.toUpperCase() : tableName;
        if (!idx.ddlsByTableKey.containsKey(tableKey)) {
            idx.ddlsByTableKey.put(tableKey, new SqlExportIndex.TableDdl(tableName, file, line, colsByKey));
        }
    }

    private void parseInsert(String sql, String file, int line, SqlExportIndex idx, boolean ci, String tableFilter) {
        String s = sql.trim();
        int intoIdx = indexOfIgnoreCase(s, "INSERT INTO");
        if (intoIdx < 0) return;

        String after = s.substring(intoIdx + "INSERT INTO".length()).trim();
        int parenCols = after.indexOf('(');
        if (parenCols < 0) return;

        String tableName = SqlParsers.cleanIdentifier(after.substring(0, parenCols));

        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            String tf = tableFilter.trim();
            String tcmp = ci ? tableName.toLowerCase() : tableName;
            String fcmp = ci ? tf.toLowerCase() : tf;
            if (!tcmp.contains(fcmp)) return;
        }

        String colsRaw = firstBalanced(after, '(', ')');
        if (colsRaw == null) return;

        int valuesIdx = indexOfIgnoreCase(after, "VALUES");
        if (valuesIdx < 0) return;

        String afterValues = after.substring(valuesIdx + "VALUES".length()).trim();
        String valsRaw = firstBalanced(afterValues, '(', ')');
        if (valsRaw == null) return;

        List<String> cols = splitTopLevelComma(colsRaw).stream().map(SqlParsers::normToken).collect(Collectors.toList());
        List<String> vals = splitTopLevelComma(valsRaw).stream().map(SqlParsers::normToken).collect(Collectors.toList());

        List<String> colsKey = new ArrayList<String>();
        for (String c : cols) colsKey.add(ci ? c.toUpperCase() : c);

        List<String> valsKey = new ArrayList<String>();
        for (String v : vals) valsKey.add(v);

        String tableKey = ci ? tableName.toUpperCase() : tableName;
        String normalizedKey = tableKey + "|" + String.join(",", colsKey) + "|" + String.join(",", valsKey);

        SqlExportIndex.InsertRow row = new SqlExportIndex.InsertRow(
                tableName, file, line, colsKey, valsKey, normalizedKey, sql.endsWith(";") ? sql : (sql + ";")
        );

        List<SqlExportIndex.InsertRow> list = idx.rowsByTableKey.get(tableKey);
        if (list == null) {
            list = new ArrayList<SqlExportIndex.InsertRow>();
            idx.rowsByTableKey.put(tableKey, list);
        }
        list.add(row);
    }

    private void parseCreateIndex(String sql, String file, int line, SqlExportIndex idx, boolean ci, String tableFilter) {
        String upper = ci ? sql.toUpperCase() : sql;
        int onIdx = upper.indexOf(" ON ");
        if (onIdx < 0) return;

        String afterOn = sql.substring(onIdx + 4).trim();
        int paren = afterOn.indexOf('(');
        if (paren < 0) return;

        String tableName = SqlParsers.cleanIdentifier(afterOn.substring(0, paren));

        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            String tcmp = ci ? tableName.toLowerCase() : tableName;
            String fcmp = ci ? tableFilter.trim().toLowerCase() : tableFilter.trim();
            if (!tcmp.contains(fcmp)) return;
        }

        String colsRaw = SqlParsers.firstBalanced(afterOn, '(', ')');
        if (colsRaw == null) return;

        List<String> pkCols = new ArrayList<>();
        for (String c : SqlParsers.splitTopLevelComma(colsRaw)) {
            pkCols.add(SqlParsers.cleanIdentifier(c));
        }

        String tableKey = ci ? tableName.toUpperCase() : tableName;
        if (!pkCols.isEmpty()) {
            idx.pksByTableKey.put(tableKey, pkCols);
        }
    }

    private void parseAlterTablePk(String sql, String file, int line, SqlExportIndex idx, boolean ci, String tableFilter) {
        String s = sql;
        String upper = ci ? s.toUpperCase() : s;
        int alterIdx = upper.indexOf("ALTER TABLE");
        if (alterIdx < 0) return;

        int addIdx = upper.indexOf(" ADD ", alterIdx);
        if (addIdx < 0) return;

        String tableName = SqlParsers.cleanIdentifier(s.substring(alterIdx + "ALTER TABLE".length(), addIdx));

        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            String tcmp = ci ? tableName.toLowerCase() : tableName;
            String fcmp = ci ? tableFilter.trim().toLowerCase() : tableFilter.trim();
            if (!tcmp.contains(fcmp)) return;
        }

        int pkIdx = upper.indexOf("PRIMARY KEY", addIdx);
        if (pkIdx < 0) return;

        String afterPk = s.substring(pkIdx + "PRIMARY KEY".length()).trim();
        String colsRaw = SqlParsers.firstBalanced(afterPk, '(', ')');
        if (colsRaw == null) return;

        List<String> pkCols = new ArrayList<>();
        for (String c : SqlParsers.splitTopLevelComma(colsRaw)) {
            pkCols.add(SqlParsers.cleanIdentifier(c));
        }

        String tableKey = ci ? tableName.toUpperCase() : tableName;
        if (!pkCols.isEmpty()) {
            idx.pksByTableKey.put(tableKey, pkCols);
        }
    }

    private String firstToken(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return "";
        int sp = t.indexOf(' ');
        if (sp < 0) return t;
        return t.substring(0, sp).trim();
    }

    private boolean startsWithIgnoreCase(String s, String prefix) {
        if (s == null || prefix == null) return false;
        if (s.length() < prefix.length()) return false;
        return s.substring(0, prefix.length()).equalsIgnoreCase(prefix);
    }

    private int indexOfIgnoreCase(String s, String token) {
        if (s == null || token == null) return -1;
        return s.toUpperCase().indexOf(token.toUpperCase());
    }
}
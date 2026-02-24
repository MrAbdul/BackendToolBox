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
        Set<String> allSourceTables = new LinkedHashSet<String>();
        allSourceTables.addAll(sourceIndex.ddlsByTableKey.keySet());
        allSourceTables.addAll(sourceIndex.rowsByTableKey.keySet());
        allSourceTables.addAll(sourceIndex.pksByTableKey.keySet());

        for (String tableKey : allSourceTables) {
            if (!targetIndex.ddlsByTableKey.containsKey(tableKey) &&
                !targetIndex.rowsByTableKey.containsKey(tableKey)) {
                
                SqlExportIndex.TableDdl ddl = sourceIndex.ddlsByTableKey.get(tableKey);
                String tName = ddl != null ? ddl.tableName : tableKey;
                String fName = "";
                int lNum = -1;
                if (ddl != null) {
                    fName = ddl.file;
                    lNum = ddl.line;
                } else if (sourceIndex.rowsByTableKey.containsKey(tableKey)) {
                    List<SqlExportIndex.InsertRow> rows = sourceIndex.rowsByTableKey.get(tableKey);
                    if (!rows.isEmpty()) {
                        fName = rows.get(0).file;
                        lNum = rows.get(0).line;
                    }
                }

                DiffFinding f = new DiffFinding("TABLE_MISSING", tName, fName, lNum,
                        "Table exists in SOURCE but not in TARGET.");
                if (ddl != null) f.ddl = ddl.fullSql;
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

        // 2.1) PKs missing
        for (String tableKey : commonTables) {
            if (sourceIndex.pksByTableKey.containsKey(tableKey) && !targetIndex.pksByTableKey.containsKey(tableKey)) {
                String pkSql = sourceIndex.pkDdlsByTableKey.get(tableKey);
                if (pkSql != null) {
                    alterStatements.add(pkSql);
                    DiffFinding f = new DiffFinding("PK_MISSING", tableKey, "", -1,
                            "Primary Key/Unique Index exists in SOURCE but not in TARGET.");
                    f.ddl = pkSql;
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
        long missingPks = findings.stream().filter(x -> "PK_MISSING".equals(x.kind)).count();
        long warnings = findings.stream().filter(x -> "WARN_NO_PK".equals(x.kind)).count();
        long parseErrors = findings.stream().filter(x -> "PARSE_ERROR".equals(x.kind)).count();

        String report = buildReport(findings, missingTables, missingColumns, missingPks, missingRows, mismatchedRows, warnings, parseErrors);
        String htmlReportPath = null;

        if (req.getHtmlOut() != null && !req.getHtmlOut().trim().isEmpty()) {
            Path htmlPath = Paths.get(req.getHtmlOut().trim()).toAbsolutePath().normalize();
            if (htmlPath.getParent() != null) Files.createDirectories(htmlPath.getParent());
            String html = buildHtmlReport(findings, missingTables, missingColumns, missingPks, missingRows, mismatchedRows, warnings, parseErrors);
            Files.write(htmlPath, html.getBytes(Charset.forName("UTF-8")));
            htmlReportPath = htmlPath.toString();
        }

        if (req.getOutDir() != null && !req.getOutDir().trim().isEmpty()) {
            Path out = Paths.get(req.getOutDir().trim()).toAbsolutePath().normalize();
            Files.createDirectories(out);

            Set<String> missingTableSet = findings.stream()
                    .filter(f -> "TABLE_MISSING".equals(f.kind))
                    .map(f -> f.table.toUpperCase())
                    .collect(Collectors.toSet());

            writeSql(out.resolve("missing_tables.sql"), buildMissingTablesSql(findings));
            writeSql(out.resolve("schema_patch.sql"), buildSchemaPatchSql(alterStatements));
            writeSql(out.resolve("insert_patch.sql"), buildInsertPatchSql(findings, missingTableSet));
            writeSql(out.resolve("update_patch.sql"), buildUpdatePatchSql(findings));

            // Split files per table for inserts
            Map<String, List<DiffFinding>> insertsByTable = findings.stream()
                    .filter(f -> "ROW_MISSING".equals(f.kind) && f.insertSql != null)
                    .collect(Collectors.groupingBy(f -> f.table));
            for (Map.Entry<String, List<DiffFinding>> entry : insertsByTable.entrySet()) {
                String tableName = entry.getKey().toLowerCase();
                writeSql(out.resolve(tableName + "_insert.sql"), buildInsertPatchSql(entry.getValue(), missingTableSet));
            }

            // Split files per table for updates
            Map<String, List<DiffFinding>> updatesByTable = findings.stream()
                    .filter(f -> "ROW_MISMATCH".equals(f.kind) && f.insertSql != null)
                    .collect(Collectors.groupingBy(f -> f.table));
            for (Map.Entry<String, List<DiffFinding>> entry : updatesByTable.entrySet()) {
                String tableName = entry.getKey().toLowerCase();
                writeSql(out.resolve(tableName + "_update.sql"), buildUpdatePatchSql(entry.getValue()));
            }

            // Created tables list
            List<String> createdTables = findings.stream()
                    .filter(f -> "TABLE_MISSING".equals(f.kind))
                    .map(f -> f.table)
                    .distinct()
                    .collect(Collectors.toList());
            if (!createdTables.isEmpty()) {
                writeSql(out.resolve("created_tables.txt"), String.join("\n", createdTables) + "\n");
            }
        }

        if (req.getJsonOut() != null && !req.getJsonOut().trim().isEmpty()) {
            Path jsonOut = Paths.get(req.getJsonOut().trim()).toAbsolutePath().normalize();
            if (jsonOut.getParent() != null) Files.createDirectories(jsonOut.getParent());
            ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            om.writeValue(jsonOut.toFile(), findings);
        }

        // Now correctly passes all 10 arguments!
        return new LookupDifferResult(findings, missingTables, missingColumns, missingRows, mismatchedRows, missingPks, warnings, parseErrors, report, htmlReportPath);
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
                if (f.ddl != null) {
                    sb.append(f.ddl).append("\n");
                } else {
                    sb.append("-- ").append(f.table).append("\n");
                }
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

    private String buildInsertPatchSql(List<DiffFinding> findings, Set<String> missingTables) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Data patch: insert missing rows (SOURCE -> TARGET)\n");

        String lastTable = null;
        for (DiffFinding f : findings) {
            if ("ROW_MISSING".equals(f.kind) && f.insertSql != null) {
                if (lastTable == null || !lastTable.equals(f.table)) {
                    lastTable = f.table;
                    sb.append("\n-- Table: ").append(f.table);
                    if (missingTables != null && missingTables.contains(f.table.toUpperCase())) {
                        sb.append(" (MISSING IN TARGET)");
                    }
                    sb.append("\n");
                }
                sb.append(f.insertSql);
                if (!f.insertSql.endsWith("\n")) sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String buildUpdatePatchSql(List<DiffFinding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Data patch: update mismatched rows (SOURCE -> TARGET)\n");

        String lastTable = null;
        for (DiffFinding f : findings) {
            if ("ROW_MISMATCH".equals(f.kind) && f.insertSql != null) {
                if (lastTable == null || !lastTable.equals(f.table)) {
                    lastTable = f.table;
                    sb.append("\n-- Table: ").append(f.table).append("\n");
                }
                sb.append(f.insertSql);
                if (!f.insertSql.endsWith("\n")) sb.append("\n");
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
                               long missingPks,
                               long missingRows,
                               long mismatchedRows,
                               long warnings,
                               long parseErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== lookupdiffer ===\n");
        sb.append("Missing tables: ").append(missingTables).append("\n");
        sb.append("Missing columns: ").append(missingColumns).append("\n");
        sb.append("Missing PKs/Indexes: ").append(missingPks).append("\n");
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

        List<DiffFinding> topPks = findings.stream().filter(f -> "PK_MISSING".equals(f.kind)).limit(50).collect(Collectors.toList());
        if (!topPks.isEmpty()) {
            sb.append("\nPKs/Indexes missing in target:\n");
            for (DiffFinding f : topPks) {
                sb.append("- ").append(f.table).append("\n");
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

    private String buildHtmlReport(List<DiffFinding> findings, long missingTables, long missingColumns, long missingPks, long missingRows, long mismatchedRows, long warnings, long parseErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>LookupDiffer Report</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: sans-serif; margin: 20px; background-color: #f4f4f9; color: #333; }\n");
        sb.append("h1, h2 { color: #2c3e50; }\n");
        sb.append(".summary { display: flex; gap: 20px; margin-bottom: 30px; }\n");
        sb.append(".card { background: white; padding: 15px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); flex: 1; text-align: center; }\n");
        sb.append(".card h3 { margin: 0; font-size: 14px; color: #7f8c8d; text-transform: uppercase; }\n");
        sb.append(".card p { margin: 10px 0 0; font-size: 24px; font-weight: bold; color: #2980b9; }\n");
        sb.append(".card.error p { color: #e74c3c; }\n");
        sb.append(".card.warning p { color: #f39c12; }\n");
        sb.append("table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        sb.append("th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #eee; }\n");
        sb.append("th { background-color: #ecf0f1; color: #2c3e50; font-weight: bold; }\n");
        sb.append("tr:hover { background-color: #f9f9f9; }\n");
        sb.append(".kind { font-weight: bold; padding: 4px 8px; border-radius: 4px; font-size: 12px; }\n");
        sb.append(".kind-TABLE_MISSING { background: #e8f4fd; color: #2980b9; }\n");
        sb.append(".kind-COLUMN_MISSING { background: #fef9e7; color: #f39c12; }\n");
        sb.append(".kind-ROW_MISSING { background: #e9f7ef; color: #27ae60; }\n");
        sb.append(".kind-ROW_MISMATCH { background: #fdf2f2; color: #e74c3c; }\n");
        sb.append(".kind-PK_MISSING { background: #f4ecf7; color: #8e44ad; }\n");
        sb.append(".kind-PARSE_ERROR { background: #fdedec; color: #c0392b; }\n");
        sb.append(".kind-WARN_NO_PK { background: #fff5e6; color: #d35400; }\n");
        sb.append("pre { background: #2c3e50; color: #ecf0f1; padding: 10px; border-radius: 4px; overflow-x: auto; font-size: 13px; margin: 5px 0; white-space: pre-wrap; word-break: break-all; }\n");
        sb.append("input[type=\"text\"] { width: 100%; padding: 10px; margin-bottom: 20px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; }\n");
        sb.append("</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<h1>LookupDiffer Report</h1>\n");

        sb.append("<div class=\"summary\">\n");
        sb.append("<div class=\"card\"><h3>Missing Tables</h3><p>").append(missingTables).append("</p></div>\n");
        sb.append("<div class=\"card\"><h3>Missing Columns</h3><p>").append(missingColumns).append("</p></div>\n");
        sb.append("<div class=\"card\"><h3>Missing PKs</h3><p>").append(missingPks).append("</p></div>\n");
        sb.append("<div class=\"card\"><h3>Missing Rows</h3><p>").append(missingRows).append("</p></div>\n");
        sb.append("<div class=\"card\"><h3>Mismatched Rows</h3><p>").append(mismatchedRows).append("</p></div>\n");
        sb.append("<div class=\"card warning\"><h3>Warnings</h3><p>").append(warnings).append("</p></div>\n");
        sb.append("<div class=\"card error\"><h3>Parse Errors</h3><p>").append(parseErrors).append("</p></div>\n");
        sb.append("</div>\n");

        sb.append("<h2>Findings</h2>\n");
        sb.append("<input type=\"text\" id=\"filter\" placeholder=\"Filter by table or message...\" onkeyup=\"filterTable()\">\n");
        sb.append("<table id=\"findingsTable\">\n");
        sb.append("<thead><tr><th>Kind</th><th>Table</th><th>File:Line</th><th>Message</th></tr></thead>\n");
        sb.append("<tbody>\n");
        for (DiffFinding f : findings) {
            sb.append("<tr>\n");
            sb.append("<td><span class=\"kind kind-").append(f.kind).append("\">").append(f.kind).append("</span></td>\n");
            sb.append("<td>").append(f.table).append("</td>\n");
            sb.append("<td>").append(f.file).append(":").append(f.line).append("</td>\n");
            sb.append("<td>").append(f.message);
            if (f.ddl != null && !f.ddl.isEmpty()) {
                sb.append("<pre>").append(f.ddl.replace("<", "&lt;").replace(">", "&gt;")).append("</pre>");
            }
            if (f.insertSql != null && !f.insertSql.isEmpty()) {
                sb.append("<pre>").append(f.insertSql.replace("<", "&lt;").replace(">", "&gt;")).append("</pre>");
            }
            sb.append("</td>\n");
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n");
        sb.append("</table>\n");

        sb.append("<script>\n");
        sb.append("function filterTable() {\n");
        sb.append("  var input, filter, table, tr, td, i, j, txtValue, found;\n");
        sb.append("  input = document.getElementById(\"filter\");\n");
        sb.append("  filter = input.value.toUpperCase();\n");
        sb.append("  table = document.getElementById(\"findingsTable\");\n");
        sb.append("  tr = table.getElementsByTagName(\"tr\");\n");
        sb.append("  for (i = 1; i < tr.length; i++) {\n");
        sb.append("    found = false;\n");
        sb.append("    td = tr[i].getElementsByTagName(\"td\");\n");
        sb.append("    for (j = 0; j < td.length; j++) {\n");
        sb.append("      if (td[j]) {\n");
        sb.append("        txtValue = td[j].textContent || td[j].innerText;\n");
        sb.append("        if (txtValue.toUpperCase().indexOf(filter) > -1) {\n");
        sb.append("          found = true;\n");
        sb.append("          break;\n");
        sb.append("        }\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("    tr[i].style.display = found ? \"\" : \"none\";\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("</script>\n");

        sb.append("</body>\n</html>");
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

        String fullSql = String.join("\n", lines);
        processStmt(fullSql, filePath, 1, idx, ci, tableFilter);
    }

    private void processStmt(String rawSql, String filePath, int stmtStartLine, SqlExportIndex idx, boolean ci, String tableFilter) {
        List<String> statements = splitStatements(rawSql);
        int currentLine = stmtStartLine;
        for (String raw : statements) {
            String sql = flattenSqlLine(raw);
            if (sql.isEmpty()) {
                currentLine += countNewlines(raw);
                continue;
            }

            if (startsWithIgnoreCase(sql, "CREATE TABLE")) {
                parseCreateTable(sql, filePath, currentLine, idx, ci, tableFilter);
            } else if (startsWithIgnoreCase(sql, "INSERT INTO")) {
                parseInsert(sql, filePath, currentLine, idx, ci, tableFilter);
            } else if (startsWithIgnoreCase(sql, "CREATE UNIQUE INDEX")) {
                parseCreateIndex(sql, filePath, currentLine, idx, ci, tableFilter);
            } else if (startsWithIgnoreCase(sql, "ALTER TABLE") && indexOfIgnoreCase(sql, "PRIMARY KEY") > 0) {
                parseAlterTablePk(sql, filePath, currentLine, idx, ci, tableFilter);
            } else if (startsWithIgnoreCase(sql, "ALTER TABLE") && indexOfIgnoreCase(sql, " ADD ") > 0) {
                parseAlterTableAdd(sql, filePath, currentLine, idx, ci, tableFilter);
            }
            
            currentLine += countNewlines(raw);
        }
    }

    private int countNewlines(String s) {
        if (s == null) return 0;
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') count++;
        }
        return count;
    }

    private void parseAlterTableAdd(String sql, String file, int line, SqlExportIndex idx, boolean ci, String tableFilter) {
        String upper = ci ? sql.toUpperCase() : sql;
        int alterIdx = upper.indexOf("ALTER TABLE");
        int addIdx = upper.indexOf(" ADD ");
        if (alterIdx < 0 || addIdx < 0 || addIdx < alterIdx) return;

        String tableName = SqlParsers.cleanIdentifier(sql.substring(alterIdx + "ALTER TABLE".length(), addIdx));
        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            String tcmp = ci ? tableName.toLowerCase() : tableName;
            String fcmp = ci ? tableFilter.trim().toLowerCase() : tableFilter.trim();
            if (!tcmp.contains(fcmp)) return;
        }

        String afterAdd = sql.substring(addIdx + " ADD ".length()).trim();
        // It could be ADD C1 NUMBER or ADD (C1 NUMBER, C2 VARCHAR2)
        String colsRaw;
        if (afterAdd.startsWith("(")) {
            colsRaw = firstBalanced(afterAdd, '(', ')');
        } else {
            // Single column, but might have other things after it like constraints or just the end of statement
            colsRaw = afterAdd;
            if (colsRaw.endsWith(";")) colsRaw = colsRaw.substring(0, colsRaw.length() - 1);
        }

        if (colsRaw == null) return;

        List<String> colLines = splitTopLevelComma(colsRaw);
        String tableKey = ci ? tableName.toUpperCase() : tableName;

        // We need the TableDdl to add columns to. If it doesn't exist, we might want to create a stub?
        // Actually, if we saw an ALTER TABLE but no CREATE TABLE, we still want to know the columns.
        SqlExportIndex.TableDdl ddl = idx.ddlsByTableKey.get(tableKey);
        if (ddl == null) {
            ddl = new SqlExportIndex.TableDdl(tableName, file, line, new LinkedHashMap<String, SqlExportIndex.ColumnDef>());
            idx.ddlsByTableKey.put(tableKey, ddl);
        }

        for (String c : colLines) {
            String colDef = c.trim();
            if (colDef.isEmpty()) continue;

            String cUp = ci ? colDef.toUpperCase() : colDef;
            if (cUp.startsWith("CONSTRAINT") || cUp.startsWith("PRIMARY KEY") || cUp.startsWith("UNIQUE") || cUp.startsWith("FOREIGN KEY")) {
                continue;
            }

            String colName = SqlParsers.cleanIdentifier(firstToken(colDef));
            if (colName.isEmpty()) continue;

            String colKey = ci ? colName.toUpperCase() : colName;
            if (!ddl.columnsByKey.containsKey(colKey)) {
                ddl.columnsByKey.put(colKey, new SqlExportIndex.ColumnDef(colName, colDef, line));
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

            String colName = SqlParsers.cleanIdentifier(firstToken(colDef));
            if (colName.isEmpty()) continue;

            String colKey = ci ? colName.toUpperCase() : colName;
            colsByKey.put(colKey, new SqlExportIndex.ColumnDef(colName, colDef, line));
        }

        String tableKey = ci ? tableName.toUpperCase() : tableName;
        SqlExportIndex.TableDdl ddl = idx.ddlsByTableKey.get(tableKey);
        if (ddl == null) {
            ddl = new SqlExportIndex.TableDdl(tableName, file, line, colsByKey);
            ddl.fullSql = sql.endsWith(";") ? sql : (sql + ";");
            idx.ddlsByTableKey.put(tableKey, ddl);
        } else {
            // Merge with existing stub or another DDL
            if (ddl.fullSql == null) {
                // It was a stub. Create a new one to properly set file/line/fullSql if desired,
                // or just update fullSql. Since TableDdl fields are final, we replace it.
                SqlExportIndex.TableDdl newDdl = new SqlExportIndex.TableDdl(tableName, file, line, colsByKey);
                newDdl.fullSql = sql.endsWith(";") ? sql : (sql + ";");
                // Carry over columns from the stub (e.g. from ALTER TABLE ADD)
                for (Map.Entry<String, SqlExportIndex.ColumnDef> entry : ddl.columnsByKey.entrySet()) {
                    if (!newDdl.columnsByKey.containsKey(entry.getKey())) {
                        newDdl.columnsByKey.put(entry.getKey(), entry.getValue());
                    }
                }
                idx.ddlsByTableKey.put(tableKey, newDdl);
            } else {
                // Already have a full DDL, just merge extra columns
                for (Map.Entry<String, SqlExportIndex.ColumnDef> entry : colsByKey.entrySet()) {
                    if (!ddl.columnsByKey.containsKey(entry.getKey())) {
                        ddl.columnsByKey.put(entry.getKey(), entry.getValue());
                    }
                }
            }
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
        for (String c : cols) {
            String clean = SqlParsers.cleanIdentifier(c);
            colsKey.add(ci ? clean.toUpperCase() : clean);
        }

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
            idx.pkDdlsByTableKey.put(tableKey, sql.endsWith(";") ? sql : (sql + ";"));
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
            idx.pkDdlsByTableKey.put(tableKey, sql.endsWith(";") ? sql : (sql + ";"));
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
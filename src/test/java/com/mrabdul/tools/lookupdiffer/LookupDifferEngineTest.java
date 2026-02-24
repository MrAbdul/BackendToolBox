package com.mrabdul.tools.lookupdiffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

class LookupDifferEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void testMissingTableAndColumn() throws Exception {
        Path source = tempDir.resolve("source");
        Path target = tempDir.resolve("target");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        Files.write(source.resolve("schema.sql"), (
            "CREATE TABLE T1 (C1 NUMBER, C2 VARCHAR2(100));\n" +
            "CREATE TABLE T2 (C1 NUMBER);\n"
        ).getBytes(StandardCharsets.UTF_8));

        Files.write(target.resolve("schema.sql"), (
            "CREATE TABLE T1 (C1 NUMBER);\n"
        ).getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null
        );

        LookupDifferResult result = engine.run(req);

        // Verify findings
        assertTrue(result.getFindings().stream().anyMatch(f -> "TABLE_MISSING".equals(f.kind) && "T2".equalsIgnoreCase(f.table)));
        assertTrue(result.getFindings().stream().anyMatch(f -> "COLUMN_MISSING".equals(f.kind) && "T1".equalsIgnoreCase(f.table) && f.message.contains("C2")));

        // Verify missing tables SQL
        Path missingTablesSql = outDir.resolve("missing_tables.sql");
        assertTrue(Files.exists(missingTablesSql), "missing_tables.sql should exist");
        String missingContent = new String(Files.readAllBytes(missingTablesSql), StandardCharsets.UTF_8);
        assertTrue(missingContent.contains("CREATE TABLE T2 (C1 NUMBER);"), "missing_tables.sql should contain CREATE TABLE for T2");

        // Verify schema patch
        Path schemaPatch = outDir.resolve("schema_patch.sql");
        assertTrue(Files.exists(schemaPatch), "schema_patch.sql should exist");
        String content = new String(Files.readAllBytes(schemaPatch), StandardCharsets.UTF_8);
        assertTrue(content.contains("ALTER TABLE T1 ADD (C2 VARCHAR2(100));"), "Schema patch should contain ALTER TABLE for T1.C2");
    }

    @Test
    void testMissingTableWithData() throws Exception {
        Path source = tempDir.resolve("source_data");
        Path target = tempDir.resolve("target_data");
        Path outDir = tempDir.resolve("out_data");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        Files.write(source.resolve("schema.sql"), (
                "CREATE TABLE T_NEW (ID NUMBER, VAL VARCHAR2(100));\n" +
                "ALTER TABLE T_NEW ADD CONSTRAINT PK_T_NEW PRIMARY KEY (ID);\n" +
                "INSERT INTO T_NEW (ID, VAL) VALUES (1, 'Hello');\n"
        ).getBytes(StandardCharsets.UTF_8));

        Files.write(target.resolve("schema.sql"), (
                "CREATE TABLE T_OLD (ID NUMBER);\n"
        ).getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null
        );

        LookupDifferResult result = engine.run(req);

        // Verify finding
        boolean tNewMissing = result.getFindings().stream().anyMatch(f -> "TABLE_MISSING".equals(f.kind) && "T_NEW".equalsIgnoreCase(f.table));
        assertTrue(tNewMissing, "T_NEW should be detected as missing");

        // Verify missing rows
        boolean rowMissing = result.getFindings().stream().anyMatch(f -> "ROW_MISSING".equals(f.kind) && "T_NEW".equalsIgnoreCase(f.table));
        assertTrue(rowMissing, "Row for T_NEW should be detected as missing");

        // Verify data patch
        Path dataPatch = outDir.resolve("insert_patch.sql");
        assertTrue(Files.exists(dataPatch), "insert_patch.sql should exist");
        String dataContent = new String(Files.readAllBytes(dataPatch), StandardCharsets.UTF_8);
        assertTrue(dataContent.contains("INSERT INTO T_NEW"), "Data patch should contain INSERT for T_NEW");
        assertFalse(dataContent.contains(";;"), "Data patch should not contain double semicolons");
    }

    @Test
    void testAlterTableAddDetection() throws Exception {
        Path source = tempDir.resolve("src_alter");
        Path target = tempDir.resolve("tgt_alter");
        Path outDir = tempDir.resolve("out_alter");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        Files.write(source.resolve("schema.sql"), (
                "CREATE TABLE T1 (C1 NUMBER);\n" +
                "ALTER TABLE T1 ADD C2 VARCHAR2(100);\n" +
                "ALTER TABLE T1 ADD (C3 NUMBER, C4 DATE);\n"
        ).getBytes(StandardCharsets.UTF_8));

        Files.write(target.resolve("schema.sql"), (
                "CREATE TABLE T1 (C1 NUMBER);\n"
        ).getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null
        );

        LookupDifferResult result = engine.run(req);

        // Verify findings for C2, C3, C4
        assertTrue(result.getFindings().stream().anyMatch(f -> "COLUMN_MISSING".equals(f.kind) && f.message.contains("C2")));
        assertTrue(result.getFindings().stream().anyMatch(f -> "COLUMN_MISSING".equals(f.kind) && f.message.contains("C3")));
        assertTrue(result.getFindings().stream().anyMatch(f -> "COLUMN_MISSING".equals(f.kind) && f.message.contains("C4")));

        // Verify schema patch
        Path schemaPatch = outDir.resolve("schema_patch.sql");
        String content = new String(Files.readAllBytes(schemaPatch), StandardCharsets.UTF_8);
        assertTrue(content.contains("ALTER TABLE T1 ADD (C2 VARCHAR2(100));"));
        assertTrue(content.contains("ALTER TABLE T1 ADD (C3 NUMBER);"));
        assertTrue(content.contains("ALTER TABLE T1 ADD (C4 DATE);"));
    }

    @Test
    void testMultiFileWithStubMerge() throws Exception {
        Path source = tempDir.resolve("src_multi");
        Path target = tempDir.resolve("tgt_multi");
        Path outDir = tempDir.resolve("out_multi");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        // Order matters for the bug: ALTER TABLE first, then CREATE TABLE.
        // Files.walk usually walks alphabetically.
        Files.write(source.resolve("A_ALTER.sql"), (
                "ALTER TABLE T1 ADD (C2 NUMBER);\n"
        ).getBytes(StandardCharsets.UTF_8));

        Files.write(source.resolve("B_CREATE.sql"), (
                "CREATE TABLE T1 (C1 NUMBER);\n"
        ).getBytes(StandardCharsets.UTF_8));

        Files.write(source.resolve("C_PK.sql"), (
                "ALTER TABLE T1 ADD CONSTRAINT T1_PK PRIMARY KEY (C1);\n"
        ).getBytes(StandardCharsets.UTF_8));

        Files.write(source.resolve("D_DATA.sql"), (
                "INSERT INTO T1 (C1, C2) VALUES (1, 100);\n"
        ).getBytes(StandardCharsets.UTF_8));

        // Target only has C1 but no PK or C2 or rows
        Files.write(target.resolve("T1.sql"), (
                "CREATE TABLE T1 (C1 NUMBER);\n"
        ).getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null
        );

        LookupDifferResult result = engine.run(req);

        // Verify C2 is detected as missing
        assertTrue(result.getFindings().stream().anyMatch(f -> "COLUMN_MISSING".equals(f.kind) && f.table.equalsIgnoreCase("T1") && f.message.contains("C2")), "C2 should be detected as missing");

        // Verify row is detected as missing
        assertTrue(result.getFindings().stream().anyMatch(f -> "ROW_MISSING".equals(f.kind) && f.table.equalsIgnoreCase("T1")), "Row should be detected as missing");

        // Verify patches
        String schemaPatch = new String(Files.readAllBytes(outDir.resolve("schema_patch.sql")), StandardCharsets.UTF_8);
        assertTrue(schemaPatch.contains("ALTER TABLE T1 ADD (C2 NUMBER);"), "Schema patch should contain C2");

        String dataPatch = new String(Files.readAllBytes(outDir.resolve("insert_patch.sql")), StandardCharsets.UTF_8);
        assertTrue(dataPatch.contains("INSERT INTO T1"), "Data patch should contain INSERT");
    }

    @Test
    void testQuotedIdentifiers() throws Exception {
        Path source = tempDir.resolve("src_quote");
        Path target = tempDir.resolve("tgt_quote");
        Path outDir = tempDir.resolve("out_quote");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        Files.write(source.resolve("schema.sql"), (
                "CREATE TABLE \"T1\" (\"C1\" NUMBER);\n" +
                "INSERT INTO \"T1\" (\"C1\") VALUES (1);\n"
        ).getBytes(StandardCharsets.UTF_8));

        // Target has it without quotes
        Files.write(target.resolve("schema.sql"), (
                "CREATE TABLE T1 (C1 NUMBER);\n" +
                "INSERT INTO T1 (C1) VALUES (1);\n"
        ).getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null
        );

        LookupDifferResult result = engine.run(req);

        // Should be no diffs if quoted and unquoted match
        assertEquals(0, result.getFindings().stream().filter(f -> !"WARN_NO_PK".equals(f.kind)).count(), "Should be no findings (except maybe PK warning)");
    }
    @Test
    void testOracleStyleExport() throws Exception {
        Path source = tempDir.resolve("src_oracle");
        Path target = tempDir.resolve("tgt_oracle");
        Path outDir = tempDir.resolve("out_oracle");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        Files.write(source.resolve("T1.sql"), (
            "--------------------------------------------------------\n" +
            "--  DDL for Table T1\n" +
            "--------------------------------------------------------\n" +
            "\n" +
            "  CREATE TABLE \"T1\" \n" +
            "   (	\"C1\" NUMBER\n" +
            "   );\n" +
            "/\n" +
            "REM INSERTING into T1\n" +
            "SET DEFINE OFF;\n" +
            "Insert into T1 (C1) values (1);\n" +
            "Insert into T1 (C1) values (2);\n"
        ).getBytes(StandardCharsets.UTF_8));

        // Target is empty
        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null
        );

        LookupDifferResult result = engine.run(req);

        // Verify that T1 is found and rows are found
        assertTrue(result.getMissingTables() > 0, "Should detect missing table T1");
        assertTrue(result.getMissingRows() >= 2, "Should detect missing rows for T1");
    }
}

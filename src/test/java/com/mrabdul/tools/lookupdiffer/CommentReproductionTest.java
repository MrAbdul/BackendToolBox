package com.mrabdul.tools.lookupdiffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

class CommentReproductionTest {

    @TempDir
    Path tempDir;

    @Test
    void testMissingTableWithLeadingComments() throws Exception {
        Path source = tempDir.resolve("source");
        Path target = tempDir.resolve("target");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        // Typical Oracle export style with leading comments
        String sql = "--------------------------------------------------------\n" +
                     "--  DDL for Table T1\n" +
                     "--------------------------------------------------------\n" +
                     "  CREATE TABLE \"BBYNIB\".\"T1\" \n" +
                     "   (	\"C1\" NUMBER, \n" +
                     "	\"C2\" VARCHAR2(100)\n" +
                     "   );\n";

        Files.write(source.resolve("T1.sql"), sql.getBytes(StandardCharsets.UTF_8));
        // Target is empty

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null,
                null
        );

        LookupDifferResult result = engine.run(req);

        // Verify findings
        boolean t1Missing = result.getFindings().stream()
                .anyMatch(f -> "TABLE_MISSING".equals(f.kind) && "T1".equalsIgnoreCase(f.table));
        
        assertTrue(t1Missing, "T1 should be detected as missing even with leading comments");
    }

    @Test
    void testMissingSemicolonAtEOF() throws Exception {
        Path source = tempDir.resolve("source_no_semi");
        Path target = tempDir.resolve("target_no_semi");
        Path outDir = tempDir.resolve("out_no_semi");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        // No semicolon at the end of the file
        String sql = "CREATE TABLE T2 (C1 NUMBER)";

        Files.write(source.resolve("T2.sql"), sql.getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null,
                null
        );

        LookupDifferResult result = engine.run(req);

        // Verify findings
        boolean t2Missing = result.getFindings().stream()
                .anyMatch(f -> "TABLE_MISSING".equals(f.kind) && "T2".equalsIgnoreCase(f.table));

        assertTrue(t2Missing, "T2 should be detected as missing even if the file lacks a trailing semicolon");
    }

    @Test
    void testAlterTableWithComments() throws Exception {
        Path source = tempDir.resolve("src_alter_comm");
        Path target = tempDir.resolve("tgt_alter_comm");
        Path outDir = tempDir.resolve("out_alter_comm");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        String sql = "CREATE TABLE T3 (C1 NUMBER);\n" +
                     "ALTER TABLE T3 ADD (\n" +
                     "  -- some comment\n" +
                     "  C2 VARCHAR2(10),\n" +
                     "  C3 NUMBER /* multi line \n" +
                     "               comment */\n" +
                     ");";

        Files.write(source.resolve("T3.sql"), sql.getBytes(StandardCharsets.UTF_8));
        Files.write(target.resolve("T3.sql"), "CREATE TABLE T3 (C1 NUMBER);".getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null,
                null
        );

        LookupDifferResult result = engine.run(req);

        // Verify findings for C2 and C3
        assertTrue(result.getFindings().stream().anyMatch(f -> "COLUMN_MISSING".equals(f.kind) && f.message.contains("C2")));
        assertTrue(result.getFindings().stream().anyMatch(f -> "COLUMN_MISSING".equals(f.kind) && f.message.contains("C3")));
    }

    @Test
    void testInsertWithCommentsAndNewlines() throws Exception {
        Path source = tempDir.resolve("src_ins_comm");
        Path target = tempDir.resolve("tgt_ins_comm");
        Path outDir = tempDir.resolve("out_ins_comm");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        String sql = "CREATE TABLE T4 (ID NUMBER PRIMARY KEY, VAL VARCHAR2(100));\n" +
                     "INSERT INTO T4 (ID, VAL) VALUES (1, 'Line1\nLine2'); -- comment at end\n" +
                     "INSERT INTO T4 (ID, VAL) VALUES (2, 'Val with -- comment-like string');\n";

        Files.write(source.resolve("T4.sql"), sql.getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null,
                null
        );

        LookupDifferResult result = engine.run(req);

        // T4 missing in target
        assertTrue(result.getFindings().stream().anyMatch(f -> "TABLE_MISSING".equals(f.kind) && "T4".equalsIgnoreCase(f.table)));
        
        // Rows missing in target
        long missingRows = result.getFindings().stream().filter(f -> "ROW_MISSING".equals(f.kind) && "T4".equalsIgnoreCase(f.table)).count();
        assertEquals(2, missingRows);

        // Verify patch content (check if newline was preserved)
        Path insertPatch = outDir.resolve("insert_patch.sql");
        String content = new String(Files.readAllBytes(insertPatch), StandardCharsets.UTF_8);
        
        // We should fix this to preserve newlines in data.
        assertTrue(content.contains("Line1\nLine2") || content.contains("Line1\r\nLine2"), "Newline in data should be preserved");
        assertTrue(content.contains("Val with -- comment-like string"), "Comment-like string was preserved");
    }

    @Test
    void testMultilineStringWithSemicolon() throws Exception {
        Path source = tempDir.resolve("src_semi_str");
        Path target = tempDir.resolve("tgt_semi_str");
        Path outDir = tempDir.resolve("out_semi_str");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        String sql = "CREATE TABLE T5 (VAL VARCHAR2(100));\n" +
                     "INSERT INTO T5 (VAL) VALUES ('Statement 1;\nStatement 2');\n";

        Files.write(source.resolve("T5.sql"), sql.getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null,
                null
        );

        LookupDifferResult result = engine.run(req);

        // Verify T5 missing
        assertTrue(result.getFindings().stream().anyMatch(f -> "TABLE_MISSING".equals(f.kind) && "T5".equalsIgnoreCase(f.table)));

        // Verify Row missing
        long missingRows = result.getFindings().stream().filter(f -> "ROW_MISSING".equals(f.kind) && "T5".equalsIgnoreCase(f.table)).count();
        assertEquals(1, missingRows, "Should detect one row even with semicolon in string");

        Path insertPatch = outDir.resolve("t5_insert.sql");
        String content = new String(Files.readAllBytes(insertPatch), StandardCharsets.UTF_8);
        assertTrue(content.contains("Statement 1;"), "Semicolon in string should be preserved");
        assertTrue(content.contains("Statement 2"), "Content after semicolon in string should be preserved");
    }
}

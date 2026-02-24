package com.mrabdul.tools.lookupdiffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

class LookupDifferSplitTest {

    @TempDir
    Path tempDir;

    @Test
    void testSplitFilesAndCreatedTablesList() throws Exception {
        Path source = tempDir.resolve("source");
        Path target = tempDir.resolve("target");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        // T1: Missing table
        String t1Sql = "CREATE TABLE T1 (C1 NUMBER, C2 VARCHAR2(100));\n" +
                       "ALTER TABLE T1 ADD PRIMARY KEY (C1);\n" +
                       "INSERT INTO T1 (C1, C2) VALUES (1, 'Val1');\n";
        Files.write(source.resolve("T1.sql"), t1Sql.getBytes(StandardCharsets.UTF_8));

        // T2: Existing table, missing row and mismatched row
        String t2SourceSql = "CREATE TABLE T2 (ID NUMBER, VAL VARCHAR2(100));\n" +
                             "ALTER TABLE T2 ADD PRIMARY KEY (ID);\n" +
                             "INSERT INTO T2 (ID, VAL) VALUES (1, 'Old');\n" +
                             "INSERT INTO T2 (ID, VAL) VALUES (2, 'New');\n";
        String t2TargetSql = "CREATE TABLE T2 (ID NUMBER, VAL VARCHAR2(100));\n" +
                             "ALTER TABLE T2 ADD PRIMARY KEY (ID);\n" +
                             "INSERT INTO T2 (ID, VAL) VALUES (1, 'Changed');\n";
        
        Files.write(source.resolve("T2.sql"), t2SourceSql.getBytes(StandardCharsets.UTF_8));
        Files.write(target.resolve("T2.sql"), t2TargetSql.getBytes(StandardCharsets.UTF_8));

        LookupDifferEngine engine = new LookupDifferEngine();
        LookupDifferRequest req = new LookupDifferRequest(
                source.toString(),
                target.toString(),
                true,
                null,
                outDir.toString(),
                null
        );

        engine.run(req);

        // Verify created_tables.txt
        Path createdTablesFile = outDir.resolve("created_tables.txt");
        assertTrue(Files.exists(createdTablesFile), "created_tables.txt should exist");
        List<String> createdTables = Files.readAllLines(createdTablesFile);
        assertTrue(createdTables.contains("T1"), "T1 should be in created_tables.txt");
        assertFalse(createdTables.contains("T2"), "T2 should NOT be in created_tables.txt");

        // Verify T1 split files
        Path t1Insert = outDir.resolve("t1_insert.sql");
        assertTrue(Files.exists(t1Insert), "t1_insert.sql should exist");
        String t1InsertContent = new String(Files.readAllBytes(t1Insert), StandardCharsets.UTF_8);
        assertTrue(t1InsertContent.contains("INSERT INTO T1"), "t1_insert.sql should contain insert for T1");

        // Verify T2 split files
        Path t2Insert = outDir.resolve("t2_insert.sql");
        assertTrue(Files.exists(t2Insert), "t2_insert.sql should exist");
        String t2InsertContent = new String(Files.readAllBytes(t2Insert), StandardCharsets.UTF_8);
        assertTrue(t2InsertContent.contains("INSERT INTO T2 (ID, VAL) VALUES (2, 'New')"), "t2_insert.sql should contain missing row for T2");

        Path t2Update = outDir.resolve("t2_update.sql");
        assertTrue(Files.exists(t2Update), "t2_update.sql should exist");
        String t2UpdateContent = new String(Files.readAllBytes(t2Update), StandardCharsets.UTF_8);
        assertTrue(t2UpdateContent.contains("UPDATE T2 SET VAL = 'Old' WHERE ID = 1"), "t2_update.sql should contain update for T2");
    }
}

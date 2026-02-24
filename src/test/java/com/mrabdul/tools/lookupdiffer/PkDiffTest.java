package com.mrabdul.tools.lookupdiffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

class PkDiffTest {

    @TempDir
    Path tempDir;

    @Test
    void testMissingPkDetection() throws Exception {
        Path source = tempDir.resolve("source");
        Path target = tempDir.resolve("target");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        // Source has T1 with PK
        Files.write(source.resolve("T1.sql"), "CREATE TABLE T1 (C1 NUMBER, C2 NUMBER);".getBytes(StandardCharsets.UTF_8));
        Files.write(source.resolve("T1_PK.sql"), "ALTER TABLE T1 ADD PRIMARY KEY (C1);".getBytes(StandardCharsets.UTF_8));

        // Target has T1 but NO PK
        Files.write(target.resolve("T1.sql"), "CREATE TABLE T1 (C1 NUMBER, C2 NUMBER);".getBytes(StandardCharsets.UTF_8));

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

        assertEquals(1, result.getMissingPks(), "Should detect 1 missing PK");
        
        Path schemaPatch = outDir.resolve("schema_patch.sql");
        String content = new String(Files.readAllBytes(schemaPatch), StandardCharsets.UTF_8);
        assertTrue(content.contains("ALTER TABLE T1 ADD PRIMARY KEY (C1);"), "Schema patch should contain missing PK DDL");
    }

    @Test
    void testMissingUniqueIndexAsPk() throws Exception {
        Path source = tempDir.resolve("src_idx");
        Path target = tempDir.resolve("tgt_idx");
        Path outDir = tempDir.resolve("out_idx");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.createDirectories(outDir);

        // Source has T2 with Unique Index used as PK
        Files.write(source.resolve("T2.sql"), "CREATE TABLE T2 (C1 NUMBER);".getBytes(StandardCharsets.UTF_8));
        Files.write(source.resolve("T2_IDX.sql"), "CREATE UNIQUE INDEX T2_PK_IDX ON T2 (C1);".getBytes(StandardCharsets.UTF_8));

        // Target has T2 but NO Index
        Files.write(target.resolve("T2.sql"), "CREATE TABLE T2 (C1 NUMBER);".getBytes(StandardCharsets.UTF_8));

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

        assertEquals(1, result.getMissingPks(), "Should detect 1 missing Unique Index");
        
        Path schemaPatch = outDir.resolve("schema_patch.sql");
        String content = new String(Files.readAllBytes(schemaPatch), StandardCharsets.UTF_8);
        assertTrue(content.contains("CREATE UNIQUE INDEX T2_PK_IDX ON T2 (C1);"), "Schema patch should contain missing Unique Index DDL");
    }
}

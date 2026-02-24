package com.mrabdul.tools.lookupdiffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

class HtmlOutputStructureTest {

    @TempDir
    Path tempDir;

    @Test
    void testHtmlOutputStructure() throws Exception {
        Path source = tempDir.resolve("source");
        Path target = tempDir.resolve("target");
        Path htmlOutDir = tempDir.resolve("html_report");
        Files.createDirectories(source);
        Files.createDirectories(target);

        // Create some sample data to generate findings
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
                null,
                null,
                htmlOutDir.toString()
        );

        LookupDifferResult result = engine.run(req);

        // Currently, it generates a single file at the path provided.
        // After refactoring, it should be a directory with multiple files.
        // For now, let's verify it creates something.
        assertTrue(Files.exists(htmlOutDir), "HTML output path should exist");
        // Current behavior: htmlOutDir is treated as a file path if it doesn't end with /
        // or just as the file name if it's the full path.
        // Let's see what happens.
    }
}

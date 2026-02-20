package com.mrabdul.tools.dbanalyzer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DbAnalyzerCliCommandTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUpStreams() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void helpFlagPrintsUsageAndReturnsZero() throws Exception {
        CapturingService svc = new CapturingService();
        DbAnalyzerCliCommand cmd = new DbAnalyzerCliCommand(svc);

        int code = cmd.run(new String[]{"--help"});

        assertEquals(0, code, "help should return 0");
        String out = outContent.toString();
        assertTrue(out.contains("Command: dbanalyzer"));
        assertTrue(out.contains("Usage:"));
        assertNull(svc.lastReq, "Service should not be called when showing help");
        assertEquals("", errContent.toString(), "No errors expected");
    }

    @Test
    void missingRequiredArgsReturns2AndPrintsErrorAndHelp() throws Exception {
        CapturingService svc = new CapturingService();
        DbAnalyzerCliCommand cmd = new DbAnalyzerCliCommand(svc);

        int code = cmd.run(new String[]{"--baseRoot", "C:/tmp/base"}); // targetRoot missing

        assertEquals(2, code, "missing args should return 2");
        String err = errContent.toString();
        assertTrue(err.contains("ERROR: Missing required arguments."));
        String out = outContent.toString();
        assertTrue(out.contains("Usage:"), "Help text should be printed to stdout");
        assertNull(svc.lastReq, "Service should not be called when args are invalid");
    }

    @Test
    void validArgsNoChangesReturnZeroAndRequestIsParsed() throws Exception {
        CapturingService svc = new CapturingService();
        // Prepare a result with no changes
        svc.toReturn = new DbAnalyzerResult(Collections.emptyList(), 5, 5);
        DbAnalyzerCliCommand cmd = new DbAnalyzerCliCommand(svc);

        int code = cmd.run(new String[]{
                "--baseRoot", "C:/code/base",
                "--targetRoot", "C:/code/target",
                "--includePackages", "com.acme.dao, com.acme.repo ",
                "--includeDynamic", "false"
        });

        assertEquals(0, code, "no changes should return 0");
        assertNotNull(svc.lastReq, "Service should be invoked");
        assertEquals("C:/code/base", svc.lastReq.getBaseRoot());
        assertEquals("C:/code/target", svc.lastReq.getTargetRoot());

        List<String> pkgs = svc.lastReq.getIncludePackages();
        assertEquals(Arrays.asList("com.acme.dao", "com.acme.repo"), pkgs);
        assertFalse(svc.lastReq.isIncludeDynamic());

        String out = outContent.toString();
        assertTrue(out.contains("DBAnalyzer v0.1 (SQL-in-code diff)"));
        assertTrue(out.contains("Changes: modified=0 added=0 removed=0"));
    }

    @Test
    void validArgsWithChangesReturnOne() throws Exception {
        CapturingService svc = new CapturingService();

        // Build a minimal change list to exercise toReport()
        SqlMeta meta = new SqlMeta(SqlMeta.Type.SELECT, Collections.singletonList("FOO"), Collections.emptyMap(), true);
        SqlArtifact target = new SqlArtifact(
                "key1", "src/Foo.java", "Foo", "bar", 10,
                "select * from foo", "SELECT * FROM FOO", false, meta);
        DbAnalyzerResult.Change change = new DbAnalyzerResult.Change(
                DbAnalyzerResult.Change.Kind.ADDED, "key1", null, target, Collections.emptyMap());
        svc.toReturn = new DbAnalyzerResult(Collections.singletonList(change), 1, 2);

        DbAnalyzerCliCommand cmd = new DbAnalyzerCliCommand(svc);

        int code = cmd.run(new String[]{
                "--baseRoot", "B",
                "--targetRoot", "T"
        });

        assertEquals(1, code, "changes should return 1");
        assertNotNull(svc.lastReq);
        String out = outContent.toString();
        assertTrue(out.contains("[ADDED] key1"));
        assertTrue(out.contains("Tables: FOO"));
    }

    private static class CapturingService extends DbAnalyzerService {
        volatile DbAnalyzerRequest lastReq;
        volatile DbAnalyzerResult toReturn = new DbAnalyzerResult(Collections.emptyList(), 0, 0);

        @Override
        public DbAnalyzerResult analyze(DbAnalyzerRequest req) {
            this.lastReq = req;
            return toReturn;
        }
    }
}

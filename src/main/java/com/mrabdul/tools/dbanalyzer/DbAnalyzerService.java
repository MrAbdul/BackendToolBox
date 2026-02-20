package com.mrabdul.tools.dbanalyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class DbAnalyzerService {

    private final SqlExtractor extractor = new SqlExtractor();
    private final SqlDiffEngine diffEngine = new SqlDiffEngine();
    private final ObjectMapper om = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);


    public DbAnalyzerResult analyze(DbAnalyzerRequest req) throws Exception {
        List<SqlArtifact> base = extractor.extractAll(
                Paths.get(req.getBaseRoot()),
                req.getIncludePackages(),
                req.isIncludeDynamic()
        );

        List<SqlArtifact> target = extractor.extractAll(
                Paths.get(req.getTargetRoot()),
                req.getIncludePackages(),
                req.isIncludeDynamic()
        );

        DbAnalyzerResult result = diffEngine.diff(base, target);

        // NEW: write JSON report if requested
        if (req.getJsonOut() != null && !req.getJsonOut().trim().isEmpty()) {
            writeJsonReport(req, result);
        }

        return result;
    }

    private void writeJsonReport(DbAnalyzerRequest req, DbAnalyzerResult result) throws Exception {
        Path outPath = Paths.get(req.getJsonOut()).toAbsolutePath().normalize();

        // ensure parent dir exists
        Path parent = outPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        DbAnalyzerJsonReport report = result.toJsonReport(req);
        om.writeValue(outPath.toFile(), report);
    }
}

package com.mrabdul.tools.dbanalyzer;

import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.List;

@Component
public class DbAnalyzerService {

    private final SqlExtractor extractor = new SqlExtractor();
    private final SqlDiffEngine diffEngine = new SqlDiffEngine();

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

        return diffEngine.diff(base, target);
    }
}

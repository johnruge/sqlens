package com.sqlens.services;

import com.sqlens.analysis.Finding;
import com.sqlens.analysis.Severity;
import com.sqlens.models.ExplainRes;
import com.sqlens.models.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    @Test
    void includesQueryPlanSchemaAndFindings() {
        var plan = List.of(new ExplainRes(1, "SIMPLE", "film", "ALL", null, null, null, null, 1000L, 33.3, "Using where"));
        var table = new TableInfo("film", "InnoDB", 1000L,
            List.of(new TableInfo.Column("length", "smallint unsigned", true, "")),
            List.of(new TableInfo.Index("PRIMARY", true, List.of("film_id"))));
        var findings = List.of(new Finding(Severity.HIGH, "film", "Full table scan.", "index it"));

        String p = PromptBuilder.build("SELECT * FROM film WHERE length > 60", plan, List.of(table), findings);

        assertTrue(p.contains("<query>\nSELECT * FROM film WHERE length > 60\n</query>"));
        assertTrue(p.contains("1 | SIMPLE | film | ALL | NULL"));
        assertTrue(p.contains("length smallint unsigned NULL"));
        assertTrue(p.contains("UNIQUE INDEX PRIMARY (film_id)"));
        assertTrue(p.contains("- HIGH [film]: Full table scan."));
    }
}

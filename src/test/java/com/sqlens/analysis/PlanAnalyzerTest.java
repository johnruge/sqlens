package com.sqlens.analysis;

import com.sqlens.models.ExplainRes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanAnalyzerTest {

    private static ExplainRes row(String selectType, String table, String type, String possibleKeys,
                                  String key, long rows, double filtered, String extra) {
        return new ExplainRes(1, selectType, table, type, possibleKeys, key, null, null, rows, filtered, extra);
    }

    private static boolean has(List<Finding> f, Severity s, String text) {
        return f.stream().anyMatch(x -> x.severity() == s && x.problem().toLowerCase().contains(text));
    }

    @Test
    void largeFullScanWithoutIndexIsHigh() {
        var f = PlanAnalyzer.analyze(List.of(row("SIMPLE", "film", "ALL", null, null, 50_000, 10, "Using where")));
        assertTrue(has(f, Severity.HIGH, "no index matches"));
        assertTrue(has(f, Severity.MEDIUM, "keeps only 10.0%"));
    }

    @Test
    void fullScanIgnoringAvailableIndex() {
        var f = PlanAnalyzer.analyze(List.of(row("SIMPLE", "film", "ALL", "idx_title", null, 5_000, 100, null)));
        assertTrue(has(f, Severity.HIGH, "could be used"));
    }

    @Test
    void smallFullScanIsOnlyInfo() {
        var f = PlanAnalyzer.analyze(List.of(row("SIMPLE", "category", "ALL", null, null, 16, 100, null)));
        assertEquals(1, f.size());
        assertEquals(Severity.INFO, f.get(0).severity());
    }

    @Test
    void goodIndexLookupHasNoFindings() {
        var f = PlanAnalyzer.analyze(List.of(row("SIMPLE", "actor", "ref", "idx_last", "idx_last", 3, 100, null)));
        assertTrue(f.isEmpty());
    }

    @Test
    void filesortTemporaryAndJoinBuffer() {
        var f = PlanAnalyzer.analyze(List.of(
            row("SIMPLE", "a", "ALL", null, null, 200, 100, "Using temporary; Using filesort"),
            row("SIMPLE", "b", "ALL", null, null, 300, 100, "Using where; Using join buffer (hash join)")));
        assertTrue(has(f, Severity.MEDIUM, "filesort"));
        assertTrue(has(f, Severity.MEDIUM, "temporary table"));
        assertTrue(has(f, Severity.HIGH, "joined without an index"));
    }

    @Test
    void correlatedSubqueryIsHigh() {
        var f = PlanAnalyzer.analyze(List.of(
            row("PRIMARY", "film", "ALL", null, null, 100, 100, "Using where"),
            row("DEPENDENT SUBQUERY", "film_actor", "ref", "idx", "idx", 5, 100, null)));
        assertTrue(has(f, Severity.HIGH, "correlated subquery"));
    }

    @Test
    void largeJoinProductIsHigh() {
        var f = PlanAnalyzer.analyze(List.of(
            row("SIMPLE", "a", "ALL", null, null, 5_000, 100, null),
            row("SIMPLE", "b", "ALL", null, null, 5_000, 100, null)));
        assertTrue(f.stream().anyMatch(x -> x.table() == null && x.severity() == Severity.HIGH));
    }

    @Test
    void impossibleWhereIsInfo() {
        var f = PlanAnalyzer.analyze(List.of(
            new ExplainRes(1, "SIMPLE", null, null, null, null, null, null, null, null, "Impossible WHERE")));
        assertEquals(1, f.size());
        assertEquals(Severity.INFO, f.get(0).severity());
    }

    @Test
    void coveringIndexScanIsInfoNotWarning() {
        var f = PlanAnalyzer.analyze(List.of(row("SIMPLE", "film", "index", null, "idx_title", 1000, 100, "Using index")));
        assertEquals(Severity.INFO, f.get(0).severity());
    }

    @Test
    void findingsAreSortedMostSevereFirst() {
        var f = PlanAnalyzer.analyze(List.of(
            row("SIMPLE", "small", "ALL", null, null, 10, 100, "Using filesort"),
            row("SIMPLE", "big", "ALL", null, null, 100_000, 100, null)));
        assertEquals(Severity.HIGH, f.get(0).severity());
        assertEquals(Severity.INFO, f.get(f.size() - 1).severity());
    }
}

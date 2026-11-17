package com.sqlens.analysis;

import com.sqlens.models.ExplainRes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Rule-based checks over MySQL's traditional EXPLAIN output. Works offline, needs no
 * LLM, and is deterministic, so every rule is unit tested.
 */
public final class PlanAnalyzer {

    /** Below this many rows a full scan is cheap and not worth complaining about. */
    static final long SMALL_TABLE_ROWS = 1_000;
    /** Estimated rows examined across the whole join above which we warn. */
    static final long LARGE_JOIN_ROWS = 1_000_000;
    /** Percent of rows kept after the WHERE filter below which we warn. */
    static final double LOW_FILTERED_PERCENT = 20.0;

    private PlanAnalyzer() {}

    public static List<Finding> analyze(List<ExplainRes> plan) {
        List<Finding> findings = new ArrayList<>();
        double estimatedRows = 1;

        for (ExplainRes row : plan) {
            String table = row.table();
            String type = lower(row.type());
            String extra = lower(row.extra());
            String selectType = lower(row.selectType());
            long rows = row.rows() == null ? 0 : row.rows();
            boolean hasPossibleKeys = row.possibleKeys() != null && !row.possibleKeys().isBlank();
            boolean usesKey = row.key() != null && !row.key().isBlank();

            if (rows > 0 && row.filtered() != null) {
                estimatedRows *= Math.max(1, rows * row.filtered() / 100.0);
            }

            if (extra.contains("impossible where") || extra.contains("no matching")) {
                findings.add(new Finding(Severity.INFO, table,
                    "The WHERE clause can never be true, so MySQL returns no rows without reading the table.",
                    "Check the conditions for a typo or contradictory filters."));
                continue;
            }

            if (type.equals("all")) {
                if (rows < SMALL_TABLE_ROWS) {
                    findings.add(new Finding(Severity.INFO, table,
                        "Full table scan, but only ~" + rows + " rows, so this is cheap.",
                        "No action needed unless the table is expected to grow."));
                } else if (hasPossibleKeys) {
                    findings.add(new Finding(Severity.HIGH, table,
                        "Full table scan of ~" + rows + " rows even though index(es) " + row.possibleKeys()
                            + " could be used.",
                        "The optimizer judged the index not selective enough, or the column is wrapped in a "
                            + "function / compared with a different type. Rewrite the condition so the indexed "
                            + "column stands alone (e.g. col >= '2024-01-01' instead of YEAR(col) = 2024)."));
                } else {
                    findings.add(new Finding(Severity.HIGH, table,
                        "Full table scan of ~" + rows + " rows: no index matches the WHERE / JOIN conditions.",
                        "Add an index on the columns this table is filtered or joined on."));
                }
            } else if (type.equals("index") && !extra.contains("using index")) {
                findings.add(new Finding(Severity.MEDIUM, table,
                    "Full index scan of ~" + rows + " rows (reads every entry of " + row.key() + ").",
                    "Add a WHERE condition on the leading index column, or an index that matches the filter."));
            } else if (type.equals("index")) {
                findings.add(new Finding(Severity.INFO, table,
                    "Scans the whole index " + row.key() + " but never touches the table rows (covering index).",
                    "Fine for small or medium tables; add a filter on the leading column if it gets large."));
            } else if (hasPossibleKeys && !usesKey) {
                findings.add(new Finding(Severity.MEDIUM, table,
                    "Index(es) " + row.possibleKeys() + " were considered but none was used.",
                    "Run ANALYZE TABLE to refresh statistics, or check that the condition is selective."));
            }

            if (extra.contains("using join buffer")) {
                findings.add(new Finding(Severity.HIGH, table,
                    "Joined without an index (MySQL buffers rows and compares them in a loop).",
                    "Add an index on the join column(s) of " + table + "."));
            }
            if (extra.contains("using filesort")) {
                findings.add(new Finding(Severity.MEDIUM, table,
                    "Rows have to be sorted after reading (filesort).",
                    "Add an index whose columns match the ORDER BY (after any equality filters), "
                        + "or drop the ORDER BY if order does not matter."));
            }
            if (extra.contains("using temporary")) {
                findings.add(new Finding(Severity.MEDIUM, table,
                    "A temporary table is needed (typical for GROUP BY, DISTINCT or UNION).",
                    "Index the GROUP BY / DISTINCT columns, or use UNION ALL if duplicates are acceptable."));
            }
            if (selectType.contains("dependent subquery")) {
                findings.add(new Finding(Severity.HIGH, table,
                    "Correlated subquery: it runs once for every row of the outer query.",
                    "Rewrite it as a JOIN (or EXISTS with an index on the correlated column)."));
            }
            if (selectType.contains("uncacheable")) {
                findings.add(new Finding(Severity.MEDIUM, table,
                    "Subquery result cannot be cached (non-deterministic function or user variable).",
                    "Move NOW()/RAND()/@vars out of the subquery if possible."));
            }
            if (row.filtered() != null && row.filtered() < LOW_FILTERED_PERCENT && rows >= SMALL_TABLE_ROWS) {
                findings.add(new Finding(Severity.MEDIUM, table,
                    String.format(Locale.ROOT, "Reads ~%d rows but the WHERE clause keeps only %.1f%% of them.",
                        rows, row.filtered()),
                    "An index on the filtered column(s) would let MySQL skip the rows it throws away."));
            }
        }

        if (plan.size() > 1 && estimatedRows > LARGE_JOIN_ROWS) {
            findings.add(new Finding(Severity.HIGH, null,
                String.format(Locale.ROOT, "The join examines roughly %,.0f row combinations in total.", estimatedRows),
                "Index the join columns so each table is looked up (ref/eq_ref) instead of scanned."));
        }

        findings.sort(Comparator.comparing(Finding::severity));
        return findings;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}

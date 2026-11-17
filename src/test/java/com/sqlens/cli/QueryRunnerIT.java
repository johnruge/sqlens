package com.sqlens.cli;

import com.sqlens.MySqlTestBase;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class QueryRunnerIT extends MySqlTestBase {

    @Test
    void printsPlanAndFindings() {
        StringWriter sw = new StringWriter();
        var runner = new QueryRunner(conn, new PrintWriter(sw), new Style(false), null);
        var findings = runner.explain("SELECT * FROM film ORDER BY length;", false);
        assertTrue(findings.isPresent());
        assertTrue(sw.toString().contains("filesort"), sw.toString());
    }

    @Test
    void printsTree() {
        StringWriter sw = new StringWriter();
        new QueryRunner(conn, new PrintWriter(sw), new Style(false), null)
            .explainTree("SELECT * FROM actor WHERE last_name = 'CHASE'");
        assertTrue(sw.toString().contains("Index lookup"), sw.toString());
    }

    @Test
    void reportsRejectionsWithoutThrowing() {
        StringWriter sw = new StringWriter();
        var findings = new QueryRunner(conn, new PrintWriter(sw), new Style(false), null).explain("DROP TABLE film", false);
        assertTrue(findings.isEmpty());
        assertTrue(sw.toString().startsWith("rejected:"));
    }
}

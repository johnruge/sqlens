package com.sqlens.cli;

import com.sqlens.analysis.Finding;
import com.sqlens.analysis.PlanAnalyzer;
import com.sqlens.models.ExplainRes;
import com.sqlens.models.TableInfo;
import com.sqlens.services.InvalidQueryException;
import com.sqlens.services.LLMException;
import com.sqlens.services.LLMService;
import com.sqlens.services.PromptBuilder;
import com.sqlens.services.SQLService;
import com.sqlens.services.SchemaService;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Explains one query and prints the plan, findings and (optionally) AI advice. */
public class QueryRunner {

    private final Connection conn;
    private final PrintWriter out;
    private final Style style;
    private final LLMService llm;

    public QueryRunner(Connection conn, PrintWriter out, Style style, LLMService llm) {
        this.conn = conn;
        this.out = out;
        this.style = style;
        this.llm = llm;
    }

    /** @return the findings, or empty if the query was rejected or failed */
    public Optional<List<Finding>> explain(String sql, boolean useAi) {
        try {
            List<ExplainRes> plan = SQLService.getExplainRes(conn, sql);
            out.print(TableFormatter.formatPlan(plan));
            out.println();
            List<Finding> findings = PlanAnalyzer.analyze(plan);
            out.print(FindingsFormatter.format(findings, style));
            if (useAi && llm != null) {
                out.flush();
                askLlm(sql, plan, findings);
            }
            return Optional.of(findings);
        } catch (InvalidQueryException e) {
            out.println(style.error("rejected: ") + e.getMessage());
        } catch (SQLException e) {
            // Show the database's message (useful for typos) but never a stack trace.
            out.println(style.error("error: ") + e.getMessage());
        } finally {
            out.flush();
        }
        return Optional.empty();
    }

    public void explainTree(String sql) {
        try {
            out.print(SQLService.getExplainTree(conn, sql));
        } catch (InvalidQueryException e) {
            out.println(style.error("rejected: ") + e.getMessage());
        } catch (SQLException e) {
            out.println(style.error("error: ") + e.getMessage());
        }
        out.flush();
    }

    private void askLlm(String sql, List<ExplainRes> plan, List<Finding> findings) throws SQLException {
        out.println();
        out.println(style.dim("asking " + llm.model() + "..."));
        out.flush();
        List<TableInfo> tables = SchemaService.describePlanTables(conn, sql, plan);
        try {
            String advice = llm.suggest(PromptBuilder.build(sql, plan, tables, findings));
            out.println(style.bold("AI suggestions") + style.dim(" (verify before applying)"));
            out.println(advice);
        } catch (LLMException e) {
            out.println(style.error("AI suggestions unavailable: ") + e.getMessage());
        }
    }
}

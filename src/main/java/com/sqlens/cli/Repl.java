package com.sqlens.cli;

import com.sqlens.models.ExplainRes;
import com.sqlens.services.InvalidQueryException;
import com.sqlens.services.SQLService;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Interactive read-eval-print loop. Statements end with ';' and may span several lines. */
public class Repl {

    private final Connection conn;
    private final LineReader reader;
    private final PrintWriter out;

    public Repl(Connection conn, LineReader reader, PrintWriter out) {
        this.conn = conn;
        this.reader = reader;
        this.out = out;
    }

    public void run() {
        out.println("Connected. Type 'help' for commands. End each query with ';'.");
        StringBuilder buffer = new StringBuilder();
        while (true) {
            String line;
            try {
                line = reader.readLine(buffer.isEmpty() ? "sqlens> " : "     -> ");
            } catch (UserInterruptException e) {   // Ctrl-C: discard the current statement
                buffer.setLength(0);
                continue;
            } catch (EndOfFileException e) {       // Ctrl-D: quit
                break;
            }

            String trimmed = line.trim();
            if (buffer.isEmpty()) {
                if (trimmed.isEmpty()) {
                    continue;
                }
                String word = trimmed.toLowerCase().replaceAll(";$", "");
                if (word.equals("exit") || word.equals("quit") || word.equals("\\q")) {
                    break;
                }
                if (word.equals("help") || word.equals("\\h") || word.equals("\\?")) {
                    printHelp();
                    continue;
                }
            }

            buffer.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = buffer.toString();
                buffer.setLength(0);
                explain(sql);
            }
        }
        out.println("bye");
        out.flush();
    }

    private void explain(String sql) {
        try {
            List<ExplainRes> plan = SQLService.getExplainRes(conn, sql);
            out.print(TableFormatter.formatPlan(plan));
        } catch (InvalidQueryException e) {
            out.println("rejected: " + e.getMessage());
        } catch (SQLException e) {
            // Show the database's message (useful for typos) but never a stack trace.
            out.println("error: " + e.getMessage());
        }
        out.flush();
    }

    private void printHelp() {
        out.println("""
            Type a SELECT query ending with ';' to see its execution plan.
              help, \\h, \\?     show this help
              exit, quit, \\q   leave sqlens (Ctrl-D also works)
              Ctrl-C           discard the query you are typing""");
    }
}

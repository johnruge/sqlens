package com.sqlens.cli;

import com.sqlens.services.LLMService;
import com.sqlens.services.SchemaService;
import com.sqlens.util.TerminalText;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/** Interactive read-eval-print loop. Statements end with ';' and may span several lines. */
public class Repl {

    private final Connection conn;
    private final LineReader reader;
    private final PrintWriter out;
    private final Style style;
    private final LLMService llm;   // null when no API key is configured
    private final QueryRunner runner;
    private boolean aiEnabled;

    public Repl(Connection conn, LineReader reader, PrintWriter out, Style style, LLMService llm) {
        this.conn = conn;
        this.reader = reader;
        this.out = out;
        this.style = style;
        this.llm = llm;
        this.runner = new QueryRunner(conn, out, style, llm);
        this.aiEnabled = llm != null;
    }

    public void run() {
        out.println("Connected. Type 'help' for commands. End each query with ';'.");
        out.println(llm == null
            ? "AI suggestions are off (set GEMINI_API_KEY to enable them)."
            : "AI suggestions are on (" + llm.model() + "). Type \\ai off to disable.");
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
                String word = trimmed.toLowerCase(Locale.ROOT).replaceAll(";$", "");
                if (word.equals("exit") || word.equals("quit") || word.equals("\\q")) {
                    break;
                }
                if (word.equals("help") || word.equals("\\h") || word.equals("\\?")) {
                    printHelp();
                    continue;
                }
                if (trimmed.startsWith("\\")) {
                    metaCommand(trimmed.replaceAll(";$", "").trim());
                    continue;
                }
            }

            buffer.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = buffer.toString();
                buffer.setLength(0);
                runner.explain(sql, aiEnabled);
            }
        }
        out.println("bye");
        out.flush();
    }

    private void metaCommand(String cmd) {
        String[] parts = cmd.split("\\s+", 2);
        try {
            switch (parts[0].toLowerCase(Locale.ROOT)) {
                case "\\tables", "\\t" -> out.print(TableFormatter.formatTableList(SchemaService.listTables(conn)));
                case "\\d", "\\describe" -> {
                    if (parts.length < 2) {
                        out.println("usage: \\d <table>");
                    } else {
                        SchemaService.describe(conn, parts[1].replace("`", "")).ifPresentOrElse(
                            t -> out.print(TableFormatter.formatTable(t)),
                            () -> out.println("no such table in the current database"));
                    }
                }
                case "\\tree" -> {
                    if (parts.length < 2) {
                        out.println("usage: \\tree <SELECT query>");
                    } else {
                        runner.explainTree(parts[1]);
                    }
                }
                case "\\ai" -> {
                    if (llm == null) {
                        out.println("AI is not configured: set GEMINI_API_KEY and restart sqlens");
                    } else if (parts.length == 2 && parts[1].equalsIgnoreCase("off")) {
                        aiEnabled = false;
                        out.println("AI suggestions off");
                    } else if (parts.length == 2 && parts[1].equalsIgnoreCase("on")) {
                        aiEnabled = true;
                        out.println("AI suggestions on");
                    } else {
                        out.println("usage: \\ai on|off (currently " + (aiEnabled ? "on" : "off") + ")");
                    }
                }
                default -> out.println("unknown command " + TableFormatter.cell(parts[0]) + " (type help)");
            }
        } catch (SQLException e) {
            out.println(style.error("error: ") + TerminalText.clean(e.getMessage()));
        }
        out.flush();
    }

    private void printHelp() {
        out.println("""
            Type a SELECT query ending with ';' to see its execution plan and findings.
              \\tables, \\t       list tables in the current database
              \\d <table>        show columns and indexes of a table
              \\tree <query>     show the plan as a tree with costs (EXPLAIN FORMAT=TREE)
              \\ai on|off        toggle AI suggestions (needs GEMINI_API_KEY)
              help, \\h, \\?     show this help
              exit, quit, \\q   leave sqlens (Ctrl-D also works)
              Ctrl-C           discard the query you are typing""");
    }
}

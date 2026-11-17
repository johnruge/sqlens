package com.sqlens;

import com.sqlens.analysis.Finding;
import com.sqlens.analysis.Severity;
import com.sqlens.cli.QueryRunner;
import com.sqlens.cli.Repl;
import com.sqlens.cli.Style;
import com.sqlens.database.ConnectionConfig;
import com.sqlens.database.DBConnection;
import com.sqlens.services.LLMService;
import com.sqlens.services.QuerySplitter;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "sqlens", mixinStandardHelpOptions = true, version = "sqlens 1.0",
    description = "Explains MySQL query plans and suggests how to make queries faster.",
    exitCodeListHeading = "%nExit codes:%n",
    exitCodeList = {
        "0:success",
        "1:connection or query error",
        "2:invalid arguments",
        "3:a finding at or above --fail-on was reported"
    })
public class App implements Callable<Integer> {

    static final long MAX_SCRIPT_BYTES = 1_000_000;

    @Option(names = "--url", description = "JDBC URL, jdbc:mysql://host[:port]/database (overrides host/port/database)")
    private String url;

    @Option(names = {"-H", "--host"}, defaultValue = "${env:MYSQL_HOST:-127.0.0.1}",
        description = "database host (default: ${DEFAULT-VALUE}, env MYSQL_HOST)")
    private String host;

    @Option(names = {"-P", "--port"}, defaultValue = "${env:MYSQL_PORT:-3306}",
        description = "database port (default: ${DEFAULT-VALUE}, env MYSQL_PORT)")
    private int port;

    @Option(names = {"-d", "--database"}, defaultValue = "${env:MYSQL_DATABASE:-sakila}",
        description = "database name (default: ${DEFAULT-VALUE}, env MYSQL_DATABASE)")
    private String database;

    @Option(names = {"-u", "--user"}, defaultValue = "${env:SQLENS_USER}",
        description = "database user (env SQLENS_USER; prompted if missing)")
    private String user;

    @ArgGroup(exclusive = true)
    private OneShot oneShot;

    static class OneShot {
        @Option(names = {"-e", "--execute"}, description = "explain this query and exit")
        String query;

        @Option(names = {"-f", "--file"}, description = "explain every query in this .sql file and exit")
        Path file;
    }

    @Option(names = "--fail-on", description = "with -e/-f: exit with code 3 if any finding is at least this severe (HIGH, MEDIUM, INFO)")
    private Severity failOn;

    @Option(names = "--no-color", description = "disable coloured output")
    private boolean noColor;

    @Option(names = "--no-ai", description = "never send anything to the AI service, even if GEMINI_API_KEY is set")
    private boolean noAi;

    @Override
    public Integer call() throws IOException {
        ConnectionConfig config;
        LLMService llm;
        List<String> queries = null;
        try {
            config = url != null ? ConnectionConfig.fromUrl(url) : new ConnectionConfig(host, port, database);
            llm = noAi ? null : LLMService.fromEnvironment().orElse(null);
            if (oneShot != null) {
                queries = oneShot.query != null ? List.of(oneShot.query) : readScript(oneShot.file);
            } else if (failOn != null) {
                throw new IllegalArgumentException("--fail-on only works together with -e or -f");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            PrintWriter out = terminal.writer();
            // Credentials are read with a reader that has no history, so they are never saved.
            LineReader credentialReader = LineReaderBuilder.builder().terminal(terminal).build();
            String password = System.getenv("SQLENS_PASSWORD");
            try {
                if (user == null || user.isBlank()) {
                    user = credentialReader.readLine("user: ").trim();
                }
                if (password == null) {
                    password = credentialReader.readLine("Password (hidden): ", '*');
                }
            } catch (UserInterruptException | EndOfFileException e) {
                return 130;
            }

            boolean color = !noColor && System.getenv("NO_COLOR") == null
                && !Terminal.TYPE_DUMB.equals(terminal.getType())
                && !Terminal.TYPE_DUMB_COLOR.equals(terminal.getType());
            Style style = new Style(color);

            try (Connection conn = DBConnection.getConnection(config, user, password)) {
                password = null; // drop our reference as soon as we are connected
                if (queries != null) {
                    return runOnce(new QueryRunner(conn, out, style, llm), queries, out, style);
                }
                new Repl(conn, historyReader(terminal), out, style, llm).run();
            } catch (SQLException e) {
                out.println(style.error("could not connect to ") + config.host() + ":" + config.port()
                    + "/" + config.database() + ": " + e.getMessage());
                out.flush();
                return 1;
            }
        }
        return 0;
    }

    private int runOnce(QueryRunner runner, List<String> queries, PrintWriter out, Style style) {
        int exit = 0;
        for (int i = 0; i < queries.size(); i++) {
            if (queries.size() > 1) {
                out.println(style.bold("-- query " + (i + 1) + " of " + queries.size()));
            }
            Optional<List<Finding>> findings = runner.explain(queries.get(i), true);
            if (findings.isEmpty()) {
                exit = 1;
            } else if (exit == 0 && failOn != null
                && findings.get().stream().anyMatch(f -> f.severity().compareTo(failOn) <= 0)) {
                exit = 3;
            }
            out.println();
        }
        out.flush();
        return exit;
    }

    static List<String> readScript(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("not a regular file: " + file);
            }
            if (Files.size(file) > MAX_SCRIPT_BYTES) {
                throw new IllegalArgumentException("file is larger than " + MAX_SCRIPT_BYTES + " bytes");
            }
            List<String> queries = QuerySplitter.split(Files.readString(file, StandardCharsets.UTF_8));
            if (queries.isEmpty()) {
                throw new IllegalArgumentException("no queries found in " + file);
            }
            return queries;
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read " + file + ": " + e.getMessage());
        }
    }

    /** REPL reader with history in ~/.sqlens_history, readable only by the current user. */
    private static LineReader historyReader(Terminal terminal) {
        LineReaderBuilder builder = LineReaderBuilder.builder().terminal(terminal);
        String home = System.getProperty("user.home");
        if (home != null) {
            Path history = Path.of(home, ".sqlens_history");
            try {
                if (Files.notExists(history)) {
                    Files.createFile(history);
                }
                Files.setPosixFilePermissions(history, PosixFilePermissions.fromString("rw-------"));
                builder.variable(LineReader.HISTORY_FILE, history);
            } catch (IOException | UnsupportedOperationException e) {
                // no history if the file cannot be secured
            }
        }
        return builder.build();
    }

    public static void main(String[] args) {
        CommandLine cli = new CommandLine(new App()).setCaseInsensitiveEnumValuesAllowed(true);
        System.exit(cli.execute(args));
    }
}

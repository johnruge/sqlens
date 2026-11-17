package com.sqlens;

import com.sqlens.cli.Repl;
import com.sqlens.cli.Style;
import com.sqlens.database.ConnectionConfig;
import com.sqlens.database.DBConnection;
import com.sqlens.services.LLMService;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "sqlens", mixinStandardHelpOptions = true, version = "sqlens 1.0",
    description = "Explains MySQL query plans and suggests how to make queries faster.")
public class App implements Callable<Integer> {

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

    @Option(names = "--no-color", description = "disable coloured output")
    private boolean noColor;

    @Option(names = "--no-ai", description = "never send anything to the AI service, even if GEMINI_API_KEY is set")
    private boolean noAi;

    @Override
    public Integer call() throws IOException {
        ConnectionConfig config;
        try {
            config = url != null ? ConnectionConfig.fromUrl(url) : new ConnectionConfig(host, port, database);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        LLMService llm;
        try {
            llm = noAi ? null : LLMService.fromEnvironment().orElse(null);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            PrintWriter out = terminal.writer();

            String password = System.getenv("SQLENS_PASSWORD");
            try {
                if (user == null || user.isBlank()) {
                    user = reader.readLine("user: ").trim();
                }
                if (password == null) {
                    password = reader.readLine("Password (hidden): ", '*');
                }
            } catch (UserInterruptException | EndOfFileException e) {
                return 130;
            }

            try (Connection conn = DBConnection.getConnection(config, user, password)) {
                password = null; // drop our reference as soon as we are connected
                boolean color = !noColor && System.getenv("NO_COLOR") == null
                    && !Terminal.TYPE_DUMB.equals(terminal.getType())
                    && !Terminal.TYPE_DUMB_COLOR.equals(terminal.getType());
                new Repl(conn, reader, out, new Style(color), llm).run();
            } catch (SQLException e) {
                out.println("could not connect to " + config.host() + ":" + config.port()
                    + "/" + config.database() + ": " + e.getMessage());
                out.flush();
                return 1;
            }
        }
        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new App()).execute(args));
    }
}

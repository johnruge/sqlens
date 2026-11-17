package com.sqlens.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Opens a single hardened connection. A pool is not needed for the interactive REPL.
 */
public final class DBConnection {

    /** Seconds any single statement may run before the driver cancels it. */
    public static final int QUERY_TIMEOUT_SECONDS = 30;

    private DBConnection() {}

    public static Connection getConnection(ConnectionConfig config, String user, String password)
    throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("connectTimeout", "10000");
        props.setProperty("socketTimeout", String.valueOf((QUERY_TIMEOUT_SECONDS + 10) * 1000));
        // Defence in depth: refuse features that could be abused by a malicious server or input.
        props.setProperty("allowMultiQueries", "false");
        props.setProperty("allowLoadLocalInfile", "false");
        props.setProperty("allowUrlInLocalInfile", "false");
        props.setProperty("autoDeserialize", "false");

        Connection conn = DriverManager.getConnection(config.jdbcUrl(), props);
        try {
            // sqlens only ever reads. Make the session read-only so that even if a
            // statement slipped past validation, the server would refuse to write.
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.execute("SET SESSION TRANSACTION READ ONLY");
                stmt.execute("SET SESSION max_execution_time = " + (QUERY_TIMEOUT_SECONDS * 1000));
            }
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }
}

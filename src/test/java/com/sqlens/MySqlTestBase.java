package com.sqlens;

import com.sqlens.database.ConnectionConfig;
import com.sqlens.database.DBConnection;
import com.sqlens.services.QuerySplitter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Gives each integration test class a MySQL loaded with test-schema.sql.
 *
 * Two ways to get a database:
 * - Existing server: set SQLENS_IT_HOST, SQLENS_IT_PORT, SQLENS_IT_USER and
 *   SQLENS_IT_PASSWORD. The user needs ALL on the `sqlens_test` schema only; the
 *   schema is dropped and recreated for every test class.
 * - Otherwise Testcontainers starts a throwaway mysql:8.0 (needs Docker).
 * Without either, the tests are skipped rather than failed.
 */
public abstract class MySqlTestBase {

    protected static final String TEST_DB = "sqlens_test";

    private static MySQLContainer<?> mysql;
    protected static Connection conn;

    @BeforeAll
    static void startDatabase() throws SQLException, IOException {
        String host = System.getenv("SQLENS_IT_HOST");
        ConnectionConfig config;
        String user;
        String password;
        if (host != null && !host.isBlank()) {
            int port = Integer.parseInt(System.getenv().getOrDefault("SQLENS_IT_PORT", "3306"));
            user = System.getenv("SQLENS_IT_USER");
            password = System.getenv("SQLENS_IT_PASSWORD");
            config = new ConnectionConfig(host, port, TEST_DB);
            loadSchema(host, port, user, password);
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "no Docker and no SQLENS_IT_HOST: skipping integration tests");
            mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName(TEST_DB).withInitScript("test-schema.sql");
            mysql.start();
            config = new ConnectionConfig(mysql.getHost(), mysql.getMappedPort(3306), TEST_DB);
            user = mysql.getUsername();
            password = mysql.getPassword();
        }
        conn = DBConnection.getConnection(config, user, password);
    }

    /** Recreates the test schema with a separate, writable admin connection. */
    private static void loadSchema(String host, int port, String user, String password) throws SQLException, IOException {
        String script;
        try (InputStream in = MySqlTestBase.class.getResourceAsStream("/test-schema.sql")) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("allowPublicKeyRetrieval", "false");
        try (Connection admin = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/", props);
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB);
            st.execute("CREATE DATABASE " + TEST_DB);
            st.execute("USE " + TEST_DB);
            for (String sql : QuerySplitter.split(script)) {
                st.execute(sql);
            }
        }
    }

    @AfterAll
    static void stopDatabase() throws SQLException {
        if (conn != null) {
            conn.close();
        }
        if (mysql != null) {
            mysql.stop();
        }
    }
}

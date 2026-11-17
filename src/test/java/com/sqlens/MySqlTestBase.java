package com.sqlens;

import com.sqlens.database.ConnectionConfig;
import com.sqlens.database.DBConnection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Starts one throwaway MySQL container per test class. Skipped (not failed) when Docker
 * is not available, so `mvn verify` still works on a laptop without Docker.
 * Credentials are random per run and never leave the test JVM.
 */
public abstract class MySqlTestBase {

    protected static MySQLContainer<?> mysql;
    protected static Connection conn;

    @BeforeAll
    static void startDatabase() throws SQLException {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("sqlens_test")
            .withInitScript("test-schema.sql");
        mysql.start();
        ConnectionConfig config = new ConnectionConfig(mysql.getHost(), mysql.getMappedPort(3306), "sqlens_test");
        conn = DBConnection.getConnection(config, mysql.getUsername(), mysql.getPassword());
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

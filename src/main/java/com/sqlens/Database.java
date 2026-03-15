package com.sqlens;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class Database {
    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s",
            requireEnv("MYSQL_HOST"),
            requireEnv("MYSQL_PORT"),
            requireEnv("MYSQL_DATABASE")));
        config.setUsername(requireEnv("MYSQL_USER"));
        config.setPassword(requireEnv("MYSQL_PASSWORD"));
        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new RuntimeException("Missing required environment variable: " + name);
        }
        return value;
    }
}

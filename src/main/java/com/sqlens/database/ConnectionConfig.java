package com.sqlens.database;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validated connection target. Host and database are restricted to safe characters so
 * that nobody can smuggle extra JDBC URL parameters (e.g. allowLoadLocalInfile,
 * autoDeserialize) in through the --host, --database or --url options.
 */
public record ConnectionConfig(String host, int port, String database) {

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.\\-]{1,253}");
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_$]{1,64}");
    private static final Pattern URL = Pattern.compile(
        "jdbc:mysql://([A-Za-z0-9.\\-]{1,253})(?::(\\d{1,5}))?/([A-Za-z0-9_$]{1,64})");

    public ConnectionConfig {
        if (host == null || !HOST.matcher(host).matches()) {
            throw new IllegalArgumentException("invalid host: only letters, digits, '.' and '-' are allowed");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("invalid port: must be between 1 and 65535");
        }
        if (database == null || !DATABASE.matcher(database).matches()) {
            throw new IllegalArgumentException("invalid database name: only letters, digits, '_' and '$' are allowed");
        }
    }

    /** Parses jdbc:mysql://host[:port]/database. Query-string parameters are rejected. */
    public static ConnectionConfig fromUrl(String url) {
        Matcher m = URL.matcher(url == null ? "" : url.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                "invalid --url: expected jdbc:mysql://host[:port]/database (no extra parameters)");
        }
        int port = m.group(2) == null ? 3306 : Integer.parseInt(m.group(2));
        return new ConnectionConfig(m.group(1), port, m.group(3));
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }
}

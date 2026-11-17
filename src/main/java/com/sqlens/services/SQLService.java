package com.sqlens.services;

import com.sqlens.database.DBConnection;
import com.sqlens.models.ExplainRes;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class SQLService {

    private SQLService() {}

    /**
     * Runs EXPLAIN on a user query.
     * The query is validated first; it is concatenated (not bound as a parameter) only
     * because placeholders cannot stand in for a whole statement.
     *
     * @throws InvalidQueryException if the query is not a single read-only SELECT
     */
    public static List<ExplainRes> getExplainRes(Connection conn, String query)
    throws SQLException {
        String safeQuery = QueryValidator.validate(query);
        List<ExplainRes> res = new ArrayList<>();

        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(DBConnection.QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = stmt.executeQuery("EXPLAIN " + safeQuery)) {
                while (rs.next()) {
                    res.add(new ExplainRes(
                        rs.getObject("id", Integer.class),
                        rs.getString("select_type"),
                        rs.getString("table"),
                        rs.getString("type"),
                        rs.getString("possible_keys"),
                        rs.getString("key"),
                        rs.getString("key_len"),
                        rs.getString("ref"),
                        rs.getObject("rows", Long.class),
                        rs.getObject("filtered", Double.class),
                        rs.getString("Extra")
                    ));
                }
            }
        }
        return res;
    }

    /** EXPLAIN FORMAT=TREE (MySQL 8.0.16+): shows join order and estimated cost per step. */
    public static String getExplainTree(Connection conn, String query) throws SQLException {
        String safeQuery = QueryValidator.validate(query);
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(DBConnection.QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = stmt.executeQuery("EXPLAIN FORMAT=TREE " + safeQuery)) {
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append(rs.getString(1)).append('\n');
                }
                return sb.toString();
            }
        }
    }
}

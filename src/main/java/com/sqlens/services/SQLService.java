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

    public static List<ExplainRes> getExplainRes(Connection conn, String query)
    throws SQLException {
        List<ExplainRes> res = new ArrayList<>();

        //TODO: validate query before building the EXPLAIN statement
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(DBConnection.QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = stmt.executeQuery("EXPLAIN " + query)) {
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
}

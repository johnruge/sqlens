package com.sqlens.service;

import com.sqlens.models.ExplainRes;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ExplainService {
    public static List<ExplainRes> getExplainRes(Connection conn, String query)
    throws SQLException {
        List<ExplainRes> res = new ArrayList<ExplainRes>();

        Statement stmt = conn.createStatement();

        //TODO: fix potential sql injection //statement vs prepared statement
        //parse to avoid multi sql excecution
        //warn while using analyze, functions,??
        ResultSet rs = stmt.executeQuery("EXPLAIN " + query);

        while (rs.next()) {
            int id = rs.getInt("id");
            String select_type = rs.getString("select_type");
            String table = rs.getString("table");
            String type = rs.getString("type");
            String possible_keys = rs.getString("possible_keys");
            String key = rs.getString("key");
            int key_len = rs.getInt("key_len");
            String ref = rs.getString("ref");
            long rows = rs.getLong("rows");
            double filtered = rs.getDouble("filtered");
            String extra = rs.getString("extra");

            res.add(new ExplainRes(id, select_type, table, type, possible_keys,
                key, key_len, ref, rows, filtered, extra
            ));
        }
        return res;
    }
}

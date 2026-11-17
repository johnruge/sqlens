package com.sqlens.services;

import com.sqlens.database.DBConnection;
import com.sqlens.models.ExplainRes;
import com.sqlens.models.TableInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads table metadata from information_schema. Every query here uses a
 * PreparedStatement because table names come from user input.
 */
public final class SchemaService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_$]{1,64}");
    private static final Set<String> NOT_ALIASES = Set.of("WHERE", "ON", "USING", "JOIN", "INNER", "LEFT",
        "RIGHT", "CROSS", "NATURAL", "STRAIGHT_JOIN", "GROUP", "ORDER", "LIMIT", "HAVING", "WINDOW",
        "UNION", "FOR", "LOCK", "USE", "FORCE", "IGNORE", "AS");
    private static final Pattern FROM_OR_JOIN = Pattern.compile(
        "\\b(?:FROM|JOIN)\\s+`?([A-Za-z0-9_$]+)`?(?:\\.`?([A-Za-z0-9_$]+)`?)?(?:\\s+(?:AS\\s+)?`?([A-Za-z0-9_$]+)`?)?",
        Pattern.CASE_INSENSITIVE);

    private SchemaService() {}

    /** All tables and views in the current database. */
    public static List<TableInfo> listTables(Connection conn) throws SQLException {
        String sql = "SELECT TABLE_NAME, ENGINE, TABLE_ROWS FROM information_schema.TABLES "
            + "WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME";
        List<TableInfo> tables = new ArrayList<>();
        try (PreparedStatement ps = prepare(conn, sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tables.add(new TableInfo(rs.getString(1), rs.getString(2), rs.getObject(3, Long.class),
                    List.of(), List.of()));
            }
        }
        return tables;
    }

    /** Columns and indexes of one table in the current database, if it exists. */
    public static Optional<TableInfo> describe(Connection conn, String table) throws SQLException {
        if (table == null || !IDENTIFIER.matcher(table).matches()) {
            return Optional.empty();
        }
        String engine = null;
        Long rows = null;
        boolean found = false;
        try (PreparedStatement ps = prepare(conn, "SELECT ENGINE, TABLE_ROWS FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    found = true;
                    engine = rs.getString(1);
                    rows = rs.getObject(2, Long.class);
                }
            }
        }
        if (!found) {
            return Optional.empty();
        }

        List<TableInfo.Column> columns = new ArrayList<>();
        try (PreparedStatement ps = prepare(conn, "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                + "ORDER BY ORDINAL_POSITION")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(new TableInfo.Column(rs.getString(1), rs.getString(2),
                        "YES".equals(rs.getString(3)), rs.getString(4)));
                }
            }
        }

        Map<String, List<String>> indexColumns = new LinkedHashMap<>();
        Map<String, Boolean> unique = new HashMap<>();
        try (PreparedStatement ps = prepare(conn, "SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME "
                + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                + "ORDER BY INDEX_NAME = 'PRIMARY' DESC, INDEX_NAME, SEQ_IN_INDEX")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    indexColumns.computeIfAbsent(name, k -> new ArrayList<>()).add(rs.getString(3));
                    unique.put(name, rs.getInt(2) == 0);
                }
            }
        }
        List<TableInfo.Index> indexes = new ArrayList<>();
        indexColumns.forEach((name, cols) -> indexes.add(new TableInfo.Index(name, unique.get(name), List.copyOf(cols))));

        return Optional.of(new TableInfo(table, engine, rows, columns, indexes));
    }

    /** Describes every real table that appears in a plan, resolving aliases from the query text. */
    public static List<TableInfo> describePlanTables(Connection conn, String query, List<ExplainRes> plan)
    throws SQLException {
        Map<String, String> aliases = aliases(query);
        Set<String> names = new LinkedHashSet<>();
        for (ExplainRes row : plan) {
            if (row.table() == null) {
                continue;
            }
            names.add(aliases.getOrDefault(row.table().toLowerCase(Locale.ROOT), row.table()));
        }
        List<TableInfo> out = new ArrayList<>();
        for (String name : names) {
            describe(conn, name).ifPresent(out::add);
        }
        return out;
    }

    /** Maps alias (lower-case) to table name for "FROM t a" / "JOIN t AS a" clauses. */
    static Map<String, String> aliases(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) {
            return map;
        }
        Matcher m = FROM_OR_JOIN.matcher(query);
        while (m.find()) {
            String table = m.group(2) != null ? m.group(2) : m.group(1);
            String alias = m.group(3);
            if (alias != null && !NOT_ALIASES.contains(alias.toUpperCase(Locale.ROOT))) {
                map.put(alias.toLowerCase(Locale.ROOT), table);
            }
        }
        return map;
    }

    private static PreparedStatement prepare(Connection conn, String sql) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setQueryTimeout(DBConnection.QUERY_TIMEOUT_SECONDS);
        return ps;
    }
}

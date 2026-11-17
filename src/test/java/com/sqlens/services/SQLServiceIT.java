package com.sqlens.services;

import com.sqlens.MySqlTestBase;
import com.sqlens.models.ExplainRes;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SQLServiceIT extends MySqlTestBase {

    @Test
    void explainsFullTableScan() throws SQLException {
        List<ExplainRes> plan = SQLService.getExplainRes(conn, "SELECT * FROM film WHERE length > 100;");
        assertEquals(1, plan.size());
        assertEquals("film", plan.get(0).table());
        assertEquals("ALL", plan.get(0).type());
        assertNull(plan.get(0).key());
    }

    @Test
    void explainsIndexLookup() throws SQLException {
        List<ExplainRes> plan = SQLService.getExplainRes(conn, "SELECT * FROM actor WHERE last_name = 'CHASE'");
        assertEquals("ref", plan.get(0).type());
        assertEquals("idx_actor_last_name", plan.get(0).key());
        assertNotNull(plan.get(0).keyLen());
    }

    @Test
    void handlesJoinsAndNullIds() throws SQLException {
        List<ExplainRes> plan = SQLService.getExplainRes(conn,
            "SELECT title FROM film UNION SELECT last_name FROM actor");
        assertTrue(plan.stream().anyMatch(r -> r.id() == null), "UNION RESULT row has NULL id");
    }

    @Test
    void rejectsWritesBeforeTheyReachTheServer() {
        assertThrows(InvalidQueryException.class, () -> SQLService.getExplainRes(conn, "DELETE FROM film"));
        assertThrows(InvalidQueryException.class, () -> SQLService.getExplainRes(conn, "SELECT 1; DROP TABLE film"));
    }

    @Test
    void sessionIsReadOnly() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            SQLException e = assertThrows(SQLException.class,
                () -> stmt.executeUpdate("INSERT INTO actor (first_name, last_name) VALUES ('X', 'Y')"));
            assertTrue(e.getMessage().toLowerCase().contains("read"), e.getMessage());
        }
    }

    @Test
    void surfacesSqlErrors() {
        SQLException e = assertThrows(SQLException.class,
            () -> SQLService.getExplainRes(conn, "SELECT * FROM no_such_table"));
        assertTrue(e.getMessage().contains("no_such_table"));
    }
}

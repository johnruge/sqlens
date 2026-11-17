package com.sqlens.services;

import com.sqlens.MySqlTestBase;
import com.sqlens.models.TableInfo;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaServiceIT extends MySqlTestBase {

    @Test
    void listsTables() throws SQLException {
        List<String> names = SchemaService.listTables(conn).stream().map(TableInfo::name).toList();
        assertEquals(List.of("actor", "film", "film_actor"), names);
    }

    @Test
    void describesColumnsAndIndexes() throws SQLException {
        TableInfo t = SchemaService.describe(conn, "film_actor").orElseThrow();
        assertEquals(2, t.columns().size());
        TableInfo.Index pk = t.indexes().get(0);
        assertEquals("PRIMARY", pk.name());
        assertTrue(pk.unique());
        assertEquals(List.of("actor_id", "film_id"), pk.columns());
    }

    @Test
    void unknownOrMaliciousNamesReturnEmpty() throws SQLException {
        assertTrue(SchemaService.describe(conn, "nope").isEmpty());
        assertTrue(SchemaService.describe(conn, "film' OR '1'='1").isEmpty());
    }

    @Test
    void describesTablesBehindAliases() throws SQLException {
        String q = "SELECT f.title FROM film f JOIN film_actor fa ON fa.film_id = f.film_id";
        var plan = SQLService.getExplainRes(conn, q);
        var tables = SchemaService.describePlanTables(conn, q, plan).stream().map(TableInfo::name).toList();
        assertTrue(tables.containsAll(List.of("film", "film_actor")), tables.toString());
    }
}

package com.sqlens.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaServiceTest {

    @Test
    void resolvesAliases() {
        Map<String, String> a = SchemaService.aliases(
            "SELECT f.title FROM film f JOIN film_actor AS fa ON fa.film_id = f.film_id "
                + "LEFT JOIN `actor` a USING (actor_id) WHERE a.last_name = 'X'");
        assertEquals("film", a.get("f"));
        assertEquals("film_actor", a.get("fa"));
        assertEquals("actor", a.get("a"));
    }

    @Test
    void ignoresKeywordsAfterTableName() {
        Map<String, String> a = SchemaService.aliases("SELECT * FROM film WHERE film_id = 1");
        assertFalse(a.containsKey("where"));
    }

    @Test
    void handlesSchemaQualifiedNames() {
        assertEquals("film", SchemaService.aliases("SELECT * FROM sakila.film f").get("f"));
    }
}

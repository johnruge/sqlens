package com.sqlens.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuerySplitterTest {

    @Test
    void splitsOnSemicolonsOutsideLiteralsAndComments() {
        String script = """
            -- first query; still a comment
            SELECT 'a;b' FROM film;
            /* block ; comment */
            SELECT `weird;name` FROM actor  ;
            SELECT "x" -- trailing ; comment
            ;
            -- trailing comment only
            """;
        List<String> q = QuerySplitter.split(script);
        assertEquals(3, q.size(), q.toString());
        assertTrue(q.get(0).endsWith("SELECT 'a;b' FROM film"));
        assertTrue(q.get(1).contains("`weird;name`"));
    }

    @Test
    void emptyScript() {
        assertTrue(QuerySplitter.split("  \n -- nothing\n").isEmpty());
    }
}

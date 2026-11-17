package com.sqlens.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class QueryValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT * FROM film",
        "select title from film where film_id = 1;",
        "SELECT * FROM film;;  ",
        "  (SELECT 1) UNION (SELECT 2)",
        "WITH c AS (SELECT * FROM customer) SELECT * FROM c",
        "SELECT 'a;b', \"drop table x\" FROM film",
        "SELECT `update`, `delete` FROM t",
        "SELECT last_update, created_by FROM film",
        "SELECT 'it''s; fine' FROM film",
        "SELECT 'C:\\\\path' FROM film",
        "SELECT * FROM film -- trailing comment; with semicolon",
        "SELECT * FROM film # mysql comment",
        "SELECT /*+ NO_INDEX(film) */ * FROM film",
        "/* leading */ SELECT 1",
        "SELECT * FROM film FOR UPDATE",
        "SELECT * FROM film;\n -- just a comment after"
    })
    void acceptsReadOnlySelects(String sql) {
        assertDoesNotThrow(() -> QueryValidator.validate(sql));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "-- only a comment",
        "DELETE FROM film",
        "UPDATE film SET title = 'x'",
        "DROP TABLE film",
        "ANALYZE SELECT * FROM film",
        "SHOW TABLES",
        "SELECT 1; DROP TABLE film",
        "SELECT 1; SELECT 2",
        "SELECT 1;'x'",
        "SELECT * FROM film INTO OUTFILE '/tmp/x'",
        "SELECT * INTO @x FROM film",
        "WITH c AS (SELECT 1) DELETE FROM film",
        "SELECT /*! 1; DROP TABLE film */ 1",
        "SELECT 'unterminated",
        "SELECT `unterminated",
        "SELECT /* unterminated",
        "SELECT 1 /* */; DROP TABLE film",
        // one literal with backslash escapes, two statements without: rejected either way
        "SELECT 'a\\'; DROP TABLE film; -- '",
    })
    void rejectsEverythingElse(String sql) {
        assertThrows(InvalidQueryException.class, () -> QueryValidator.validate(sql), sql);
    }

    @Test
    void stripsTrailingSemicolons() {
        assertEquals("SELECT 1", QueryValidator.validate("  SELECT 1 ; ; "));
    }

    @Test
    void rejectsOversizedInput() {
        String huge = "SELECT " + "1,".repeat(QueryValidator.MAX_QUERY_LENGTH) + "1";
        assertThrows(InvalidQueryException.class, () -> QueryValidator.validate(huge));
    }

    @Test
    void nullIsRejected() {
        assertThrows(InvalidQueryException.class, () -> QueryValidator.validate(null));
    }
}

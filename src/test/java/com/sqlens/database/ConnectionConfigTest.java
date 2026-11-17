package com.sqlens.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionConfigTest {

    @Test
    void parsesPlainUrl() {
        ConnectionConfig c = ConnectionConfig.fromUrl("jdbc:mysql://localhost:3307/sakila");
        assertEquals("localhost", c.host());
        assertEquals(3307, c.port());
        assertEquals("sakila", c.database());
        assertEquals("jdbc:mysql://localhost:3307/sakila", c.jdbcUrl());
    }

    @Test
    void defaultsPortTo3306() {
        assertEquals(3306, ConnectionConfig.fromUrl("jdbc:mysql://db.local/sakila").port());
    }

    @Test
    void detectsLoopbackHosts() {
        assertTrue(new ConnectionConfig("localhost", 3306, "db").isLoopback());
        assertTrue(new ConnectionConfig("127.0.0.1", 3306, "db").isLoopback());
        assertFalse(new ConnectionConfig("db.example.com", 3306, "db").isLoopback());
        assertFalse(new ConnectionConfig("10.0.0.5", 3306, "db").isLoopback());
        assertFalse(new ConnectionConfig("127.evil.com", 3306, "db").isLoopback());
    }

    @Test
    void rejectsUrlParameters() {
        assertThrows(IllegalArgumentException.class,
            () -> ConnectionConfig.fromUrl("jdbc:mysql://localhost/sakila?allowLoadLocalInfile=true"));
        assertThrows(IllegalArgumentException.class,
            () -> ConnectionConfig.fromUrl("jdbc:mysql://localhost/sakila?autoDeserialize=true"));
    }

    @Test
    void rejectsInjectionThroughHostOrDatabase() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConnectionConfig("evil/?autoDeserialize=true#", 3306, "sakila"));
        assertThrows(IllegalArgumentException.class,
            () -> new ConnectionConfig("localhost", 3306, "sakila?allowMultiQueries=true"));
        assertThrows(IllegalArgumentException.class,
            () -> new ConnectionConfig("localhost", 0, "sakila"));
    }
}

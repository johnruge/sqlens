package com.sqlens.cli;

import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the REPL with scripted keyboard input. The connection is a fake that records
 * every call and fails like an unreachable server, so we can check which input
 * reaches the database at all.
 */
class ReplTest {

    private final List<String> dbCalls = new ArrayList<>();

    private Connection fakeConnection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                dbCalls.add(method.getName());
                throw new SQLException("Communications link failure (fake)");
            });
    }

    private String run(String input) throws IOException {
        Terminal terminal = TerminalBuilder.builder().dumb(true).system(false)
            .streams(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream())
            .build();
        LineReader reader = Readers.create(terminal, null);
        StringWriter sw = new StringWriter();
        new Repl(fakeConnection(), reader, new PrintWriter(sw), new Style(false), null).run();
        terminal.close();
        return sw.toString();
    }

    @Test
    void helpAndExit() throws IOException {
        String out = run("help\nexit\n");
        assertTrue(out.contains("\\tables"));
        assertTrue(out.trim().endsWith("bye"));
        assertTrue(dbCalls.isEmpty());
    }

    @Test
    void endOfInputQuitsCleanly() throws IOException {
        assertTrue(run("").trim().endsWith("bye"));
    }

    @Test
    void rejectedQueriesNeverTouchTheDatabase() throws IOException {
        String out = run("DROP TABLE film;\nSELECT 1; DELETE FROM film;\nexit\n");
        assertEquals(2, out.split("rejected:", -1).length - 1, out);
        assertTrue(dbCalls.isEmpty(), "database was called: " + dbCalls);
    }

    @Test
    void multiLineQueryRunsOnceAtSemicolonAndErrorsDoNotKillTheLoop() throws IOException {
        String out = run("SELECT *\nFROM film\nWHERE film_id = 1;\nhelp\nquit\n");
        assertEquals(1, dbCalls.stream().filter("createStatement"::equals).count(), dbCalls.toString());
        assertTrue(out.contains("error: Communications link failure (fake)"));
        assertTrue(out.contains("\\tables"), "REPL kept running after the error");
        assertFalse(out.contains("Exception"), "no stack trace");
    }

    @Test
    void unknownAndAiCommands() throws IOException {
        String out = run("\\nope\n\\ai on\nexit\n");
        assertTrue(out.contains("unknown command \\nope"));
        assertTrue(out.contains("AI is not configured"));
    }

    @Test
    void readerKeepsBackslashesAndBangsExactly() throws IOException {
        String typed = "SELECT 'C:\\path\\x' FROM t WHERE a != 1 AND b = '!!' -- \\q";
        Terminal terminal = TerminalBuilder.builder().dumb(true).system(false)
            .streams(new ByteArrayInputStream((typed + "\n" + "pa\\ss!word\n").getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream())
            .build();
        LineReader reader = Readers.create(terminal, null);
        assertEquals(typed, reader.readLine("> "));
        assertEquals("pa\\ss!word", reader.readLine("Password (hidden): ", '*'));
    }

    @Test
    void metaCommandErrorsAreReported() throws IOException {
        String out = run("\\tables\n\\d film\nexit\n");
        assertEquals(2, out.split("error:", -1).length - 1, out);
    }

    @Test
    void escapeSequencesInServerErrorsAreStripped() throws IOException {
        Terminal terminal = TerminalBuilder.builder().dumb(true).system(false)
            .streams(new ByteArrayInputStream("SELECT 1;\nexit\n".getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream())
            .build();
        Connection evil = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
            (p, m, a) -> { throw new SQLException("bad \u001b]0;owned\u0007\u001b[2Jthing"); });
        StringWriter sw = new StringWriter();
        new Repl(evil, Readers.create(terminal, null), new PrintWriter(sw), new Style(false), null).run();
        assertTrue(sw.toString().contains("error: bad thing"), sw.toString());
        assertFalse(sw.toString().contains("\u001b"));
    }
}

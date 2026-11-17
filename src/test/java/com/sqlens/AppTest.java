package com.sqlens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    void readsScriptIntoQueries(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("q.sql");
        Files.writeString(f, "SELECT 1; SELECT 2;");
        assertEquals(2, App.readScript(f).size());
    }

    @Test
    void rejectsMissingOrEmptyScripts(@TempDir Path dir) throws IOException {
        assertThrows(IllegalArgumentException.class, () -> App.readScript(dir.resolve("missing.sql")));
        assertThrows(IllegalArgumentException.class, () -> App.readScript(dir));
        Path empty = Files.writeString(dir.resolve("e.sql"), "-- nothing");
        assertThrows(IllegalArgumentException.class, () -> App.readScript(empty));
    }

    @Test
    void executeAndFileAreMutuallyExclusive() {
        StringWriter err = new StringWriter();
        int code = new CommandLine(new App()).setErr(new PrintWriter(err))
            .execute("-e", "SELECT 1", "-f", "x.sql");
        assertEquals(2, code);
        assertTrue(err.toString().contains("mutually exclusive"));
    }

    @Test
    void failOnRequiresOneShotMode() {
        assertEquals(2, new CommandLine(new App()).execute("--fail-on", "HIGH", "--no-ai"));
    }
}

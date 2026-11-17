package com.sqlens.cli;

import com.sqlens.analysis.Finding;
import com.sqlens.analysis.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FindingsFormatterTest {

    @Test
    void plainOutputHasNoEscapeCodes() {
        String out = FindingsFormatter.format(
            List.of(new Finding(Severity.HIGH, "film", "Full scan.", "Add an index.")), new Style(false));
        assertTrue(out.contains("HIGH"));
        assertTrue(out.contains("[film] Full scan."));
        assertTrue(out.contains("-> Add an index."));
        assertFalse(out.contains("\u001b"));
    }

    @Test
    void colouredOutputUsesAnsi() {
        String out = FindingsFormatter.format(
            List.of(new Finding(Severity.INFO, null, "ok", "none")), new Style(true));
        assertTrue(out.contains("\u001b[36m"));
    }

    @Test
    void stripsEscapesFromDatabaseSuppliedText() {
        String out = FindingsFormatter.format(List.of(new Finding(Severity.HIGH, "t\u001b[2J",
            "index idx\u001b]0;pwn\u0007 ignored", "fix")), new Style(false));
        assertFalse(out.contains("\u001b"));
        assertFalse(out.contains("\u0007"));
    }

    @Test
    void emptyFindings() {
        assertTrue(FindingsFormatter.format(List.of(), new Style(false)).startsWith("No problems found"));
    }
}

package com.sqlens.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalTextTest {

    @Test
    void stripsEscapesKeepsNewlinesAndTabs() {
        assertEquals("red\n\tok", TerminalText.clean("\u001b[31mred\u001b[0m\n\tok"));
        assertEquals("title", TerminalText.clean("\u001b]0;pwned\u0007title"));
        assertEquals("ab", TerminalText.clean("a\u0007\u001b[2Jb"));
        assertNull(TerminalText.clean(null));
    }
}

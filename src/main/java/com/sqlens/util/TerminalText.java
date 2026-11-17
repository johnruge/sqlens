package com.sqlens.util;

/**
 * Removes terminal escape sequences and control characters from text that did not
 * come from sqlens itself (database identifiers, server messages, AI output), so it
 * cannot recolour, move the cursor, retitle or otherwise manipulate the terminal.
 */
public final class TerminalText {

    private TerminalText() {}

    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        return text
            .replaceAll("\u001b\\][^\u0007\u001b]*(\u0007|\u001b\\\\)?", "")   // OSC sequences
            .replaceAll("\u001b\\[[0-9;?]*[ -/]*[@-~]", "")                    // CSI sequences
            .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");                         // other control chars
    }
}

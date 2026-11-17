package com.sqlens.cli;

import com.sqlens.analysis.Severity;

/** ANSI colours, switched off with --no-color or on dumb terminals. */
public final class Style {

    private final boolean enabled;

    public Style(boolean enabled) {
        this.enabled = enabled;
    }

    public String severity(Severity s) {
        String label = String.format("%-6s", s.name());
        return switch (s) {
            case HIGH -> wrap("1;31", label);
            case MEDIUM -> wrap("33", label);
            case INFO -> wrap("36", label);
        };
    }

    public String bold(String text) {
        return wrap("1", text);
    }

    public String dim(String text) {
        return wrap("2", text);
    }

    public String error(String text) {
        return wrap("31", text);
    }

    private String wrap(String code, String text) {
        return enabled ? "\u001b[" + code + "m" + text + "\u001b[0m" : text;
    }
}

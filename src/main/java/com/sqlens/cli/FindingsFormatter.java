package com.sqlens.cli;

import com.sqlens.analysis.Finding;
import com.sqlens.util.TerminalText;

import java.util.List;

public final class FindingsFormatter {

    private FindingsFormatter() {}

    public static String format(List<Finding> findings, Style style) {
        if (findings.isEmpty()) {
            return "No problems found: every table is read through an index.\n";
        }
        StringBuilder sb = new StringBuilder(style.bold("Findings") + "\n");
        for (Finding f : findings) {
            sb.append("  ").append(style.severity(f.severity())).append(' ');
            if (f.table() != null) {
                sb.append('[').append(TableFormatter.cell(f.table())).append("] ");
            }
            sb.append(TerminalText.clean(f.problem())).append('\n');
            sb.append("         ").append(style.dim("-> " + TerminalText.clean(f.suggestion()))).append('\n');
        }
        return sb.toString();
    }
}

package com.sqlens.cli;

import com.sqlens.models.ExplainRes;

import java.util.ArrayList;
import java.util.List;

/** Renders rows as a plain-text table, like the mysql client does. */
public final class TableFormatter {

    static final int MAX_CELL_WIDTH = 60;

    private TableFormatter() {}

    public static String format(List<String> headers, List<List<String>> rows) {
        int cols = headers.size();
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = headers.get(i).length();
        }
        List<List<String>> cells = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> clean = new ArrayList<>();
            for (int i = 0; i < cols; i++) {
                String v = i < row.size() ? cell(row.get(i)) : "";
                clean.add(v);
                widths[i] = Math.max(widths[i], v.length());
            }
            cells.add(clean);
        }

        StringBuilder sb = new StringBuilder();
        String sep = separator(widths);
        sb.append(sep).append(line(headers, widths)).append(sep);
        for (List<String> row : cells) {
            sb.append(line(row, widths));
        }
        sb.append(sep);
        return sb.toString();
    }

    public static String formatPlan(List<ExplainRes> plan) {
        List<String> headers = List.of("id", "select_type", "table", "type", "possible_keys",
            "key", "key_len", "ref", "rows", "filtered", "Extra");
        List<List<String>> rows = new ArrayList<>();
        for (ExplainRes r : plan) {
            rows.add(java.util.Arrays.asList(
                str(r.id()), r.selectType(), r.table(), r.type(), r.possibleKeys(), r.key(),
                r.keyLen(), r.ref(), str(r.rows()), str(r.filtered()), r.extra()));
        }
        return format(headers, rows);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** NULL for SQL nulls, control characters stripped so output cannot mess up the terminal. */
    static String cell(String v) {
        if (v == null) {
            return "NULL";
        }
        String s = v.replaceAll("\\p{Cntrl}", " ");
        return s.length() > MAX_CELL_WIDTH ? s.substring(0, MAX_CELL_WIDTH - 3) + "..." : s;
    }

    private static String separator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) {
            sb.append("-".repeat(w + 2)).append('+');
        }
        return sb.append('\n').toString();
    }

    private static String line(List<String> values, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String v = values.get(i);
            sb.append(' ').append(v).append(" ".repeat(widths[i] - v.length())).append(" |");
        }
        return sb.append('\n').toString();
    }
}

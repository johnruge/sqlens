package com.sqlens.services;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks that user input is exactly one read-only SELECT (or WITH ... SELECT) statement
 * before sqlens prefixes it with EXPLAIN.
 *
 * Why not a PreparedStatement? Placeholders can only stand in for values, never for a
 * whole query, so the query text itself has to be validated. This is one layer of
 * several: the session is also read-only, the DB user should only have SELECT, and the
 * driver refuses multi-statement strings.
 *
 * The scanner understands MySQL string literals ('..', "..", optional backslash escapes),
 * quoted identifiers (`..`) and comments (--, #, block comments), so a ';' or keyword
 * inside a string does not cause a false alarm, and one hidden in a comment trick does
 * not slip through.
 */
public final class QueryValidator {

    public static final int MAX_QUERY_LENGTH = 100_000;

    private static final Pattern FIRST_WORD = Pattern.compile("^\\(*\\s*(SELECT|WITH)\\b.*", Pattern.DOTALL);
    private static final Pattern FORBIDDEN = Pattern.compile(
        "\\b(INSERT|UPDATE|DELETE|REPLACE|DROP|ALTER|CREATE|TRUNCATE|RENAME|GRANT|REVOKE"
            + "|CALL|LOAD|HANDLER|INTO|ANALYZE|OPTIMIZE|KILL|SHUTDOWN|LOCK|UNLOCK)\\b");
    // "SELECT ... FOR UPDATE" / "LOCK IN SHARE MODE" are harmless under EXPLAIN (nothing runs).
    private static final Pattern LOCKING_CLAUSE = Pattern.compile(
        "\\bFOR\\s+UPDATE\\b|\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b");

    private QueryValidator() {}

    /**
     * @return the query with trailing semicolons and surrounding whitespace removed
     * @throws InvalidQueryException if the input is not a single read-only SELECT
     */
    public static String validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new InvalidQueryException("empty query");
        }
        if (sql.length() > MAX_QUERY_LENGTH) {
            throw new InvalidQueryException("query is too long (max " + MAX_QUERY_LENGTH + " characters)");
        }

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if ((c < 0x20 && c != '\t' && c != '\n' && c != '\r') || c == 0x7f) {
                throw new InvalidQueryException("control characters are not allowed (found U+"
                    + String.format("%04X", (int) c) + " at position " + i + ")");
            }
        }

        // Whether backslash escapes inside '..' and ".." depends on the server's sql_mode
        // (NO_BACKSLASH_ESCAPES, and ANSI_QUOTES makes ".." an identifier with no escapes).
        // Scan under every combination so input that is "one statement" under only some
        // readings is rejected.
        codeOnly(sql, false, false);
        codeOnly(sql, false, true);
        codeOnly(sql, true, false);
        String code = codeOnly(sql, true, true);   // literals and comments blanked out
        String upper = code.toUpperCase(Locale.ROOT).strip();
        if (upper.isEmpty()) {
            throw new InvalidQueryException("query contains only comments");
        }
        if (!FIRST_WORD.matcher(upper).matches()) {
            throw new InvalidQueryException("only SELECT queries can be explained (query must start with SELECT or WITH)");
        }
        String withoutLocking = LOCKING_CLAUSE.matcher(upper).replaceAll(" ");
        var forbidden = FORBIDDEN.matcher(withoutLocking);
        if (forbidden.find()) {
            throw new InvalidQueryException("'" + forbidden.group(1) + "' is not allowed: sqlens only explains read-only SELECT queries");
        }

        String cleaned = sql.strip();
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).stripTrailing();
        }
        return cleaned;
    }

    /**
     * Returns the input with string literals, quoted identifiers and comments replaced by
     * spaces. Rejects unterminated quotes/comments, MySQL executable comments, and any ';'
     * that is followed by more code.
     */
    static String codeOnly(String sql, boolean singleQuoteEscapes, boolean doubleQuoteEscapes) {
        StringBuilder code = new StringBuilder(sql.length());
        int n = sql.length();
        int i = 0;
        boolean sawTerminator = false;

        while (i < n) {
            char c = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : '\0';

            if (c == '\'' || c == '"' || c == '`') {
                int end = skipQuoted(sql, i, c, c == '\'' ? singleQuoteEscapes : c == '"' && doubleQuoteEscapes);
                if (sawTerminator) {
                    throw new InvalidQueryException("only one statement is allowed");
                }
                code.append(" ".repeat(end - i));
                i = end;
                continue;
            }
            // MySQL only starts a "--" comment when an ASCII space/tab/newline follows. Other
            // control characters were rejected above; anything else (e.g. U+2028) keeps "--"
            // as two minus signs, exactly as the server reads it.
            if (c == '#' || (c == '-' && next == '-' && (i + 2 >= n || isSqlSpace(sql.charAt(i + 2))))) {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                code.append(" ".repeat(end - i));
                i = end;
                continue;
            }
            if (c == '/' && next == '*') {
                if (sql.startsWith("/*!", i) || sql.regionMatches(true, i, "/*M!", 0, 4)) {
                    throw new InvalidQueryException("executable comments (/*! ... */, /*M! ... */) are not allowed");
                }
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) {
                    throw new InvalidQueryException("unterminated comment");
                }
                code.append(" ".repeat(end + 2 - i));
                i = end + 2;
                continue;
            }
            if (c == ';') {
                sawTerminator = true;
                code.append(' ');
                i++;
                continue;
            }
            if (sawTerminator && !isSqlSpace(c)) {
                throw new InvalidQueryException("only one statement is allowed");
            }
            code.append(c);
            i++;
        }
        return code.toString();
    }

    private static boolean isSqlSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    /** @return index just past the closing quote */
    private static int skipQuoted(String sql, int start, char quote, boolean backslashEscapes) {
        int i = start + 1;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (backslashEscapes && c == '\\') {
                i += 2;            // backslash escape inside string literals
                continue;
            }
            if (c == quote) {
                if (i + 1 < n && sql.charAt(i + 1) == quote) {
                    i += 2;        // doubled quote is an escaped quote
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        throw new InvalidQueryException("unterminated " + (quote == '`' ? "identifier" : "string") + " starting at position " + start);
    }
}

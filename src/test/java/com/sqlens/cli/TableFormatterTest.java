package com.sqlens.cli;

import com.sqlens.models.ExplainRes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableFormatterTest {

    @Test
    void formatsNullsAndAlignsColumns() {
        ExplainRes row = new ExplainRes(1, "SIMPLE", "film", "ALL", null, null, null, null,
            1000L, 10.0, "Using where");
        String out = TableFormatter.formatPlan(List.of(row));
        String[] lines = out.split("\n");
        assertEquals(5, lines.length);
        assertTrue(lines[1].contains("select_type"));
        assertTrue(lines[3].contains("NULL"));
        assertTrue(lines[3].contains("Using where"));
        // every line has the same width
        for (String l : lines) {
            assertEquals(lines[0].length(), l.length());
        }
    }

    @Test
    void truncatesLongCellsAndStripsControlChars() {
        String longValue = "x".repeat(200);
        assertEquals(TableFormatter.MAX_CELL_WIDTH, TableFormatter.cell(longValue).length());
        assertEquals("ab", TableFormatter.cell("a\u001bb"));
        assertEquals("a b", TableFormatter.cell("a\nb"));
        assertEquals("ab", TableFormatter.cell("a\u001b[31mb"));
    }
}

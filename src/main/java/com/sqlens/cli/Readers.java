package com.sqlens.cli;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;

import java.nio.file.Path;

/**
 * Builds JLine readers that return exactly what was typed. JLine's defaults treat '\\'
 * as an escape character and '!' as history expansion, which would break \commands,
 * change backslashes inside SQL strings and passwords, and mangle "!=".
 */
public final class Readers {

    private Readers() {}

    public static LineReader create(Terminal terminal, Path historyFile) {
        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(null);
        parser.setEofOnUnclosedQuote(false);
        parser.setEofOnEscapedNewLine(false);

        LineReaderBuilder builder = LineReaderBuilder.builder()
            .terminal(terminal)
            .parser(parser)
            .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true);
        if (historyFile != null) {
            builder.variable(LineReader.HISTORY_FILE, historyFile);
        }
        return builder.build();
    }
}

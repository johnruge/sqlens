package com.sqlens;

import picocli.CommandLine.Command;

@Command(
        name = "sqlens",
        description = "sql analyser cli",
        mixinStandardHelpOptions = true,
        subcommands = {
        }
)
public class RootCommand implements Runnable {
    public void run() {
        System.out.println("Use a subcommand. Try --help.");
    }
}

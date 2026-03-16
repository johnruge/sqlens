package com.sqlens;

import java.io.Console;
import java.util.Scanner;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "shell", description = "initiates the sqlens shell")
public class App implements Runnable {

    @Option(names = {"--url"}, description = "database url")
    private String url;

    public void run() {
        // get authentication info, and make connection
        Console console = System.console();
        String user = console.readLine("user: ");
        char [] password = console.readPassword("password: ");

        try {
            Connection conn = Database.getConnection(url, user, password);

            //REPL loop
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("sqlens> ");

                String input = scanner.nextLine();

                if (input.equals("exit")) {
                    break;
                } //TODO: add help logic later

                //for now, I'll just print the sql output
                //i will abstract this to the service layer in future commits
                //handle_request()
                //-will take the request and get all ANALYZE information
                //-get info about the tables
                //-send to chat/gemini for analysis and suggestions
                //-give an agent tools they can use (with limited access ofc) - future
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(input);

                while (rs.next()) {
                    System.out.println(rs.getString("first_name"));
                }

                //output the the analysis from LLM
            }
            scanner.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main( String[] args ) {
        new CommandLine(new App()).execute(args);
    }
}

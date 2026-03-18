package com.sqlens;

import java.io.Console;
import java.util.Scanner;
import java.util.List;

import com.sqlens.database.DBConnection;
import com.sqlens.service.ExplainService;
import com.sqlens.models.ExplainRes;

import java.sql.Connection;
import java.sql.SQLException;

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
            Connection conn = DBConnection.getConnection(url, user, password);

            //REPL loop
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("sqlens> ");

                String input = scanner.nextLine();

                if (input.equals("exit")) {
                    break;
                } //TODO: add help logic later

                List<ExplainRes> list_res = ExplainService.getExplainRes(conn, input);
                System.out.print(list_res.getFirst());

                //TODO
                //-get info about the tables
                //-send to chat/gemini for analysis and suggestions
                //-give an agent tools they can use (with limited access ofc) - future

                //get the final suggestion from the agent
            }
            scanner.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main( String[] args ) {
        new CommandLine(new App()).execute(args);
    }
}

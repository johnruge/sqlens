package com.sqlens.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

//consider using a connection pool for batch requests. this is not necessary for the REPL impl
public class DBConnection {
    public static Connection getConnection(String url, String user, char [] password)
    throws SQLException {
        return DriverManager.getConnection(url, user, new String(password));
    }
}

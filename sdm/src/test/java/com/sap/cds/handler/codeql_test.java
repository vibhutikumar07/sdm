package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the username: ");
        String username = scanner.nextLine();

        // Vulnerable code: SQL Injection
        String query = "SELECT * FROM users WHERE username='" + username + "'";

        try {
            Connection connection = getConnection();
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);

            while (resultSet.next()) {
                System.out.println("User ID: " + resultSet.getInt("id"));
                System.out.println("Username: " + resultSet.getString("username"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static Connection getConnection() throws SQLException {
        String dbUrl = "jdbc:mysql://localhost:3306/mydatabase";
        String dbUsername = "root";
        String dbPassword = "password";
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }
}

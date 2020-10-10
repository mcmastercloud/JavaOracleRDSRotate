package com.mcmaster.aws.lambda.oracleRdsRotate;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.sql.*;

public class Oracle {

    public static Connection connect(String host, String dbname, String username, String password) throws java.sql.SQLException, java.lang.ClassNotFoundException, JsonProcessingException {

        DriverManager.registerDriver(new oracle.jdbc.OracleDriver());
        String sConnection = String.format("jdbc:oracle:thin:@%s:1521:%s", host, dbname);
        Connection nconn = DriverManager.getConnection(
                sConnection,
                username,
                password
        );

        System.out.println(String.format("Connection String: '%s'", sConnection));
        System.out.println("Successfully connected to Oracle");

        return nconn;
    }

}

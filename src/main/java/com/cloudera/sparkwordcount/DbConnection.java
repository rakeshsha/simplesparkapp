package com.cloudera.sparkwordcount;

import scala.runtime.AbstractFunction0;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Created by cloudera on 8/16/17.
 */

class DbConnection extends AbstractFunction0<Connection> implements Serializable {

    private String driverClassName;
    private String connectionUrl;
    private String userName;
    private String password;

    public DbConnection(String driverClassName, String connectionUrl, String userName, String password) {
        this.driverClassName = driverClassName;
        this.connectionUrl = connectionUrl;
        this.userName = userName;
        this.password = password;
    }

    @Override
    public Connection apply() {
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            System.out.println("Failed to load driver class"+e.toString());
        }

        Properties properties = new Properties();
        properties.setProperty("user", userName);
        properties.setProperty("password", password);

        Connection connection = null;
        try {
            connection = DriverManager.getConnection(connectionUrl, properties);
        } catch (SQLException e) {
            System.out.println("Connection failed"+ e.toString());
        }

        return connection;
    }
}

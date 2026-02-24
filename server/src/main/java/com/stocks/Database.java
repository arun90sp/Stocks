package com.stocks;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private static final String URL = "jdbc:h2:file:./stocksdb;AUTO_SERVER=TRUE";
    private static final String USER = "sa";
    private static final String PASS = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public static void initSchema() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS demat_accounts (
                    id IDENTITY PRIMARY KEY,
                    account_name VARCHAR(200) UNIQUE NOT NULL,
                    broker VARCHAR(200) NOT NULL,
                    account_number VARCHAR(200) NOT NULL,
                    owner_name VARCHAR(200) NOT NULL,
                    currency VARCHAR(20) NOT NULL DEFAULT 'INR'
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS instruments (
                    id IDENTITY PRIMARY KEY,
                    symbol VARCHAR(30) NOT NULL,
                    exchange VARCHAR(30) NOT NULL,
                    CONSTRAINT uq_instrument UNIQUE(symbol, exchange)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS trades (
                    id IDENTITY PRIMARY KEY,
                    account_name VARCHAR(200) NOT NULL,
                    symbol VARCHAR(30) NOT NULL,
                    exchange VARCHAR(30) NOT NULL,
                    trade_date DATE NOT NULL,
                    side VARCHAR(10) NOT NULL,
                    quantity DOUBLE NOT NULL,
                    price DOUBLE NOT NULL,
                    fees DOUBLE DEFAULT 0,
                    taxes DOUBLE DEFAULT 0,
                    order_id VARCHAR(100),
                    notes VARCHAR(1000)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cash_flows (
                    id IDENTITY PRIMARY KEY,
                    account_name VARCHAR(200) NOT NULL,
                    flow_date DATE NOT NULL,
                    flow_type VARCHAR(20) NOT NULL,
                    amount DOUBLE NOT NULL,
                    reference VARCHAR(200),
                    notes VARCHAR(1000)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS income_events (
                    id IDENTITY PRIMARY KEY,
                    account_name VARCHAR(200) NOT NULL,
                    symbol VARCHAR(30),
                    exchange VARCHAR(30),
                    income_date DATE NOT NULL,
                    income_type VARCHAR(20) NOT NULL,
                    amount DOUBLE NOT NULL,
                    notes VARCHAR(1000)
                )
            """);
        }
    }
}

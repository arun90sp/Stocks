package com.stocks;

import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/*")
public class ApiServlet extends HttpServlet {
    private final Gson gson = new Gson();

    @Override
    public void init() {
        try {
            Database.initSchema();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        setCors(resp);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCors(resp);
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();

        try {
            if ("/init".equals(path)) {
                Database.initSchema();
                writeJson(resp, Map.of("message", "Schema initialized"));
                return;
            }

            if (path.startsWith("/import/")) {
                String type = path.substring("/import/".length());
                String csvText = readBody(req);
                int count = importCsv(type, csvText);
                writeJson(resp, Map.of("message", "Imported", "count", count, "type", type));
                return;
            }

            resp.sendError(404, "Unsupported endpoint");
        } catch (Exception e) {
            resp.setStatus(400);
            writeJson(resp, Map.of("error", e.getMessage()));
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCors(resp);
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();

        try {
            if ("/summary".equals(path)) {
                writeJson(resp, getSummary());
                return;
            }
            resp.sendError(404, "Unsupported endpoint");
        } catch (Exception e) {
            resp.setStatus(400);
            writeJson(resp, Map.of("error", e.getMessage()));
        }
    }

    private int importCsv(String type, String csvText) throws SQLException {
        List<Map<String, String>> rows = parseCsv(csvText);
        int count = 0;

        try (Connection conn = Database.getConnection()) {
            for (Map<String, String> row : rows) {
                switch (type) {
                    case "demat_accounts" -> {
                        if (trim(row.get("account_name")).isEmpty()) continue;
                        upsertDemat(conn, row);
                    }
                    case "trades" -> {
                        if (trim(row.get("account_name")).isEmpty()) continue;
                        upsertInstrument(conn, trim(row.get("symbol")).toUpperCase(), upperOrDefault(row.get("exchange"), "NSE"));
                        insertTrade(conn, row);
                    }
                    case "cash_flows" -> {
                        if (trim(row.get("account_name")).isEmpty()) continue;
                        insertCashFlow(conn, row);
                    }
                    case "income" -> {
                        if (trim(row.get("account_name")).isEmpty()) continue;
                        String symbol = trim(row.get("symbol")).toUpperCase();
                        if (!symbol.isEmpty()) upsertInstrument(conn, symbol, upperOrDefault(row.get("exchange"), "NSE"));
                        insertIncome(conn, row);
                    }
                    default -> throw new IllegalArgumentException("Unsupported import type: " + type);
                }
                count++;
            }
        }

        return count;
    }

    private Map<String, Object> getSummary() throws SQLException {
        Map<String, Object> result = new HashMap<>();

        try (Connection conn = Database.getConnection()) {
            List<Map<String, Object>> accounts = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT account_name, broker, account_number, owner_name, currency FROM demat_accounts ORDER BY account_name");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    accounts.add(Map.of(
                            "account_name", rs.getString(1),
                            "broker", rs.getString(2),
                            "account_number", rs.getString(3),
                            "owner_name", rs.getString(4),
                            "currency", rs.getString(5)
                    ));
                }
            }

            List<Map<String, Object>> holdings = new ArrayList<>();
            String holdingsSql = """
                SELECT account_name, symbol, exchange,
                SUM(CASE WHEN side='BUY' THEN quantity ELSE -quantity END) AS net_quantity,
                SUM(CASE WHEN side='BUY' THEN quantity*price ELSE -quantity*price END) AS net_trade_value
                FROM trades
                GROUP BY account_name, symbol, exchange
                HAVING SUM(CASE WHEN side='BUY' THEN quantity ELSE -quantity END) <> 0
                ORDER BY account_name, symbol
            """;
            try (PreparedStatement ps = conn.prepareStatement(holdingsSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    holdings.add(Map.of(
                            "account_name", rs.getString(1),
                            "symbol", rs.getString(2),
                            "exchange", rs.getString(3),
                            "net_quantity", rs.getDouble(4),
                            "net_trade_value", rs.getDouble(5)
                    ));
                }
            }

            double netFunds = scalar(conn, "SELECT COALESCE(SUM(CASE WHEN flow_type='ADD' THEN amount ELSE -amount END),0) FROM cash_flows");
            double dividends = scalar(conn, "SELECT COALESCE(SUM(amount),0) FROM income_events WHERE income_type='DIVIDEND'");
            double interest = scalar(conn, "SELECT COALESCE(SUM(amount),0) FROM income_events WHERE income_type='INTEREST'");

            result.put("accounts", accounts);
            result.put("holdings", holdings);
            result.put("net_funds", netFunds);
            result.put("dividends", dividends);
            result.put("interest", interest);
        }

        return result;
    }

    private double scalar(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    private void upsertDemat(Connection conn, Map<String, String> row) throws SQLException {
        String sql = """
            MERGE INTO demat_accounts (account_name, broker, account_number, owner_name, currency)
            KEY (account_name) VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trim(row.get("account_name")));
            ps.setString(2, trim(row.get("broker")));
            ps.setString(3, trim(row.get("account_number")));
            ps.setString(4, trim(row.get("owner_name")));
            ps.setString(5, trimOrDefault(row.get("currency"), "INR"));
            ps.executeUpdate();
        }
    }

    private void upsertInstrument(Connection conn, String symbol, String exchange) throws SQLException {
        String sql = "MERGE INTO instruments (symbol, exchange) KEY (symbol, exchange) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setString(2, exchange);
            ps.executeUpdate();
        }
    }

    private void insertTrade(Connection conn, Map<String, String> row) throws SQLException {
        String sql = "INSERT INTO trades (account_name, symbol, exchange, trade_date, side, quantity, price, fees, taxes, order_id, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trim(row.get("account_name")));
            ps.setString(2, trim(row.get("symbol")).toUpperCase());
            ps.setString(3, upperOrDefault(row.get("exchange"), "NSE"));
            ps.setDate(4, Date.valueOf(trim(row.get("trade_date"))));
            ps.setString(5, trim(row.get("side")).toUpperCase());
            ps.setDouble(6, toDouble(row.get("quantity")));
            ps.setDouble(7, toDouble(row.get("price")));
            ps.setDouble(8, toDoubleOrDefault(row.get("fees"), 0));
            ps.setDouble(9, toDoubleOrDefault(row.get("taxes"), 0));
            ps.setString(10, emptyToNull(row.get("order_id")));
            ps.setString(11, emptyToNull(row.get("notes")));
            ps.executeUpdate();
        }
    }

    private void insertCashFlow(Connection conn, Map<String, String> row) throws SQLException {
        String sql = "INSERT INTO cash_flows (account_name, flow_date, flow_type, amount, reference, notes) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trim(row.get("account_name")));
            ps.setDate(2, Date.valueOf(trim(row.get("flow_date"))));
            ps.setString(3, trim(row.get("flow_type")).toUpperCase());
            ps.setDouble(4, toDouble(row.get("amount")));
            ps.setString(5, emptyToNull(row.get("reference")));
            ps.setString(6, emptyToNull(row.get("notes")));
            ps.executeUpdate();
        }
    }

    private void insertIncome(Connection conn, Map<String, String> row) throws SQLException {
        String symbol = trim(row.get("symbol")).toUpperCase();
        String sql = "INSERT INTO income_events (account_name, symbol, exchange, income_date, income_type, amount, notes) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trim(row.get("account_name")));
            ps.setString(2, symbol.isEmpty() ? null : symbol);
            ps.setString(3, symbol.isEmpty() ? null : upperOrDefault(row.get("exchange"), "NSE"));
            ps.setDate(4, Date.valueOf(trim(row.get("income_date"))));
            ps.setString(5, trim(row.get("income_type")).toUpperCase());
            ps.setDouble(6, toDouble(row.get("amount")));
            ps.setString(7, emptyToNull(row.get("notes")));
            ps.executeUpdate();
        }
    }

    private List<Map<String, String>> parseCsv(String csvText) {
        List<Map<String, String>> rows = new ArrayList<>();
        String[] lines = csvText.split("\\r?\\n");
        if (lines.length < 2) return rows;

        List<String> headers = parseCsvLine(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) continue;
            List<String> values = parseCsvLine(lines[i]);
            Map<String, String> map = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                map.put(headers.get(j), j < values.size() ? values.get(j) : "");
            }
            rows.add(map);
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                out.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        out.add(current.toString().trim());
        return out;
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void writeJson(HttpServletResponse resp, Object payload) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(gson.toJson(payload));
    }

    private void setCors(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimOrDefault(String value, String fallback) {
        String t = trim(value);
        return t.isEmpty() ? fallback : t;
    }

    private String upperOrDefault(String value, String fallback) {
        return trimOrDefault(value, fallback).toUpperCase();
    }

    private String emptyToNull(String value) {
        String t = trim(value);
        return t.isEmpty() ? null : t;
    }

    private double toDouble(String value) {
        return Double.parseDouble(trim(value));
    }

    private double toDoubleOrDefault(String value, double fallback) {
        String t = trim(value);
        return t.isEmpty() ? fallback : Double.parseDouble(t);
    }
}

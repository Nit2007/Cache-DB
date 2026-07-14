package com.cachedb.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Minimal JDBC wrapper for MySQL.
 * Single connection, prepared statements, no ORM.
 * The table schema is auto-created on connect if missing.
 */
public class MysqlClient {

    private Connection conn;
    private final String url;
    private final String user;
    private final String password;

    private PreparedStatement loadStmt;
    private PreparedStatement saveStmt;
    private PreparedStatement removeStmt;

    public MysqlClient(String host, int port, String database, String user, String password) {
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        this.user = user;
        this.password = password;
    }

    public void connect() throws SQLException {
        conn = DriverManager.getConnection(url, user, password);
        conn.setAutoCommit(true);
        initSchema();
        prepareStatements();
    }

    private void initSchema() throws SQLException {
        // VARBINARY(512) for keys, LONGBLOB for values — binary safe
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS kv_store ("
            + "  `key` VARBINARY(512) NOT NULL,"
            + "  `value` LONGBLOB NOT NULL,"
            + "  PRIMARY KEY (`key`)"
            + ") ENGINE=InnoDB"
        );
    }

    private void prepareStatements() throws SQLException {
        loadStmt = conn.prepareStatement("SELECT `value` FROM kv_store WHERE `key` = ?");
        // REPLACE INTO: deletes existing row with same PK then inserts — simpler than INSERT ON DUPLICATE KEY UPDATE for this use case
        saveStmt = conn.prepareStatement("REPLACE INTO kv_store (`key`, `value`) VALUES (?, ?)");
        removeStmt = conn.prepareStatement("DELETE FROM kv_store WHERE `key` = ?");
    }

    public byte[] load(byte[] key) throws SQLException {
        loadStmt.setBytes(1, key);
        try (ResultSet rs = loadStmt.executeQuery()) {
            if (rs.next()) {
                return rs.getBytes("value");
            }
            return null;
        }
    }

    public void save(byte[] key, byte[] value) throws SQLException {
        saveStmt.setBytes(1, key);
        saveStmt.setBytes(2, value);
        saveStmt.executeUpdate();
    }

    public boolean remove(byte[] key) throws SQLException {
        removeStmt.setBytes(1, key);
        return removeStmt.executeUpdate() > 0;
    }

    public void close() {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException ignored) {}
    }

    public boolean isConnected() {
        try {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}

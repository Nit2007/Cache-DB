package com.cachedb;

import com.cachedb.cache.CacheManager;
import com.cachedb.db.MysqlClient;
import java.sql.SQLException;

/**
 * Entry point for cache-backed mode.
 *
 * Usage:
 *   java com.cachedb.CacheDB --mysql-host localhost --mysql-port 3306
 *        --mysql-db mydb --mysql-user root --mysql-pass secret
 *
 * This wires MysqlClient → CacheManager and exposes
 * the CacheManager singleton for use by the Redis server's ClientHandler.
 * The Redis server itself (RedisServer) still runs independently on port 6380.
 *
 * When no --mysql-* flags are given, CacheManager runs in cache-only mode
 * (identical to the original Redis clone behavior).
 */
public class CacheDB {

    private static CacheManager instance;

    public static CacheManager getManager() { return instance; }

    public static CacheManager init(String[] args) {
        String host = "localhost";
        int port = 3306;
        String database = "cachedb";
        String user = "root";
        String password = "";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mysql-host": host = args[++i]; break;
                case "--mysql-port": port = Integer.parseInt(args[++i]); break;
                case "--mysql-db":   database = args[++i]; break;
                case "--mysql-user": user = args[++i]; break;
                case "--mysql-pass": password = args[++i]; break;
            }
        }

        MysqlClient db = new MysqlClient(host, port, database, user, password);
        try {
            db.connect();
            System.out.println("[CacheDB] Connected to MySQL at " + host + ":" + port + "/" + database);
        } catch (SQLException e) {
            System.err.println("[CacheDB] MySQL connection failed: " + e.getMessage());
            System.err.println("[CacheDB] Running in cache-only mode (no DB persistence)");
            db = null;
        }

        instance = new CacheManager(db);
        return instance;
    }
}

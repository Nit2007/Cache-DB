package com.cachedb.cache;

import com.cachedb.db.MysqlClient;
import com.miniredis.InMemoryStorage;
import java.sql.SQLException;

/**
 * Cache-aside (read-through / write-through) manager.
 *
 * READ:  cache hit → return immediately
 *        cache miss → load from MySQL → populate cache → return
 * WRITE: update cache + synchronous DB write (write-through)
 * DEL:   remove from cache + DB
 *
 * All keys/values stay byte[] — no String conversion in the data path.
 */
public class CacheManager {

    private final InMemoryStorage cache = InMemoryStorage.get();
    private final MysqlClient db;

    public CacheManager(MysqlClient db) {
        this.db = db;
    }

    public byte[] get(byte[] key) {
        // Try cache first
        byte[] cached = cache.get(key);
        if (cached != null) return cached;

        // Cache miss — load from DB
        if (db == null || !db.isConnected()) return null;
        try {
            byte[] fromDb = db.load(key);
            if (fromDb != null) {
                cache.set(key, fromDb);
            }
            return fromDb;
        } catch (SQLException e) {
            System.err.println("[CacheManager] DB load failed: " + e.getMessage());
            return null;
        }
    }

    public void set(byte[] key, byte[] value) {
        cache.set(key, value);

        // Write-through to DB
        if (db != null && db.isConnected()) {
            try {
                db.save(key, value);
            } catch (SQLException e) {
                System.err.println("[CacheManager] DB save failed: " + e.getMessage());
            }
        }
    }

    public boolean del(byte[] key) {
        boolean removed = cache.del(key);

        if (db != null && db.isConnected()) {
            try {
                removed = db.remove(key) || removed;
            } catch (SQLException e) {
                System.err.println("[CacheManager] DB remove failed: " + e.getMessage());
            }
        }
        return removed;
    }

    /** Delegates to InMemoryStorage — hash ops stay cache-only for now. */
    public int hset(byte[] key, byte[] field, byte[] value) {
        return cache.hset(key, field, value);
    }

    public byte[] hget(byte[] key, byte[] field) {
        return cache.hget(key, field);
    }

    public int hdel(byte[] key, byte[] field) {
        return cache.hdel(key, field);
    }

    public int expire(byte[] key, long ms) {
        return cache.expire(key, ms);
    }

    public long ttl(byte[] key) {
        return cache.ttl(key);
    }
}

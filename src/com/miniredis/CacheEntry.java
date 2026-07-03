package com.miniredis;

import java.util.concurrent.ConcurrentHashMap;

public class CacheEntry {
    private final Object value;
    private volatile long expiresAt; // -1 if no expiration

    public CacheEntry(Object value, long expiresAt) {
        this.value = value;
        this.expiresAt = expiresAt;
    }

    public byte[] getValue() {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        throw new RuntimeException("WRONGTYPE Operation against a key holding the wrong kind of value");
    }

    @SuppressWarnings("unchecked")
    public ConcurrentHashMap<ByteArrayWrapper, byte[]> getHashValue() {
        if (value instanceof ConcurrentHashMap) {
            return (ConcurrentHashMap<ByteArrayWrapper, byte[]>) value;
        }
        throw new RuntimeException("WRONGTYPE Operation against a key holding the wrong kind of value");
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        if (expiresAt == -1) {
            return false;
        }
        return System.currentTimeMillis() > expiresAt;
    }
}

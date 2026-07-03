package com.miniredis;

public class CacheEntry {
    private final byte[] value;
    private volatile long expiresAt; // -1 if no expiration

    public CacheEntry(byte[] value, long expiresAt) {
        this.value = value;
        this.expiresAt = expiresAt;
    }

    public byte[] getValue() {
        return value;
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

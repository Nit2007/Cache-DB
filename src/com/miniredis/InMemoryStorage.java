package com.miniredis;

import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryStorage {

    // ---------- singleton ----------
    private static final InMemoryStorage INSTANCE = new InMemoryStorage();
    private InMemoryStorage() { }
    public static InMemoryStorage get() { return INSTANCE; }

    private final ConcurrentHashMap<ByteArrayWrapper, CacheEntry> map = new ConcurrentHashMap<>();
    private final ExpiryHeap expiryHeap = new ExpiryHeap();
    
    public ConcurrentHashMap<ByteArrayWrapper, CacheEntry> getMap() { return map; }
    
    public void setRaw(byte[] key, byte[] val, long expiresAt) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        map.put(obj, new CacheEntry(val, expiresAt));
        if (expiresAt != -1) {
            expiryHeap.offer(obj, expiresAt);
        }
    }
    
    public void setRawHash(byte[] key, ConcurrentHashMap<ByteArrayWrapper, byte[]> hash, long expiresAt) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        map.put(obj, new CacheEntry(hash, expiresAt));
        if (expiresAt != -1) {
            expiryHeap.offer(obj, expiresAt);
        }
    }
    
    public void set(byte[] key, byte[] value) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        map.put(obj, new CacheEntry(value, -1)); // -1 means no expiry
    }

    public byte[] get(byte[] key) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        CacheEntry entry = map.get(obj);
        if (entry == null) return null;
        //-----------------------LAZY DELETION ------------------------------------
        if (entry.isExpired()) {
            map.remove(obj, entry); // Only remove if it's the exact exact entry to avoid race condition
            return null;
        }
        return entry.getValue();
    }

    public boolean del(byte[] key) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        CacheEntry entry = map.get(obj);
        if (entry == null) return false;
        
        if (entry.isExpired()) {
            map.remove(obj, entry);
            return false; // Act like it was already gone
        }
        return map.remove(obj, entry);
    }

    public int expire(byte[] key, long ms) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        CacheEntry entry = map.get(obj);
        if (entry == null || entry.isExpired()) {
            if (entry != null) map.remove(obj, entry);
            return 0;
        }
        long expiresAt = System.currentTimeMillis() + ms;
        entry.setExpiresAt(expiresAt);
        expiryHeap.offer(obj, expiresAt);
        return 1;
    }

    public ExpiryHeap getExpiryHeap() { return expiryHeap; }

    // Used by the background sweeper thread to evict expired keys
    public boolean forceRemoveIfExpired(ByteArrayWrapper key) {
        CacheEntry entry = map.get(key);
        if (entry == null) return false;
        if (entry.isExpired()) {
            return map.remove(key, entry);
        }
        return false;
    }

    public long ttl(byte[] key) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        CacheEntry entry = map.get(obj);
        if (entry == null) return -2; // Key does not exist
        if (entry.isExpired()) {
            map.remove(obj, entry);
            return -2;
        }
        if (entry.getExpiresAt() == -1) return -1; // Key exists but has no associated expire
        
        long remaining = entry.getExpiresAt() - System.currentTimeMillis();
        return Math.max(0, remaining / 1000); // Return in seconds
    }

    public int hset(byte[] key, byte[] field, byte[] value) {
        ByteArrayWrapper objKey = new ByteArrayWrapper(key);
        ByteArrayWrapper objField = new ByteArrayWrapper(field);
        
        while (true) {
            CacheEntry entry = map.get(objKey);
            if (entry != null && entry.isExpired()) {
                map.remove(objKey, entry);
                entry = null;
            }
            if (entry == null) {
                CacheEntry newEntry = new CacheEntry(new ConcurrentHashMap<ByteArrayWrapper, byte[]>(), -1);
                entry = map.putIfAbsent(objKey, newEntry);
                if (entry == null) {
                    entry = newEntry;
                }
            }
            if (entry.isExpired()) {
                continue;
            }
            
            ConcurrentHashMap<ByteArrayWrapper, byte[]> hash = entry.getHashValue();
            
            byte[] old = hash.put(objField, value);
            return old == null ? 1 : 0;
        }
    }

    public byte[] hget(byte[] key, byte[] field) {
        ByteArrayWrapper objKey = new ByteArrayWrapper(key);
        CacheEntry entry = map.get(objKey);
        if (entry == null) return null;
        if (entry.isExpired()) {
            map.remove(objKey, entry);
            return null;
        }
        
        ConcurrentHashMap<ByteArrayWrapper, byte[]> hash = entry.getHashValue();
        
        return hash.get(new ByteArrayWrapper(field));
    }

    public int hdel(byte[] key, byte[] field) {
        ByteArrayWrapper objKey = new ByteArrayWrapper(key);
        CacheEntry entry = map.get(objKey);
        if (entry == null) return 0;
        if (entry.isExpired()) {
            map.remove(objKey, entry);
            return 0;
        }
        
        ConcurrentHashMap<ByteArrayWrapper, byte[]> hash = entry.getHashValue();
        
        byte[] old = hash.remove(new ByteArrayWrapper(field));
        return old != null ? 1 : 0;
    }
}

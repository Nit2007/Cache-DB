package com.miniredis;

import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryStorage {

    // ---------- singleton ----------
    private static final InMemoryStorage INSTANCE = new InMemoryStorage();
    private InMemoryStorage() { }
    public static InMemoryStorage get() { return INSTANCE; }

    private final ConcurrentHashMap<ByteArrayWrapper, byte[]> map = new ConcurrentHashMap<>();
    
    public void set(byte[] key, byte[] value) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        map.put(obj, value);
    }

    public byte[] get(byte[] key) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        return map.get(obj);
    }

    public boolean del(byte[] key) {
        ByteArrayWrapper obj = new ByteArrayWrapper(key);
        return map.remove(obj) != null;
    }
}

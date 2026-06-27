package com.miniredis;

import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryStorage {

    // ---------- singleton ----------
    private static final InMemoryStorage INSTANCE = new InMemoryStorage();
    private InMemoryStorage() { }
    public static InMemoryStorage get() { return INSTANCE; }

    private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
    
    public void set(String key, String value) {
        map.put(key, value);
    }

    public String get(String key) {
        return map.get(key);
    }

    public boolean del(String key) {
        return map.remove(key) != null;
    }
}

package com.miniredis;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RdbPersister {

    private static final byte[] MAGIC = "MINIRDB1".getBytes();
    private static final byte TYPE_EOF = -1;
    private static final byte TYPE_STRING = 0;
    private static final byte TYPE_HASH = 1;
    
    private final InMemoryStorage storage = InMemoryStorage.get();
    private final String filename;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rdb-persister");
        t.setDaemon(true);
        return t;
    });

    public RdbPersister(String filename) {
        this.filename = filename;
    }

    public void startPeriodicSnapshot(long periodSeconds) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                save();
            } catch (Exception e) {
                System.err.println("[RDB] Background save failed: " + e.getMessage());
            }
        }, periodSeconds, periodSeconds, TimeUnit.SECONDS);
    }

    public synchronized void save() throws IOException {
        long start = System.currentTimeMillis();
        System.out.println("[RDB] Starting snapshot to " + filename);
        // Write to a temporary file first, then rename for atomic replacement
        File tempFile = new File(filename + ".tmp");
        
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)))) {
            out.write(MAGIC);
            
            // ConcurrentHashMap's iterator is weakly consistent. It doesn't block the map.
            // This is our design trade-off for not having fork().
            int count = 0;
            for (Map.Entry<ByteArrayWrapper, CacheEntry> entry : storage.getMap().entrySet()) {
                CacheEntry ce = entry.getValue();
                if (ce.isExpired()) continue;
                
                byte[] key = entry.getKey().getBytes();
                byte[] val = ce.getValue(); // will return null if it's a hash (actually throws)
                ConcurrentHashMap<ByteArrayWrapper, byte[]> hash = null;
                
                try {
                    val = ce.getValue();
                } catch (RuntimeException e) {
                    hash = ce.getHashValue();
                }
                
                if (hash != null) {
                    out.writeByte(TYPE_HASH);
                    out.writeLong(ce.getExpiresAt());
                    out.writeInt(key.length);
                    out.write(key);
                    out.writeInt(hash.size());
                    for (Map.Entry<ByteArrayWrapper, byte[]> hEntry : hash.entrySet()) {
                        byte[] field = hEntry.getKey().getBytes();
                        byte[] fieldVal = hEntry.getValue();
                        out.writeInt(field.length);
                        out.write(field);
                        out.writeInt(fieldVal.length);
                        out.write(fieldVal);
                    }
                } else if (val != null) {
                    out.writeByte(TYPE_STRING);
                    out.writeLong(ce.getExpiresAt());
                    out.writeInt(key.length);
                    out.write(key);
                    out.writeInt(val.length);
                    out.write(val);
                }
                count++;
            }
            out.writeByte(TYPE_EOF);
            System.out.println("[RDB] Saved " + count + " keys in " + (System.currentTimeMillis() - start) + "ms");
        }
        
        File realFile = new File(filename);
        if (realFile.exists()) {
            realFile.delete();
        }
        tempFile.renameTo(realFile);
    }

    public void load() throws IOException {
        File file = new File(filename);
        if (!file.exists()) return;
        
        System.out.println("[RDB] Loading snapshot from " + filename);
        long start = System.currentTimeMillis();
        int count = 0;
        
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException("Invalid RDB format");
            }
            
            while (true) {
                byte type = in.readByte();
                if (type == TYPE_EOF) break;
                
                long expiresAt = in.readLong();
                if (expiresAt != -1 && expiresAt < System.currentTimeMillis()) {
                    // Skip expired entry
                    skipEntry(in, type);
                    continue;
                }
                
                int keyLen = in.readInt();
                byte[] key = new byte[keyLen];
                in.readFully(key);
                
                if (type == TYPE_STRING) {
                    int valLen = in.readInt();
                    byte[] val = new byte[valLen];
                    in.readFully(val);
                    storage.setRaw(key, val, expiresAt);
                } else if (type == TYPE_HASH) {
                    int hashSize = in.readInt();
                    ConcurrentHashMap<ByteArrayWrapper, byte[]> hash = new ConcurrentHashMap<>();
                    for (int i = 0; i < hashSize; i++) {
                        int fieldLen = in.readInt();
                        byte[] field = new byte[fieldLen];
                        in.readFully(field);
                        int valLen = in.readInt();
                        byte[] val = new byte[valLen];
                        in.readFully(val);
                        hash.put(new ByteArrayWrapper(field), val);
                    }
                    storage.setRawHash(key, hash, expiresAt);
                }
                count++;
            }
        }
        System.out.println("[RDB] Loaded " + count + " keys in " + (System.currentTimeMillis() - start) + "ms");
    }
    
    private void skipEntry(DataInputStream in, byte type) throws IOException {
        int keyLen = in.readInt();
        in.skipBytes(keyLen);
        if (type == TYPE_STRING) {
            int valLen = in.readInt();
            in.skipBytes(valLen);
        } else if (type == TYPE_HASH) {
            int hashSize = in.readInt();
            for (int i = 0; i < hashSize; i++) {
                int fieldLen = in.readInt();
                in.skipBytes(fieldLen);
                int valLen = in.readInt();
                in.skipBytes(valLen);
            }
        }
    }
}

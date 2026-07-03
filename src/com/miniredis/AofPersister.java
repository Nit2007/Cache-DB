package com.miniredis;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.BufferedInputStream;

public class AofPersister {

    public enum FsyncPolicy { ALWAYS, EVERYSEC, NO }

    private final String filename;
    private final FsyncPolicy policy;
    private FileOutputStream fileOut;
    private OutputStream out;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "aof-fsync");
        t.setDaemon(true);
        return t;
    });

    public AofPersister(String filename, FsyncPolicy policy) throws IOException {
        this.filename = filename;
        this.policy = policy;
        this.fileOut = new FileOutputStream(filename, true);
        this.out = new BufferedOutputStream(fileOut, 65536);
        
        if (policy == FsyncPolicy.EVERYSEC) {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    fsync();
                } catch (IOException e) {
                    System.err.println("[AOF] Background fsync failed: " + e.getMessage());
                }
            }, 1, 1, TimeUnit.SECONDS);
        }
    }

    public synchronized void append(byte[][] command) throws IOException {
        // Write as a RESP Array
        out.write(("*" + command.length + "\r\n").getBytes());
        for (byte[] arg : command) {
            out.write(("$" + arg.length + "\r\n").getBytes());
            out.write(arg);
            out.write("\r\n".getBytes());
        }
        
        if (policy == FsyncPolicy.ALWAYS) {
            out.flush();
            fileOut.getFD().sync();
        } else if (policy == FsyncPolicy.NO) {
            // Let the OS handle it, or flush occasionally if buffer is full
        }
    }

    public synchronized void fsync() throws IOException {
        out.flush();
        fileOut.getFD().sync();
    }

    public void load(ClientHandler handler) throws IOException {
        File file = new File(filename);
        if (!file.exists()) return;
        
        System.out.println("[AOF] Loading from " + filename);
        long start = System.currentTimeMillis();
        int count = 0;
        
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            RespParser parser = new RespParser(in);
            OutputStream dummyOut = new OutputStream() {
                @Override public void write(int b) {}
                @Override public void write(byte[] b) {}
                @Override public void write(byte[] b, int off, int len) {}
            };
            while (true) {
                byte[][] command = parser.readCommand();
                if (command == null) break;
                // Only mutate commands are in AOF
                handler.handleCommand(command, dummyOut);
                count++;
            }
        }
        System.out.println("[AOF] Loaded " + count + " commands in " + (System.currentTimeMillis() - start) + "ms");
    }

    public synchronized void rewrite() throws IOException {
        // Simple compaction by triggering an RDB save, but actually AOF rewrite in Redis 
        // creates a new AOF with SET/HSET commands for the current state.
        // For simplicity and speed, we can iterate InMemoryStorage and emit SET/HSET/EXPIRE
        System.out.println("[AOF] Starting rewrite to " + filename + ".rewrite");
        long start = System.currentTimeMillis();
        
        File tempFile = new File(filename + ".rewrite");
        try (OutputStream tempOut = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            InMemoryStorage storage = InMemoryStorage.get();
            for (java.util.Map.Entry<ByteArrayWrapper, CacheEntry> entry : storage.getMap().entrySet()) {
                CacheEntry ce = entry.getValue();
                if (ce.isExpired()) continue;
                
                byte[] key = entry.getKey().getBytes();
                byte[] val = null;
                java.util.concurrent.ConcurrentHashMap<ByteArrayWrapper, byte[]> hash = null;
                
                try {
                    val = ce.getValue();
                } catch (RuntimeException e) {
                    hash = ce.getHashValue();
                }
                
                if (val != null) {
                    writeCommand(tempOut, new byte[][]{ "SET".getBytes(), key, val });
                } else if (hash != null) {
                    for (java.util.Map.Entry<ByteArrayWrapper, byte[]> hEntry : hash.entrySet()) {
                        writeCommand(tempOut, new byte[][]{ "HSET".getBytes(), key, hEntry.getKey().getBytes(), hEntry.getValue() });
                    }
                }
                
                if (ce.getExpiresAt() != -1) {
                    long remainingMs = ce.getExpiresAt() - System.currentTimeMillis();
                    if (remainingMs > 0) {
                        writeCommand(tempOut, new byte[][]{ "EXPIRE".getBytes(), key, String.valueOf(remainingMs / 1000).getBytes() });
                    }
                }
            }
            tempOut.flush();
            if (tempOut instanceof BufferedOutputStream) {
                ((FileOutputStream)((BufferedOutputStream)tempOut).getClass().getDeclaredField("out").get(tempOut)).getFD().sync();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Swap files
        this.out.close();
        File realFile = new File(filename);
        realFile.delete();
        tempFile.renameTo(realFile);
        
        this.fileOut = new FileOutputStream(filename, true);
        this.out = new BufferedOutputStream(this.fileOut, 65536);
        System.out.println("[AOF] Rewrite complete in " + (System.currentTimeMillis() - start) + "ms");
    }
    
    private void writeCommand(OutputStream out, byte[][] command) throws IOException {
        out.write(("*" + command.length + "\r\n").getBytes());
        for (byte[] arg : command) {
            out.write(("$" + arg.length + "\r\n").getBytes());
            out.write(arg);
            out.write("\r\n".getBytes());
        }
    }
}

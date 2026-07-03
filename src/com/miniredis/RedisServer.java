package com.miniredis;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class RedisServer {

    public static void main(String[] args) {
        int port = 6380;

        // Active expiry sweeper thread
        InMemoryStorage storage = InMemoryStorage.get();
        ExpiryHeap heap = storage.getExpiryHeap();
        Thread sweeper = new Thread(() -> {
            while (true) {
                try {
                    long now = System.currentTimeMillis();
                    while (heap.peekTimestamp() != -1 && heap.peekTimestamp() <= now) {
                        ByteArrayWrapper key = heap.poll();
                        if (key != null) {
                            storage.forceRemoveIfExpired(key);
                        }
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        sweeper.setDaemon(true);
        sweeper.start();
        System.out.println("[Server] Active expiry sweeper started");

        AofPersister aof = null;
        AofPersister.FsyncPolicy policy = AofPersister.FsyncPolicy.EVERYSEC;
        boolean useAof = false;
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--aof") && i + 1 < args.length) {
                useAof = true;
                String p = args[i + 1].toLowerCase();
                if (p.equals("always")) policy = AofPersister.FsyncPolicy.ALWAYS;
                else if (p.equals("no")) policy = AofPersister.FsyncPolicy.NO;
                else policy = AofPersister.FsyncPolicy.EVERYSEC;
            }
        }
        
        RdbPersister rdb = new RdbPersister("dump.rdb");
        
        try {
            if (useAof) {
                aof = new AofPersister("appendonly.aof", policy);
                aof.load(new ClientHandler(null, rdb, null)); // null aof to prevent append on load
            } else {
                rdb.load();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error loading persistence: " + e.getMessage());
        }
        
        rdb.startPeriodicSnapshot(60);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            System.out.println("[Server] Listening on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] New client connected: "
                    + clientSocket.getInetAddress().getHostAddress());

                Thread clientThread = new Thread(new ClientHandler(clientSocket, rdb, aof)); 
                clientThread.setDaemon(true);
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
package com.miniredis;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Sets N keys with a 1-second TTL, then polls to see how quickly
 * the active sweeper evicts them all.
 *
 * Usage: java -cp out com.miniredis.BenchmarkTTLEviction [numKeys]
 * Default: 50000 keys
 */
public class BenchmarkTTLEviction {

    public static void main(String[] args) throws Exception {
        int numKeys = args.length > 0 ? Integer.parseInt(args[0]) : 50000;
        String host = "127.0.0.1";
        int port = 6380;

        System.out.println("TTL Eviction benchmark: " + numKeys + " keys with 1s TTL");

        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            byte[] buffer = new byte[1024];

            // 1) SET N keys
            System.out.println("Setting " + numKeys + " keys...");
            for (int i = 0; i < numKeys; i++) {
                String key = "ttlkey" + i;
                String val = "v" + i;
                String cmd = "*3\r\n$3\r\nSET\r\n$" + key.length() + "\r\n" + key + "\r\n$" + val.length() + "\r\n" + val + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer);
            }

            // 2) EXPIRE all with 1 second TTL
            System.out.println("Setting 1s EXPIRE on all keys...");
            long expireStart = System.currentTimeMillis();
            for (int i = 0; i < numKeys; i++) {
                String key = "ttlkey" + i;
                String cmd = "*3\r\n$6\r\nEXPIRE\r\n$" + key.length() + "\r\n" + key + "\r\n$1\r\n1\r\n";
                out.write(cmd.getBytes());
                in.read(buffer);
            }
            long expireMs = System.currentTimeMillis() - expireStart;
            System.out.printf("EXPIRE phase done in %.2fs\n", expireMs / 1000.0);

            // 3) Wait for expiry + sweeper
            System.out.println("Waiting 2 seconds for expiry + active sweep...");
            Thread.sleep(2000);

            // 4) Check how many are gone (GET returns nil)
            System.out.println("Checking eviction...");
            long checkStart = System.currentTimeMillis();
            int evicted = 0;
            for (int i = 0; i < numKeys; i++) {
                String key = "ttlkey" + i;
                String cmd = "*2\r\n$3\r\nGET\r\n$" + key.length() + "\r\n" + key + "\r\n";
                out.write(cmd.getBytes());
                int bytesRead = in.read(buffer);
                String resp = new String(buffer, 0, bytesRead);
                if (resp.startsWith("$-1")) {
                    evicted++;
                }
            }
            long checkMs = System.currentTimeMillis() - checkStart;

            System.out.println("--- Results ---");
            System.out.println("Keys set: " + numKeys);
            System.out.println("Keys evicted (returned nil): " + evicted + " / " + numKeys);
            System.out.printf("Eviction rate: %.1f%%\n", (evicted * 100.0) / numKeys);
            System.out.printf("Check phase took: %.2fs\n", checkMs / 1000.0);
        }
    }
}

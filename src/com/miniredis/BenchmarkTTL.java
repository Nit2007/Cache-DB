package com.miniredis;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
//java -cp out com.miniredis.BenchmarkTTL
public class BenchmarkTTL {

    public static void main(String[] args) {
        int requests = 100000;
        int port = 6380;
        String host = "127.0.0.1";

        System.out.println("Starting TTL benchmark: " + requests + " SET + EXPIRE + TTL + GET-after-expiry requests...");

        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            byte[] buffer = new byte[1024];

            // Phase 1: SET keys
            long setStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "ttlkey" + i;
                String value = "val" + i;
                String cmd =
                    "*3\r\n" +
                    "$3\r\nSET\r\n" +
                    "$" + key.length() + "\r\n" + key + "\r\n" +
                    "$" + value.length() + "\r\n" + value + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer);
            }
            long setEnd = System.currentTimeMillis();
            double setTime = (setEnd - setStart) / 1000.0;
            double setOps = requests / setTime;

            // Phase 2: EXPIRE each key with 60s TTL
            long expireStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "ttlkey" + i;
                String seconds = "60";
                String cmd =
                    "*3\r\n" +
                    "$6\r\nEXPIRE\r\n" +
                    "$" + key.length() + "\r\n" + key + "\r\n" +
                    "$" + seconds.length() + "\r\n" + seconds + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer);
            }
            long expireEnd = System.currentTimeMillis();
            double expireTime = (expireEnd - expireStart) / 1000.0;
            double expireOps = requests / expireTime;

            // Phase 3: TTL check on each key
            long ttlStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "ttlkey" + i;
                String cmd =
                    "*2\r\n" +
                    "$3\r\nTTL\r\n" +
                    "$" + key.length() + "\r\n" + key + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer);
            }
            long ttlEnd = System.currentTimeMillis();
            double ttlTime = (ttlEnd - ttlStart) / 1000.0;
            double ttlOps = requests / ttlTime;

            // Phase 4: GET (keys should still exist, TTL is 60s)
            long getStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "ttlkey" + i;
                String cmd =
                    "*2\r\n" +
                    "$3\r\nGET\r\n" +
                    "$" + key.length() + "\r\n" + key + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer);
            }
            long getEnd = System.currentTimeMillis();
            double getTime = (getEnd - getStart) / 1000.0;
            double getOps = requests / getTime;

            System.out.printf("SET:    %,.2f ops/sec (%.2fs)\n", setOps, setTime);
            System.out.printf("EXPIRE: %,.2f ops/sec (%.2fs)\n", expireOps, expireTime);
            System.out.printf("TTL:    %,.2f ops/sec (%.2fs)\n", ttlOps, ttlTime);
            System.out.printf("GET:    %,.2f ops/sec (%.2fs)\n", getOps, getTime);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

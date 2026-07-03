package com.miniredis;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
//java -cp out com.miniredis.Benchmark
public class Benchmark {

    public static void main(String[] args) {
        int requests = 100000;
        int port = 6380;
        String host = "127.0.0.1";

        System.out.println("Starting benchmark: " + requests + " SET and GET requests...");

        long startTime = System.currentTimeMillis();

        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            byte[] buffer = new byte[1024];

            // Benchmark SET
            long setStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "key" + i;
                String value = "val" + i;
                String cmd =
                    "*3\r\n" +
                    "$3\r\nSET\r\n" +
                    "$" + key.length() + "\r\n" + key + "\r\n" +
                    "$" + value.length() + "\r\n" + value + "\r\n";
                // String cmd = "*3\r\n$3\r\nSET\r\n$4\r\nkey" + i + "\r\n$4\r\nval" + i + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer); // Read +OK\r\n
            }
            long setEnd = System.currentTimeMillis();
            double setTimeSeconds = (setEnd - setStart) / 1000.0;
            double setOps = requests / setTimeSeconds;

            // Benchmark GET
            long getStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "key" + i;
                String cmd =
                    "*2\r\n" +
                    "$3\r\nGET\r\n" +
                    "$" + key.length() + "\r\n" + key + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer); // Read the bulk string response
            }
            long getEnd = System.currentTimeMillis();
            double getTimeSeconds = (getEnd - getStart) / 1000.0;
            double getOps = requests / getTimeSeconds;

            // Benchmark EXPIRE
            long expireStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "key" + i;
                String cmd = "*3\r\n$6\r\nEXPIRE\r\n$" + key.length() + "\r\n" + key + "\r\n$2\r\n60\r\n";
                out.write(cmd.getBytes());
                in.read(buffer); 
            }
            long expireEnd = System.currentTimeMillis();
            double expireTimeSeconds = (expireEnd - expireStart) / 1000.0;
            double expireOps = requests / expireTimeSeconds;

            // Benchmark TTL
            long ttlStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "key" + i;
                String cmd = "*2\r\n$3\r\nTTL\r\n$" + key.length() + "\r\n" + key + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer); 
            }
            long ttlEnd = System.currentTimeMillis();
            double ttlTimeSeconds = (ttlEnd - ttlStart) / 1000.0;
            double ttlOps = requests / ttlTimeSeconds;

            // Benchmark HSET
            long hsetStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "hkey" + i;
                String field = "field" + i;
                String value = "hval" + i;
                String cmd =
                    "*4\r\n" +
                    "$4\r\nHSET\r\n" +
                    "$" + key.length() + "\r\n" + key + "\r\n" +
                    "$" + field.length() + "\r\n" + field + "\r\n" +
                    "$" + value.length() + "\r\n" + value + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer);
            }
            long hsetEnd = System.currentTimeMillis();
            double hsetTimeSeconds = (hsetEnd - hsetStart) / 1000.0;
            double hsetOps = requests / hsetTimeSeconds;

            // Benchmark HGET
            long hgetStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String key = "hkey" + i;
                String field = "field" + i;
                String cmd =
                    "*3\r\n" +
                    "$4\r\nHGET\r\n" +
                    "$" + key.length() + "\r\n" + key + "\r\n" +
                    "$" + field.length() + "\r\n" + field + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer);
            }
            long hgetEnd = System.currentTimeMillis();
            double hgetTimeSeconds = (hgetEnd - hgetStart) / 1000.0;
            double hgetOps = requests / hgetTimeSeconds;

            // Benchmark SAVE
            long saveStart = System.currentTimeMillis();
            String saveCmd = "*1\r\n$4\r\nSAVE\r\n";
            out.write(saveCmd.getBytes());
            in.read(buffer); // Read +OK\r\n
            long saveEnd = System.currentTimeMillis();
            double saveTimeSeconds = (saveEnd - saveStart) / 1000.0;

            System.out.printf("SET: %.2f ops/sec (Took %.2fs)\n", setOps, setTimeSeconds);
            System.out.printf("GET: %.2f ops/sec (Took %.2fs)\n", getOps, getTimeSeconds);
            System.out.printf("EXPIRE: %.2f ops/sec (Took %.2fs)\n", expireOps, expireTimeSeconds);
            System.out.printf("TTL: %.2f ops/sec (Took %.2fs)\n", ttlOps, ttlTimeSeconds);
            System.out.printf("HSET: %.2f ops/sec (Took %.2fs)\n", hsetOps, hsetTimeSeconds);
            System.out.printf("HGET: %.2f ops/sec (Took %.2fs)\n", hgetOps, hgetTimeSeconds);
            System.out.printf("SAVE (Snapshot): Took %.2fs\n", saveTimeSeconds);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

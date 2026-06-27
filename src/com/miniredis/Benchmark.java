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
                String cmd = "*3\r\n$3\r\nSET\r\n$4\r\nkey" + i + "\r\n$4\r\nval" + i + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer); // Read +OK\r\n
            }
            long setEnd = System.currentTimeMillis();
            double setTimeSeconds = (setEnd - setStart) / 1000.0;
            double setOps = requests / setTimeSeconds;

            // Benchmark GET
            long getStart = System.currentTimeMillis();
            for (int i = 0; i < requests; i++) {
                String cmd = "*2\r\n$3\r\nGET\r\n$4\r\nkey" + i + "\r\n";
                out.write(cmd.getBytes());
                in.read(buffer); // Read response
            }
            long getEnd = System.currentTimeMillis();
            double getTimeSeconds = (getEnd - getStart) / 1000.0;
            double getOps = requests / getTimeSeconds;

            System.out.printf("SET: %.2f ops/sec (Took %.2fs)\n", setOps, setTimeSeconds);
            System.out.printf("GET: %.2f ops/sec (Took %.2fs)\n", getOps, getTimeSeconds);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

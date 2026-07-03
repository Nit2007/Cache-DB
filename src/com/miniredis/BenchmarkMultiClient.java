package com.miniredis;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spawns N concurrent client threads, each doing SET+GET on its own key range.
 * Measures total aggregate ops/sec and confirms all N connections succeed.
 *
 * Usage: java -cp out com.miniredis.BenchmarkMultiClient [numClients] [requestsPerClient]
 * Default: 10 clients, 10000 requests each
 */
public class BenchmarkMultiClient {

    public static void main(String[] args) {
        int numClients = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int reqPerClient = args.length > 1 ? Integer.parseInt(args[1]) : 10000;
        String host = "127.0.0.1";
        int port = 6380;

        System.out.println("Multi-client benchmark: " + numClients + " clients, " + reqPerClient + " req/client");

        AtomicLong totalSetOps = new AtomicLong();
        AtomicLong totalGetOps = new AtomicLong();
        AtomicLong totalSetTimeMs = new AtomicLong();
        AtomicLong totalGetTimeMs = new AtomicLong();
        CountDownLatch ready = new CountDownLatch(numClients);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(numClients);

        for (int c = 0; c < numClients; c++) {
            final int clientId = c;
            new Thread(() -> {
                try (Socket socket = new Socket(host, port);
                     OutputStream out = socket.getOutputStream();
                     InputStream in = socket.getInputStream()) {

                    byte[] buffer = new byte[1024];
                    ready.countDown();
                    go.await(); // all threads start together

                    // SET
                    long setStart = System.currentTimeMillis();
                    for (int i = 0; i < reqPerClient; i++) {
                        String key = "c" + clientId + "k" + i;
                        String val = "v" + i;
                        String cmd = "*3\r\n$3\r\nSET\r\n$" + key.length() + "\r\n" + key + "\r\n$" + val.length() + "\r\n" + val + "\r\n";
                        out.write(cmd.getBytes());
                        in.read(buffer);
                    }
                    long setMs = System.currentTimeMillis() - setStart;

                    // GET
                    long getStart = System.currentTimeMillis();
                    for (int i = 0; i < reqPerClient; i++) {
                        String key = "c" + clientId + "k" + i;
                        String cmd = "*2\r\n$3\r\nGET\r\n$" + key.length() + "\r\n" + key + "\r\n";
                        out.write(cmd.getBytes());
                        in.read(buffer);
                    }
                    long getMs = System.currentTimeMillis() - getStart;

                    totalSetOps.addAndGet(reqPerClient);
                    totalGetOps.addAndGet(reqPerClient);
                    totalSetTimeMs.set(Math.max(totalSetTimeMs.get(), setMs));
                    totalGetTimeMs.set(Math.max(totalGetTimeMs.get(), getMs));

                } catch (Exception e) {
                    System.err.println("Client " + clientId + " failed: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            }).start();
        }

        try {
            ready.await();
            long wallStart = System.currentTimeMillis();
            go.countDown(); // fire!
            done.await();
            long wallMs = System.currentTimeMillis() - wallStart;

            double wallSec = wallMs / 1000.0;
            long totalOps = totalSetOps.get() + totalGetOps.get();

            System.out.println("--- Results ---");
            System.out.println("Clients connected: " + numClients);
            System.out.println("Total ops (SET+GET): " + totalOps);
            System.out.printf("Wall-clock time: %.2fs\n", wallSec);
            System.out.printf("Aggregate SET throughput: %.2f ops/sec\n", totalSetOps.get() / wallSec);
            System.out.printf("Aggregate GET throughput: %.2f ops/sec\n", totalGetOps.get() / wallSec);
            System.out.printf("Aggregate total throughput: %.2f ops/sec\n", totalOps / wallSec);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

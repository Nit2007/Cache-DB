package com.miniredis;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class RedisServer {

    public static void main(String[] args) {
        int port = 6380;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);

            System.out.println("[Server] Listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();

                System.out.println("[Server] New client connected: "
                    + clientSocket.getInetAddress().getHostAddress());

                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                
                clientThread.setDaemon(true);
                clientThread.start();
            }

        } catch (IOException e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
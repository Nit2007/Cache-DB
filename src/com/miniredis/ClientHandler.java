package com.miniredis;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final InMemoryStorage storage = InMemoryStorage.get();
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }
    @Override
    public void run() {
        String clientAddr = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        System.out.println("[Handler] Connected: " + clientAddr);
        try (
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream()
        ){
            RespParser parser = new RespParser(in);
            while (true) {
                byte[][] command = parser.readCommand();
                if (command == null) {
                    System.out.println("[Handler] Disconnected: " + clientAddr);
                    break;
                }
                handleCommand(command, out);
                // Flush only after pipeline finishes.
                if (in.available() == 0) {
                    out.flush();
                }
            }

        } catch (IOException e) {
            System.err.println("[Handler] Error for "
                    + clientAddr + ": " + e.getMessage());
        }
    }

    private void handleCommand(byte[][] command, OutputStream out)
            throws IOException {

        String cmd = new String(command[0]).toUpperCase();

        switch (cmd) {

            case "PING":
                handlePing(command, out);
                break;

            case "ECHO":
                handleEcho(command, out);
                break;

            case "SET":
                handleSet(command, out);
                break;

            case "GET":
                handleGet(command, out);
                break;

            case "DEL":
                handleDel(command, out);
                break;

            case "HSET":
                handleHSet(command, out);
                break;

            case "HGET":
                handleHGet(command, out);
                break;

            default:
                writeError(out, "unknown command '" + cmd + "'");
        }
    }

    // ---------------------------------------------------------------------------------------------
    //                      Command Handlers
    // -----------------------------------------------------------------------------------------------

    private void handlePing(byte[][] command, OutputStream out) throws IOException {
        if (command.length == 1) {
            writeSimpleString(out, "PONG");
            return;
        }
        writeBulkString(out, command[1]);
    }

    private void handleEcho(byte[][] command, OutputStream out) throws IOException {
        if (command.length < 2) {
            writeError(out, "wrong number of arguments");
            return;
        }
        writeBulkString(out, command[1]);
    }

    private void handleSet(byte[][] command, OutputStream out) throws IOException {
        if (command.length != 3) {
            writeError(out, "wrong number of arguments for 'SET'");
            return;
        }
        storage.set(command[1], command[2]);
        writeSimpleString(out, "OK");
    }

    private void handleGet(byte[][] command, OutputStream out) throws IOException {
        if (command.length != 2) {
            writeError(out, "wrong number of arguments for 'GET'");
            return;
        }
        writeBulkString(out, storage.get(command[1]));
    }

    private void handleDel(byte[][] command, OutputStream out) throws IOException {
        if (command.length < 2) {
            writeError(out, "wrong number of arguments for 'DEL'");
            return;
        }

        int removed = 0;
        for (int i = 1; i < command.length; i++) {
            if (storage.del(command[i])) {
                removed++;
            }
        }

        writeInteger(out, removed);
    }

    private void handleHSet(byte[][] command, OutputStream out) throws IOException {
        if (command.length != 4) {
            writeError(out, "wrong number of arguments for 'HSET'");
            return;
        }
        storage.hset(command[1], command[2], command[3]);
        writeSimpleString(out, "OK");
    }

    private void handleHGet(byte[][] command, OutputStream out) throws IOException {
        if (command.length != 3) {
            writeError(out, "wrong number of arguments for 'HGET'");
            return;
        }
        writeBulkString(out, storage.hget(command[1], command[2]));
    }

    // ---------------------------------------------------
    // RESP Writers
    // ---------------------------------------------------

    private void writeSimpleString(OutputStream out, String value) throws IOException {
        out.write(("+" + value + "\r\n").getBytes());
    }

    private void writeError(OutputStream out, String message) throws IOException {
        out.write(("-ERR " + message + "\r\n").getBytes());
    }

    private void writeInteger(OutputStream out, int value) throws IOException {
        out.write((":" + value + "\r\n").getBytes());
    }

    private void writeBulkString(OutputStream out, byte[] value) throws IOException {
        if (value == null) {
            out.write("$-1\r\n".getBytes());
            return;
        }
        out.write(("$" + value.length + "\r\n").getBytes());
        out.write(value);
        out.write("\r\n".getBytes());
    }
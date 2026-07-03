package com.miniredis;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {

    public static volatile double defaultRateCapacity = -1;
    public static volatile double defaultRateRefill = -1;

    private final Socket socket;
    private final InMemoryStorage storage = InMemoryStorage.get();
    private final RdbPersister rdb;
    private final AofPersister aof;
    
    private final TokenBucket rateLimiter = new TokenBucket(Double.MAX_VALUE, Double.MAX_VALUE);
    private double currentCapacity = -1;

    public ClientHandler(Socket socket, RdbPersister rdb, AofPersister aof) {
        this.socket = socket;
        this.rdb = rdb;
        this.aof = aof;
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

    public void handleCommand(byte[][] command, OutputStream out)
            throws IOException {

        String cmd = new String(command[0]).toUpperCase();

        if (defaultRateCapacity != -1 && out != null) {
            if (currentCapacity != defaultRateCapacity) {
                rateLimiter.setConfig(defaultRateCapacity, defaultRateRefill);
                currentCapacity = defaultRateCapacity;
            }
            if (!rateLimiter.tryConsume(1)) {
                writeError(out, "rate limit exceeded");
                return;
            }
        }

        try {
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
                case "EXPIRE":
                    handleExpire(command, out);
                    break;
                case "TTL":
                    handleTtl(command, out);
                    break;
                case "HSET":
                    handleHSet(command, out);
                    break;
                case "HGET":
                    handleHGet(command, out);
                    break;
                case "HDEL":
                    handleHDel(command, out);
                    break;
                case "SAVE":
                    handleSave(command, out);
                    break;
                case "RATELIMIT":
                    handleRateLimit(command, out);
                    break;
                default:
                    writeError(out, "unknown command '" + cmd + "'");
            }
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("WRONGTYPE")) {
                writeError(out, e.getMessage());
            } else {
                throw e;
            }
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
        if (aof != null) aof.append(command);
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
        if (removed > 0 && aof != null) aof.append(command);

        writeInteger(out, removed);
    }

    private void handleHSet(byte[][] command, OutputStream out) throws IOException {
        if (command.length != 4) {
            writeError(out, "wrong number of arguments for 'HSET'");
            return;
        }
        int result = storage.hset(command[1], command[2], command[3]);
        if (aof != null) aof.append(command);
        writeInteger(out, result);
    }

    private void handleHGet(byte[][] command, OutputStream out) throws IOException {
        if (command.length != 3) {
            writeError(out, "wrong number of arguments for 'HGET'");
            return;
        }
        writeBulkString(out, storage.hget(command[1], command[2]));
    }

    private void handleHDel(byte[][] command, OutputStream out) throws IOException {
        if (command.length != 3) {
            writeError(out, "wrong number of arguments for 'HDEL'");
            return;
        }
        int result = storage.hdel(command[1], command[2]);
        if (result > 0 && aof != null) aof.append(command);
        writeInteger(out, result);
    }

    private void handleExpire(byte[][] command, OutputStream out) throws IOException {
        if (command.length != 3) {
            writeError(out, "wrong number of arguments for 'EXPIRE'");
            return;
        }
        try {
            long seconds = Long.parseLong(new String(command[2]));
            int result = storage.expire(command[1], seconds * 1000L);
            if (result == 1 && aof != null) aof.append(command);
            writeInteger(out, result);
        } catch (NumberFormatException e) {
            writeError(out, "value is not an integer or out of range");
        }
    }

    private void handleTtl(byte[][] command, OutputStream out) throws IOException {
        if (command.length != 2) {
            writeError(out, "wrong number of arguments for 'TTL'");
            return;
        }
        long result = storage.ttl(command[1]);
        writeInteger(out, (int)result);
    }

    private void handleSave(byte[][] command, OutputStream out) throws IOException {
        if (rdb != null) {
            try {
                rdb.save();
                writeSimpleString(out, "OK");
            } catch (Exception e) {
                writeError(out, "background save err");
            }
        } else {
            writeError(out, "RDB not configured");
        }
    }

    private void handleRateLimit(byte[][] command, OutputStream out) throws IOException {
        if (command.length == 4 && new String(command[1]).equalsIgnoreCase("SET")) {
            try {
                double capacity = Double.parseDouble(new String(command[2]));
                double refill = Double.parseDouble(new String(command[3]));
                ClientHandler.defaultRateCapacity = capacity;
                ClientHandler.defaultRateRefill = refill;
                writeSimpleString(out, "OK");
            } catch (NumberFormatException e) {
                writeError(out, "value is not a valid float");
            }
        } else {
            writeError(out, "syntax error");
        }
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
}
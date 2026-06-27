package com.miniredis;

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
        String clientAddr = socket.getInetAddress().getHostAddress()
                          + ":" + socket.getPort();
        System.out.println("[Handler] Connected: " + clientAddr);

        try (
            InputStream in   = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
        ) {
            RespParser parser = new RespParser(in);

            while (true) {
                // Blocks until a full RESP command arrives.
                String[] command = parser.readCommand();

                // null means client disconnected cleanly.
                if (command == null) {
                    System.out.println("[Handler] Disconnected: " + clientAddr);
                    break;
                }

                // Log what we parsed. You should see ["SET", "name", "nithish"] etc.
                // System.out.print("[Handler:" + clientAddr + "] Command: ");
                // for (String token : command) System.out.print("[" + token + "] ");
                // System.out.println();

                // Route to the right handler based on command[0].
                // command[0] is always the Redis command name.
                String response = handleCommand(command);
                out.write(response.getBytes());
                out.flush();
            }

        } catch (IOException e) {
            System.err.println("[Handler] Error for " + clientAddr + ": " + e.getMessage());
        }
    }

    // ================================================================
    //  COMMAND ROUTER
    //  This method receives the parsed command tokens and decides
    //  what to do. Each "case" handles one Redis command.
    //
    //  RESP response cheat-sheet (you'll use these constantly):
    //    Simple string  →  +<text>\r\n           e.g.  +OK\r\n
    //    Error          →  -<message>\r\n         e.g.  -ERR unknown command\r\n
    //    Bulk string    →  $<len>\r\n<data>\r\n   e.g.  $3\r\nbar\r\n
    //    Null           →  $-1\r\n               (key doesn't exist)
    //    Integer        →  :<number>\r\n          e.g.  :1\r\n
    // ================================================================
    private String handleCommand(String[] command) {

        // Redis commands are case-insensitive, so we normalise to uppercase.
        String cmd = command[0].toUpperCase();

        switch (cmd) {

            // ────────────────────────────────────────────────
            //  PING  – health-check / connection test
            //  Usage:  PING          → +PONG
            //          PING hello    → $5\r\nhello\r\n
            // ────────────────────────────────────────────────
            case "PING":
                if (command.length == 1) {
                    return "+PONG\r\n";
                } else {
                    String msg = command[1];
                    return "$" + msg.length() + "\r\n" + msg + "\r\n";
                }

            // ────────────────────────────────────────────────
            //  ECHO  – parrot back whatever the client sends
            //  Usage:  ECHO hello    → $5\r\nhello\r\n
            // ────────────────────────────────────────────────
            case "ECHO":
                if (command.length < 2) return "-ERR wrong number of arguments\r\n";
                String echo = command[1];
                return "$" + echo.length() + "\r\n" + echo + "\r\n";

            // ────────────────────────────────────────────────
            //  SET  – store a key-value pair in memory
            //  Usage:  SET mykey myvalue
            //
            //  How it works:
            //    1. Validate that the client sent exactly 3 tokens:
            //       command[0] = "SET", command[1] = key, command[2] = value
            //    2. Call storage.set(key, value) which does
            //       ConcurrentHashMap.put(key, value) under the hood.
            //    3. Return "+OK\r\n" – the standard Redis success response
            //       for SET.
            //
            //  Why "+OK\r\n"?
            //    The '+' prefix tells the RESP parser "this is a Simple
            //    String response". Every RESP line ends with \r\n.
            //    Real Redis returns exactly this for a successful SET.
            // ────────────────────────────────────────────────
            case "SET":
                // Guard: SET needs exactly key + value (3 tokens total)
                if (command.length != 3) {
                    return "-ERR wrong number of arguments for 'SET'\r\n";
                }

                // command[1] = key,  command[2] = value
                // e.g. SET name nithish  →  key="name", value="nithish"
                String setKey = command[1];
                String setVal = command[2];

                // Put it in our ConcurrentHashMap.
                // If the key already exists, its old value is silently replaced.
                // This matches real Redis behaviour.
                storage.set(setKey, setVal);

                // Tell the client it worked.
                return "+OK\r\n";

            // ────────────────────────────────────────────────
            //  GET  – retrieve a value by key
            //  Usage:  GET mykey
            //
            //  Two possible outcomes:
            //    a) Key exists   → return its value as a Bulk String
            //       Format:  $<length>\r\n<value>\r\n
            //       Example: GET name → $7\r\nnithish\r\n
            //
            //    b) Key missing  → return a Null Bulk String
            //       Format:  $-1\r\n
            //       This is how Redis says "this key doesn't exist".
            //       The client library (redis-cli, Jedis, etc.) turns
            //       this into null / nil / None in the caller's language.
            // ────────────────────────────────────────────────
            case "GET":
                // Guard: GET needs exactly 1 key (2 tokens total)
                if (command.length != 2) {
                    return "-ERR wrong number of arguments for 'GET'\r\n";
                }

                String getKey = command[1];

                // Look up the key. Returns null if absent.
                String val = storage.get(getKey);

                if (val == null) {
                    // Key doesn't exist → RESP Null Bulk String
                    return "$-1\r\n";
                }

                // Key exists → RESP Bulk String
                // Format: $<byte-length>\r\n<actual-data>\r\n
                // The '$' prefix tells the parser "a bulk string follows,
                // and its length is <N> bytes". The parser reads exactly
                // N bytes after the first \r\n, then expects another \r\n.
                return "$" + val.length() + "\r\n" + val + "\r\n";

            // ────────────────────────────────────────────────
            //  DEL  – delete one or more keys
            //  Usage:  DEL key1 key2 key3 ...
            //
            //  Returns an Integer reply :<count>\r\n where count is the
            //  number of keys that actually existed and were removed.
            //
            //  Example:
            //    SET a 1        → +OK
            //    SET b 2        → +OK
            //    DEL a b c      → :2    (a & b removed, c didn't exist)
            //
            //  Why a loop?
            //    Real Redis DEL supports deleting multiple keys in one
            //    command. We iterate from command[1] to command[N-1],
            //    try to remove each, and count the successes.
            // ────────────────────────────────────────────────
            case "DEL":
                // Guard: DEL needs at least 1 key
                if (command.length < 2) {
                    return "-ERR wrong number of arguments for 'DEL'\r\n";
                }

                int removed = 0;

                // Loop through every key the client wants deleted.
                // command[0] = "DEL", so keys start at index 1.
                for (int i = 1; i < command.length; i++) {
                    // storage.get() returns null if absent.
                    // We only count it as "removed" if the key actually existed.
                    if (storage.get(command[i]) != null) {
                        storage.del(command[i]);   // we need to add del() to InMemoryStorage
                        removed++;
                    }
                }

                // RESP Integer reply: :<number>\r\n
                return ":" + removed + "\r\n";

            // ────────────────────────────────────────────────
            //  Unknown command → error
            // ────────────────────────────────────────────────
            default:
                return "-ERR unknown command '" + command[0] + "'\r\n";
        }
    }
}
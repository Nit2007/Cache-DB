package com.miniredis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class RespParser {

    private final BufferedReader reader;
    public RespParser(InputStream in) {
        this.reader = new BufferedReader(new InputStreamReader(in));
    }

    // Returns the next complete command as a String array, e.g. ["SET", "name", "Random"]
    // Returns null if the client disconnected.
    // Throws IOException on stream errors.
    public String[] readCommand() throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) return null;
        if (!firstLine.startsWith("*")) {
            throw new IOException("Expected '*', got: " + firstLine);
        }

        int numArgs = Integer.parseInt(firstLine.substring(1));
        String[] command = new String[numArgs];

        for (int i = 0; i < numArgs; i++) {
            String lengthLine = reader.readLine();
            if (lengthLine == null) return null;
            if (!lengthLine.startsWith("$")) {
                throw new IOException("Expected '$', got: " + lengthLine);
            }

            int tokenLength = Integer.parseInt(lengthLine.substring(1));

            char[] tokenBuf = new char[tokenLength];
            int charsRead = 0;
            while (charsRead < tokenLength) {
                int result = reader.read(tokenBuf, charsRead, tokenLength - charsRead);
                if (result == -1) return null;
                charsRead += result;
            }
            command[i] = new String(tokenBuf);

            // Consume the trailing \r\n after the token,discards the empty line after the token
            reader.readLine(); 
        }

        return command;
    }
}
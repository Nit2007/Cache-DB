package com.miniredis;

import java.io.IOException;
import java.io.InputStream;

public class RespParser {
    private final InputStream in;
    public RespParser(InputStream in) {
        this.in = in;
    }
    // Reads a RESP protocol line ending with \r\n.
    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                int next = in.read();
                if (next == '\n') {
                    break;
                }
                sb.append((char) c);
                if (next != -1) {
                    sb.append((char) next);
                }
            } else {
                sb.append((char) c);
            }
        }

        if (sb.length() == 0 && c == -1) {
            return null;
        }

        return sb.toString();
    }

    // Returns null if the client disconnects cleanly.
    public byte[][] readCommand() throws IOException {
        String firstLine = readLine();
        if (firstLine == null) {
            return null;
        }
        if (!firstLine.startsWith("*")) {
            throw new IOException("Expected '*', got: " + firstLine);
        }
        int numArgs = Integer.parseInt(firstLine.substring(1));
        byte[][] command = new byte[numArgs][];

        for (int i = 0; i < numArgs; i++) {
            String lengthLine = readLine();
            if (lengthLine == null) {
                return null;
            }

            if (!lengthLine.startsWith("$")) {
                throw new IOException("Expected '$', got: " + lengthLine);
            }

            int tokenLength = Integer.parseInt(lengthLine.substring(1));
            byte[] data = new byte[tokenLength];
            int bytesRead = 0;

            while (bytesRead < tokenLength) {
                int result = in.read(data, bytesRead, tokenLength - bytesRead);
                if (result == -1) {
                    throw new IOException("Unexpected end of stream while reading data");
                }
                bytesRead += result;
            }
            command[i] = data;

            int cr = in.read();
            int lf = in.read();
            if (cr != '\r' || lf != '\n') {
                throw new IOException("Expected \\r\\n after bulk string data");
            }
        }
        return command;
    }
}
package com.miniredis;

public class ByteArrayWrapper {
    private final byte[] bytes;
    private final int hash;
    
    public ByteArrayWrapper(byte[] bytes) {
        this.bytes = bytes;
        this.hash = java.util.Arrays.hashCode(bytes); // Cache the hash code!
    }
    
    public byte[] getBytes() {
        return bytes;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteArrayWrapper that = (ByteArrayWrapper) o;
        return java.util.Arrays.equals(bytes, that.bytes);
    }
    
    @Override
    public int hashCode() {
        return hash; // Return the cached hash immediately!
    }
}
package com.miniredis;

public class TokenBucket {
    private double capacity;
    private double refillRate; // tokens per second
    private double tokens;
    private long lastRefillTime;

    public TokenBucket(double capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillTime = System.currentTimeMillis();
    }

    public synchronized void setConfig(double capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillTime = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume(int count) {
        long now = System.currentTimeMillis();
        double elapsedSeconds = (now - lastRefillTime) / 1000.0;
        
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillRate);
        lastRefillTime = now;
        
        if (tokens >= count) {
            tokens -= count;
            return true;
        }
        return false;
    }
}

package com.miniredis;

public class ExpiryHeap {

    private static final int DEFAULT_CAPACITY = 1024;

    private long[] timestamps;
    private ByteArrayWrapper[] keys;
    private int size;

    public ExpiryHeap() {
        this.timestamps = new long[DEFAULT_CAPACITY];
        this.keys = new ByteArrayWrapper[DEFAULT_CAPACITY];
        this.size = 0;
    }

    public synchronized void offer(ByteArrayWrapper key, long expiresAt) {
        if (size == timestamps.length) grow();
        timestamps[size] = expiresAt;
        keys[size] = key;
        siftUp(size);
        size++;
    }

    public synchronized long peekTimestamp() {
        return size > 0 ? timestamps[0] : -1;
    }

    public synchronized ByteArrayWrapper peekKey() {
        return size > 0 ? keys[0] : null;
    }

    public synchronized ByteArrayWrapper poll() {
        if (size == 0) return null;
        ByteArrayWrapper result = keys[0];
        size--;
        timestamps[0] = timestamps[size];
        keys[0] = keys[size];
        keys[size] = null; // help GC
        if (size > 0) siftDown(0);
        return result;
    }

    public synchronized int size() {
        return size;
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            if (timestamps[parent] <= timestamps[i]) break;
            swap(i, parent);
            i = parent;
        }
    }

    private void siftDown(int i) {
        int half = size >>> 1;
        while (i < half) {
            int child = (i << 1) + 1;
            int right = child + 1;
            if (right < size && timestamps[right] < timestamps[child]) {
                child = right;
            }
            if (timestamps[i] <= timestamps[child]) break;
            swap(i, child);
            i = child;
        }
    }

    private void swap(int i, int j) {
        long tmpTs = timestamps[i];
        timestamps[i] = timestamps[j];
        timestamps[j] = tmpTs;

        ByteArrayWrapper tmpKey = keys[i];
        keys[i] = keys[j];
        keys[j] = tmpKey;
    }

    private void grow() {
        int newCap = timestamps.length << 1;
        long[] newTs = new long[newCap];
        ByteArrayWrapper[] newKeys = new ByteArrayWrapper[newCap];
        System.arraycopy(timestamps, 0, newTs, 0, size);
        System.arraycopy(keys, 0, newKeys, 0, size);
        timestamps = newTs;
        keys = newKeys;
    }
}

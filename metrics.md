Start Date : 20 June 2026

SET: 15598.19 ops/sec (Took 6.41s)
GET: 15903.31 ops/sec (Took 6.29s)

SET: 73367.57 ops/sec (Took 1.36s)
GET: 84745.76 ops/sec (Took 1.18s)



changed Map<String,String> to Map<ByteArrayWrapper, byte[]> 
SET: 16260.16 ops/sec (Took 6.15s)
GET: 40733.20 ops/sec (Took 2.46s)

Lazy TTL , Active TTL (Min Heap,Random Sampling,Naive Map iteration)
SET:    15,503.88 ops/sec (6.45s)
EXPIRE: 16,531.66 ops/sec (6.05s)
TTL:    17,340.04 ops/sec (5.77s)
GET:    36,114.12 ops/sec (2.77s)

Ref : 
https://redis.io/docs/latest/develop/reference/protocol-spec/

RDB Snapshot Only (No AOF)
SET: 13504.39 ops/sec (7.41s)
GET: 27397.26 ops/sec (3.65s)
EXPIRE: 14679.98 ops/sec (6.81s)
TTL: 14764.51 ops/sec (6.77s)
HSET: 14534.88 ops/sec (6.88s)
HGET: 19588.64 ops/sec (5.11s)
SAVE: 0.00s

AOF Everysec
SET: 15260.19 ops/sec (6.55s)
GET: 21734.41 ops/sec (4.60s)
EXPIRE: 15537.60 ops/sec (6.44s)
TTL: 15470.30 ops/sec (6.46s)
HSET: 15523.13 ops/sec (6.44s)
HGET: 21276.60 ops/sec (4.70s)

AOF No (OS page cache)
SET: 15511.09 ops/sec (6.45s)
GET: 35423.31 ops/sec (2.82s)
EXPIRE: 16512.55 ops/sec (6.06s)
TTL: 16920.47 ops/sec (5.91s)
HSET: 14760.15 ops/sec (6.78s)
HGET: 26075.62 ops/sec (3.84s)
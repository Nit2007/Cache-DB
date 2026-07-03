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
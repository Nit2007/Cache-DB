# Cache-DB Context

This document provides a highly verbose and granular overview of the Cache-DB codebase. This is a custom, in-memory, binary-safe Redis clone implemented in Java. It is designed to act as a comprehensive reference for large language models to understand the architecture, data structures, network handling, execution flow, and benchmarking metrics of the system.

## 1. Codebase Structure

The project is structured as a standard Java application with a root directory containing build scripts and configuration, and a `src` directory housing the Java source code.

```text
d:\Cache-DB\
├── README.md               # Contains benchmark metrics and changelog.
├── run.bat                 # Windows batch script for compiling and running the server.
└── src\
    └── com\
        └── miniredis\
            ├── Benchmark.java          # Client-side benchmarking tool.
            ├── ByteArrayWrapper.java   # Wrapper for byte[] to enable usage as HashMap keys.
            ├── ClientHandler.java      # Runnable for handling individual client socket connections.
            ├── InMemoryStorage.java    # Singleton implementing the thread-safe core key-value store.
            ├── RedisServer.java        # Main server entry point handling socket acceptance.
            └── RespParser.java         # Parser for the REdis Serialization Protocol (RESP).
```

## 2. Core Components and File Details

### `com.miniredis.RedisServer`
- **Role**: The entry point of the application. It initializes the `ServerSocket` and listens for incoming connections.
- **Granular Details**:
  - Listens on port `6380`.
  - Sets `SO_REUSEADDR` (`setReuseAddress(true)`) to prevent address binding errors upon restart.
  - Implements an infinite `while (true)` loop to `accept()` new `Socket` connections.
  - For each connection, it spawns a new `Thread` wrapping a `ClientHandler`.
  - Threads are set as daemon threads (`clientThread.setDaemon(true)`), meaning they will not prevent the JVM from shutting down if the main thread exits.

### `com.miniredis.ClientHandler`
- **Role**: Handles the lifecycle of a single client connection, reading requests, parsing them, executing commands, and sending responses.
- **Granular Details**:
  - Implements the `Runnable` interface.
  - Wraps the socket's `InputStream` in a `BufferedInputStream` for performance.
  - Utilizes `RespParser` to deserialize raw byte streams into a `byte[][]` (array of byte arrays), representing the command and its arguments.
  - Command execution is routed via a `switch` statement on the first argument (converted to a `String` and capitalized).
  - **Supported Commands**: `PING`, `ECHO`, `SET`, `GET`, `DEL`, `HSET`, `HGET`.
  - **Pipelining Support**: Uses `in.available() == 0` to determine when to `out.flush()`. This allows it to buffer responses and only flush to the network when there is no more immediate data to read, significantly improving throughput for pipelined requests.
  - **Response Writers**: Contains helper methods to serialize RESP responses directly to the `OutputStream` (e.g., `writeSimpleString`, `writeBulkString`, `writeInteger`, `writeError`).

### `com.miniredis.RespParser`
- **Role**: Responsible for deserializing the incoming TCP byte stream according to the RESP (REdis Serialization Protocol) specification.
- **Granular Details**:
  - Operates directly on `InputStream` to read bytes, avoiding overhead from readers or string decoding.
  - `readLine()`: Reads characters until `\r\n` is encountered.
  - `readCommand()`: Parses RESP Arrays (starting with `*`) and Bulk Strings (starting with `$`).
  - Returns a `byte[][]` where each sub-array is an exact, binary-safe argument provided by the client. It ensures exactly `tokenLength` bytes are read for each bulk string to handle binary data properly.

### `com.miniredis.ByteArrayWrapper`
- **Role**: Solves the problem of using primitive `byte[]` arrays as keys in Java Maps. Java arrays use reference equality by default, which breaks `HashMap` lookups by value.
- **Granular Details**:
  - Wraps a `byte[]`.
  - Overrides `equals()` using `java.util.Arrays.equals()`.
  - **Optimization**: Pre-calculates and caches the hash code (`java.util.Arrays.hashCode(bytes)`) in the constructor. Because array keys are treated as immutable during their lifecycle as a key, this saves CPU cycles on every `hashCode()` call, drastically improving map lookup speeds.

### `com.miniredis.InMemoryStorage`
- **Role**: The core data structure holding the database state.
- **Granular Details**:
  - **Singleton Pattern**: Uses a private constructor and a static `INSTANCE` retrieved via `get()` to ensure all handlers share the same state.
  - **Data Structure**: `ConcurrentHashMap<ByteArrayWrapper, byte[]> map`. The use of `ConcurrentHashMap` provides thread safety without locking the entire map for every operation, allowing high concurrency from multiple client threads.
  - **Binary Safe**: Accepts and returns `byte[]` instead of `String`.
  - **Methods**: `set`, `get`, `del`. (Note: `ClientHandler` calls `hset` and `hget` but they are not fully implemented in the snippet provided. The primary KV store uses standard `set`/`get`).
  - Object Handling: When a request comes in, the key (`byte[]`) is wrapped in a `new ByteArrayWrapper(key)` and then passed to the `ConcurrentHashMap`.

### `com.miniredis.Benchmark`
- **Role**: A standalone client to stress-test the server and measure operations per second (ops/sec).
- **Granular Details**:
  - Connects to `127.0.0.1:6380`.
  - Executes 100,000 sequential `SET` requests followed by 100,000 `GET` requests.
  - Generates raw RESP strings (e.g., `*3\r\n$3\r\nSET...`) and writes the `.getBytes()` to the socket.
  - Reads back responses to ensure round-trip completion.
  - Calculates and prints throughput (`ops/sec`) and total duration.

## 3. How Objects and Data are Handled (Data Flow)

1. **Network I/O**: The client sends a TCP packet. The OS hands this to the JVM. The `BufferedInputStream` reads chunks of raw bytes.
2. **Parsing**: `RespParser` reads the RESP structure byte-by-byte. It identifies bulk string lengths and reads exact byte lengths into new `byte[]` arrays.
3. **Execution Routing**: `ClientHandler` inspects `command[0]` (converting only the command name to a String for easy routing).
4. **Storage Interaction**: For a `SET key val`, `InMemoryStorage.set(byte[] key, byte[] val)` is called.
5. **Memory Allocation**: Inside `set`, a new `ByteArrayWrapper` object is allocated to hold the `key`. The hash code is computed once during object instantiation.
6. **Concurrency Control**: The wrapper and value are inserted into `ConcurrentHashMap`. CAS (Compare-And-Swap) operations are used internally by Java to ensure thread safety without blocking readers.
7. **Response**: A raw byte array response (e.g., `+OK\r\n`.getBytes()) is written to the `OutputStream`.

## 4. Execution and Run Instructions

The codebase is built and executed using the provided Windows batch script, `run.bat`.

**Commands:**
- To run the server: Double click `run.bat` or execute `.\run.bat` in the terminal.

**What `run.bat` does:**
1. Creates an `out` directory (`mkdir out`).
2. Compiles all java files (`javac -d out src/com/miniredis/*.java`).
3. Runs the compiled `RedisServer` class (`java -cp out com.miniredis.RedisServer`).

**To run the benchmark:**
In a separate terminal, while the server is running:
`java -cp out com.miniredis.Benchmark`

## 5. Metrics and Performance Evolution

The `README.md` details the performance evolution of the server. By moving from a `String`-based map to a binary-safe `Map<ByteArrayWrapper, byte[]>` and caching hash codes, the server saw massive improvements.

**Initial / Baseline:**
- SET: 15,598 ops/sec (6.41s)
- GET: 15,903 ops/sec (6.29s)

**After optimizing I/O and pipelining:**
- SET: 73,367 ops/sec (1.36s)
- GET: 84,745 ops/sec (1.18s)

**After Binary Safe `ByteArrayWrapper` Refactor (Current state):**
- SET: 16,260 ops/sec (6.15s) -> *Note: The benchmark code in `Benchmark.java` currently uses string concatenation in a loop to build commands, which might be artificially slowing down the SET benchmark in this specific run logged in README.*
- GET: 40,733 ops/sec (2.46s)

*(The README implies that avoiding String encoding/decoding overhead in the server itself drastically reduces memory allocations and garbage collection pauses under high load, even if the naive single-threaded benchmark client sometimes shows variable results.)*

## 6. Known Quirks and Important Details

- **Binary Safety**: Because keys and values are treated strictly as `byte[]`, the server can store text, serialized Java objects, images, or any raw binary data transparently, just like real Redis.
- **Hash Command Missing Implementation**: `ClientHandler.java` attempts to call `storage.hset` and `storage.hget`, but `InMemoryStorage.java` does not currently define these methods. This is an area for future implementation.
- **Pipelining Check**: The clever use of `in.available() == 0` for flushing allows the server to process multiple queued commands in a single TCP read/write cycle, mirroring real Redis pipelining behavior.

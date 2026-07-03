# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY src /app/src
RUN javac src/com/miniredis/*.java
RUN mkdir -p /app/out/com/miniredis && cp src/com/miniredis/*.class /app/out/com/miniredis/

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/out /app
EXPOSE 6380
# Enable AOF with everysec policy by default for production use
CMD ["java", "-cp", ".", "com.miniredis.RedisServer", "--aof", "everysec"]

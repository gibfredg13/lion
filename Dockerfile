# Multi-stage build for optimal image size
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

# Install Node.js and build tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    nodejs npm python3 build-essential git ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY . .
RUN chmod +x ./gradlew && ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Create data directory for SQLite
RUN mkdir -p /app/data

# Copy JAR from builder
COPY --from=builder /build/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/ || exit 1

# Run application with optimized JVM settings
ENTRYPOINT ["java", \
  "-Dspring.profiles.active=prod", \
  "-Xmx512M", \
  "-Xms256M", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-Djava.awt.headless=true", \
  "-jar", "/app/app.jar"]

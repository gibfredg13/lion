# Multi-stage build for optimal image size
FROM bellsoft/liberica-openjdk-alpine:21 AS builder
WORKDIR /build
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM bellsoft/liberica-openjdk-alpine:21
WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

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
  "-XX:+UnlockExperimentalVMOptions", \
  "-XX:G1NewCollectionPercentage=30", \
  "-XX:G1MaxNewGenPercent=40", \
  "-XX:InitialRAMPercentage=50.0", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.awt.headless=true", \
  "-jar", "/app/app.jar"]

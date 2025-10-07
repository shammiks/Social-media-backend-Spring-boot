# ===========================================
# OPTIMIZED DOCKERFILE FOR RENDER FREE TIER
# ===========================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./

# Make Maven wrapper executable
RUN chmod +x ./mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application (skip tests to save memory)
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime (smaller image)
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Create non-root user
RUN groupadd -r spring && useradd -r -g spring spring && \
    chown spring:spring app.jar

USER spring:spring

# Expose port
EXPOSE 8080

# JVM options optimized for 512MB RAM
# Allocating 384MB max heap, 100MB min heap
ENV JAVA_OPTS="-Xmx384m \
-Xms100m \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=100 \
-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+DisableExplicitGC \
-XX:+ExitOnOutOfMemoryError \
-XX:G1HeapRegionSize=4m \
-XX:InitiatingHeapOccupancyPercent=70 \
-Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

# Run application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
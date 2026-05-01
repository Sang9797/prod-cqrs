# ============================================================
# Stage 1 — Build (Maven + JDK 25)
# ============================================================
FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /build

# Copy pom first — Docker layer cache: only re-downloads deps when pom changes
COPY pom.xml .
RUN mvn dependency:go-offline -q 2>/dev/null || true

# Copy source and build
COPY src ./src
RUN apt-get update -qq && apt-get install -y --no-install-recommends maven && \
    mvn clean package -DskipTests -q && \
    # Extract layered JAR for optimised layer caching in Stage 2
    java -Djarmode=layertools -jar target/*.jar extract --destination /build/extracted

# ============================================================
# Stage 2 — Runtime (JRE 25, minimal image)
# ============================================================
FROM eclipse-temurin:25-jre-noble AS runtime

LABEL maintainer="platform-team@company.com"
LABEL org.opencontainers.image.title="cqrs-order-service"
LABEL org.opencontainers.image.description="CQRS Order Service — Spring Boot 4 / Java 25 / Virtual Threads"
LABEL org.opencontainers.image.version="1.0.0"

# Security: never run as root
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --no-create-home appuser

WORKDIR /app

# Copy layered JAR in dependency order — layers least → most likely to change
# This means only the "application" layer rebuilds on each code change
COPY --from=builder /build/extracted/dependencies          ./
COPY --from=builder /build/extracted/spring-boot-loader   ./
COPY --from=builder /build/extracted/snapshot-dependencies ./
COPY --from=builder /build/extracted/application          ./

# Own everything by appuser
RUN chown -R appuser:appgroup /app

USER appuser

# Application port
EXPOSE 8080
# Management / metrics port (internal only — do NOT expose in docker-compose externally)
EXPOSE 9090

# Health check — uses liveness probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O- http://localhost:9090/actuator/health/liveness || exit 1

# JVM flags optimised for containers + virtual threads:
#   -XX:+UseContainerSupport          → respect cgroup CPU/memory limits
#   -XX:MaxRAMPercentage=75.0         → use 75% of container RAM for heap
#   -XX:+UseZGC                       → low-latency GC, pairs well with virtual threads
#   -XX:+ZGenerational                → ZGC generational mode (Java 21+ GA, Java 25 default)
#   -Djdk.tracePinnedThreads=short    → log pinned virtual threads (optional, remove in prod)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseZGC \
               -XX:+ZGenerational \
               -Dfile.encoding=UTF-8 \
               -Dspring.profiles.active=prod"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

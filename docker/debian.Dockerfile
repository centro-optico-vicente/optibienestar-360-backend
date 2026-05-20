# syntax=docker/dockerfile:1

# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy wrapper + build descriptors first — layer is cached until they change
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Download dependencies into BuildKit cache (never written to image layers)
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && \
    ./gradlew dependencies --no-daemon --console=plain

# Copy source and compile executable JAR
COPY src/ src/
ARG DEPLOY_VERSION=local-0.0.1
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --console=plain \
    -PdeployVersion=${DEPLOY_VERSION}


# ─── Stage 2: Runtime (Debian — mayor compatibilidad de librerías) ───────────
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy only the Spring Boot fat JAR (exclude *-plain.jar)
COPY --from=builder /app/build/libs /tmp/libs
RUN find /tmp/libs -name "*.jar" ! -name "*-plain.jar" -exec cp {} /app/app.jar \; \
    && rm -rf /tmp/libs

# Container-aware JVM: respects cgroup memory/CPU limits
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

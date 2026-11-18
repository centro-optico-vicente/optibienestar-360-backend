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

# Container-aware JVM: respects cgroup memory/CPU limits. Heap is capped at 60%
# and every non-heap pool is bounded, so heap + metaspace + code cache + direct
# buffers stay under the container limit: at 75% with unbounded non-heap the JVM
# crossed a 1152M limit and was OOM-killed by the kernel with no Java log.
# ExitOnOutOfMemoryError + heap dump make a real exhaustion visible instead.
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=96m -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Djava.security.egd=file:/dev/./urandom"

# Release metadata injected at build time from the GitHub release that triggered
# the workflow (BUILD_VERSION = tag_name, BUILD_DATE = published_at, ISO-8601).
# Consumed by SystemInfoController via `app.version` / `app.version-date`.
# Defaults are placeholders so locally-built images still expose sane values.
ARG BUILD_VERSION="dev-0.0.1"
ARG BUILD_DATE=""
ENV VERSION=${BUILD_VERSION} \
    VERSION_DATE=${BUILD_DATE}

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]


# ─── Stage 3: CI fast path (JAR pre-compilado, sin Gradle) ───────────────────
# Populated via --build-context prebuilt=./prebuilt/ in docker-build-check
FROM scratch AS prebuilt-src

FROM eclipse-temurin:25-jre AS from-prebuilt
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

COPY --from=prebuilt-src . /tmp/libs/
RUN find /tmp/libs -name "*.jar" ! -name "*-plain.jar" -exec cp {} /app/app.jar \; \
    && rm -rf /tmp/libs

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=96m -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Djava.security.egd=file:/dev/./urandom"

ARG BUILD_VERSION="dev-0.0.1"
ARG BUILD_DATE=""
ENV VERSION=${BUILD_VERSION} \
    VERSION_DATE=${BUILD_DATE}

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

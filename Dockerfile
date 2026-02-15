# Multi-stage build - Build stage
FROM eclipse-temurin:23-jdk AS builder

WORKDIR /app

# Install Git (needed for version determination)
RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*
# Copy Git directory as we need the version and build history
COPY .git .git

# Copy Gradle wrapper and build files first
COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

# Make gradlew executable
RUN chmod +x gradlew

# Copy source code
COPY src src

# Copy documentation resources needed at runtime
COPY docs docs

# Build using your Gradle wrapper (skip tests, they're run in CI/CD)
RUN ./gradlew build -x test --no-daemon

# Runtime stage
FROM amazoncorretto:25-al2023-headless

# Amazon Corretto 25 on AL2023 addresses high severity CVEs present in eclipse-temurin:23
# Using headless variant (runtime-only, no GUI libraries) for optimal production container size

WORKDIR /app

# Create non-root user with configurable UID/GID (default 1000:1000)
# Override at build time: docker build --build-arg APP_UID=1001 --build-arg APP_GID=1001
ARG APP_UID=1000
ARG APP_GID=1000
RUN yum install -y shadow-utils && yum clean all && \
    groupadd -g ${APP_GID} appuser && \
    useradd -u ${APP_UID} -g ${APP_GID} -m -s /bin/bash appuser

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/mcp-task-orchestrator-*.jar /app/orchestrator.jar

# Copy documentation resources needed at runtime
COPY --from=builder /app/docs /app/docs

# Ensure app user owns the working directory and create data dir before VOLUME
RUN mkdir -p /app/data && chown -R appuser:appuser /app

# Volume for the SQLite database and configuration
# Declared AFTER mkdir+chown so new volumes inherit appuser ownership
VOLUME /app/data

# Environment variables for configuration
ENV DATABASE_PATH=/app/data/tasks.db
ENV MCP_TRANSPORT=stdio
ENV LOG_LEVEL=info
ENV USE_FLYWAY=true

# Run as non-root user
USER appuser

# Run the application with explicit stdio handling
# --enable-native-access=ALL-UNNAMED: Required for SQLite JDBC native library loading in Java 25+
CMD ["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED", "-jar", "orchestrator.jar"]

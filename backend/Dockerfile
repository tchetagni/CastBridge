# syntax=docker/dockerfile:1
# CastBridge server image: Maven build inside the container, small JRE runtime, non-root user.

# ---- build ----------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
# dependencies first: cached as long as pom.xml does not change
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# tests run with "mvn verify" outside the image (they need Docker for the MySQL test)
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package && cp target/castbridge-server.jar /castbridge-server.jar

# ---- runtime --------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="castbridge-api" \
      org.opencontainers.image.description="CastBridge server (updates, quiz, devices, admin)" \
      org.opencontainers.image.revision="${VCS_REF}"

RUN set -eux; \
    if ! command -v curl >/dev/null; then \
        apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*; \
    fi; \
    groupadd --system --gid 10001 castbridge; \
    useradd --system --uid 10001 --gid castbridge --home-dir /app --shell /usr/sbin/nologin castbridge; \
    mkdir -p /app /data/apk/.multipart /data/apk/.incoming /geoip; \
    chown -R castbridge:castbridge /data/apk

WORKDIR /app
COPY --from=build /castbridge-server.jar /app/castbridge-server.jar

ENV TZ=Africa/Douala \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs \
    CASTBRIDGE_STORAGE_DIR=/data/apk

USER castbridge:castbridge
EXPOSE 8080
# readiness = app up AND database reachable; the management port 8081 is never published
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8081/actuator/health/readiness >/dev/null || exit 1

# -XX:MaxRAMPercentage=70 of the container limit (512 MiB) for the heap; Serial GC and small stacks/code cache
# keep the rest of the process small on a shared server.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-Xss512k", "-XX:ReservedCodeCacheSize=48m", \
            "-XX:MaxMetaspaceSize=192m", "-XX:+ExitOnOutOfMemoryError", "-Dfile.encoding=UTF-8", "-Duser.timezone=Africa/Douala", \
            "-jar", "/app/castbridge-server.jar"]

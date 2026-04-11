# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN groupadd --system beach && useradd --system --gid beach beach

COPY --from=build /build/target/beach-assistant-*.jar app.jar
RUN chown beach:beach app.jar

USER beach

EXPOSE 8080

# PORT is set by PaaS (e.g. Render); default 8080 for local/docker-compose
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD sh -c 'curl -fsS "http://127.0.0.1:${PORT:-8080}/actuator/health" >/dev/null || exit 1'

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]

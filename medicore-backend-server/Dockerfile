# Build stage — no local Gradle needed.
# JDK 21.0.11+: earlier 21.0.x builds crash with SIGILL on Apple silicon (M4) containers.
FROM gradle:8.14-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# Run stage (Design v1.3 §2.3)
FROM eclipse-temurin:21-jre
WORKDIR /app
# curl is not in the base image; the compose healthcheck on /api/health needs it
# so `web` can wait for Flyway to finish migrating rather than racing it.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 4000
# server.port is ${PORT:4000} (application.yml) — the platform can override it.
ENTRYPOINT ["java","-jar","app.jar"]

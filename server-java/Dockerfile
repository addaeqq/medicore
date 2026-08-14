# Build stage — no local Gradle needed
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# Run stage (Design v1.3 §2.3)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 4000
ENTRYPOINT ["java","-jar","app.jar"]

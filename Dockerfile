# syntax=docker/dockerfile:1

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies first (only re-resolved when pom.xml changes).
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
# Then build the application.
COPY src/ src/
RUN mvn -B -q -DskipTests clean package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# Run as a non-root user.
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

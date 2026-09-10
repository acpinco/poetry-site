# Build the executable Spring Boot JAR with the same Java major version as production.
FROM maven:3.9.12-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy the dependency manifest first so Docker can cache downloaded dependencies.
COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline

COPY src src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

# Run the application in a smaller image that contains only a Java runtime.
FROM eclipse-temurin:21.0.12_8-jre-jammy

WORKDIR /app
RUN useradd --system --uid 10001 --create-home appuser

COPY --from=build --chown=appuser:appuser /workspace/target/*.jar app.jar

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

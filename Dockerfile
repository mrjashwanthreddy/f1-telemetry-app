# Build Stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create logs directory
RUN mkdir -p /app/logs

# Copy built jar from build stage
COPY --from=build /app/target/f1-telemetry-0.0.1-SNAPSHOT.jar app.jar

# Expose HTTP port (default 8080 or PORT env var)
EXPOSE 8080

# Run in headless mode for cloud container hosting
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]

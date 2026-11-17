# Build stage: compile and package the Spring Boot jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Run stage: a small Java runtime, running as a non-root user.
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home growbie
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER growbie
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

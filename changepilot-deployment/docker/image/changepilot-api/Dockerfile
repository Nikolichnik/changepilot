# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS deps
WORKDIR /workspace/changepilot-api

COPY changepilot-api/.mvn .mvn
COPY changepilot-api/mvnw changepilot-api/pom.xml ./

RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

FROM deps AS build
COPY changepilot-api/src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN useradd --system --create-home --uid 10001 changepilot
COPY --from=build /workspace/changepilot-api/target/changepilot-api-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
USER changepilot
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM deps AS development
WORKDIR /workspace/changepilot-api
COPY changepilot-api/src src
EXPOSE 8080
CMD ["./mvnw", "-B", "-ntp", "spring-boot:run"]

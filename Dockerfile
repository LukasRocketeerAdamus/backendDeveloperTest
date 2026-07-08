# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Copy only the reactor's pom.xml files first so dependency:go-offline is cached as its own layer —
# module source changes below don't invalidate it. Maven resolves the inter-module dependencies
# (service -> countryConnector, api -> service) from the reactor graph itself, not from a repo, so this
# works even though none of the modules have been built yet.
COPY pom.xml .
COPY countryConnector/pom.xml countryConnector/pom.xml
COPY service/pom.xml service/pom.xml
COPY api/pom.xml api/pom.xml
RUN mvn -q -B dependency:go-offline

COPY countryConnector/src ./countryConnector/src
COPY service/src ./service/src
COPY api/src ./api/src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN useradd --system --create-home appuser
USER appuser

# api is the only module with spring-boot-maven-plugin, so it's the only one that produces an executable
# (repackaged) fat jar.
COPY --from=build /app/api/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

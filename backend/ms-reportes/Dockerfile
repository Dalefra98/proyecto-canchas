# Patron oficial del proyecto (CLAUDE.md seccion 1). El cache mount de BuildKit reemplaza
# al volumen m2repo dentro del build: sin el, la descarga completa desde
# repo.maven.apache.org falla por DNS o por handshake TLS.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/ms-reportes-0.0.1-SNAPSHOT.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

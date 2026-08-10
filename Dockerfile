FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependency downloads separately from source so `docker compose up
# --build` only re-resolves Maven Central when pom.xml actually changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/scheduler-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

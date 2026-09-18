FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /project
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /project/target/quarkus-app/ /app/
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
# inventory-service depende do contrato gRPC standalone org.acme:inventory-proto
# (schema-first). Este Dockerfile usa o contexto da RAIZ do repositório
# (definido no docker-compose) e compila o contrato antes do serviço.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /project
COPY inventory-proto/pom.xml inventory-proto/
COPY inventory-proto/src inventory-proto/src
COPY inventory-service/pom.xml inventory-service/
COPY inventory-service/src inventory-service/src
RUN mvn -B -f inventory-proto/pom.xml install -DskipTests \
    && mvn -B -f inventory-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /project/inventory-service/target/quarkus-app/ /app/
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
FROM maven:3.9.14-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY payment-service/pom.xml payment-service/
COPY payment-service/src payment-service/src
RUN mvn -B -f payment-service/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/payment-service/target/payment-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

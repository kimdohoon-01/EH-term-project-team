FROM maven:3.9.14-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY menu-service/pom.xml menu-service/
COPY menu-service/src menu-service/src
RUN mvn -B -f menu-service/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/menu-service/target/menu-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

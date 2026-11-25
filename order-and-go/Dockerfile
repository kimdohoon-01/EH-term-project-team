FROM maven:3.9.14-eclipse-temurin-17 AS build
WORKDIR /build

COPY order-and-go/pom.xml ./
COPY order-and-go/common/pom.xml common/
COPY order-and-go/user-service/pom.xml user-service/
COPY order-and-go/order-service/pom.xml order-service/
COPY order-and-go/notification-service/pom.xml notification-service/
COPY order-and-go/common/src common/src
COPY order-and-go/user-service/src user-service/src
COPY order-and-go/order-service/src order-service/src
COPY order-and-go/notification-service/src notification-service/src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS user-service
COPY --from=build /build/user-service/target/user-service-0.1.0.jar app.jar

FROM runtime AS order-service
COPY --from=build /build/order-service/target/order-service-0.1.0.jar app.jar

FROM runtime AS notification-service
COPY --from=build /build/notification-service/target/notification-service-0.1.0.jar app.jar

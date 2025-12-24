FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/scala-2.13/order-service*.jar /app/order-service.jar

EXPOSE 8084

CMD ["java", "-jar", "order-service.jar"]
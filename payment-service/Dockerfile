FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/scala-2.13/payment-service*.jar /app/payment-service.jar

EXPOSE 8085

CMD ["java", "-jar", "payment-service.jar"]
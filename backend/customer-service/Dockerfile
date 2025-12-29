FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/scala-2.13/customer-service*.jar /app/customer-service.jar

EXPOSE 8082

CMD ["java", "-jar", "customer-service.jar"]
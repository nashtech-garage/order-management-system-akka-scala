FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/scala-2.13/product-service*.jar /app/product-service.jar

EXPOSE 8083

CMD ["java", "-jar", "product-service.jar"]
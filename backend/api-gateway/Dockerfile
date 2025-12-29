FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/scala-2.13/api-gateway*.jar /app/api-gateway.jar

EXPOSE 8080

CMD ["java", "-jar", "api-gateway.jar"]
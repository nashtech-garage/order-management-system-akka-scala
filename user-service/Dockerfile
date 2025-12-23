FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/scala-2.13/user-service*.jar /app/user-service.jar

EXPOSE 8081

CMD ["java", "-jar", "user-service.jar"]
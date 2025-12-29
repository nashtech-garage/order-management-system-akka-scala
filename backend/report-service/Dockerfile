FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/scala-2.13/report-service*.jar /app/report-service.jar

EXPOSE 8086

CMD ["java", "-jar", "report-service.jar"]
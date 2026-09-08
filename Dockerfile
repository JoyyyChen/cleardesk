# Docker 镜像构建
FROM maven:3.8.6-openjdk-8 as builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests

FROM openjdk:8-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/cleardesk-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar","--spring.profiles.active=prod"]

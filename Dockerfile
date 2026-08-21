FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew .
COPY build.gradle .
COPY settings.gradle .
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app
COPY --from=build /app/build/libs/payment-gateway-challenge-java-0.0.1-SNAPSHOT.jar /app/app.jar
USER app

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

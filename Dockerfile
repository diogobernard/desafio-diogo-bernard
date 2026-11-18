FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY build-logic ./build-logic
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY app ./app
COPY domain ./domain
COPY migrations ./migrations
RUN chmod +x gradlew && ./gradlew --no-daemon :app:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

# syntax=docker/dockerfile:1

FROM eclipse-temurin:18-jdk AS build
WORKDIR /src
# dependencies first, so they're cached until the build files change
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -q

FROM eclipse-temurin:18-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
# uid 1000 matches the usual host user, so the mounted data/db stays writable
USER 1000:1000
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

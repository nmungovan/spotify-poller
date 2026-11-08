# --- build stage: JDK + Gradle wrapper ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Copy build files first so dependency downloads are cached unless they change
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# --- runtime stage: JRE only, non-root ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

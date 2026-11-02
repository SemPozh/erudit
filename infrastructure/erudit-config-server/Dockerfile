FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY infrastructure ./infrastructure
COPY services ./services
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew :infrastructure:erudit-config-server:bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/infrastructure/erudit-config-server/build/libs/erudit-config-server.jar app.jar
USER appuser
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

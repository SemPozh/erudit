FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY infrastructure ./infrastructure
COPY services ./services
COPY platform ./platform
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew :infrastructure:erudit-config-server:bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
ARG VCS_REF=worktree
ARG BUILD_DATE=unspecified
ARG WORKTREE_STATE=local
ARG MODULE
LABEL org.opencontainers.image.source="https://github.com/SemPozh/erudit" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.title="erudit-config-server" \
      io.erudit.worktree-state="${WORKTREE_STATE}"
RUN useradd --system --uid 10001 appuser && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/infrastructure/erudit-config-server/build/libs/erudit-config-server.jar app.jar
USER appuser
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

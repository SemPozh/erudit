# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copy Gradle wrapper and build configuration
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Convert Windows line endings and make Gradle wrapper executable
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Download dependencies for better Docker layer caching
RUN ./gradlew dependencies --no-daemon || true

# Copy application source
COPY src ./src

# Build the executable Spring Boot JAR
RUN ./gradlew bootJar --no-daemon


# ---- Runtime stage ----
FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

RUN useradd --system --uid 10001 appuser

# Copy the generated JAR
COPY --from=build /app/build/libs/*.jar /app/app.jar

# Run as non-root user
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

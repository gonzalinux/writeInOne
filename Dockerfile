# syntax=docker/dockerfile:1
FROM amazoncorretto:25-alpine AS builder
WORKDIR /app
ENV GRADLE_USER_HOME=/root/.gradle

# Copy only build scripts and wrapper — changes to source won't bust this layer
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle.kts build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts

# Download all dependencies. Cache mount persists the dependency + build
# cache across builds even when this layer itself gets invalidated.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew dependencies --no-daemon

# Copy source and compile. The same cache mount lets Gradle's build cache
# skip recompiling unchanged classes instead of rebuilding from scratch.
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew bootJar --no-daemon --build-cache

FROM amazoncorretto:25-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
RUN mkdir -p /app/logs
ENTRYPOINT ["java", "-jar", "app.jar"]

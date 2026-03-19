FROM amazoncorretto:25-alpine AS builder
WORKDIR /app

# Copy only build scripts and wrapper — changes to source won't bust this layer
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle.kts build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts

# Download all dependencies (cached as long as build files don't change)
RUN ./gradlew dependencies --no-daemon

# Copy source and compile
COPY src/ src/
RUN ./gradlew bootJar --no-daemon

FROM amazoncorretto:25-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

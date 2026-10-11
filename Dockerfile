# Rebuild CSS from the same sources shipped in the Java application.
FROM node:22-alpine AS styles
WORKDIR /styles
COPY package.json package-lock.json tailwind.config.js tailwind-input.css ./
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY src/main/resources/static ./src/main/resources/static
RUN npm run build:css

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
COPY --from=styles /styles/src/main/resources/static/tailwind.css ./src/main/resources/static/tailwind.css
RUN mvn -B -ntp clean verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/app.jar app.jar
USER 10001
ENV PORT=8080 SPRING_PROFILES_ACTIVE=prod JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

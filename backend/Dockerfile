# syntax=docker/dockerfile:1

# ---- Compilación ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# Primero solo el pom: las dependencias quedan en caché mientras no cambie.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/hucha-*.jar /app.jar

# ---- Ejecución ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S hucha && adduser -S hucha -G hucha
WORKDIR /app
COPY --from=build /app.jar app.jar
USER hucha
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

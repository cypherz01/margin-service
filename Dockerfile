# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests and quality gates (Checkstyle, JaCoCo) run in CI via `mvn verify`; the image build only packages
RUN mvn -B -q package -DskipTests -Dcheckstyle.skip -Djacoco.skip

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /workspace/target/margin-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

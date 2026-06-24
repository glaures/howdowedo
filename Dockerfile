# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app
# Run as an unprivileged user instead of root.
RUN useradd --system --uid 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8081
# JAVA_OPTS lets production tune the JVM (heap, GC, ...) without rebuilding the image.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

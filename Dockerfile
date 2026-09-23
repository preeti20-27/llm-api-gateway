# Multi-stage build: compile with the JDK, run with just the JRE — the final image
# never carries Maven, the build cache, or source code, only the runnable jar.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Dependencies are copied and resolved before the source code so Docker's layer
# cache can skip re-downloading them on every build — only invalidated when the
# pom itself changes, not on every source edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Runs as a non-root user — the default is root, which is more privilege than a
# stateless HTTP service ever needs.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/llm-api-gateway-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

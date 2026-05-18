# Stage 1 - Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Cache Maven and dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Build
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# Stage 2 - Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S smartvolt && adduser -S smartvolt -G smartvolt
USER smartvolt

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
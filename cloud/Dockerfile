# Stage 1 - Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Cache Maven and dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY shared/pom.xml shared/pom.xml
COPY cloud/pom.xml cloud/pom.xml
COPY edge/pom.xml edge/pom.xml
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -pl shared,cloud -am

# Build shared + cloud
COPY shared/src/ shared/src/
COPY cloud/src/ cloud/src/
RUN ./mvnw package -DskipTests -B -pl shared,cloud -am

# Stage 2 - Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S smartvolt && adduser -S smartvolt -G smartvolt
USER smartvolt

COPY --from=build /app/cloud/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

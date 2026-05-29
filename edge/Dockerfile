# Stage 1 - Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY shared/pom.xml shared/pom.xml
COPY edge/pom.xml edge/pom.xml
COPY cloud/pom.xml cloud/pom.xml
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -pl shared,edge -am

COPY shared/src/ shared/src/
COPY edge/src/ edge/src/
RUN ./mvnw package -DskipTests -B -pl shared,edge -am

# Stage 2 - Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S smartvolt && adduser -S smartvolt -G smartvolt
RUN mkdir -p /app/data && chown smartvolt:smartvolt /app/data
USER smartvolt

COPY --from=build /app/edge/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

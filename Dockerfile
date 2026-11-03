FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/cloud-service-1.0.0.jar app.jar
RUN useradd --system --uid 10001 cloud && mkdir -p /data/files && chown cloud /data/files
USER cloud
ENV STORAGE_DIR=/data/files
VOLUME /data/files
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

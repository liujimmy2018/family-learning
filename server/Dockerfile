FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
COPY packaging/portable ./packaging/portable
RUN mvn -B -ntp -Pportable -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
RUN mkdir -p /app/data/uploads && chown -R 10001:10001 /app
COPY --from=build /build/target/family-learning-server-0.5.0.jar /app/server.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]

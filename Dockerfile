FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 readjava \
    && useradd --uid 10001 --gid readjava --no-create-home readjava

WORKDIR /app
COPY --from=build --chown=readjava:readjava /workspace/target/read-java-*.jar /app/read-java.jar

USER readjava
ENV SPRING_PROFILES_ACTIVE=prod SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/read-java.jar"]

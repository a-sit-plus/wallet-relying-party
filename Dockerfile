# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY service/build.gradle.kts service/build.gradle.kts
COPY service/src service/src

RUN chmod +x gradlew
RUN ./gradlew --no-daemon --refresh-dependencies :service:bootJar

RUN set -eux; \
    jar="$(find /workspace/service/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)"; \
    test -n "$jar"; \
    cp "$jar" /workspace/service.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=build /workspace/service.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]

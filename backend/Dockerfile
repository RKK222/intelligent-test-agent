# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY test-agent-common/pom.xml test-agent-common/pom.xml
COPY test-agent-domain/pom.xml test-agent-domain/pom.xml
COPY test-agent-observability/pom.xml test-agent-observability/pom.xml
COPY test-agent-opencode-sdk-generated/pom.xml test-agent-opencode-sdk-generated/pom.xml
COPY test-agent-opencode-client/pom.xml test-agent-opencode-client/pom.xml
COPY test-agent-persistence/pom.xml test-agent-persistence/pom.xml
COPY test-agent-event/pom.xml test-agent-event/pom.xml
COPY test-agent-test-support/pom.xml test-agent-test-support/pom.xml
COPY test-agent-app/pom.xml test-agent-app/pom.xml
RUN mvn -q -DskipTests dependency:go-offline

COPY . .
RUN mvn -q -pl test-agent-app -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S testagent && adduser -S testagent -G testagent
COPY --from=build /workspace/test-agent-app/target/test-agent-app-*.jar /app/test-agent-app.jar
USER testagent

EXPOSE 8080
# 容器只运行后端 Java 进程；数据库、Redis 和 opencode server 必须通过环境变量指向外部服务。
ENTRYPOINT ["java", "-jar", "/app/test-agent-app.jar"]

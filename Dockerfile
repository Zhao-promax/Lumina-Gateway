# 构建阶段
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# SkyWalking Java Agent（全链路追踪）
FROM apache/skywalking-java-agent:9.7.0-java17 AS agent

# 运行阶段
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=agent /skywalking/agent /opt/skywalking
COPY --from=build /app/target/lumina-gateway-*.jar app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/skywalking/skywalking-agent.jar"
ENV SW_AGENT_NAME="lumina-gateway"
ENV SW_AGENT_COLLECTOR_BACKEND_SERVICES="skywalking-oap:11800"
ENTRYPOINT ["java", "-jar", "app.jar"]

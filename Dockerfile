# ClearDesk 镜像构建
#
# 构建：docker build -t cleardesk:local .
# 运行（需先有可用的 MySQL / Redis，见 README「本地启动」）：
#   docker run --rm -p 8080:8080 \
#     -e MYSQL_HOST=host.docker.internal -e MYSQL_USERNAME=root -e MYSQL_PASSWORD=123456 \
#     -e REDIS_HOST=host.docker.internal \
#     cleardesk:local
#
# 以 prod profile 启动。application-prod.yml 要求 MYSQL_HOST / MYSQL_USERNAME / MYSQL_PASSWORD /
# REDIS_HOST 必须由环境变量注入；MYSQL_PORT、MYSQL_DB、REDIS_PORT、REDIS_DATABASE 有默认值。
#
# 拉不到 Docker Hub 时，用镜像源覆盖基础镜像，例如：
#   docker build --build-arg REGISTRY=docker.1ms.run/ -t cleardesk:local .

ARG REGISTRY=

# ---------- 构建阶段：JDK 21 + Maven 3.9 ----------
FROM ${REGISTRY}maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先只复制 pom.xml 单独拉依赖，改源码时能命中这一层缓存
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

# ---------- 运行阶段：只要 JRE ----------
FROM ${REGISTRY}eclipse-temurin:21-jre
WORKDIR /app

# 不用 root 跑应用
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser

COPY --from=builder --chown=appuser:appuser /build/target/cleardesk-*.jar app.jar

USER appuser
EXPOSE 8080

# exec 形式，让 java 成为 PID 1，能正确收到 SIGTERM 并优雅停机
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]

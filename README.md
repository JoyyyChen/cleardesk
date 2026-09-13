# 清办（ClearDesk）

清办是面向小团队的工单后台。本期只提供账号能力：注册、登录、登出、当前用户，以及管理员分页搜索与删除。工单流程后续再加。

需要 **JDK 21**。技术栈为 Spring Boot 4.1、MyBatis-Plus 3.5、MySQL 8、Redis（Spring Session）。

## 本地启动

1. 启动 MySQL 和 Redis（应用不在 Compose 里）：

```bash
docker compose up -d
```

默认库名 `cleardesk`，MySQL `root` / `123456`（本机 3306），Redis `localhost:6379`。初始化脚本会创建 `user` 表，并保留账号唯一索引 `uk_userAccount`。本机 Windows 的 MySQL84 已改为手动启动，避免和 Docker 抢 3306。

2. 需要覆盖默认值时，参考 `.env.example`。未设置环境变量则使用本地默认配置。

3. 在 IDE 中启动 `ClearDeskApplication`。接口前缀：`http://localhost:8080/api`。

生产配置见 `application-prod.yml`，通过环境变量注入 `MYSQL_HOST`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`REDIS_HOST`。不要把密钥提交进仓库。

## 构建与命令行运行

Maven Wrapper 已可用，首次运行会下载 Maven 3.9.11（配置见 `.mvn/wrapper/maven-wrapper.properties`）：

```bash
./mvnw clean package        # 产物 target/cleardesk-0.0.1-SNAPSHOT.jar
./mvnw test                 # 需要本地 MySQL；测试配置已排除 Redis
```

也可以打成镜像运行。镜像是**两阶段构建**（Maven + JDK 21 编译，Temurin 21 JRE 运行），以 `prod` profile 启动，因此必须显式传库/缓存地址；容器内 `localhost` 指容器自己，连宿主机要用 `host.docker.internal`：

```bash
docker build -t cleardesk:local .
docker run --rm -p 8080:8080 \
  -e MYSQL_HOST=host.docker.internal -e MYSQL_USERNAME=root -e MYSQL_PASSWORD=123456 \
  -e REDIS_HOST=host.docker.internal \
  cleardesk:local
```

拉不到 Docker Hub 时用镜像源覆盖基础镜像：`docker build --build-arg REGISTRY=docker.1ms.run/ -t cleardesk:local .`

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/user/register` | 无 | 请求体：userAccount、userPassword、checkPassword |
| POST | `/user/login` | 无 | Session 存在 Redis |
| POST | `/user/logout` | 无 | 使 Session 失效 |
| GET | `/user/current` | 登录 | 返回 UserVO，不含密码 |
| GET | `/user/search` | 管理员 | username / current / pageSize |
| POST | `/user/delete` | 管理员 | 请求体：`{"id": 1}` |

管理员接口使用 `@AuthCheck`。Session 只存用户 id，角色和状态每次从数据库读取。

## 设计说明

- 账号仅支持 4-16 位字母、数字或下划线，密码 8-32 位。
- 密码使用 BCrypt 存储，登录用 `matches` 校验。
- `userAccount` 唯一索引，避免并发重复注册。
- 删除用户为逻辑删除（`isDelete`），不是物理删。
- 登录态通过 Spring Session 存 Redis，命名空间为 `cleardesk:session`。
- 时间字段为 `LocalDateTime`，JSON 使用 ISO-8601（时区 `Asia/Shanghai`）。
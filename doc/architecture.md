# 清办（ClearDesk）架构说明

本文件记录代码结构、关键设计决策和已知取舍。接口清单见 `README.md`。当前只实现账号能力，工单流程未开始。

## 1. 技术栈与版本

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | `pom.xml` 里 `java.version=21`，Maven Wrapper 默认 3.9.11 |
| Spring Boot | 4.1.1 | 注意 starter 名是 `spring-boot-starter-webmvc`（Boot 4 改名），Servlet 容器为 Tomcat 11 |
| MyBatis-Plus | 3.5.17 | `mybatis-plus-spring-boot4-starter`，`jakarta` 命名空间 |
| MySQL | 8.0 | `sql/create_table.sql` 建表 |
| Redis | 7 | 只用于 Spring Session 存登录态 |
| 其他 | spring-security-crypto（仅 BCrypt）、Lombok、commons-lang3 | 未引入完整 Spring Security |

## 2. 包结构

```
com.cleardesk
├── ClearDeskApplication        启动类，@MapperScan("com.cleardesk.mapper")
├── annotation/AuthCheck        标记接口需要登录 / 需要管理员
├── common/                     BaseResponse、ResultUtils、ErrorCode、PageResult
├── config/                     MyBatisPlusConfig（分页插件）、PasswordEncoderConfig、WebMvcConfig
├── constant/UserConstant       角色、状态、分页默认值、Session key
├── context/UserHolder          ThreadLocal 保存当前请求的登录用户
├── controller/UserController   6 个接口
├── exception/                  BusinessException + GlobalExceptionHandler
├── interceptor/AuthInterceptor 鉴权 + 装载 UserHolder
├── mapper/UserMapper           仅继承 BaseMapper，无自定义 SQL
├── model/domain/User           数据库实体
├── model/dto/                  4 个入参对象
├── model/vo/UserVO             出参对象，无 userPassword
└── service/UserService(+impl)  业务逻辑
```

## 3. 一次请求的完整路径

以 `GET /api/user/search` 为例：

1. Tomcat 从 `Cookie: SESSION=...` 反查 Redis（Spring Session，命名空间 `cleardesk:session`）恢复 HttpSession。
2. `AuthInterceptor.preHandle` 看到方法上有 `@AuthCheck(mustAdmin = true)`：
   - Session 里取不到 `userLoginState`（Long）→ 抛 `NOT_LOGIN`；
   - 拿 id 查库，用户不存在 → `session.invalidate()` 后抛 `NOT_LOGIN`；
   - 用户状态不是 0 → `session.invalidate()` 后抛 `FORBIDDEN`；
   - 角色不是 1 → 抛 `NO_AUTH`；
   - 全部通过 → `UserHolder.set(user)`。
3. Controller → Service 查库分页，`User` 转 `UserVO`。
4. `afterCompletion` 里 `UserHolder.remove()`，避免线程池复用导致用户串号。
5. 返回统一体 `{code, data, message, description}`；业务异常由 `GlobalExceptionHandler` 转成对应错误码，系统异常只回 `50000`，不暴露堆栈。

`@AuthCheck` 是方法级注解，`AuthInterceptor` 拦截 `/**` 但只对带注解的 HandlerMethod 生效，所以未标注的接口（注册、登录、登出）不校验登录态。

## 4. 关键设计决策

**Session 只存用户 id。** 角色和状态每次请求从数据库读，避免「管理员被降权 / 禁用 / 删除后，旧 Session 仍带旧权限」的窗口期。代价是每次鉴权多一次主键查询，对当前规模可忽略。

**登录失败文案统一。** 账号格式非法、账号不存在、密码错误都返回「账号或密码错误」，防止攻击者用响应差异枚举有效账号。原始失败原因只进日志（`user_login_failed`）。

**唯一索引兜底并发注册。** Service 先 `count` 一次做前置校验，但真正防重靠 `uk_userAccount`：并发下会抛 `DuplicateKeyException`，Service 捕获后转成业务错误。

**逻辑删除而非物理删。** `User` 上的 `@TableLogic` 让 MyBatis-Plus 自动改写 SQL：查询自动加 `isDelete = 0`，`removeById` 变成 `update ... set isDelete = 1`。管理员不能删自己，避免删掉最后一个管理入口。

**密码 BCrypt。** 用 `matches` 校验，不自行比对密文。`PasswordEncoderConfig` 单独抽成 Bean，注册和登录共用。

**分页必须注册插件。** `PaginationInnerInterceptor` 没注册时 `page()` 不会拼 `limit`，会把整表拉进内存；`MAX_PAGE_SIZE = 20` 再兜一层，防止 `pageSize=999999`。

**列名不转下划线。** `map-underscore-to-camel-case: false`，因为表里列名本身就是 `userAccount` 这种驼峰，与实体字段一致。

## 5. 数据模型

单表 `user`，见 `sql/create_table.sql`。要点：

- `id` 自增主键；`userAccount` 唯一索引 `uk_userAccount`；`userPassword` 存 BCrypt 密文。
- `userRole`：0 普通用户 / 1 管理员；`userStatus`：0 正常 / 1 禁用；`isDelete`：0 未删 / 1 已删。
- `createTime` / `updateTime` 由 MySQL 默认值和 `on update` 维护，Java 侧不赋值。
- 时间字段是 `LocalDateTime`，JSON 序列化为 ISO-8601，时区 `Asia/Shanghai`。

## 6. 配置

- `application.yml`：本地默认值，全部用 `${ENV:default}` 兜底（本机 MySQL `root`/`123456`、Redis `localhost:6379`），接口前缀 `server.servlet.context-path: /api`。
- `application-prod.yml`：密钥无默认值，`MYSQL_HOST` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` / `REDIS_HOST` 必须由环境变量注入，且开启 `useSSL=true`。
- 仓库里的 `.env.example` 只列变量名，真实 `.env` 已被 `.gitignore` 忽略。

## 7. 测试

| 测试 | 依赖 | 覆盖 |
|------|------|------|
| `PasswordEncoderTest` | 无容器 | BCrypt 加解密、同一密码两次哈希不同 |
| `ClearDeskApplicationTests` | MySQL | Spring 上下文可加载 |
| `UserServiceTest` | MySQL | 注册参数校验、重复账号；`@Transactional` 回滚，不留数据 |

`src/test/resources/application.yml` 排除了 Redis 及其 Session 自动配置，所以测试不需要 Redis，但仍需一个可连的本地 MySQL（默认库 `cleardesk`，表要先按 `sql/create_table.sql` 建好）。CI 上要先起 MySQL 再跑 `./mvnw test`。

## 8. 构建与部署

Maven Wrapper（`.mvn/wrapper/`）随仓库提交，`./mvnw` 首次运行会下载 Maven 3.9.11。

`Dockerfile` 是两阶段构建：`maven:3.9-eclipse-temurin-21` 编译并 `dependency:go-offline` 预热依赖层（只改源码时能命中缓存），`eclipse-temurin:21-jre` 运行，以非 root 用户 `appuser` 启动，`ENTRYPOINT` 用 exec 形式让 `java` 成为 PID 1 以正确接收 SIGTERM。基础镜像可用 `--build-arg REGISTRY=<镜像源>/` 覆盖（国内网络拉不到 Docker Hub 时需要）。

镜像里跑的是 `prod` profile，所以 `docker run` 必须提供 `MYSQL_*` / `REDIS_HOST`；容器内 `localhost` 指容器自己，连宿主机数据库用 `host.docker.internal`。

## 9. 已知取舍与后续

- **`Content-Type` 不带 `charset`**：返回体实际是 UTF-8 字节（已按字节核对），但响应头只写 `application/json`。`curl` / 浏览器按 UTF-8 解析没问题；若客户端依赖 charset 参数，可在 `application.yml` 指定 `spring.http.converters.preferred-json-mapper` 之外单独配置 `StringHttpMessageConverter`。
- **`/user/search` 只支持按 `username` 模糊查询**，不支持按角色、状态过滤；`UserQueryRequest` 需要相应扩字段。
- **没有自定义 SQL**：`UserMapper` 只继承 `BaseMapper`，原来占位的 `mapper/UserMapper.xml`（空的 resultMap / 列清单）已删除。将来加复杂查询时再新建并配置 `mybatis-plus.mapper-locations`。
- **拦截器里 `getById` 的性能**：见第 4 节，可按需加缓存。
- **工单模块尚未开始**：表结构、状态机、权限模型都还没有。

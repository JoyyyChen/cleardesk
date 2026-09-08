# 清办（ClearDesk）

清办是面向小团队的工单后台。本期只提供账号能力：注册、登录、登出、当前用户，以及管理员分页搜索与删除。工单流程后续再加。

## 本地启动

1. 启动 MySQL 和 Redis（应用不在 Compose 里）：

```bash
docker compose up -d
```

默认库名 `cleardesk`，MySQL `root` / `123456`，Redis `localhost:6379`。初始化脚本会创建 `user` 表，并保留账号唯一索引 `uk_userAccount`。

2. 需要覆盖默认值时，参考 `.env.example`。未设置环境变量则使用本地默认配置。

3. 在 IDE 中启动 `ClearDeskApplication`。接口前缀：`http://localhost:8080/api`。

生产配置见 `application-prod.yml`，通过环境变量注入 `MYSQL_HOST`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`REDIS_HOST`。不要把密钥提交进仓库。

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

- 密码使用 BCrypt 存储，登录用 `matches` 校验。
- `userAccount` 唯一索引，避免并发重复注册。
- 登录态通过 Spring Session 存 Redis，命名空间为 `cleardesk:session`。
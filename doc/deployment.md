# 部署手册：把 ClearDesk 挂到公网

目标：有一个能点开的地址，面试时可以直接演示。这是求职场景下投入产出比最高的一件事。

> **服务器已经买好了？** 这份文件讲原理（面试要问的"为什么"），
> 逐步操作请照 **[deploy-runbook.md](deploy-runbook.md)** 走：命令、预期输出、报错对照表都在那里。

## 先决定用哪条路

| 方案 | 成本 | 适合 | 代价 |
|------|------|------|------|
| **A. 云服务器 + Docker Compose**（推荐） | 学生机约 10 元/月 | 想学真实运维链路、面试可讲 MySQL/Redis/Nginx/证书 | 要自己配 Nginx、证书、防火墙 |
| **B. PaaS（Railway / Render）** | 有免费额度 | 只想快速拿到一个地址 | 免费层有休眠；托管环境里讲不出部署细节 |
| **C. 云数据库 + 应用上 PaaS** | 约 10-40 元/月 | 不想碰服务器但想要稳定数据库 | 配置项更多 |

**建议 A。** 理由不是"更好"，而是：面试官问"你怎么部署的"，答"我配了 Nginx 反代、用 certbot 签的证书、MySQL 和 Redis 关掉公网只在内网暴露"比答"我推到 Railway 上了"能多聊十分钟。

本文按方案 A 写。

## 1. 服务器准备

1. 买一台学生机（阿里云/腾讯云，2 核 2G 起），系统选 **Ubuntu 22.04/24.04**。
2. 安全组只放这 3 个端口：**22（SSH）、80（HTTP）、443（HTTPS）**。
   **不要放 3306 和 6379** —— 这是最常见的生产事故来源，把数据库直接裸奔在公网上会被扫库勒索。这条本身就是面试加分点。
3. 装 Docker（官方脚本）：

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # 重新登录后生效
```

如果拉不到镜像（国内网络），配置镜像加速器：编辑 `/etc/docker/daemon.json`

```json
{ "registry-mirrors": ["https://docker.1ms.run"] }
```

然后 `sudo systemctl restart docker`。

## 2. 代码上传

推荐用 git（服务器上 `git clone` 你的仓库），这样后续更新只要 `git pull`。私有仓库用 deploy key 或访问令牌。

**不要**把 `.env` 提交进仓库；它在本地和服务器上各存一份。

## 3. 服务器上的目录结构

```
/opt/cleardesk/
├── .env                  # 真实密码，权限 600，不进仓库
├── docker-compose.prod.yml
├── Dockerfile
├── sql/create_table.sql
└── src/ ...
```

`.env`（**在服务器上手写，不要提交**）：

```bash
MYSQL_ROOT_PASSWORD=<自己生成一个强密码>
MYSQL_DATABASE=cleardesk
MYSQL_USER=cleardesk
MYSQL_PASSWORD=<另一个强密码>
```

生成随机密码：`openssl rand -base64 24`

## 4. 启动

`docker-compose.prod.yml`（本仓库已提供）做了这几件事，起服务前先读一遍注释理解：

- MySQL 只在**内部网络**暴露，不映射到宿主机端口；
- 应用用 `mysql` / `redis` 作为主机名（Docker 内部 DNS），**不是 localhost**；
- 用 `healthcheck` + `depends_on: condition: service_healthy` 保证 MySQL 就绪后才起应用（否则应用启动时连不上库会直接退出）；
- 数据库和 Redis 的数据挂在 `volumes` 上，容器重建不丢数据。

```bash
cd /opt/cleardesk
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f app     # 看启动日志
```

看到 `Started ClearDeskApplication` 就成了。此时应用监听容器内 8080，但还没暴露给外网。

> **注意：如果你在本机同时用过开发用的 `docker-compose.yml`，两个文件的项目名默认都是目录名，容器名会撞**
> （都叫 `cleardesk-mysql-1`），后起的会把先起的**替换掉**。
> 解决办法是给生产编排显式指定项目名：
>
> ```bash
> docker compose -p cleardesk-prod -f docker-compose.prod.yml up -d
> ```
>
> 服务器上只有一个编排，不会遇到这个问题，但本地验证时会踩到。

## 4.5 启动失败时的排查顺序

按这个顺序看，能覆盖绝大多数部署问题：

```bash
# 1) 应用日志里最下面的 Caused by 才是根因，不要只看最上面那个异常
docker compose -f docker-compose.prod.yml logs app | tail -50

# 2) 应用能否解析到数据库主机名（应输出内网 IP）
docker compose -f docker-compose.prod.yml exec app getent hosts mysql

# 3) 应用实际拿到的环境变量对不对
docker compose -f docker-compose.prod.yml exec app env | grep -E 'MYSQL|REDIS'

# 4) 数据库里的表建了没有（application 账号能否登录）
docker compose -f docker-compose.prod.yml exec mysql \
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -D cleardesk -e "show tables;"
```

**已经踩过的坑，记下来避免重犯：**

| 现象 | 根因 | 修法 |
|------|------|------|
| 启动报 `Public Key Retrieval is not allowed` | MySQL 8 默认 `caching_sha2_password`，非 SSL 连接下需要显式允许取公钥。开发配置里有这个参数，生产配置曾经漏了 | JDBC URL 加 `allowPublicKeyRetrieval=true`（已在 `application-prod.yml` 修好） |
| 报 `Communications link failure` / `UnknownHostException` | 主机名写了 `localhost`，容器里的 `localhost` 指容器自己 | 改用 compose 的 service 名 `mysql` / `redis` |
| 应用比 MySQL 先就绪，起来就退出 | `depends_on` 只保证启动顺序，不保证 MySQL ready | 用 `healthcheck` + `condition: service_healthy` |
| 本机两个编排互相顶掉容器 | 项目名都是目录名 | `docker compose -p <名字> -f ...` |

## 5. Nginx + HTTPS

应用本身不处理证书，交给 Nginx（生产惯例：应用只监听内网，TLS 在入口终止）。

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

`/etc/nginx/sites-available/cleardesk`：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/cleardesk /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d your-domain.com     # 自动签证书并改配置
```

域名：没有的话可以在阿里云/腾讯云买一个便宜后缀（.top/.xyz 一年几块钱），解析 A 记录到服务器 IP。

**注意本项目的 context-path 是 `/api`**，所以对外地址是 `https://your-domain.com/api/user/login`。Nginx 不需要额外改路径，`proxy_pass` 会原样转发。

## 6. 部署后必查的 5 件事

```bash
# 1) 服务是否在跑
docker compose -f docker-compose.prod.yml ps

# 2) 数据库表是否建好（首次启动才执行 init.sql）
docker compose -f docker-compose.prod.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk -e "show tables;"

# 3) 接口是否通（HTTPS）
curl -s https://your-domain.com/api/user/register \
  -H 'Content-Type: application/json' \
  -d '{"userAccount":"probe001","userPassword":"12345678","checkPassword":"12345678"}'

# 4) 数据库端口是否确实没暴露（应该连不上）
nc -zv your-server-ip 3306     # 期望失败
nc -zv your-server-ip 6379     # 期望失败

# 5) 重启后数据是否还在（验证 volume 生效）
docker compose -f docker-compose.prod.yml restart mysql
```

## 7. 更新代码

```bash
cd /opt/cleardesk
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

## 8. 面试会追问的点（部署部分）

提前想清楚答案：

1. 为什么把 3306/6379 关掉？如果必须远程连数据库怎么办？（答：SSH 隧道或跳板机，不要直接开放端口）
2. 应用连数据库为什么用 `mysql` 而不是 `localhost`？（答：容器各有自己的网络命名空间，localhost 指容器自身；Docker Compose 的 service 名会解析成内部 DNS）
3. 为什么用 `service_healthy` 而不是 `depends_on` 的简单写法？（答：后者只保证容器"启动顺序"，不保证 MySQL 真的 ready，应用会在初始化未完成时连库失败退出）
4. 改了 `MYSQL_SSL` 默认值是为了什么？（答：云数据库走公网需要 SSL；应用与库同在内网时关掉省开销，两种场景默认值不同，所以做成了环境变量）
5. 日志和监控怎么看？（这一项现在是空的，属于你的待补项：加 Spring Boot Actuator 的 `/actuator/health`，让 Nginx 或平台能探活）

## 9. 待补项（做完上面再来）

- [ ] 加 `spring-boot-starter-actuator`，暴露 `/actuator/health`，再配一个 HTTP 健康检查
- [x] 数据备份：`scripts/backup-db.sh`（mysqldump + gzip + 轮转，挂 cron 即可）；**还没做的**是推到对象存储，机器挂了备份会一起没
- [ ] 用 Flyway 或 Liquibase 管表结构变更（现在还是手工执行 `create_table.sql`，改了字段只能手改库）
- [ ] 前端页面（只投后端岗可以不做）
- [ ] GitHub Actions：push 自动构建 + 推镜像到服务器（把"我手动部署"升级成"有 CI/CD"）

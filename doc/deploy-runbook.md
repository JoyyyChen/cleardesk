# 部署执行单：服务器已到手 → 可以点开的地址

> `doc/deployment.md` 讲的是**为什么**这样部署（面试可讲的原理）；
> 这份文件讲的是**按顺序敲什么**、每步该看到什么、报错怎么查。
> 目标：拿到 `http://<服务器IP>:8080/api/...` 能通的地址，且数据库没有裸奔在公网。

## 当前选定的路线

| 项 | 选择 | 说明 |
|----|------|------|
| 服务器 | Ubuntu 22.04 / 24.04 | 云服务器，2 核 2G 起 |
| 对外访问 | **先用 `IP:8080`**，暂无域名 | 先拿到能点开的地址，域名和 HTTPS 放第二阶段 |
| 代码上传 | GitHub / Gitee + 服务器 `git clone` | 后续更新只要 `git pull` |
| 执行方式 | 你自己在服务器上敲，卡住贴日志 | 推荐，面试时讲得出来 |

**这个阶段不做的**：域名、HTTPS、Nginx。等 `IP:8080` 通了再接（见阶段 5）。

---

## 阶段 0：本机（Windows）要改的两处

### 0.1 把应用端口放开给公网（用覆盖文件，不改主文件）

`docker-compose.prod.yml` 里应用只监听 `127.0.0.1:8080`（留给 Nginx）。
没有域名时外面进不来，仓库已经准备了覆盖文件 `docker-compose.ip-only.yml`，
启动时多加一个 `-f` 即可，生产编排一个字都不用动：

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.ip-only.yml up -d --build
```

> 为什么不直接改主文件：那份文件描述的是"最终态"（应用只监听回环）。
> 临时过渡写在覆盖文件里，接入域名那天去掉 `-f docker-compose.ip-only.yml` 就回到最终态，
> 不会出现"改了忘改回来"——面试被问"你的应用端口为什么对公网开放"，答案是
> "过渡期用 override 文件，最终态是只监听回环 + Nginx 终止 TLS"，而不是"我不知道"。
>
> 覆盖文件用了 `ports: !override`（需要 Docker Compose v2.24.4+，`docker compose version` 确认）。
> 版本更低的话，把主文件里的 `"127.0.0.1:8080:8080"` 直接改成 `"8080:8080"` 也行，只是别忘了以后改回来。

### 0.2 把代码推到远程仓库

本地仓库现在**没有 remote**，服务器无法 clone。到 GitHub/Gitee 建一个**私有**仓库，然后：

```powershell
cd D:\Program_Code\User_Center\cleardesk

# 1) 给脚本加可执行位（不然服务器上只能 bash scripts/xxx.sh，不能 ./scripts/xxx.sh）
git update-index --chmod=+x scripts/smoke-test.sh scripts/backup-db.sh

# 2) 推送前确认两件事故高发点
git check-ignore -v .env      # 期望：输出 .gitignore:28:.env  → 被忽略，安全
git status --short            # 期望：看不到 .env / target / .idea

# 3) 提交并推到一个新建的【私有】仓库
git add -A
git commit -m "部署准备：ip-only 覆盖文件、自检与备份脚本"
git remote add origin git@github.com:<你的账号>/cleardesk.git
git branch -M main
git push -u origin main
```

服务器用的是**私有仓库**，clone 时用 deploy key 或访问令牌，不要用账号密码。

---

## 阶段 1：服务器初始化（SSH 登录后执行）

### 1.1 安全组 / 防火墙：只放 3 个端口

云控制台安全组入方向规则：

| 端口 | 用途 | 现在要不要开 |
|------|------|--------------|
| 22 | SSH | 开（建议只允许你的出口 IP） |
| 8080 | 阶段一：无域名时直接用 IP 访问应用 | 开；**接入 Nginx 后关掉** |
| 80 / 443 | 阶段二 Nginx + 证书 | 现在不用开 |
| 3306 / 6379 | 数据库、缓存 | **绝对不开** |

```bash
# 确认服务器自己的 ufw 状态；如果云镜像默认开了 ufw，要放行 8080
sudo ufw status
# 只在 ufw 处于 active 时才需要下面两条
sudo ufw allow 22/tcp && sudo ufw allow 8080/tcp
```

⚠️ **Docker 会绕过 ufw**：端口映射由 Docker 直接写 iptables，`ufw deny 3306` 挡不住容器发布出来的端口。
所以安全组是第一道也是唯一可靠的闸门 —— 这也是本编排**故意不映射 3306/6379** 的原因。

### 1.2 装 Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
```

**重新登录 SSH**（组权限要新会话才生效），然后验证：

```bash
docker version && docker compose version
```

预期：能看到 Client 和 Server 两段版本号，`Docker Compose version v2.x`。看不到 Server 段说明没重登或 daemon 没起来。

国内网络拉不到镜像时，配加速器：

```bash
sudo mkdir -p /etc/docker
printf '{\n  "registry-mirrors": ["https://docker.1ms.run"]\n}\n' | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker
```

### 1.3 拉代码到 /opt/cleardesk

```bash
sudo mkdir -p /opt/cleardesk && sudo chown $USER:$USER /opt/cleardesk
git clone <你的私有仓库地址> /opt/cleardesk
cd /opt/cleardesk
ls
```

预期能看到 `Dockerfile`、`docker-compose.prod.yml`、`sql/`、`src/`。

---

## 阶段 2：配置与启动

### 2.1 生成 .env（在服务器上生成，不要从 Windows 传）

```bash
cd /opt/cleardesk
ROOT_PW=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
APP_PW=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
umask 077
cat > .env <<EOF
MYSQL_ROOT_PASSWORD=$ROOT_PW
MYSQL_DATABASE=cleardesk
MYSQL_USER=cleardesk
MYSQL_PASSWORD=$APP_PW
EOF
chmod 600 .env
echo "ROOT=$ROOT_PW"; echo "APP=$APP_PW"
```

> **把输出的两个密码抄到你的密码管理器里。** `.env` 丢了 = 数据库密码丢了（MySQL 密码只在数据卷首次初始化时生效，改文件不会改库里的密码）。
> 用 `cat > .env` 而不是从 Windows 上传，是为了避免 **CRLF 换行**被当成密码的一部分，那是极难查的登录失败。

确认文件干净：

```bash
cat -A .env | head -3
```

预期行尾是 `$`；如果是 `^M$`，说明是 Windows 换行，必须重写。

### 2.2 起服务

```bash
cd /opt/cleardesk
docker compose -f docker-compose.prod.yml -f docker-compose.ip-only.yml up -d --build
```

首次构建要拉 `maven:3.9-eclipse-temurin-21` 并编译，**2 核机大约 3-8 分钟**。
拉基础镜像失败就用镜像源（构建参数从 `.env` 读，所以用环境变量传进去）：

```bash
REGISTRY=docker.1ms.run/ docker compose -f docker-compose.prod.yml -f docker-compose.ip-only.yml up -d --build
```

看启动日志：

```bash
docker compose -f docker-compose.prod.yml logs -f app
```

**成功的标志**（Ctrl+C 只是退出看日志，不影响容器）：

```
Started ClearDeskApplication in x.xxx seconds
```

### 2.3 启动失败时的排查顺序

先让当前终端认识 `.env` 里的变量（下面要用到一个 shell 变量），**每个新开的 SSH 会话都要先做一次**：

```bash
cd /opt/cleardesk && set -a && . ./.env && set +a
```

然后按顺序查，别乱猜：

```bash
# 1) 找最下面的 Caused by（最上面那个异常通常只是包装）
docker compose -f docker-compose.prod.yml logs app | tail -50

# 2) 应用能否解析到数据库主机名（应输出内网 IP，不是报错）
docker compose -f docker-compose.prod.yml exec app getent hosts mysql

# 3) 应用拿到的环境变量对不对
docker compose -f docker-compose.prod.yml exec app env | grep -E 'MYSQL|REDIS'

# 4) 表建了没有（首次启动才执行 sql/create_table.sql）
docker compose -f docker-compose.prod.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk -e "show tables;"
```

| 现象 | 根因 | 修法 |
|------|------|------|
| `Public Key Retrieval is not allowed` | MySQL 8 `caching_sha2_password` + 非 SSL | JDBC URL 加 `allowPublicKeyRetrieval=true`（已修） |
| `Communications link failure` / `UnknownHostException` | 主机名写成 localhost | 用 service 名 `mysql` / `redis` |
| 应用起来就退出 | MySQL 没 ready | 已用 `service_healthy`（已修） |
| `Access denied for user 'cleardesk'` | `.env` 里的 `MYSQL_PASSWORD` 被改过，但卷里还是老密码 | 用 root 改库里的密码，或 `down -v` 重建（**会清数据**） |
| 构建卡在拉基础镜像 | 国内网络 | 加 `REGISTRY=docker.1ms.run/` |
| 改了 `.env` 但没生效 | compose 只在创建容器时读 | `up -d --force-recreate` |

---

## 阶段 3：验证（这一步做完才算部署成功）

### 3.1 容器状态

```bash
docker compose -f docker-compose.prod.yml ps
```

预期：`mysql`、`redis` 是 `healthy`，`app` 是 `Up`。

### 3.2 从服务器本机打接口

```bash
curl -s -X POST http://127.0.0.1:8080/api/user/register \
  -H 'Content-Type: application/json' \
  -d '{"userAccount":"probe001","userPassword":"12345678","checkPassword":"12345678"}'
```

预期返回 `{"code":0,...,"data":<id>}`。返回 `code:40000` 之类是业务码（参数问题），能返回 JSON 就说明**应用活着**。

### 3.3 从你自己的电脑打接口（真正验证公网链路）

连通性用 `curl.exe`（Windows 上的 PowerShell 里 `curl` 是 `Invoke-WebRequest` 的别名，参数不通用）。
反斜杠转义是 PowerShell 的需要，容易敲错；嫌麻烦就先写个 `body.json` 文件，再用 `-d "@body.json"`：

```powershell
curl.exe -s -X POST http://<服务器IP>:8080/api/user/register `
  -H 'Content-Type: application/json' `
  -d '{\"userAccount\":\"probe002\",\"userPassword\":\"12345678\",\"checkPassword\":\"12345678\"}'
```

连不上时按这个顺序查：

```powershell
Test-NetConnection <服务器IP> -Port 8080   # TcpTestSucceeded : True ？
```

三个最常见的失败点：

1. **安全组没放 8080**（最常见，`Test-NetConnection` 直接 False）；
2. compose 里端口还是 `127.0.0.1:8080:8080`（服务器本机 curl 能通、外网不通，就是这个）；
3. 服务器上 `sudo ufw status` 是 active 且没放 8080。

### 3.4 数据库确实没暴露（面试必讲的一条）

在你 Windows 上执行，**两个都应该是失败**：

```powershell
Test-NetConnection <服务器IP> -Port 3306
Test-NetConnection <服务器IP> -Port 6379
```

`TcpTestSucceeded : False` = 正确。如果 `True`，立刻回安全组删掉这两条规则。

### 3.5 造一个管理员账号

**这一步不做，演示时 `search` / `delete` 两个管理员接口全部 403。**
注册接口只能创建普通用户（`userRole` 默认 0），提权必须手工改库：

```bash
cd /opt/cleardesk
set -a; . ./.env; set +a
docker compose -f docker-compose.prod.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk \
  -e "update user set userRole=1 where userAccount='probe001'; select id, userAccount, userRole from user;"
```

然后验证管理员接口（cookie 存成文件，便于复用）：

```bash
curl -s -c /tmp/ck.txt -X POST http://127.0.0.1:8080/api/user/login \
  -H 'Content-Type: application/json' \
  -d '{"userAccount":"probe001","userPassword":"12345678"}'

curl -s -b /tmp/ck.txt "http://127.0.0.1:8080/api/user/current"
curl -s -b /tmp/ck.txt "http://127.0.0.1:8080/api/user/search?current=1&pageSize=10"
```

### 3.6 重启和数据持久化

```bash
docker compose -f docker-compose.prod.yml restart
docker compose -f docker-compose.prod.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk -e "select count(*) from user;"
sudo reboot   # 再等 1 分钟重连，接口应该还能用（restart: unless-stopped 生效）
```

### 3.7 一键自检

仓库提供了 `scripts/smoke-test.sh`：容器健康、内网 DNS 解析、环境变量、建表、管理员账号、
端口暴露、接口连通性一次全查一遍，并给出下一步该做什么。

```bash
cd /opt/cleardesk
bash scripts/smoke-test.sh
```

脚本对**本次部署用的是什么文件**很敏感：如果启动时带了 `-f docker-compose.ip-only.yml`，
自检也要带上，否则它看到的状态不是真实的：

```bash
COMPOSE_FILE="docker-compose.prod.yml -f docker-compose.ip-only.yml" bash scripts/smoke-test.sh
```

预期结尾：`通过 N 项，失败 0 项`。有 FAIL 就按提示回到第 2.3 节的排查顺序。

---

## 阶段 4：日常运维

把一次 `up` 用到的文件固定成别名，避免每次都要打两个 `-f`（写进 `~/.bashrc` 更省事）：

```bash
cd /opt/cleardesk
alias dc='docker compose -f docker-compose.prod.yml -f docker-compose.ip-only.yml'

dc ps                                  # 看状态
dc logs --tail=100 -f app              # 看日志
git pull && dc up -d --build           # 拉新代码并重建应用
```

> 接入域名后（阶段 5）记得把别名里的 `-f docker-compose.ip-only.yml` 去掉。

**回滚**：把 `git pull` 换成 `git checkout <上一个 commit>`，再 `up -d --build`。
这就是"有版本控制"的价值，面试可以主动讲——比"部署失败了只能重装"强得多。

**备份**（`doc/deployment.md` 里的待补项，脚本已提供）：

```bash
cd /opt/cleardesk && bash scripts/backup-db.sh          # 产出 backups/cleardesk-<时间>.sql.gz
```

挂定时任务（每天 3 点）：

```bash
crontab -e
# 加一行：
0 3 * * * cd /opt/cleardesk && bash scripts/backup-db.sh >> backups/backup.log 2>&1
```

**恢复演练**（没演练过的备份不算备份）：

```bash
gunzip -c backups/cleardesk-<时间>.sql.gz | \
  docker compose -f docker-compose.prod.yml exec -T mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk
```

---

## 阶段 5：接域名和 HTTPS（IP 验证通过后再做）

1. 买个便宜域名（.top/.xyz），解析 A 记录到服务器 IP；
2. **去掉覆盖文件**：启动命令不再带 `-f docker-compose.ip-only.yml`（主文件本来就是 `127.0.0.1:8080:8080`），
   同时安全组关掉 8080、开 80/443；
3. 按 `doc/deployment.md` 第 5 节配 Nginx + `certbot --nginx -d 你的域名`；
4. 对外地址变成 `https://你的域名/api/user/login`（项目 context-path 是 `/api`，Nginx 不用改路径）；
5. 顺手把 session cookie 加上 `secure: true`（`application.yml` 的 `server.servlet.session.cookie`），
   有了 HTTPS 之后明文 HTTP 就不该再带 cookie。

> 国内服务器要注意：**域名解析到大陆 IP 做 80/443 访问需要备案**，备案没下来时先用 `IP:8080` 演示，或者用海外轻量机。

---

## 这个阶段故意留下的技术债（面试可主动说）

| 债 | 风险 | 后续动作 |
|----|------|----------|
| 只有 IP、没有 HTTPS | 流量明文；`Set-Cookie` 没有 `Secure` 标记 | 阶段 5 上 Nginx + 证书，并把 cookie 加 `secure: true` |
| 应用端口直接对公网开 8080 | 暴露面比 Nginx 方案大 | 阶段 5 收回到回环地址 |
| 没有健康检查探针 | 挂了没人知道 | 加 `spring-boot-starter-actuator`，暴露 `/actuator/health`，再让 Nginx/监控探活 |
| 手工改库提权 | 无法审计 | 后续做管理端"角色变更"接口 + 操作日志 |
| 表结构靠 init.sql 手工维护 | 改字段只能手改库 | 引入 Flyway / Liquibase |

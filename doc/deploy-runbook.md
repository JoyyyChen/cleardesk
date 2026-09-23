# 部署执行单：服务器已到手 → 可以点开的地址

> `doc/deployment.md` 讲的是**为什么**这样部署（面试可讲的原理）；
> 这份文件讲的是**按顺序敲什么**、每步该看到什么、报错怎么查。
> 目标：拿到 `http://<服务器IP>:8080/api/...` 能通的地址，且数据库没有裸奔在公网。
>
> **配套文档**：
> - 第一次部署、需要逐条命令解释 → 看 **[beginner-deploy.md](beginner-deploy.md)**（更详细，含 13 个真实踩过的坑）
> - 部署完成后还要做什么 → 看 **[TODO.md](TODO.md)**
>
> **本执行单已经过一次实战校验**，下面的命令和预期输出都是实测过的（含阿里云轻量应用服务器）。
> 凡标注"实测"的地方都附了真实数据，可以用来判断你的环境是否正常。

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
# 启动（镜像已构建好的前提下；构建步骤见 2.2）
docker compose -f docker-compose.prod.yml -f docker-compose.ip-only.yml up -d
```

> 覆盖文件只做一件事：把端口从 `127.0.0.1:8080:8080` 改成 `8080:8080`。**它不影响构建**，
> 所以启动命令里不该出现 `--build`（早期版本这里是 `up -d --build`，已按实测流程改掉）。

> 为什么不直接改主文件：那份文件描述的是"最终态"（应用只监听回环）。
> 临时过渡写在覆盖文件里，接入域名那天去掉 `-f docker-compose.ip-only.yml` 就回到最终态，
> 不会出现"改了忘改回来"——面试被问"你的应用端口为什么对公网开放"，答案是
> "过渡期用 override 文件，最终态是只监听回环 + Nginx 终止 TLS"，而不是"我不知道"。
>
> 覆盖文件用了 `ports: !override`（需要 Docker Compose v2.24.4+，`docker compose version` 确认）。
> 版本更低的话，把主文件里的 `"127.0.0.1:8080:8080"` 直接改成 `"8080:8080"` 也行，只是别忘了以后改回来。

### 0.2 代码推到远程仓库

> **本项目当前状态**：remote 已配好（`origin` = `https://github.com/<你的账号>/cleardesk.git`），
> 本地 `main` 与 `origin/main` 同步。本节保留完整流程，给"从零开始"或"换了新仓库"的场景用。

```powershell
cd D:\Program_Code\User_Center\cleardesk

# 1) 给脚本加可执行位（不然服务器上只能 bash scripts/xxx.sh，不能 ./scripts/xxx.sh）
git update-index --chmod=+x scripts/smoke-test.sh scripts/backup-db.sh

# 2) 推送前确认两件事故高发点
git check-ignore -v .env      # 期望：输出 .gitignore:<行号>:.env  → 被忽略，安全
git status --short            # 期望：看不到 .env / target / .idea / *.tar

# 3) 提交并推送
git add 具体文件名1 具体文件名2        # ⚠️ 不要用 git add -A，见下面的警告
git commit -m "..."
git push
```

**只有首次推送需要做的事**（新仓库 / 换仓库时）：

```powershell
git remote add origin https://github.com/<你的账号>/cleardesk.git
git branch -M main
git push -u origin main
```

> ### ⚠️ 三条纪律，都是踩过的坑
>
> **① 永远不要 `git add -A`。** 要指名道姓地 `git add 文件名`。
> 这个仓库可能同时存在：带真实 IP 的本地文档、384MB 的 `*.tar` 镜像包、`backups/` 里的真实数据。
> 一个 `git add -A` 就能把其中任何一样推进**公开**仓库，而且**提交进 Git 历史后即使删掉也还在**。
>
> **② 改过 GitHub 用户名后，remote 地址要更新。**
> `git remote set-url origin https://github.com/<新名>/<仓库>.git`
> 旧名的重定向**不是永久保证** —— 被抢注后旧地址会指向别人。
>
> **③ 服务器上绝对不要手工改代码/配置。** 统一走"本地改 → push → 服务器 pull"。
> 实测在服务器上手工改一次 `docker-compose.prod.yml`，衍生出四个连环问题：
> `git commit` 因缺 git 身份失败 → `git push` 被**只读 Deploy Key** 拒绝 →
> 本地改动挡住 `git pull` → `sed -i` 引入 CRLF 造成 `git status` 永远显示 `M`（但 `git diff` 是空的）。
> Deploy Key 的只读权限就是在物理上强制这条纪律 —— 服务器**没有能力**偏离流程。
>
> **私有仓库**用 Deploy Key（只读）或访问令牌，不要用账号密码。
> 完整原理与操作见 [beginner-deploy.md](beginner-deploy.md) 第 7 节。

---

## 阶段 1：服务器初始化（SSH 登录后执行）

### 1.1 安全组 / 防火墙：只放 3 个端口

> ⚠️ **先确认你买的是哪种实例**，两者的菜单完全不同：
> **轻量应用服务器** → 实例详情 → 顶部菜单 **「防火墙」**；
> **标准 ECS** → 实例详情 → 左侧菜单 **「安全组」**。
> 轻量版的「防火墙」只有「入方向」一个列表，每行可单独「禁用 / 修改 / 删除」——**"禁用"= 不放行**。

| 端口 | 用途 | 现在要不要开 |
|------|------|--------------|
| 22 | SSH | 开，**且来源限定成你自己的出口 IP 段**（如 `1.2.3.0/24`） |
| 8080 | 阶段一：无域名时直接用 IP 访问应用 | 开；**接入 Nginx 后关掉** |
| 80 / 443 | 阶段二 Nginx + 证书 | 现在不用开 |
| 3306 / 6379 | 数据库、缓存 | **绝对不开** |

> **为什么 22 写 `/24` 而不是精确 IP**：家用宽带是动态 IP，运营商重新分配时通常只变最后一段。
> 实测本项目期间，家宽出口 IP **换过整个网段**（详见 [beginner-deploy.md](beginner-deploy.md) 坑 12）。
> 查自己的出口 IP：`curl.exe -s https://ipinfo.io/ip`（**必须写 `curl.exe`**）。
>
> **保命通道**：云控制台的「**远程连接**」走厂商自己的通道，**不受防火墙影响**。
> 任何"SSH 连不上"的情况都有它兜底 —— 所以改防火墙规则是**安全的实验**。

```bash
# 确认服务器自己的 ufw 状态；如果云镜像默认开了 ufw（阿里云默认不开），才需要下面两条
sudo ufw status
sudo ufw allow 22/tcp && sudo ufw allow 8080/tcp
```

⚠️ **Docker 会绕过 ufw**：端口映射由 Docker 直接写 iptables，`ufw deny 3306` 挡不住容器发布出来的端口。
所以**云防火墙/安全组是第一道也是唯一可靠的闸门** —— 这也是本编排**故意不映射 3306/6379** 的原因。

### 1.2 装 Docker（**官方脚本在国内跑不通**）

```bash
curl -fsSL https://get.docker.com | sudo sh
# 实测报错：curl: (35) OpenSSL SSL_connect: Connection reset by peer in connection to get.docker.com:443
```

**改用阿里云的 Docker 源**（顺带先把系统更新掉）：

```bash
# ① 更新系统（新实例/重置系统后通常有 200+ 个待更新包；内核更新必须重启才生效）
apt update && apt upgrade -y && reboot
# ② 重连 SSH 后，加阿里云 Docker 源
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu jammy stable" > /etc/apt/sources.list.d/docker.list
# ③ 安装（docker-compose-plugin 是重点）
apt update && apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
docker version && docker compose version
```

预期：`Client` 和 `Server` **两段都有版本号**（实测 Docker 29.8.1 / Compose v5.5.1）。只有 Client 段说明引擎没起来。

> ❌ **不要**用系统提示的 `snap install docker`（打包版，和 `docker compose` 子命令配合常出问题）。
> ❌ **不要**用 `apt install docker.io`（Ubuntu 仓库旧版，**不带 compose 插件**，`!override` 语法会直接报错）。
> ✅ 必须装 **`docker-compose-plugin`** 这个包，它才提供 `docker compose` 子命令（≥ v2.24.4，`!override` 可用）。
>
> 因为全程用 root 操作，不需要 `usermod -aG docker`（那条是给非 root 用户免 sudo 用的）。

**配镜像加速器**（国内拉 Docker Hub 镜像必需，否则 `docker pull` 会超时或被掐断）：

```bash
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run"
  ],
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
EOF
systemctl restart docker && docker info | grep -A3 "Registry Mirrors"
```

> **`log-opts` 那三行别省**：Docker 默认把容器输出**永久写进磁盘**，一个长期运行的容器能把 40G 磁盘写满。
> 限定"每个文件 10MB、最多 3 个" = **封顶 30MB**。
>
> **顺序有讲究**：实测 `docker.1ms.run` 放第一位时拉取中途断了（`short read: expected ... bytes but got 0: unexpected EOF`），
> 把 `docker.m.daocloud.io` 放第一位后一次成功。**公共加速器不稳定是常态，配两个做冗余。**
> 失败了先**重试**（`docker pull` 有断点续传），连续失败再调换顺序。

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

### 2.2 构建镜像并起服务

**推荐流程：先在服务器上构建，再启动**（镜像从交付流程进来，服务器只负责跑）：

```bash
cd /opt/cleardesk
# ① 确认 Maven 国内镜像配置在（这是国内构建成功的关键，没有它构建会去连国外仓库）
ls -l .mvn/settings.xml

# ② 构建镜像（REGISTRY 让基础镜像也从国内拉，走 Dockerfile 里预留的 ARG 后门）
REGISTRY=docker.1ms.run/ docker compose -f docker-compose.prod.yml build app 2>&1 | tail -30

# ③ 启动
docker compose -f docker-compose.prod.yml -f docker-compose.ip-only.yml up -d
```

> **实测构建耗时**：首次（走国外仓库）**233 秒**且可能中途失败；配上 `.mvn/settings.xml` 国内镜像后 **14 秒**，而且可复现。
> 没有 `.mvn/settings.xml` 时的典型报错：
> ```
> [ERROR] Could not transfer artifact ... from/to central (https://repo.maven.apache.org/maven2)
> [ERROR] Remote host terminated the handshake
> ```
> 根因：**Docker 里的 Maven 是隔离环境，不读你本机的 `~/.m2/settings.xml`**，
> 所以"用国内镜像"这件事必须**写进项目**（`.mvn/settings.xml`），让它跟着代码走。
>
> ⚠️ **不要给 `up -d` 加 `--build`**：`app` 服务有 `build:` 段，实测 Compose **即使不加 `--build` 也可能自己触发构建**
> （日志里出现 `[+] Building ...`）。仓库里已给 `app` 加了 **`pull_policy: never`**，明确声明"只用本地已有镜像，
> 不要构建也不要拉取"。看到 `Building` 就说明这行没生效，先检查 compose 文件。

> **`--build` 的旧写法为什么被换掉**：早期版本这里是一步 `up -d --build`（顺带在服务器上构建）。
> 对 2 核 2G 机器来说，构建和运行抢内存有 OOM 风险，而且构建时长不可控、失败时和启动失败混在一起难判断。
> 现在拆成"先 build、再 up"两步，**每步都能单独验证**。

看启动日志：

```bash
docker compose -f docker-compose.prod.yml logs -f app
```

**成功的标志**（Ctrl+C 只是退出看日志，不影响容器）：

```
Started ClearDeskApplication in x.xxx seconds
```

> 首次启动 MySQL 要 30–60 秒做初始化（建库、建应用账号、执行 `sql/create_table.sql` 建表），
> 期间状态是 `Created` 而不是 `Healthy`，应用会一直等到它健康才启动 —— **这是 healthcheck 的设计，不是卡住**。

那还是拉不到基础镜像？用环境变量把 `REGISTRY` 传给构建（`REGISTRY` 不在 `.env` 里，所以用 shell 变量传）：

```bash
REGISTRY=docker.1ms.run/ docker compose -f docker-compose.prod.yml build app
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
reboot   # 再等 1 分钟重连，接口应该还能用（restart: unless-stopped 生效）
```

### 3.7 一键自检

仓库提供了 `scripts/smoke-test.sh`：容器健康、内网 DNS 解析、环境变量、建表、管理员账号、
端口暴露、接口连通性一次全查一遍，并给出下一步该做什么。

```bash
cd /opt/cleardesk
bash scripts/smoke-test.sh
```

不需要额外传参数：脚本自己按 `cleardesk-prod` 这个项目名（写死在编排文件末尾）查容器，
并按容器名而不是列位置解析状态，所以 `docker compose ps` 的列被截断也不影响它。

预期结尾：`通过 N 项，失败 0 项`。有 FAIL 就按提示回到第 2.3 节的排查顺序。

---

## 阶段 4：日常运维

把一次 `up` 用到的文件固定成别名，避免每次都要打两个 `-f`（写进 `~/.bashrc` 更省事）：

```bash
cd /opt/cleardesk
alias dc='docker compose -f docker-compose.prod.yml -f docker-compose.ip-only.yml'

dc ps                                  # 看状态
dc logs --tail=100 -f app              # 看日志

# 拉新代码并重建（三步分开，哪一步失败一目了然）
git pull
REGISTRY=docker.1ms.run/ docker compose -f docker-compose.prod.yml build app
dc up -d
```

> 接入域名后（阶段 5）记得把别名里的 `-f docker-compose.ip-only.yml` 去掉。

**回滚**：把 `git pull` 换成 `git checkout <上一个 commit>`，再照上面 build + up 走一遍。
这就是"有版本控制"的价值，面试可以主动讲 —— 比"部署失败了只能重装"强得多。

> ⚠️ **服务器上不要手工改文件**：`git checkout` 是切提交，不是改文件。
> 任何配置改动都要走"本地改 → push → 服务器 pull"，否则会遇到
> commit 缺身份、push 被只读密钥拒绝、pull 被本地改动挡住、CRLF 幻影修改这一连串问题（见 0.2 节的警告）。

**备份**（脚本已提供，含完整性校验和按天轮转）：

```bash
cd /opt/cleardesk
set -a; . ./.env; set +a                                # 让当前 shell 认识 $MYSQL_ROOT_PASSWORD（恢复演练要用）
bash scripts/backup-db.sh                               # 产出 backups/cleardesk-<时间>.sql.gz

# 验证备份真的可用（只看文件存在是不够的）
ls -lh backups/
gunzip -c backups/cleardesk-*.sql.gz | head -20         # 应看到 CREATE TABLE / INSERT INTO
```

挂定时任务（每天 3 点）：

```bash
# 先确认「cron 的环境」下也能跑（cron 的环境极简，这是最经典的坑）
cd /opt/cleardesk && env -i PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin HOME=/root bash scripts/backup-db.sh

crontab -e
# 加一行（2>&1 必须写，否则 cron 的报错会静默消失，你永远不知道备份失败了）：
0 3 * * * cd /opt/cleardesk && bash scripts/backup-db.sh >> backups/backup.log 2>&1

crontab -l      # 确认挂上了
```

> **想验证 cron 真的会跑**：临时把 `0 3 * * *` 改成 `* * * * *`（每分钟），等 2 分钟看 `backups/` 有没有新文件，
> **然后立刻改回 `0 3 * * *`** —— 忘了改回来会每分钟产生一个备份文件。
>
> ⚠️ **别忘了这条技术债**：备份和数据库**在同一台机器**上，机器/磁盘挂了就一起没。
> 下一步应推到对象存储（OSS/COS/S3）做异地。`scripts/backup-db.sh` 第 72 行自己留了这句待办。

**恢复演练**（没演练过的备份不算备份）：

```bash
gunzip -c backups/cleardesk-<时间>.sql.gz | \
  docker compose -f docker-compose.prod.yml exec -T mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk
```

> 预期无任何输出（SQL 默默执行完）。之后 `select count(*) from user;` 确认数据在。

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

> **完整待办清单见 [TODO.md](TODO.md)**（按优先级排，含每项的命令和验收标准）。
> 下面是"为什么这些是债"的说明 —— 面试被问"你的方案有什么不足"，照着答。

| 债 | 风险 | 后续动作 |
|----|------|----------|
| 只有 IP、没有 HTTPS | 流量明文；`Set-Cookie` 没有 `Secure` 标记 | 阶段 5 上 Nginx + 证书，并把 cookie 加 `secure: true` |
| 应用端口直接对公网开 8080 | 暴露面比 Nginx 方案大 | 阶段 5 收回到回环地址 |
| 没有健康检查探针 | 挂了没人知道 | 加 `spring-boot-starter-actuator`，暴露 `/actuator/health`，再让 Nginx/监控探活 |
| 手工改库提权 | 无法审计 | 后续做管理端"角色变更"接口 + 操作日志 |
| 表结构靠 init.sql 手工维护 | 改字段只能手改库，无版本记录、无法回滚 | 引入 Flyway / Liquibase |
| **备份与数据库同一台机器** | 防得住误删，**防不住机器/磁盘故障** | 推到对象存储（OSS/COS/S3）做异地 |
| 服务器仍是密码登录 root | 只有"防火墙限定来源 IP 段"一层保护 | 改用密钥登录（先验证密钥能登入，再关 `PasswordAuthentication`） |

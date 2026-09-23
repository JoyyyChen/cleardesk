# 从零部署：把 ClearDesk 从本机搬到阿里云

> **读者假设**：你从没登录过服务器，看到一个终端会紧张，遇到英文报错会想关掉窗口。
> 这份文档按"敲一条命令 → 看一眼输出 → 再敲下一条"写，每条命令都解释**它在干什么**和**你该看到什么**。
>
> **和其他三份文档的分工**：
>
> | 文档 | 读者 | 讲什么 |
> |------|------|--------|
> | **本文** | 第一次部署的人 | 一步步陪着做，命令逐条解释，卡住怎么办 |
> | [deploy-runbook.md](deploy-runbook.md) | 已经做过一次的人 | 速查执行单：命令清单 + 预期输出 + 报错对照表 |
> | [deployment.md](deployment.md) | 面试前复习 | 原理：为什么这么配 |
> | [interview-prep.md](interview-prep.md) | 面试前 | 话术和演示脚本 |
>
> 本文是最长、最啰嗦的那份，这是故意的：**啰嗦是给第一次用的**。

---

## 1. 术语表：先把黑话翻译成人话

后面每条命令都会引用这里的比喻。看一遍就行，不用背。

| 术语 | 一句话解释 | 打比方 |
|---|---|---|
| **终端 / 命令行** | 一个用打字代替点鼠标的窗口 | 和鼠标点图标干的是同一件事。`PowerShell`（本机）和 `bash`（服务器）是两种方言，**没有一条命令通用** |
| **SSH** | 从你电脑连到另一台电脑的加密通道 | 给服务器打电话。打通后你敲的每个字都在服务器上执行，**不是你本机** |
| **公网 IP / 内网 IP** | 公网 IP 是全世界能找到这台机器的门牌号；内网 IP 是机房内部的座位号 | 快递先到小区（公网），再按房间号（内网）送 |
| **防火墙 / 安全组** | 阿里云在机房门口设的保安，按"端口"决定谁能进 | **它才是真正的闸门**，服务器自己装的 ufw 管不住 Docker 开的端口 |
| **端口** | 一台机器上区分不同服务的编号 | 门牌号是 IP，"第几个门"是端口：22 是 SSH，8080 是应用，3306 是 MySQL |
| **镜像（image）** | 只读的"安装包"，装着程序 + 运行环境 | **菜谱 + 配好的食材包** |
| **容器（container）** | 镜像跑起来之后的那个活着的进程 | 按菜谱**做出来的那盘菜** |
| **Dockerfile** | 描述"怎么做镜像"的文本文件 | 菜谱本身。本项目分两页：第一页备料（编译），第二页只留成品（运行） |
| **Docker Compose** | 用一个文件描述"起哪几个容器、怎么连" | **一桌菜的总单**，还写"汤好了才上主食" |
| **卷（volume）** | 容器外面的硬盘空间，存不能丢的东西 | 盘子可以扔，**存货放冰箱** |
| **环境变量** | 通过"运行时外部设置"把配置喂给程序 | 同一份菜谱，口味（密码、地址）写在旁边小纸条上 |
| **`.env`** | 存这张小纸条的文件 | 里面是密码，**绝不进 Git**，本地和服务器各一份 |
| **Deploy Key** | 只有读权限、只对某一个仓库有效的钥匙 | 给保洁阿姨配一把"只能进这一间、只能看不能改"的钥匙 |
| **Maven / 依赖** | Java 的包管理器，去网上拉第三方库 | 装修前按清单去建材市场拉货，**国内拉国外市场很慢** |
| **profile（`prod`）** | Spring Boot 的"配置档位" | 同一台车，`dev` 是练车场，`prod` 是上路 |
| **健康检查（healthcheck）** | Compose 定期问容器"你还活着吗" | 上菜前先尝一口。MySQL 没通过就不让应用启动 |
| **curl** | 命令行版的"浏览器"，只取返回数据 | 不渲染页面，只把服务器回的话原样打出来 |

---

## 2. 整条链路的地图

```
你的 Windows 电脑                        阿里云服务器（<服务器公网IP>）
┌──────────────────────┐                ┌─────────────────────────────────┐
│ 源代码               │                │  /opt/cleardesk                 │
│ D:\...\cleardesk     │                │    ├── .env      ← 密码在这      │
│         │ git push   │                │    ├── Dockerfile                │
│         ▼            │                │    ├── .mvn/settings.xml ← 镜像源 │
│ GitHub（公开仓库）    │──git clone────▶│    └── src/ ...                  │
│ <你的账号>/cleardesk  │  （Deploy Key） │                                 │
│                      │                │  Docker 内网 cleardesk-net       │
│ 本机也用于：          │                │   ┌────────┐ ┌───────┐ ┌───────┐│
│ docker build（备用）  │                │   │  app   │ │ mysql │ │ redis ││
└──────────────────────┘                │   │ :8080  │ │ :3306 │ │ :6379 ││
                                        │   └───┬────┘ └───────┘ └───────┘│
                                        │       │ 只映射 8080 到宿主机       │
                                        │   "8080:8080"（覆盖文件里）       │
                                        └───────┬─────────────────────────┘
                                                ▼
                              http://<服务器公网IP>:8080/api/...
```

**三条必须记住的原则**：

1. **永远清楚"在谁的机器上敲"**。`PS D:\...>` 是本机，`root@iZ2ze1...:~#` 是服务器。
2. **容器里的 `localhost` 是容器自己**，不是服务器。所以应用连库要写 `mysql`（Compose 服务名）。
3. **密码不进 Git，数据库端口不对公网开**。

---

## 3. 已拍板的决定（做过一次就别中途改主意）

| # | 决定 | 选择 | 为什么 |
|---|------|------|--------|
| D1 | 讲解粒度 | 每条命令都解释，术语先打比方 | 第一次做，卡住时要知道为什么 |
| D2 | 验收方式 | 每步都贴输出，通过才往下走 | 这条链路的坑大多表现为"看着启动了但外面连不上" |
| D3 | 谁敲命令 | 自己敲 | 面试要讲这条链路，敲过和没敲过是两种状态 |
| D4 | 对外访问 | 先用 `IP:8080`，不做域名/HTTPS | 先拿到能点开的地址；大陆 IP 挂域名要备案 |
| D5 | 代码传递 | 私有/公开仓库 + Deploy Key（只读） | 不泄露代码，`git pull` 长期可用 |
| D6 | 登录服务器 | 先用密码，跑通后换密钥 | 第一天排除"连不上"变量 |
| D7 | 22 端口来源 | 限定成自家宽带 IP 段 | 公网 root 密码登录会被全天候爆破 |
| D8 | 镜像构建 | **先本机构建+传输，后改为服务器构建** | 见坑 7、坑 8，条件变了结论就变 |
| D9 | 内存 | 加 2G swap + 给 JVM 设堆上限 | 防 OOM；也是面试题 |

---

## 4. 阶段 R0：代码已经在 GitHub 上

这一步在本项目里**已经完成**，记录一下结论：

- 远程仓库：`https://github.com/<你的账号>/cleardesk.git`（**改过用户名**，GitHub 会把旧地址自动重定向到新地址）
- 本地分支 `main` 与 `origin/main` 同步
- `.gitignore` 保证了 `.env`、`target/`、`*.tar` 不会被提交

**要记住的两个操作纪律**：

1. **改过 GitHub 用户名后，本地 remote 要更新成新地址**：`git remote set-url origin https://github.com/<新名>/<仓库>.git`。
   旧名的重定向**不是永久保证** —— 被抢注后旧地址会指向别人。
2. **永远不要 `git add -A`**，至少在这个仓库里。要指名道姓地 `git add 文件名`，否则很容易把带真实 IP、带密码的本地文件推进公开仓库。

---

## 5. 阶段 R1：服务器之前 —— 本机环境（可跳过，但备用方案需要）

本机实际状态：JDK 21（Microsoft OpenJDK 21.0.12）、Git 2.55、Docker Desktop 29.7.2 / Compose v5.5.1、OpenSSH 9.5p1。

**这条路线现在是"备用方案"**（主路线改成服务器构建，见 R5），但两个场景仍然会用到：

- 服务器构建失败时的退路
- 想做"构建与运行分离"的正式交付流程时

备用方案的三条命令：

```powershell
cd D:\Program_Code\User_Center\cleardesk
docker build --platform linux/amd64 --progress=plain -t cleardesk:prod .
docker save -o cleardesk-images.tar cleardesk:prod mysql:8.0
scp cleardesk-images.tar root@<服务器公网IP>:/opt/cleardesk/
```

服务器端配套：

```bash
docker load -i cleardesk-images.tar
docker tag cleardesk:prod cleardesk-prod_app     # 名字要和 compose 期望的一致
```

> **为什么必须打这个 tag**：`docker save` 存的是 `cleardesk:prod`，而 compose 默认找 `cleardesk-prod_app`（项目名 `cleardesk-prod` + 服务名 `app`）。名字对不上，compose 就会自己重新构建。
>
> **实测数据**：镜像包 384.5MB，家用宽带上行传输耗时 **6 分钟到 1 小时不等**（同一文件三次尝试分别是 94.7KB/s、失败、1.1MB/s）。这个不稳定性就是后来改走服务器构建的直接原因。

---

## 6. 阶段 R2：服务器初始化

### 6.1 防火墙：放行 22 和 8080，别的一律不放

⚠️ **本机是「轻量应用服务器」，不是标准 ECS**，菜单完全不同：

| 服务器类型 | 在哪儿设端口规则 |
|---|---|
| 轻量应用服务器 | 实例详情 → 顶部菜单 **「防火墙」** |
| 标准 ECS | 实例详情 → 左侧菜单 **「安全组」** |

实测的初始规则（阿里云默认给的）：

| 协议 | 端口 | 来源 | 状态 |
|------|------|------|------|
| TCP | 80 / 443 | 0.0.0.0/0 | 已启用 |
| TCP | **22** | 0.0.0.0/0 | 已启用（**要收紧**） |
| ICMP | 全部 | 0.0.0.0/0 | 已启用 |
| TCP | **8080** | 0.0.0.0/0 | 已禁用（**要启用**） |

**没有 3306 / 6379** —— 这一点很关键。

改动：22 的来源改成 `<自家公网IP>/24`；8080 点「应用」启用。

> **为什么 22 写 `/24` 而不是精确 IP**：家用宽带是动态 IP，运营商重新分配通常只变最后一段。写 `/24` 能避免"某天早上突然连不上"。
> **保命通道**：右上角「**远程连接**」走阿里云自己的通道，**不受防火墙影响**，任何"SSH 连不上"都有它兜底。
> 查自家 IP：`curl.exe -s https://ipinfo.io/ip`（**必须写 `curl.exe`**，PowerShell 里 `curl` 是别名）。

### 6.2 登录服务器

控制台「重置密码」设密码（8-30 位，含大小写/数字/符号中至少三类），然后：

```powershell
ssh root@<服务器公网IP>
```

首次连接会出现主机指纹确认，打 `yes`。成功后提示符变成 `root@<主机名>:~#`。

**这个提示符就是你"在哪台机器上"的路标**，后面每次敲命令前都要看它。

### 6.3 装 Docker：官方脚本在国内跑不通

```bash
curl -fsSL https://get.docker.com | sh
# curl: (35) OpenSSL SSL_connect: Connection reset by peer in connection to get.docker.com:443
```

**改用阿里云的 Docker 源**：

```bash
apt update && apt upgrade -y     # 重置系统后通常有 200+ 个待更新包
reboot                            # 内核更新要重启才生效
# 重连 SSH 后继续
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu jammy stable" > /etc/apt/sources.list.d/docker.list
apt update && apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
docker version && docker compose version
```

预期：`Client` 和 `Server` **两段都有版本号**（实测 29.8.1），`Docker Compose version v5.5.1`。

> **不要**用 `snap install docker`（打包版，和 `docker compose` 子命令配合常出问题），
> **也不要**用 `apt install docker.io`（Ubuntu 仓库旧版，**不带 compose 插件**，`!override` 语法会直接报错）。
> 必须装 `docker-compose-plugin` 这个包。

### 6.4 加 2G swap

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab     # ← 少了这行，重启后 swap 就没了
free -h && swapon --show
```

实测：加之前 `Mem total 1.6Gi / available 1.2Gi`（`total` 小于 2Gi 是内核自己占了一部分，正常），加之后 `Swap: 2.0Gi`。

> ⚠️ **`>>` 和 `>` 的区别**：`>>` 追加，`>` **清空后写入**。对 `/etc/fstab` 用错 `>`，下次开机直接起不来。

### 6.5 配镜像加速器（拉容器镜像用）

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

> `log-opts` 那三行容易被忽略但很重要：Docker 默认把容器输出永久写进磁盘，**一个长期运行的容器能把磁盘写满**。限定"每个文件 10MB、最多 3 个"= 封顶 30MB。
>
> **顺序有讲究**：实测 `docker.1ms.run` 放第一位时拉取中途断了（`short read: expected 4625979 bytes but got 0: unexpected EOF`），把 `docker.m.daocloud.io` 放第一位后一次成功。**公共加速器不稳定是常态，配两个做冗余。**

---

## 7. 阶段 R3：把代码弄到服务器（Deploy Key）

### 7.1 服务器上生成密钥

```bash
ssh-keygen -t ed25519 -C "cleardesk-server" -f ~/.ssh/id_ed25519_deploy
ls -l ~/.ssh/
cat ~/.ssh/id_ed25519_deploy.pub
```

两次问 passphrase 都**直接回车**（留空）。预期两个文件：

```
-rw-------  id_ed25519_deploy       ← 私钥，600 = 只有 root 能读写
-rw-r--r--  id_ed25519_deploy.pub   ← 公钥，可以给人看
```

> ⚠️ **私钥权限必须是 600**。太开放（比如 644）时 SSH 会直接拒绝使用，报 `Permissions 0644 are too open`。这是保护机制。
>
> **为什么文件名不叫默认的 `id_ed25519`**：默认名是"登录服务器用的"。将来给 SSH 登录也配密钥时，两个用途混在一个文件里会很乱。**分开命名，一眼知道哪把钥匙开哪扇门。**

**公钥 / 私钥怎么理解**：想象一把**只能锁不能开的锁**（公钥）和**唯一能开它的钥匙**（私钥）。把锁发给 GitHub，钥匙留在服务器。以后服务器说"我是持有者"，GitHub 出一道只有那把钥匙能解的题。**全程没有密码在网络上传输。**

### 7.2 贴到 GitHub 的 Deploy keys

仓库 → **Settings → Deploy keys → Add deploy key**：

| 字段 | 填什么 |
|---|---|
| Title | `cleardesk-server` |
| Key | `cat` 出来的那一整行 |
| Allow write access | **不勾**（保持只读） |

> ⚠️ **必须是「Deploy keys」，不是「SSH and GPG keys」**：后者是**账号级**的（在个人 Settings 里），贴上能访问你所有仓库。
> 不勾 write access 的理由是**最小权限原则**：服务器只需要读代码。万一被入侵，攻击者最多读到这一个仓库，**无法往你仓库植入后门**。

### 7.3 告诉 SSH "连 GitHub 用哪把钥匙"

```bash
cat >> ~/.ssh/config <<'EOF'
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/id_ed25519_deploy
  IdentitiesOnly yes
EOF
chmod 600 ~/.ssh/config
ssh -T git@github.com
```

预期输出：

```
Hi <你的账号>/cleardesk! You've successfully authenticated, but GitHub does not provide shell access.
```

> **这一行有两个信息**：
> 1. 显示的是 **`<你的账号>/cleardesk`（带仓库名）** 而不是 `<你的账号>` —— **这正是 Deploy Key 的特征**。账号级 key 只会显示 `Hi <你的账号>!`。所以这一行同时验证了"钥匙对"和"贴对了地方"。
> 2. `does not provide shell access` **不是错误** —— GitHub 只让你传代码，不给你命令行。**这是成功的标志**。
>
> `IdentitiesOnly yes` 很重要：SSH 默认会把 `~/.ssh` 下所有钥匙都试一遍，**试太多次会被 GitHub 判定异常并拒绝**（`Too many authentication failures`）。

### 7.4 clone

```bash
mkdir -p /opt/cleardesk
git clone git@github.com:<你的账号>/cleardesk.git /opt/cleardesk
cd /opt/cleardesk && ls -la
```

> **为什么放 `/opt`**：Linux 约定里 `/opt` 是"自己装的软件"该待的地方，行业惯例。
> **为什么用 `git@github.com:...` 而不是 `https://`**：前者走 SSH（用刚配的钥匙），后者走网页协议（要输账号密码/token）。

---

## 8. 阶段 R4：生成 `.env`（密码只存在服务器上）

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
echo "MySQL root 密码：$ROOT_PW"
echo "应用账号密码　：$APP_PW"
```

| 片段 | 作用 |
|---|---|
| `openssl rand -base64 24` | 生成密码学随机数（不是"想一个复杂密码"，是机器随机） |
| `tr -d '/+='` | 删掉 `/ + =`，避免写进 `.env` 和 JDBC 连接串时被解析截断 |
| `cut -c1-24` | 长度可控 |
| `umask 077` | 之后创建的文件默认只有自己能读写 |
| `cat > .env <<EOF ... EOF` | `$ROOT_PW` 会被替换成真实值（因为 `EOF` 没加引号） |

**验证（关键一步）**：

```bash
ls -l .env && cat -A .env | sed 's/=.*/=<已隐藏>/'
```

预期：

```
-rw------- 1 root root 131 ... .env      ← 权限 600 ✅
MYSQL_ROOT_PASSWORD=<已隐藏>$            ← 行尾是 $，不是 ^M$ ✅
MYSQL_DATABASE=cleardesk$
MYSQL_USER=cleardesk$
MYSQL_PASSWORD=<已隐藏>$
```

> ⚠️ **屏幕上的两个密码要立刻抄进密码管理器**。MySQL 的密码**只在数据卷第一次初始化时生效**，以后改 `.env` 里的密码，**数据库里的密码不会跟着变** → 应用报 `Access denied`，而你会以为是配置写错了。
> ⚠️ **`cat -A` 那步不能省**：行尾是 `^M$` 就说明有 Windows 换行污染，`^M` 会被当成密码的一部分 —— "密码明明对却登录失败"，极难排查。这也是**在服务器上生成而不是从 Windows 传**的原因。
> `sed 's/=.*/=<已隐藏>/'` 让输出可以安全地贴给别人看。

---

## 9. 阶段 R5：构建 + 启动

### 9.1 主路线：在服务器上构建（14 秒）

**前置条件：`.mvn/settings.xml` 必须在服务器上**（见坑 8，这是国内构建成功的关键）。

```bash
cd /opt/cleardesk
git pull && ls -l .mvn/            # 确认 settings.xml 在
REGISTRY=docker.1ms.run/ docker compose -f docker-compose.prod.yml build app 2>&1 | tail -30
```

**实测结果**：

```
[INFO] BUILD SUCCESS
[INFO] Total time:  14.079 s
#18 naming to docker.io/library/cleardesk-prod-app:latest
✔ Image cleardesk-prod_app Built
```

> **`REGISTRY=docker.1ms.run/` 是给 Dockerfile 里 `ARG REGISTRY` 传值**，让基础镜像从国内拉。这是 Dockerfile 里早就留好的后门。
>
> **那个 14 秒值得解释**：第一次构建（用旧 Dockerfile、走国外仓库）要 **233 秒**，配了国内镜像后是 **14 秒**。差距不在"下载"，而在 Maven 每次构建都要去仓库**检查元数据更新** —— 那一步受国外网络影响最大。
>
> **更重要的是"可复现"**：233 秒那次是"运气好没被掐断"，**靠运气成功的东西下次改代码时可能就失败，而那时你会以为是新代码有问题**。把不确定性消掉，比省几分钟重要。

### 9.2 禁止 Compose 自作主张重新构建

```bash
cd /opt/cleardesk
sed -i 's|^  app:$|  app:\n    # 镜像已构建好，禁止 Compose 重新构建\n    pull_policy: never|' docker-compose.prod.yml
grep -A4 "^  app:" docker-compose.prod.yml
```

> **为什么需要**：`docker-compose.prod.yml` 的 `app` 服务有 `build:` 段。实测 `up -d` **即使不加 `--build` 也会自己重新构建**（日志里出现 `[+] Building ...`）。
> **`pull_policy: never`** 明确告诉 Compose："**只用本地已有镜像，不要构建也不要拉取**"。这在 2 核 2G 机器上是几分钟的差别，而且降低失败率。

### 9.3 启动

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.ip-only.yml up -d
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs app | tail -40
```

预期：`mysql` / `redis` 是 `healthy`，`app` 是 `Up`，日志里有 `Started ClearDeskApplication in x.xxx seconds`。

> **MySQL 第一次启动要 30–60 秒**（建库、执行 `sql/create_table.sql` 建表、创建 `cleardesk` 应用账号），期间状态是 `Created` 直到健康检查通过 —— **这是设计好的等待，不是卡住**。
> **两个 `-f` 的作用**：主文件描述"最终态"（应用只监听回环，留给 Nginx），覆盖文件把端口放开成 `0.0.0.0:8080`。接入域名后去掉第二个 `-f` 就回到最终态。
> **不要加 `--build`**：镜像已经构建好了。

### 9.4 JVM 内存上限（D9）

在 compose 的 `app` 服务环境变量里加：

```yaml
      JAVA_TOOL_OPTIONS: "-Xms128m -Xmx512m"
```

> **为什么用 `JAVA_TOOL_OPTIONS` 而不改 Dockerfile**：这是 **JVM 自动读取的环境变量**，任何 Java 进程启动时都会应用。好处是**配置留在"部署编排"里**（环境相关的归 compose），镜像保持通用。
> 面试问"怎么限制容器内 JVM 内存"，答"从编排层注入 `JAVA_TOOL_OPTIONS`，镜像不含环境相关配置"是个好答案。

---

## 10. 阶段 R6：验证（做完这步才算部署成功）

```bash
# 1) 容器状态
docker compose -f docker-compose.prod.yml ps

# 2) 服务器本机打接口
curl -s -X POST http://127.0.0.1:8080/api/user/register \
  -H 'Content-Type: application/json' \
  -d '{"userAccount":"probe001","userPassword":"12345678","checkPassword":"12345678"}'
```

**然后在你自己的电脑上验证公网链路**：

```
浏览器打开：http://<服务器公网IP>:8080/api/user/current
```

预期返回"未登录"的 JSON。**看到 JSON 就是全线贯通**：

```
你家宽带 → 阿里云防火墙(8080) → 服务器 → Docker 端口映射 → 容器 → Spring Boot
```

**验证数据库没裸奔**（PowerShell，两个都应该 False）：

```powershell
Test-NetConnection <服务器公网IP> -Port 3306 -WarningAction SilentlyContinue | Select-Object TcpTestSucceeded
Test-NetConnection <服务器公网IP> -Port 6379 -WarningAction SilentlyContinue | Select-Object TcpTestSucceeded
```

**造管理员账号**（不造的话 `search` / `delete` 接口全是 403）：

```bash
cd /opt/cleardesk && set -a && . ./.env && set +a
docker compose -f docker-compose.prod.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk \
  -e "update user set userRole=1 where userAccount='probe001'; select id, userAccount, userRole from user;"
```

**一键自检**：仓库提供了 `scripts/smoke-test.sh`，容器健康、内网 DNS、环境变量、建表、管理员账号、端口暴露、接口连通性一次查完。

```bash
cd /opt/cleardesk && bash scripts/smoke-test.sh
```

---

## 11. 部署成功之后

主链路在本文写作时**已全部完成并验证**。后续待办见 **[TODO.md](TODO.md)**（含每项的具体命令和验收标准）。

已经完成的（留档，别重复做）：

| 事项 | 结果 |
|---|---|
| 配置提交进仓库（`pull_policy` + `JAVA_TOOL_OPTIONS`） | 提交 `6066f953` |
| 造管理员账号 + 跑 `smoke-test.sh` | 19 项全通过，`probe001` 已是管理员 |
| 修 Git 提交身份 | 改为真实账号 + GitHub noreply 邮箱 |
| 本文档脱敏后提交 | 公网 IP / 实例 ID / 内网 IP / 家宽 IP 段全部改为占位符 |

还没做的，按优先级：

| 优先级 | 事项 | 为什么 | 详见 |
|---|---|---|---|
| 高 | **配每日数据库备份（cron）** | 现在一个备份都没有 | TODO 第 1 项 |
| 高 | **做一次恢复演练** | 没演练过的备份不算备份 | TODO 第 2 项 |
| 高 | 把备份推到对象存储 | 备份和数据库同机器，机器挂了就一起没 | TODO 第 3 项 |
| 中 | SSH 登录改密钥（D6 第二阶段） | 现在还是密码登录 root | TODO 第 4 项 |
| 中 | `.env` 备份到密码管理器 | 文件丢了密码就找不回来 | TODO 第 5 项 |
| 中 | 加 Actuator 健康检查端点 | 应用挂了只能靠人发现 | TODO 第 6 项 |
| 低 | 域名 + Nginx + HTTPS | 需要备案 | TODO 第 9 项 |
| 低 | Flyway 管表结构变更 | 现在改字段只能手改库 | TODO 第 10 项 |
| 低 | GitHub Actions CI/CD | 把手动部署升级成自动 | TODO 第 11 项 |

> ⚠️ **一个必须记住的技术债**：备份脚本自己都写了 —— **"备份还在同一台机器上，机器挂了就一起没了"**。
> 它能防"误删数据"，防不了"磁盘/机器故障"。面试被问"你的备份在哪"，答"同一台机器"是减分项。

---

## 12. 踩坑记录（全部是本次实操真实遇到的）

### 坑 1：重置系统后 SSH 报 "SOMEONE IS DOING SOMETHING NASTY!"

```
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
@    WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!     @
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
IT IS POSSIBLE THAT SOMEONE IS DOING SOMETHING NASTY!
Host key verification failed.
```

**原因**：SSH 第一次连一台机器时会把指纹记进本机 `known_hosts`，以后比对防冒充。**重置系统盘后服务器重新生成指纹**，旧记录对不上就报警。是误报，但你要知道这条警告的设计意图 —— **如果哪天你没重置系统却看到它，那才要警惕**。

**修法**（在 **PowerShell** 里）：

```powershell
ssh-keygen -R <服务器公网IP>
```

### 坑 2：`get.docker.com` 连不上（国内网络）

```
curl: (35) OpenSSL SSL_connect: Connection reset by peer in connection to get.docker.com:443
```

**修法**：改用阿里云 Docker 源，见 6.3。`doc/deployment.md` 里原来那条官方脚本在国内服务器上不可用。

### 坑 3：升级系统时某条命令突然 `not found`

```
root@...:~# reboot
Command 'reboot' not found, did you mean:
  command 'reboot' from deb systemd-sysv (249.11-0ubuntu3.21)
```

**原因**：`apt upgrade` 正在升级的包里包含 `systemd-sysv`，而**这个包正是提供 `/sbin/reboot` 的包**。在"旧版已卸、新版未配置好"那几秒里，命令真的不存在。**隔几秒重试**即可，也可用 `systemctl reboot`。

### 坑 4：`curl` 和 `curl.exe` 不是一回事

PowerShell 里 `curl` 是 `Invoke-WebRequest` 的别名，参数不通用。**查公网 IP 一类的事一律写 `curl.exe`**。

### 坑 5：在错误的地方敲命令

```
root@iZ2ze1...:/opt/cleardesk# Get-Item cleardesk-images.tar | Select-Object Name, @{N='大小MB';E={...}}
-bash: syntax error near unexpected token `('
```

**原因**：`Get-Item`、`Select-Object`、`@{...}` 全是 **PowerShell 语法**，而当时人在**服务器的 bash** 里。bash 里 `{` 表示代码块，`(` 出现在它后面就是语法错误。

**这个报错的意思不是"你命令写错了"，而是"你走错房间了"。** 看提示符。

### 坑 6：`git pull` / `git clone` 报 `not a git repository`

```
fatal: not a git repository (or any of the parent directories): .git
```

**原因**：git 认"当前所在目录"。仓库在 `D:\Program_Code\User_Center\cleardesk`，但人在 `C:\Users\28167`，找不到 `.git` 文件夹。

**修法**：`cd D:\Program_Code\User_Center\cleardesk`，看提示符变成 `PS D:\Program_Code\User_Center\cleardesk>` 再敲 git 命令。

### 坑 7：`docker pull` 中途断掉

```
short read: expected 4625979 bytes but got 0: unexpected EOF
```

**原因**：公共加速器带宽有限、会被限速掐断。**不是配置错**。

**修法**：① 直接重试（`docker pull` 有断点续传，已下好的层会缓存）；② 连续失败就把 `daemon.json` 里两个加速器**调换顺序**。实测调换后一次成功。

### 坑 8：Docker 构建拉 Maven 依赖失败（最重要的一条）

```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-dependency-plugin:3.10.0:go-offline
[ERROR] Could not transfer artifact ... from/to central (https://repo.maven.apache.org/maven2)
[ERROR] -> [Help 1]
[ERROR] Remote host terminated the handshake
```

**根因**：Docker 里的 Maven 是**隔离环境，不读你本机的 `~/.m2/settings.xml`**。不额外配置的话，容器里的 Maven 会去**国外官方仓库**拉依赖。

**修法**：把"用国内镜像"这件事**写进项目**（`.mvn/settings.xml`），让它跟着代码走：

```xml
<mirror>
  <id>central</id>
  <url>https://maven.aliyun.com/repository/public</url>
  <mirrorOf>central</mirrorOf>
</mirror>
```

配套在 Dockerfile 里加一行（必须在 `mvn` 命令之前）：

```dockerfile
COPY .mvn .mvn
```

**两个细节**：
- **位置选 `.mvn/settings.xml`** 是因为 Maven 3.9+ 会自动读取项目级配置，不用传 `-s`、不用改 `ENTRYPOINT`、不用动 `pom.xml`。
- **`<id>` 必须写 `central`** —— 要和被覆盖的仓库 id 一致才能生效。

**效果实测**：233 秒 → 14 秒，且从"靠运气"变成"可复现"。

**这个坑的思维方式比命令值钱**：**配置要跟着代码走，不要跟着机器走。**

### 坑 9：`docker compose up -d` 自己触发了构建

```
[+] Building 176.6s (9/16)
 => [internal] load build definition from Dockerfile
 => [builder 1/6] FROM docker.io/library/maven:3.9-eclipse-temurin-21
```

**原因**：`docker-compose.prod.yml` 的 `app` 服务有 `build:` 段。**即使不加 `--build`，Compose 在认为"本地镜像不是从当前配置构建的"时也会自己构建。**

**修法**：给 `app` 服务加 `pull_policy: never`，明确声明"只用本地已有镜像"。

### 坑 10：`git push` 报 proxy 连接失败

```
fatal: unable to access 'https://github.com/<你的账号>/cleardesk.git/':
       Failed to connect to github.com:443 over proxy 127.0.0.1:7081
```

**根因**：git 配了本地代理（`http.proxy`），但代理软件没运行或端口变了。

**排查顺序**：

```powershell
git config --global --get-regexp "http\.|https\.|url\."        # 代理配在哪
Test-NetConnection 127.0.0.1 -Port 7892                        # 代理在不在听
Test-NetConnection github.com -Port 443                        # 直连通不通
git -c http.proxy= -c https.proxy= push                        # 临时绕过代理
```

**关键点**：`git config` 的代理和系统代理是**两套东西**，git 只认自己配置文件里的（以及 `HTTP_PROXY` 环境变量）。实测直连 GitHub 是通的，所以临时绕过代理即可推送。

### 坑 11：`Add-Content` 把 `.gitignore` 的编码搞成混合的

```powershell
Add-Content .gitignore "`n# 本机构建后导出的镜像包`n*.tar"
```

结果：文件变成**前半段 UTF-8 + 后半段 GBK**，任何单一编码读出来都有一半是乱码。

**根因**：**Windows PowerShell 5.1 的 `Add-Content` 默认用系统 ANSI 编码（中文系统是 GBK）写文件**，而目标文件原本是 UTF-8。

**修法**：用编辑器改文本文件；或在代码里显式指定：

```powershell
[System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
```

> **为什么不用 `-Encoding utf8`**：PowerShell 5.1 的 `utf8` 会**写入 BOM**（开头多三个隐藏字节 `EF BB BF`），而 BOM 在 Linux 上常被当成内容的一部分 —— 又是另一个坑。

### 坑 12：SSH 突然连不上（实测发生过）

**第一个怀疑对象：自家宽带的公网 IP 变了。** 修法：阿里云控制台 → 防火墙 → 22 那条 → 来源改成新的 `/24`。兜底：右上角「远程连接」。

**实测**：本项目实操期间，家宽出口 IP 从 `<旧网段>.x` 变成了 `<新网段>.x` —— **换了整个网段**。当时 SSH 会话还活着（长连接不会因 IP 变化而断），但只要重连就会失败。

> 这条从"理论风险"变成了"实测事实"。也正是为什么防火墙来源要写 `/24` 而不是精确 IP —— 至少留一个网段的缓冲。

### 坑 13：`git status` 一直显示 `M`，但 `git diff` 是空的（"幻影修改"）

**现象**：在服务器上用 `sed -i` 改过 `docker-compose.prod.yml` 之后，`M docker-compose.prod.yml` 一直消不掉，
`git checkout -- 文件`、`git update-index --refresh` 都无效，**但 `git diff` 输出完全为空**。

**诊断**（三条命令看穿）：

```bash
file docker-compose.prod.yml                        # 看有没有 "with CRLF line terminators"
head -3 docker-compose.prod.yml | cat -A            # 工作区行尾：$ 还是 ^M$
git show HEAD:docker-compose.prod.yml | head -3 | cat -A   # 版本库里行尾：作对比
```

本例输出确认了：**工作区是 CRLF（`^M$`），版本库里是 LF（`$`）**。

**根因**：`sed -i` 重写文件时引入了 `\r`。
`git status` 走"元数据快速判断"（时间戳/inode 变了就报 `M`），而 `git diff` 会按 `.gitattributes` 里的 `eol=lf` **规范化后再比对**（所以内容一致、diff 为空）。
**两个命令都对，只是回答的问题不同：`git diff` 是内容真相，`git status` 是元数据警报。**

**修法**：

```bash
git fetch origin && git reset --hard origin/main
```

`checkout -- 文件` 只从**索引**恢复到工作区；`reset --hard origin/main` 是**索引和工作区一起**用远程内容重建，所以元数据和内容同时被纠正。
⚠️ `--hard` 会永久丢弃未提交改动 —— 这次改动已在远程，所以是安全的；别在有未提交工作时随手用。

**根本预防**：还是那句 —— **不要在服务器上手工改文件**。这一个 5 秒的改动，衍生出 4 个连环问题：
`git commit` 因缺身份失败 → `git push` 被只读密钥拒绝 → 本地改动挡住 `git pull` → `sed -i` 带来幻影修改。

---

## 13. 环境档案（模板：换成你自己的值）

> 这份档案是排障时最省时间的东西 —— 出问题时先对照它，能立刻排除掉"版本不对""内存不够"这类猜测。
> 下面是**模板**，把你自己的实际值填进去（这些值是**环境相关的，不该提交进公开仓库**）。

```
服务器       阿里云轻量应用服务器，华北2（北京）
实例 ID      <你的实例ID>
公网 IP      <服务器公网IP>      内网 IP  <服务器内网IP>
系统         Ubuntu 22.04.5 LTS (GNU/Linux 5.15.0-142-generic x86_64)
规格         通用型 2 vCPU / 2 GiB / ESSD 云盘 40 GiB
家宽出口 IP   <你家宽带IP段>（动态，会变）
主机名       <主机名>
代码目录     /opt/cleardesk

本机         Windows + PowerShell
JDK          21.x（Microsoft OpenJDK）
Git          2.55.0.windows.5
Docker       29.7.2 / Compose v5.5.1（Docker Desktop，备用构建路径）
SSH          OpenSSH_for_Windows_9.5p1

服务器上     Docker Engine 29.8.1 / Compose v5.5.1 / containerd 2.3.5 / runc 1.5.1
内存         total 1.6Gi / available 1.2Gi（内核占掉一部分，正常）
Swap         2.0Gi（/swapfile，已写进 /etc/fstab）
构建耗时     首次 233 秒（走国外仓库）→ 配国内镜像后 14 秒
```

**查这些值的命令**（都在服务器上跑）：

```bash
# 公网 IP（从服务器看自己的出口 IP，和 ping 服务器得到的公网 IP 通常一致）
curl -s ifconfig.me; echo
# 内网 IP
hostname -I
# 主机名
hostname
# 系统版本 + 内核
lsb_release -d && uname -r
# 内存和 swap
free -h
# Docker 版本
docker version --format '{{.Server.Version}}' && docker compose version
```

> ⚠️ **提交本文档前先确认脱敏**：公网 IP、实例 ID、内网 IP、家宽 IP 段、主机名都不该进公开仓库。
> 一个快速的检查命令（在项目根目录跑）：
>
> ```bash
> grep -nE '([0-9]{1,3}\.){3}[0-9]{1,3}|iZ[a-zA-Z0-9]{10,}' doc/*.md
> ```
>
> 如果输出里出现你的真实 IP，说明还有漏网的。

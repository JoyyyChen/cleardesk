# 待办清单（部署已完成，剩下的是运维与加固）

> 部署本身**已经完成并验证**：公网可访问、数据库未暴露、配置可复现。
> 这份清单是"接下来该做什么"，按优先级排。做完一项就把 `[ ]` 改成 `[x]`。
>
> 进度详见 [beginner-deploy.md](beginner-deploy.md)（完整实操记录）与 [deploy-runbook.md](deploy-runbook.md)（速查执行单）。

---

## 高优先级

### [ ] 1. 配每日数据库备份（cron）

**为什么是最高优先级**：现在数据库**一个备份都没有**。脚本已经写好（`scripts/backup-db.sh`，含完整性校验和按天轮转），只差挂上定时任务。一次误操作、一次机器故障，数据就没了。

**步骤**（在服务器 `/opt/cleardesk` 下执行）：

```bash
# ① 先手工跑一次，确认脚本本身能work
cd /opt/cleardesk && bash scripts/backup-db.sh
ls -lh backups/ && gunzip -c backups/cleardesk-*.sql.gz | head -20   # 要能看到 CREATE TABLE / INSERT INTO

# ② 用「cron 的环境」测一次，提前发现 PATH 之类的坑
cd /opt/cleardesk && env -i PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin HOME=/root bash scripts/backup-db.sh

# ③ 挂定时任务
crontab -e     # 第一次会让你选编辑器，选 1 (nano)：打字 → Ctrl+O 回车存盘 → Ctrl+X 退出
# 加入这一行（每天 3:00）：
0 3 * * * cd /opt/cleardesk && bash scripts/backup-db.sh >> backups/backup.log 2>&1

# ④ 确认挂上了
crontab -l
```

**注意两个坑**：

1. **`>> backups/backup.log 2>&1` 不能省** —— 没有它，cron 跑了你也看不到结果，报错会静默消失。**"配了 cron 但从来没看过日志"等于没配。**
2. **想验证 cron 真的会跑**：临时把 `0 3 * * *` 改成 `* * * * *`（每分钟），等 2 分钟看 `backups/` 有没有新文件，**然后立刻改回 `0 3 * * *`** —— 忘了改回来会每分钟产生一个备份文件。

**做完的验收标准**：`crontab -l` 有那一行，且 `backups/backup.log` 里有"备份完成"的记录。

### [ ] 2. 做一次恢复演练

**"备份脚本每天在跑"和"我能从备份恢复数据"是两件不同的事。** 没演练过的备份不算备份。

```bash
cd /opt/cleardesk && set -a && . ./.env && set +a
gunzip -c backups/cleardesk-<时间戳>.sql.gz | \
  docker compose -f docker-compose.prod.yml exec -T mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk
```

预期无任何输出（SQL 默默执行完）。之后 `select count(*) from user;` 确认数据在。

### [ ] 3. 把备份推到对象存储（异地）

`scripts/backup-db.sh` 第 72 行自己留了这句待办：

> `# 待补项：备份还在同一台机器上，机器挂了就一起没了。`

**现在备份和数据库在同一块盘上** —— 这能防"误删数据"，**防不了"机器/磁盘挂了"**。

下一步：开一个阿里云 OSS bucket，在脚本末尾加一行上传（`ossutil cp` 或 `rclone copy`）。
**这一条面试会问**："你的备份在哪？" 答"同机器"是减分项，答"本地 + OSS 异地"才是完整答案。

---

## 中优先级

### [ ] 4. SSH 登录改用密钥（D6 的第二阶段）

现在还是**密码登录 root**，只有"防火墙限定来源 IP 段"这一层保护，长期有被爆破的风险。

思路：在你 Windows 上生成一对密钥 → 公钥贴到服务器的 `~/.ssh/authorized_keys` → 验证能用密钥登录 → **确认无误后**再关掉 `PasswordAuthentication`。
⚠️ **顺序很重要**：必须先验证密钥能登进去，再关密码登录，否则会把自己锁在门外（救急用阿里云控制台的「远程连接」）。

### [ ] 5. 备份 `.env` 到密码管理器

`.env` 里的 MySQL 密码**只在数据卷首次初始化时生效**，文件丢了就找不回来（只能删卷重建 = 丢数据）。
现在它只存在于服务器那一个文件里。**把两个密码抄进密码管理器**，或把 `.env` 加密后存一份到别处。

### [ ] 6. 加健康检查端点（Actuator）

现在没有探活接口，"应用挂了"只能靠人发现。

```
加 spring-boot-starter-actuator → 暴露 /actuator/health
→ Nginx 或云监控定时探测
```

### [ ] 7. 把 session cookie 加上 `secure: true`

**前提：得有 HTTPS**（第 9 项）。现在 `Set-Cookie` 没有 `Secure` 标记，明文 HTTP 下 cookie 会裸奔。

---

## 低优先级 / 功能完善

### [ ] 8. 管理端"角色变更"接口 + 操作日志

现在**提权是手工改数据库**：

```bash
docker compose -f docker-compose.prod.yml exec mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk \
  -e "update user set userRole=1 where userAccount='xxx';"
```

不可审计、容易出错。正式做法是做接口 + 记录"谁在什么时候把谁提权了"。

### [ ] 9. 域名 + Nginx + HTTPS

- 买域名、解析 A 记录到服务器 IP
- **大陆 IP 做 80/443 访问需要备案**，备案没下来时继续用 `IP:8080` 演示
- 装 Nginx + `certbot --nginx -d 你的域名` 自动签证书
- 启动命令**去掉** `-f docker-compose.ip-only.yml`（回到"应用只监听回环"的最终态）
- 阿里云防火墙**关掉 8080**、开 80/443

### [ ] 10. 表结构变更用 Flyway / Liquibase

现在还是手工执行 `sql/create_table.sql`，改字段只能手改库（**没有任何版本记录，也无法回滚**）。

### [ ] 11. GitHub Actions：push 自动构建 + 部署

把"手动 `git pull` + `build` + `up -d`"升级成 CI/CD。
注意：我们的 Deploy Key 是**只读**的，CI 里推送镜像需要另外的凭据（比如阿里云 ACR 的访问凭证）。

---

## 已完成（留档，别重复做）

- [x] 代码推到 GitHub，remote 更新为新用户名
- [x] 服务器初始化：系统更新、Docker（阿里云源）、2G swap、镜像加速器
- [x] 防火墙：22 收紧到自家 IP 段、8080 放行、3306/6379 不放行
- [x] Deploy Key（只读）+ `git clone` 到 `/opt/cleardesk`
- [x] `.env` 生成（权限 600、无 CRLF 污染）
- [x] `.mvn/settings.xml` 国内 Maven 镜像（构建从 233 秒降到 14 秒）
- [x] `pull_policy: never` + `JAVA_TOOL_OPTIONS` 内存上限，**已提交进仓库**
- [x] 三容器启动 + 公网访问验证 + `smoke-test.sh` 19 项全通过
- [x] 数据库端口未暴露（3306/6379 公网均连不上，已实测）
- [x] 管理员账号（`probe001` 提权为 `userRole=1`）
- [x] Git 提交身份改为真实账号 + noreply 邮箱
- [x] 部署实录文档 [beginner-deploy.md](beginner-deploy.md) 脱敏后提交

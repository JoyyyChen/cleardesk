#!/usr/bin/env bash
# ClearDesk 数据库备份：在服务器上、仓库根目录执行
#   bash scripts/backup-db.sh
# 产出：backups/cleardesk-YYYYmmdd-HHMMSS.sql.gz
#
# 定时任务（每天 3 点）：
#   crontab -e
#   0 3 * * * cd /opt/cleardesk && bash scripts/backup-db.sh >> backups/backup.log 2>&1
#
# 恢复演练（务必真跑一次，没演练过的备份不算备份）：
#   gunzip -c backups/cleardesk-<时间>.sql.gz | \
#     docker compose -f docker-compose.prod.yml exec -T mysql \
#     mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D cleardesk
set -euo pipefail

# 不要用 COMPOSE_FILE 这个变量名传递编排文件：docker compose 不认它，
# 会把整串当成一个文件名报 "set by COMPOSE_FILE environment variable is invalid"。
# 这里用数组，需要时改这一行即可。
COMPOSE_FILES=(-f docker-compose.prod.yml)
ENV_FILE="${ENV_FILE:-.env}"
KEEP_DAYS="${KEEP_DAYS:-7}"

[ -f "${COMPOSE_FILES[1]}" ] || { echo "找不到 ${COMPOSE_FILES[1]}，请在仓库根目录执行" >&2; exit 1; }
[ -f "$ENV_FILE" ]          || { echo "找不到 $ENV_FILE" >&2; exit 1; }

set -a; . "./$ENV_FILE"; set +a
: "${MYSQL_ROOT_PASSWORD:?$ENV_FILE 缺少 MYSQL_ROOT_PASSWORD}"
DB="${MYSQL_DATABASE:-cleardesk}"
stamp=$(date +%Y%m%d-%H%M%S)
outdir="backups"
mkdir -p "$outdir"
out="$outdir/cleardesk-$stamp.sql.gz"

echo "[$(date '+%F %T')] 开始备份 $DB -> $out"
# set -e/pipefail 下管道里任何一环失败都会立刻退出，这里要自己判断并留下可读的报错，
# 所以临时关掉 -e，用 PIPESTATUS 拿到 mysqldump 的真实退出码。
set +e
docker compose "${COMPOSE_FILES[@]}" exec -T mysql \
  mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
    --single-transaction --quick --default-character-set=utf8mb4 \
    "$DB" | gzip > "$out"
codes=("${PIPESTATUS[@]}")
set -e
if [ "${codes[0]}" -ne 0 ]; then
  echo "mysqldump 失败（退出码 ${codes[0]}）：mysql 容器没起？密码不对？见 doc/deploy-runbook.md 第 2.3 节" >&2
  rm -f "$out"
  exit 1
fi

# gzip 流失败时管道里最后一条命令是 gzip，用文件大小兜底校验
size=$(stat -c '%s' "$out")
if [ "$size" -lt 200 ]; then
  echo "备份文件只有 ${size} 字节，基本可以确定失败（库连接/密码问题），删除并退出" >&2
  rm -f "$out"
  exit 1
fi

# 能解压出 SQL 结尾标记才算完整
if ! gunzip -c "$out" | tail -5 | grep -q 'Dump completed'; then
  echo "备份流缺少 'Dump completed' 标记，文件可能被截断：$out" >&2
  exit 1
fi

human=$(numfmt --to=iec "$size" 2>/dev/null || echo "${size}B")
echo "[$(date '+%F %T')] 备份完成：$out ($human)"

# 轮转：删掉超过 KEEP_DAYS 天的备份
deleted=$(find "$outdir" -name 'cleardesk-*.sql.gz' -type f -mtime "+$KEEP_DAYS" -print -delete | wc -l)
[ "$deleted" -gt 0 ] && echo "已清理 $deleted 个超过 ${KEEP_DAYS} 天的旧备份"

# 待补项：备份还在同一台机器上，机器挂了就一起没了。
# 下一步应把 $out 推到对象存储（OSS/COS/S3）或异地，届时在这里加一行 rclone/ossutil 上传。
ls -lh "$outdir" | tail -5

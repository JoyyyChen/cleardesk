#!/usr/bin/env bash
# ClearDesk 部署自检：在服务器上、仓库根目录执行
#   cd /opt/cleardesk && bash scripts/smoke-test.sh
#
# 只做检查，不修改任何数据（注册探针账号属于写操作，见最后一段提示）。
#
# 实现说明（踩过的坑，别再改回去）：
#   1) 不要用 COMPOSE_FILE="a.yml -f b.yml" 这种写法传编排文件。那个变量名
#      docker compose 不认，会把整串当成一个文件名，报
#      "set by COMPOSE_FILE environment variable is invalid: stat ..."。
#   2) 也不要用 `compose ps --format '{{.Health}}'` 解析状态：列会被截断
#      （CMD 显示成 "java -jar /app/app.j..."），按列位置取值必然错位。
#      改成直接用容器名查运行状态，两件事同时解决。
set -uo pipefail

ENV_FILE="${ENV_FILE:-.env}"
APP_PORT="${APP_PORT:-8080}"
# 编排文件末尾写死了 name: cleardesk-prod，所以容器名是固定的
PROJECT="${PROJECT:-cleardesk-prod}"
COMPOSE_FILES=(-f docker-compose.prod.yml -f docker-compose.ip-only.yml)

pass=0; fail=0; warn=0
ok()   { printf '  \033[32m[OK]\033[0m   %s\n' "$1"; pass=$((pass+1)); }
bad()  { printf '  \033[31m[FAIL]\033[0m %s\n' "$1"; fail=$((fail+1)); }
meh()  { printf '  \033[33m[WARN]\033[0m %s\n' "$1"; warn=$((warn+1)); }
step() { printf '\n\033[1m== %s\033[0m\n' "$1"; }

# 同时带上基础文件和 ip-only 覆盖文件是安全的：compose 只读文件不启动容器，
# 而且 ip-only 只覆盖端口映射，不会影响下面这些 exec / logs 的结果。
compose() { docker compose "${COMPOSE_FILES[@]}" "$@"; }

# 输出该容器的状态串（例如 "Up 20 hours (healthy)" / "Exited (137) 5 minutes ago"），
# 不存在则返回 1。用制表符做分隔，避免花括号模板里的空格被 docker 拆成多列。
container_status() {
  local line
  line=$(docker ps -a --filter "name=^/${PROJECT}-$1-1\$" --format '{{.Names}}	{{.Status}}' 2>/dev/null | head -1)
  [ -n "$line" ] || return 1
  printf '%s' "${line#*	}"
}

step "0. 前置条件"
if [ -f docker-compose.prod.yml ]; then ok "找到 docker-compose.prod.yml"; else bad "当前目录没有 docker-compose.prod.yml（请在仓库根目录执行）"; exit 1; fi
[ -f docker-compose.ip-only.yml ] && ok "找到 docker-compose.ip-only.yml（无域名阶段的端口覆盖）" \
                                 || meh "没有 docker-compose.ip-only.yml：如果你启动时没带它，忽略这条"
if docker info >/dev/null 2>&1; then ok "Docker daemon 可用"; else bad "Docker 不可用：检查 docker 是否启动、当前用户是否在 docker 组（需重新登录）"; exit 1; fi
if [ -f "$ENV_FILE" ]; then
  ok "找到 $ENV_FILE"
  # 权限和换行是两个最容易埋雷的地方
  perm=$(stat -c '%a' "$ENV_FILE" 2>/dev/null || echo '?')
  [ "$perm" = "600" ] && ok "$ENV_FILE 权限 600" || meh "$ENV_FILE 权限是 $perm，建议 chmod 600 .env"
  if grep -q $'\r' "$ENV_FILE"; then bad "$ENV_FILE 含 CRLF 换行（从 Windows 传上来的），密码会带上 \\r，务必用 printf/cat 重写"; else ok "$ENV_FILE 换行正常（LF）"; fi
else
  bad "缺少 $ENV_FILE：参照 doc/deploy-runbook.md 第 2.1 节生成"; exit 1
fi
set -a; . "./$ENV_FILE"; set +a
# set -u 下引用不存在的变量会直接退出，检查脚本不能悄悄死在这里，
# 所以缺变量的情况交给下面的 :? 报到具体名字。
: "${MYSQL_ROOT_PASSWORD:?$ENV_FILE 里缺少 MYSQL_ROOT_PASSWORD}"
: "${MYSQL_PASSWORD:?$ENV_FILE 里缺少 MYSQL_PASSWORD}"
MYSQL_DATABASE="${MYSQL_DATABASE:-cleardesk}"
MYSQL_USER="${MYSQL_USER:-cleardesk}"

step "1. 容器状态（直接按容器名查，最可靠）"
for svc in mysql redis app; do
  if status=$(container_status "$svc"); then
    case "$status" in
      *"(healthy)"*) ok "$svc：$status" ;;
      Up*healthy*)   ok "$svc：$status" ;;
      Up*)           [ "$svc" = "app" ] && ok "$svc：$status" \
                                       || meh "$svc：$status（还没变 healthy，等 30 秒再跑一次）" ;;
      *)             bad "$svc：$status → compose logs $svc --tail 30" ;;
    esac
  else
    bad "$svc 容器不存在（可能启动命令没带 -f docker-compose.ip-only.yml，或项目名不是 $PROJECT）"
  fi
done
docker ps -a --filter "name=^/${PROJECT}-" --format '  {{.Names}}	{{.Status}}'

step "2. 容器内网络解析（应为内网 IP）"
hosts=$(compose exec -T app getent hosts mysql 2>/dev/null || true)
[ -n "$hosts" ] && ok "app 能解析 mysql -> $(printf '%s' "$hosts" | awk '{print $1}')" \
                || bad "app 解析不到 mysql：application-prod.yml 的 MYSQL_HOST 必须是 compose service 名"
rhosts=$(compose exec -T app getent hosts redis 2>/dev/null || true)
[ -n "$rhosts" ] && ok "app 能解析 redis -> $(printf '%s' "$rhosts" | awk '{print $1}')" \
                 || bad "app 解析不到 redis：REDIS_HOST 必须是 compose service 名"

step "3. 应用实际拿到的环境变量"
appenv=$(compose exec -T app env 2>/dev/null | grep -E '^(MYSQL|REDIS)_' || true)
printf '%s\n' "$appenv" | sed 's/\(PASSWORD=\).*/\1***/' | sed 's/^/       /'
printf '%s' "$appenv" | grep -q '^MYSQL_HOST=mysql' && ok "MYSQL_HOST=mysql" || bad "MYSQL_HOST 不是 mysql（写成 localhost 就会 Communications link failure）"
printf '%s' "$appenv" | grep -q '^REDIS_HOST=redis' && ok "REDIS_HOST=redis" || bad "REDIS_HOST 不是 redis"

step "4. 数据库表与初始化"
tables=$(compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE" -N -e "show tables;" 2>/dev/null || true)
if printf '%s' "$tables" | grep -q '^user$'; then
  ok "$MYSQL_DATABASE.user 表存在"
  cnt=$(compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE" -N \
        -e "select concat(count(*),' 行 / ',sum(userRole=1),' 个管理员') from user;" 2>/dev/null)
  ok "user 表数据：$cnt"
  admins=$(compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE" -N \
           -e "select count(*) from user where userRole=1 and isDelete=0;" 2>/dev/null)
  [ "${admins:-0}" -gt 0 ] && ok "有 $admins 个可用管理员账号" \
                           || meh "没有管理员账号，/user/search 和 /user/delete 会 403：见 runbook 第 3.5 节手工提权"
else
  bad "表不存在：init.sql 只在数据卷为空时执行；若是全新部署，compose logs mysql | tail -30"
fi

step "5. 应用账号能否登录数据库"
if compose exec -T mysql mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -D "$MYSQL_DATABASE" -e "select 1;" >/dev/null 2>&1; then
  ok "应用账号 $MYSQL_USER 可登录 $MYSQL_DATABASE"
else
  meh "应用账号 $MYSQL_USER 登录失败：常见原因是数据卷已初始化后又在 .env 改了 MYSQL_PASSWORD（改文件不会改库里的密码）"
fi

step "6. 接口连通性（容器内 -> 宿主机回环）"
http_code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${APP_PORT}/api/user/current" || echo 000)
case "$http_code" in
  200) ok "/api/user/current 返回 200（应用存活；未登录时返回 200 也是正常的，具体看响应体 code）" ;;
  401|403) ok "/api/user/current 返回 $http_code（未登录被拦，说明应用和鉴权都正常）" ;;
  000) bad "连不上 127.0.0.1:${APP_PORT}：app 容器没起、或端口映射被改过" ;;
  *)   meh "/api/user/current 返回 $http_code（能返回状态码说明应用活着，值是否符合预期要自己判断）" ;;
esac

step "7. 端口暴露自检（本机视角）"
for p in ${APP_PORT} 3306 6379; do
  if command -v ss >/dev/null 2>&1; then
    line=$(ss -lntp 2>/dev/null | awk -v p=":$p\$" '$4 ~ p' | head -1)
    if [ -n "$line" ]; then
      addr=$(printf '%s' "$line" | awk '{print $4}')
      case "$addr" in
        127.0.0.1:*|\[::1\]:*) ok "端口 $p 只监听回环（$addr）" ;;
        *":$p") meh "端口 $p 监听所有网卡（$addr）：3306/6379 出现这种结果说明数据库暴露了，去改 compose" ;;
      esac
    else
      ok "端口 $p 未被宿主机监听"
    fi
  fi
done
meh "宿主机看不见端口 ≠ 公网连不上：Docker 会绕过 ufw，最终以云安全组为准"
meh "请在自己电脑上确认：3306 / 6379 必须连不上，$APP_PORT 能连上"

step "结论"
printf '  通过 %d 项，失败 %d 项，提醒 %d 项\n' "$pass" "$fail" "$warn"
if [ "$fail" -eq 0 ]; then
  printf '\n  核心链路没问题。接着做：\n'
  printf '   1) 自己电脑上 curl http://<服务器IP>:%s/api/user/current 验证公网链路\n' "$APP_PORT"
  printf '   2) 注册一个探针账号（写库操作，自检脚本不替你写）：\n'
  printf "      curl -s -X POST http://127.0.0.1:%s/api/user/register -H 'Content-Type: application/json' \\\\\n" "$APP_PORT"
  printf "        -d '%s'\n" '{"userAccount":"probe001","userPassword":"12345678","checkPassword":"12345678"}'
  printf '   3) 提权成管理员并验证 /user/search（runbook 第 3.5 节）\n'
  exit 0
else
  printf '\n  先修 FAIL 项，再谈公网访问。排查顺序见 doc/deploy-runbook.md 第 2.3 节。\n'
  exit 1
fi

# 企业 XXL-JOB 只读排查手册 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为当前企业双后台拓扑交付一份端到端 XXL-JOB 只读排查手册、三套可直接运行的分机脚本、只读 MySQL SQL 和离线验证脚本。

**Architecture:** 浏览器网段、前端 Nginx 和两个后台 Java 节点分别运行职责单一的 Bash 诊断脚本，所有检查只读取 DNS、HTTP、systemd、监听端口、配置摘要和有界日志。MySQL 元数据由 DBA 使用独立只读 SQL 检查；仓库验证脚本通过临时 fixture、PATH 假命令和测试专用路径覆盖脚本行为，不访问真实企业地址。

**Tech Stack:** Bash、curl、getent、iproute2、systemd/journalctl、Nginx `-T`、MySQL 8 SQL、Markdown。

## Global Constraints

- 固定拓扑：域名 `http://mimo.sdc.cs.icbc:9996` → 企业入口/网关 → 实体 Nginx `122.233.30.2:80`；IP `http://122.233.30.2:9996` → 实体 Nginx `122.233.30.2:9996`；实体 Nginx 同时监听 `80` 与 `9996`。后台为 `122.233.30.4` 与 `122.233.30.114`，Redis 为 `122.233.30.20:6379`，XXL MySQL 为 `122.233.30.148:3306/xxl_job`。
- 三个现场脚本严格只读：不得启动、停止或重启服务，不得 reload Nginx，不得修改配置、身份文件、日志、Redis 或 MySQL，不得签发/消费 SSO 票据或触发任务。
- `TEST_AGENT_NGINX_XXL_JOB_ADMINS` 是前端 `nginx.env` 的有效变量；不得恢复已删除的三个 Java 地址变量。
- 输出统一使用 `[PASS]`、`[WARN]`、`[FAIL]`、`[INFO]`；退出码 `0` 表示无关键失败，`1` 表示关键异常，`2` 表示误用、错误机器或关键前提缺失。
- 密码、token、Cookie、ticket、Authorization、secret 和 session digest 不得输出原值；URL 去除 query/hash。数据库/Redis/XXL MySQL password、普通 API token、manager token 和内部代理 key 等低熵凭据只输出 `SET/UNSET`；只有生产强随机 `TEST_AGENT_XXL_JOB_ACCESS_TOKEN` 输出长度与 SHA-256 前缀用于跨节点比对。
- 默认日志窗口 15 分钟，后台脚本只接受 5～120 分钟；前端日志固定最多读取末尾 200 行。
- 当前 HTTP 入口与强制 `Secure` Admin Cookie 的兼容风险必须提示，但不得建议删除 `Secure` 或放宽 iframe 安全策略。
- SSO 被动证据顺序固定为：票据签发 POST → 登录 POST → iframe ready `postMessage` → Admin GET。全过程只允许检查事故时既有证据，不得为补证主动发起请求。
- 不修改 Java、前端运行时代码、HTTP API、RunEvent、数据库结构、Flyway、生产环境文件或现有企业部署脚本。
- 不修改或暂存工作区中与本任务无关的文件；不新建分支、不拉取、不 rebase、不推送。

---

## File Map

| 文件 | 单一职责 |
|---|---|
| `deploy/internal/diagnose-xxl-job-entry.sh` | 从实际浏览器网段验证 DNS、域名/IP 入口与同源 Admin readiness |
| `deploy/internal/diagnose-xxl-job-frontend.sh` | 在 `.2` 读取有效 Nginx 配置、探测两个 Admin、摘要读取代理日志 |
| `deploy/internal/diagnose-xxl-job-backend.sh` | 在 `.4`/`.114` 验证 systemd、8080/18080/9999、共享依赖、配置摘要和有界日志 |
| `deploy/internal/xxl-job-readonly-check.sql` | 由 MySQL 只读账号检查 Flyway、用户、任务、执行器组、registry 和日志统计 |
| `tools/verify-internal-xxl-job-diagnostics.sh` | 使用临时夹具和假命令验证脚本退出码、脱敏、只读边界与 SQL 语句类型 |
| `deploy/internal/XXL-JOB-TROUBLESHOOTING.md` | 固定企业拓扑的分层排查、现象速查、命令、决策树和升级证据模板 |
| `docs/README.md` | 增加正式排查手册入口 |
| `docs/testing/xxl-job-integration.md` | 增加诊断脚本自动验证命令和覆盖范围 |
| `.agents/session-log.huangzhenren.md` | 记录本次只读诊断交付的 Why/What/How/Result |

## Shared Script Contract

三个脚本不新增共享运行库，避免现场漏传依赖。每个脚本都内置以下同名最小函数，测试只依赖行为，不从其它脚本 `source`：

```bash
FAILURES=0

pass() { printf '[PASS] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*"; }
fail() { printf '[FAIL] %s\n' "$*"; FAILURES=$((FAILURES + 1)); }
info() { printf '[INFO] %s\n' "$*"; }

finish() {
  if (( FAILURES > 0 )); then
    printf '[FAIL] 诊断完成：发现 %s 个关键异常\n' "${FAILURES}"
    exit 1
  fi
  printf '[PASS] 诊断完成：未发现关键异常\n'
  exit 0
}
```

现场路径保持固定；仅验证脚本使用以下未写入手册的测试钩子覆盖路径或命令：

```text
TEST_AGENT_DIAG_NGINX_BIN
TEST_AGENT_DIAG_NGINX_ENV
TEST_AGENT_DIAG_NGINX_ACCESS_LOG
TEST_AGENT_DIAG_NGINX_ERROR_LOG
TEST_AGENT_DIAG_BACKEND_ENV
TEST_AGENT_DIAG_DATA_ROOT
```

测试钩子不允许覆盖企业 IP、端口、服务名或 URL，因此生产判断仍固定。

### Task 1: 浏览器网段入口诊断

**Files:**
- Create: `deploy/internal/diagnose-xxl-job-entry.sh`
- Create/Modify: `tools/verify-internal-xxl-job-diagnostics.sh`

**Interfaces:**
- Consumes: 固定域名/IP 与系统 `ip`、`getent`、`curl`。
- Produces: 无参数命令；只允许从实际浏览器网段 Linux 诊断终端运行。`ip/getent/curl` 缺失、`ip` 失败或无法识别本机全局 IPv4，以及命中 `.2/.4/.114/.20/.148` 已知基础设施节点时返回 `2` 且不发起网络探测；其它检查输出统一前缀并返回 `0/1`。

- [ ] **Step 1: 创建失败的入口脚本验证夹具**

在验证脚本中创建临时目录、自动清理、假 `ip/getent/curl`。入口健康夹具使用正常浏览器网段地址；五个已知基础设施地址逐一验证返回 `2`，`ip` 缺失/失败/空结果同样返回 `2`，并用调用哨兵证明错误机器不会执行 fake curl。假 curl 必须按 URL 输出 `__TEST_AGENT_HTTP_STATUS__:<code>`，使测试不访问真实网络：

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "${TMP_ROOT}"' EXIT
FAKE_BIN="${TMP_ROOT}/bin"
mkdir -p "${FAKE_BIN}"

cat >"${FAKE_BIN}/getent" <<'EOF'
#!/usr/bin/env bash
[[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" != "dns-fail" ]] || exit 2
printf '122.233.30.2 STREAM mimo.sdc.cs.icbc\n'
EOF

cat >"${FAKE_BIN}/curl" <<'EOF'
#!/usr/bin/env bash
url="${*: -1}"
if [[ "${XXL_DIAG_FIXTURE_MODE:-healthy}" == "admin-down" && "${url}" == *'/actuator/health/readiness' ]]; then
  printf '<html>Bad Gateway</html>\n__TEST_AGENT_HTTP_STATUS__:502'
else
  case "${url}" in
    */actuator/health/readiness) printf '{"status":"UP"}\n__TEST_AGENT_HTTP_STATUS__:200' ;;
    *) printf '<!doctype html><title>MIMO</title>\n__TEST_AGENT_HTTP_STATUS__:200' ;;
  esac
fi
EOF
chmod +x "${FAKE_BIN}/getent" "${FAKE_BIN}/curl"

ENTRY_SCRIPT="${ROOT_DIR}/deploy/internal/diagnose-xxl-job-entry.sh"
PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=healthy bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-ok.log"
grep -Fq '[PASS] 域名解析' "${TMP_ROOT}/entry-ok.log"
grep -Fq '[WARN] 当前入口使用 HTTP' "${TMP_ROOT}/entry-ok.log"

set +e
PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=admin-down bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-down.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
grep -Fq '[FAIL]' "${TMP_ROOT}/entry-down.log"

set +e
PATH="${FAKE_BIN}:${PATH}" XXL_DIAG_FIXTURE_MODE=dns-fail bash "${ENTRY_SCRIPT}" >"${TMP_ROOT}/entry-dns.log" 2>&1
status=$?
set -e
test "${status}" -eq 1
```

- [ ] **Step 2: 运行验证并确认 RED**

Run: `bash tools/verify-internal-xxl-job-diagnostics.sh`

Expected: 非零退出，错误包含 `diagnose-xxl-job-entry.sh: No such file or directory`。

- [ ] **Step 3: 实现入口脚本**

脚本使用 `set -uo pipefail`，先确认 `getent` 与 `curl` 存在。实现 `probe_page` 和 `probe_readiness`，curl 必须使用 `--connect-timeout 3 --max-time 8 --location --max-redirs 0`，并依据状态码和响应类型分类：

```bash
DOMAIN_HOST='mimo.sdc.cs.icbc'
DOMAIN_BASE='http://mimo.sdc.cs.icbc:9996'
IP_BASE='http://122.233.30.2:9996'
STATUS_MARKER='__TEST_AGENT_HTTP_STATUS__:'

http_get() {
  curl --silent --show-error --connect-timeout 3 --max-time 8 \
    --location --max-redirs 0 --write-out "\n${STATUS_MARKER}%{http_code}" "$1" 2>&1
}

probe_page() {
  local label="$1" url="$2" response status body
  if ! response="$(http_get "${url}")"; then
    fail "${label} 连接失败"
    return
  fi
  status="${response##*${STATUS_MARKER}}"
  body="${response%$'\n'${STATUS_MARKER}*}"
  if [[ "${status}" == '200' && -n "${body//[[:space:]]/}" ]]; then
    pass "${label} 返回 HTTP 200"
  elif [[ "${status}" == '502' || "${status}" == '504' ]]; then
    fail "${label} 返回 ${status}，检查企业入口或 Nginx upstream"
  else
    fail "${label} 返回 HTTP ${status:-000} 或空响应"
  fi
}

probe_readiness() {
  local label="$1" url="$2" response status body
  if ! response="$(http_get "${url}")"; then
    fail "${label} 连接失败"
    return
  fi
  status="${response##*${STATUS_MARKER}}"
  body="${response%$'\n'${STATUS_MARKER}*}"
  case "${status}" in
    200)
      if grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"${body}"; then
        pass "${label} 返回 HTTP 200 / UP"
      elif grep -Eqi '<!doctype|<html' <<<"${body}"; then
        fail "${label} 被错误路由到 HTML 页面"
      else
        fail "${label} 返回 HTTP 200，但不是 UP health JSON"
      fi
      ;;
    404) fail "${label} 返回 404，检查 /xxl-job-admin/ location" ;;
    502|504) fail "${label} 返回 ${status}，检查 Nginx Admin upstream" ;;
    *) fail "${label} 返回 HTTP ${status}" ;;
  esac
}
```

命令前提和调用顺序必须先检查 `ip`，可靠读取全局 IPv4 并拒绝 `.2/.4/.114/.20/.148` 五个基础设施节点；这一步失败直接返回 `2`，不得执行 `getent/curl` 或输出未验证 PASS。随后网络探测部分完整写为：

```bash
command -v getent >/dev/null 2>&1 || { printf '[FAIL] 缺少 getent，无法执行固定入口诊断\n' >&2; exit 2; }
command -v curl >/dev/null 2>&1 || { printf '[FAIL] 缺少 curl，无法执行固定入口诊断\n' >&2; exit 2; }

if dns_result="$(getent ahostsv4 "${DOMAIN_HOST}" 2>&1)" && grep -Fq '122.233.30.2' <<<"${dns_result}"; then
  pass '域名解析到 122.233.30.2'
else
  fail '域名未解析到 122.233.30.2'
fi
probe_page '域名入口' "${DOMAIN_BASE}/"
probe_page 'IP 入口' "${IP_BASE}/"
probe_readiness '域名同源 Admin readiness' "${DOMAIN_BASE}/xxl-job-admin/actuator/health/readiness"
probe_readiness 'IP 同源 Admin readiness' "${IP_BASE}/xxl-job-admin/actuator/health/readiness"
warn '当前入口使用 HTTP；浏览器可能拒收带 Secure 属性的 XXL Admin Cookie，请在浏览器 Network/Application 中取证'
finish
```

脚本不得请求 `/api/internal/platform/xxl-job/sso-tickets` 或 `/platform-sso/login`。

- [ ] **Step 4: 运行入口验证并确认 GREEN**

Run: `bash tools/verify-internal-xxl-job-diagnostics.sh`

Expected: 退出 `0`，末行包含 `XXL-JOB enterprise diagnostics verified`。

- [ ] **Step 5: 提交入口诊断**

```bash
git add deploy/internal/diagnose-xxl-job-entry.sh tools/verify-internal-xxl-job-diagnostics.sh
git commit -m "feat: 新增 XXL-JOB 入口只读诊断"
```

### Task 2: 前端 Nginx 诊断

**Files:**
- Create: `deploy/internal/diagnose-xxl-job-frontend.sh`
- Modify: `tools/verify-internal-xxl-job-diagnostics.sh`

**Interfaces:**
- Consumes: `/data/testagent/config/nginx.env`、`/data/apps/nginx/sbin/nginx -T`、两个后台 Admin readiness、Nginx 访问/错误日志。
- Produces: 无参数命令；错误机器或关键文件缺失返回 `2`，有效配置/节点健康异常返回 `1`。

- [ ] **Step 1: 扩展验证脚本，先覆盖前端失败场景**

新增 fake `ip`，以及 `frontend-fixture` 下的 `nginx.env`、假 Nginx binary 和日志。健康 Nginx 输出必须含：

```nginx
upstream test_agent_xxl_job_admin {
    server 122.233.30.4:18080 max_fails=3 fail_timeout=10s;
    server 122.233.30.114:18080 max_fails=3 fail_timeout=10s;
}
location /xxl-job-admin/ {
    proxy_pass http://test_agent_xxl_job_admin;
}
```

验证以下精确结果：

```bash
PATH="${FAKE_BIN}:${PATH}" \
TEST_AGENT_DIAG_NGINX_BIN="${FRONTEND_FIXTURE}/nginx" \
TEST_AGENT_DIAG_NGINX_ENV="${FRONTEND_FIXTURE}/nginx.env" \
TEST_AGENT_DIAG_NGINX_ACCESS_LOG="${FRONTEND_FIXTURE}/access.log" \
TEST_AGENT_DIAG_NGINX_ERROR_LOG="${FRONTEND_FIXTURE}/error.log" \
bash "${ROOT_DIR}/deploy/internal/diagnose-xxl-job-frontend.sh"

# 将有效配置删掉 .114:18080 后应返回 1。
# fake ip 改为 122.233.30.3 后应返回 2。
# 日志 fixture 中放入 ticket/cookie/token/password/Authorization 原值，输出不得包含原值。
```

- [ ] **Step 2: 运行验证并确认 RED**

Run: `bash tools/verify-internal-xxl-job-diagnostics.sh`

Expected: 非零退出，错误指向缺失的 `diagnose-xxl-job-frontend.sh`。

- [ ] **Step 3: 实现前端脚本**

脚本验证 `ip -4 -o addr show scope global` 包含 `122.233.30.2/`。关键路径使用固定默认值并允许测试钩子覆盖：

```bash
NGINX_BIN="${TEST_AGENT_DIAG_NGINX_BIN:-/data/apps/nginx/sbin/nginx}"
NGINX_ENV="${TEST_AGENT_DIAG_NGINX_ENV:-/data/testagent/config/nginx.env}"
ACCESS_LOG="${TEST_AGENT_DIAG_NGINX_ACCESS_LOG:-/data/apps/nginx/logs/access.log}"
ERROR_LOG="${TEST_AGENT_DIAG_NGINX_ERROR_LOG:-/data/apps/nginx/logs/error.log}"
NGINX_PREFIX='/data/apps/nginx/'
NGINX_MAIN_CONF='/data/apps/nginx/conf/nginx.conf'
```

用以下逐行文本解析函数读取 `TEST_AGENT_NGINX_MODE`、`TEST_AGENT_NGINX_XXL_JOB_ADMINS`、`TEST_AGENT_NGINX_LISTEN_PORT` 和 `TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS`，不得 `source nginx.env`：

```bash
env_value() {
  local file="$1" wanted_key="$2" line key value result=''
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    [[ "${key}" == "${wanted_key}" ]] || continue
    value="${line#*=}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    result="${value}"
  done <"${file}"
  printf '%s' "${result}"
}
```

随后执行：

```bash
effective_config="$("${NGINX_BIN}" -p "${NGINX_PREFIX}" -c "${NGINX_MAIN_CONF}" -T 2>&1)"
```

分别用锚定到首个非空白字符的指令正则断言 upstream 名、两个 `server`、location 和 `proxy_pass`，排除 `#` 注释；全部要求只存在于注释、或单个必须指令被注释时均返回 `1`，合法活跃配置及附加 server 参数继续通过。随后直连 GET：

```text
http://122.233.30.4:18080/xxl-job-admin/actuator/health/readiness
http://122.233.30.114:18080/xxl-job-admin/actuator/health/readiness
```

日志只执行 `tail -n 200`，再筛选 `xxl-job-admin|upstream|connect\(\) failed|timed out|no live upstreams|502|504`。实现以下 `redact_stream`，覆盖大小写不敏感的 ticket/cookie/token/password/secret/authorization/digest 键值并把 URL query 替换为 `[REDACTED_QUERY]`：

```bash
redact_stream() {
  sed -E \
    -e 's|(https?://[^[:space:]?"#]+)\?[^[:space:]"#]*|\1?[REDACTED_QUERY]|g' \
    -e 's/((ticket|cookie|token|password|secret|authorization|digest)[[:space:]]*[=:][[:space:]]*)[^,;[:space:]"}]+/\1[REDACTED]/Ig' \
    -e 's/("(ticket|cookie|token|password|secret|authorization|digest)"[[:space:]]*:[[:space:]]*")[^"]*/\1[REDACTED]/Ig'
}
```

不得执行 `nginx -t`、reload 或配置生成。

- [ ] **Step 4: 运行前端验证并确认 GREEN**

Run: `bash tools/verify-internal-xxl-job-diagnostics.sh`

Expected: 健康夹具返回 `0`，缺失 upstream 返回 `1`，错误机器返回 `2`，敏感 fixture 原值不出现在输出。

- [ ] **Step 5: 提交前端诊断**

```bash
git add deploy/internal/diagnose-xxl-job-frontend.sh tools/verify-internal-xxl-job-diagnostics.sh
git commit -m "feat: 新增 XXL-JOB 前端只读诊断"
```

### Task 3: 后台 Java/Admin/executor 诊断

**Files:**
- Create: `deploy/internal/diagnose-xxl-job-backend.sh`
- Modify: `tools/verify-internal-xxl-job-diagnostics.sh`

**Interfaces:**
- Consumes: `--expected-host 122.233.30.4|122.233.30.114`、可选 `--minutes 5..120`，以及 systemd、ss、curl、nc、journalctl、backend.env 和身份文件。
- Produces: 可跨节点人工比较的 `REDIS_ENDPOINT`、`XXL_MYSQL_ENDPOINT`、`TEST_AGENT_XXL_JOB_ACCESS_TOKEN` 长度/SHA-256 前缀、`ADMIN_PORT`、`EXECUTOR_PORT` 摘要；其它 password/token/key 只输出 `SET/UNSET`，不输出任何 secret 原文、长度或摘要。

- [ ] **Step 1: 扩展验证脚本，固化后台参数与脱敏契约**

新增假 `ip/systemctl/ss/nc/journalctl` 和后台 env/身份 fixture。健康 fixture 必须模拟 systemd `MainPID=4242`，并让 8080、18080、9999 都由该 PID 监听；Admin-down fixture 让 18080 readiness 返回 503；配置不一致 fixture 让 advertised host 与 `--expected-host` 不同。

精确验证：

```bash
# healthy -> 0
bash diagnose-xxl-job-backend.sh --expected-host 122.233.30.4 --minutes 15
# admin-down/config mismatch -> 1
# missing expected-host、非法 host、--minutes 4、--minutes 121、错误机器 -> 2
# 输出包含 token SET、长度、SHA-256 前缀，但不包含 fixture 中的原始密码/token/digest。
```

- [ ] **Step 2: 运行验证并确认 RED**

Run: `bash tools/verify-internal-xxl-job-diagnostics.sh`

Expected: 非零退出，错误指向缺失的 `diagnose-xxl-job-backend.sh`。

- [ ] **Step 3: 实现参数、节点身份与配置摘要**

使用精确参数解析：

```bash
EXPECTED_HOST=''
MINUTES=15
usage() {
  printf '%s\n' 'Usage: diagnose-xxl-job-backend.sh --expected-host <122.233.30.4|122.233.30.114> [--minutes <5-120>]'
}
usage_error() {
  printf '[FAIL] %s\n' "$1" >&2
  usage >&2
  exit 2
}
while (( $# > 0 )); do
  case "$1" in
    --expected-host) [[ $# -ge 2 ]] || usage_error '--expected-host 缺少值'; EXPECTED_HOST="$2"; shift 2 ;;
    --minutes) [[ $# -ge 2 ]] || usage_error '--minutes 缺少值'; MINUTES="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage_error "未知参数：$1" ;;
  esac
done
[[ "${EXPECTED_HOST}" == '122.233.30.4' || "${EXPECTED_HOST}" == '122.233.30.114' ]] || usage_error '非法 expected host'
[[ "${MINUTES}" =~ ^[0-9]+$ ]] && (( MINUTES >= 5 && MINUTES <= 120 )) || usage_error '--minutes 必须为 5～120'
```

本机 `ip` 不含 expected host 时返回 `2`。backend.env 使用以下文本解析函数，不得 source：

```bash
env_value() {
  local file="$1" wanted_key="$2" line key value result=''
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    [[ "${key}" == "${wanted_key}" ]] || continue
    value="${line#*=}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    result="${value}"
  done <"${file}"
  printf '%s' "${result}"
}
```

输出以下非敏感键：profile、deployment mode、advertised host、linux server ID、Redis endpoint、XXL enabled、MySQL endpoint/username、Admin/executor 端口；JDBC URL 必须去掉 query 和 userinfo。以下低熵键只调用 `secret_presence`，输出 `SET/UNSET`：

```text
TEST_AGENT_DB_PASSWORD
TEST_AGENT_REDIS_PASSWORD
TEST_AGENT_XXL_JOB_MYSQL_PASSWORD
TEST_AGENT_API_TOKEN
TEST_AGENT_OPENCODE_MANAGER_TOKEN
TEST_AGENT_INTERNAL_PROXY_API_KEY
```

只有 `TEST_AGENT_XXL_JOB_ACCESS_TOKEN` 调用 `secret_summary`，输出 `UNSET` 或 `SET length=<N> sha256=<前16位>`：

```bash
sha256_text() {
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s' "$1" | sha256sum | awk '{print substr($1, 1, 16)}'
  elif command -v shasum >/dev/null 2>&1; then
    printf '%s' "$1" | shasum -a 256 | awk '{print substr($1, 1, 16)}'
  else
    return 1
  fi
}
secret_summary() {
  local key="$1" value digest
  value="$(env_value "${BACKEND_ENV}" "${key}")"
  if [[ -z "${value}" ]]; then
    info "${key}=UNSET"
    return
  fi
  digest="$(sha256_text "${value}")" || { fail '缺少 SHA-256 命令，无法安全生成配置摘要'; return; }
  info "${key}=SET length=${#value} sha256=${digest}"
}
```

低熵凭据使用独立 `secret_presence`，只读取文本值并输出 `SET/UNSET`；不得复用 `secret_summary`：

```bash
secret_presence() {
  local key="$1" value
  value="$(env_value "${BACKEND_ENV}" "${key}")"
  if [[ -z "${value}" ]]; then
    info "${key}=UNSET"
  else
    info "${key}=SET"
  fi
}
```

身份目录默认从 env 的 `SYS_DATA_ROOT_DIR` 读取，测试钩子优先；`.serverhost` 必须等于 expected host，`.serverid` 必须等于 env 的稳定 ID。

- [ ] **Step 4: 实现 systemd、端口、网络、health 和日志检查**

执行只读命令：

```bash
systemctl show test-agent-backend \
  --property=LoadState,ActiveState,SubState,MainPID,ExecStart,EnvironmentFiles,ActiveEnterTimestamp \
  --no-pager
ss -ltnp
curl --silent --show-error --connect-timeout 3 --max-time 8 \
  http://127.0.0.1:8080/actuator/health/readiness
curl --silent --show-error --connect-timeout 3 --max-time 8 \
  http://127.0.0.1:18080/xxl-job-admin/actuator/health/readiness
journalctl -u test-agent-backend --since "${MINUTES} minutes ago" --no-pager -o short-iso
```

8080、18080、9999 必须监听；可见 PID 时必须等于 `MainPID`，权限导致看不到 PID 时只 WARN。用 `nc -z -w 3` 检查 Redis、MySQL 和对端 8080/18080/9999；`nc` 缺失时使用有超时的 Bash `/dev/tcp` fallback。日志只保留以下关键词行并脱敏：

```text
xxl-job|XxlJob|Flyway|Hikari|MySQL|Admin.*readiness|ExecutorRegistryThread|
registry error|Connection refused|SKIPPED_LOCK_HELD|schedule|trigger|handle
```

manager、worker、`.serverid/.serverhost` 以外的 `4096-4115` 仅输出“非管理页首要链路”说明，不执行 Docker 命令。

- [ ] **Step 5: 运行后台验证并确认 GREEN**

Run: `bash tools/verify-internal-xxl-job-diagnostics.sh`

Expected: 所有健康/失败/误用/脱敏断言通过，验证脚本退出 `0`。

- [ ] **Step 6: 提交后台诊断**

```bash
git add deploy/internal/diagnose-xxl-job-backend.sh tools/verify-internal-xxl-job-diagnostics.sh
git commit -m "feat: 新增 XXL-JOB 后台只读诊断"
```

### Task 4: MySQL 只读检查 SQL 与静态边界

**Files:**
- Create: `deploy/internal/xxl-job-readonly-check.sql`
- Modify: `tools/verify-internal-xxl-job-diagnostics.sh`

**Interfaces:**
- Consumes: MySQL 8 `xxl_job` schema 与只读账号。
- Produces: 不含 password、token、digest 原文、executor_param、trigger_msg 或 handle_msg 的元数据结果集。

- [ ] **Step 1: 先加入 SQL 静态失败验证**

验证脚本去掉 `--` 注释后，以分号分隔语句，要求每条语句首词只能是 `SELECT`、`SHOW` 或 `WITH`，并拒绝以下独立关键字：

```text
INSERT UPDATE DELETE REPLACE MERGE TRUNCATE ALTER CREATE DROP CALL
GRANT REVOKE LOCK UNLOCK SET START TRANSACTION COMMIT ROLLBACK
```

同时拒绝敏感列投影：``password``、``token``、``platform_session_digest`` 原值、``executor_param``、``trigger_msg``、``handle_msg``。允许 `CHAR_LENGTH(platform_session_digest)`。

即使语句首词为只读类型，也必须显式拒绝 `INTO OUTFILE`、`INTO DUMPFILE`、`GET_LOCK`、`RELEASE_LOCK`、`IS_FREE_LOCK`、`IS_USED_LOCK` 和用户变量赋值 `:=`；验证只对临时 SQL 文本做静态检查，不连接 MySQL。

- [ ] **Step 2: 运行验证并确认 RED**

Run: `bash tools/verify-internal-xxl-job-diagnostics.sh`

Expected: 非零退出，指出 `xxl-job-readonly-check.sql` 不存在。

- [ ] **Step 3: 编写只读 SQL**

按以下结果集顺序编写完整语句：

```sql
SELECT VERSION() AS mysql_version, DATABASE() AS current_database, @@global.time_zone AS global_time_zone, @@session.time_zone AS session_time_zone;

SELECT installed_rank, version, description, type, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT COUNT(*) AS jit_user_count,
       SUM(platform_user_id IS NULL) AS missing_platform_user_id_count,
       SUM(CHAR_LENGTH(platform_session_digest) = 64) AS active_digest_shape_count,
       SUM(platform_session_expires_at > NOW(3)) AS unexpired_session_count
FROM xxl_job_user;

SELECT id, platform_user_id, username, role,
       CHAR_LENGTH(platform_session_digest) AS session_digest_length,
       platform_session_expires_at
FROM xxl_job_user
ORDER BY id;

SELECT id, app_name, title, address_type,
       CASE WHEN address_list IS NULL OR address_list = '' THEN 0
            ELSE 1 + CHAR_LENGTH(address_list) - CHAR_LENGTH(REPLACE(address_list, ',', '')) END AS configured_address_count,
       update_time
FROM xxl_job_group
WHERE app_name = 'test-agent-backend';

SELECT platform_task_key, job_desc, schedule_type, schedule_conf, trigger_status,
       misfire_strategy, executor_route_strategy, executor_handler,
       executor_block_strategy, executor_timeout, executor_fail_retry_count,
       update_time
FROM xxl_job_info
WHERE platform_task_key IS NOT NULL
ORDER BY platform_task_key;

SELECT registry_group, registry_key, registry_value, update_time,
       TIMESTAMPDIFF(SECOND, update_time, NOW()) AS age_seconds
FROM xxl_job_registry
WHERE registry_group = 'EXECUTOR' AND registry_key = 'test-agent-backend'
ORDER BY registry_value;

SELECT DATE(trigger_time) AS trigger_day,
       COUNT(*) AS total_count,
       SUM(trigger_code = 200) AS trigger_success_count,
       SUM(handle_code = 200) AS handle_success_count,
       SUM(handle_code <> 0 AND handle_code <> 200) AS handle_failure_count,
       SUM(handle_code = 0) AS unfinished_count
FROM xxl_job_log
WHERE trigger_time >= NOW() - INTERVAL 7 DAY
GROUP BY DATE(trigger_time)
ORDER BY trigger_day DESC;

SHOW INDEX FROM xxl_job_user;
SHOW INDEX FROM xxl_job_info;
```

SQL 注释明确要求 `--password` 交互输入或受管客户端配置，不把凭据写入命令历史。

- [ ] **Step 4: 运行 SQL 静态验证并确认 GREEN**

Run: `bash tools/verify-internal-xxl-job-diagnostics.sh`

Expected: SQL 语句类型和敏感列检查全部通过。

- [ ] **Step 5: 提交只读 SQL**

```bash
git add deploy/internal/xxl-job-readonly-check.sql tools/verify-internal-xxl-job-diagnostics.sh
git commit -m "feat: 新增 XXL-JOB MySQL 只读检查"
```

### Task 5: 正式排查手册与文档入口

**Files:**
- Create: `deploy/internal/XXL-JOB-TROUBLESHOOTING.md`
- Modify: `docs/README.md`
- Modify: `docs/testing/xxl-job-integration.md`
- Modify: `tools/verify-internal-xxl-job-diagnostics.sh`

**Interfaces:**
- Consumes: Tasks 1～4 的真实脚本路径、参数、退出码和 SQL。
- Produces: 固定现场逐机执行单、现象速查表、端到端决策树、脱敏证据模板和测试文档入口。

- [ ] **Step 1: 先为文档契约增加静态断言**

验证脚本必须检查手册包含固定地址、三个脚本名、SQL 名、`TEST_AGENT_NGINX_XXL_JOB_ADMINS`、三个已删除 Java 变量、`Secure` Cookie、`postMessage`、`SKIPPED_LOCK_HELD` 和退出码说明；检查 `docs/README.md` 与测试文档包含正式入口/验证命令。

- [ ] **Step 2: 运行验证并确认 RED**

Run: `bash tools/verify-internal-xxl-job-diagnostics.sh`

Expected: 非零退出，首先报告正式手册不存在。

- [ ] **Step 3: 编写正式手册**

手册必须按以下章节落地，每个节点都写出完整命令：

```text
1. 适用范围、双入口固定拓扑与只读红线（域名经企业入口到 `.2:80`，IP 直达 `.2:9996`，实体 Nginx 同时监听两端口）
2. 五分钟现象速查表
3. 排查前记录项与脱敏规则
4. 浏览器网段入口（执行入口脚本）
5. 122.233.30.2 前端 Nginx（执行前端脚本）
6. 122.233.30.4 后台（expected-host=.4）
7. 122.233.30.114 后台（expected-host=.114）
8. 122.233.30.20 Redis 的人工只读边界
9. 122.233.30.148 MySQL（DBA 执行只读 SQL）
10. 浏览器 SSO Network/Console/Cookie/CSP/postMessage
11. executor 在线、任务不触发与 SKIPPED_LOCK_HELD
12. 多 Admin 间歇故障与共享配置摘要比对
13. HTTP 状态码、日志关键字、责任边界决策树
14. 证据保存、脱敏复核与升级模板
15. 固定命令与有效/废弃变量附录
```

每一步先写操作机器和绝对目录，再给完整命令、预期成功条件与失败停止点。例如两个后台必须分别给出：

```bash
cd /data/testagent
bash /data/testagent/deploy/internal/diagnose-xxl-job-backend.sh \
  --expected-host 122.233.30.4 --minutes 15 \
  | tee /data/0709/xxl-job-diagnostics-backend-122.233.30.4.log
```

```bash
cd /data/testagent
bash /data/testagent/deploy/internal/diagnose-xxl-job-backend.sh \
  --expected-host 122.233.30.114 --minutes 15 \
  | tee /data/0709/xxl-job-diagnostics-backend-122.233.30.114.log
```

MySQL 命令必须使用交互式密码输入：

```bash
mysql --host=122.233.30.148 --port=3306 --user='<只读账号>' --password \
  --database=xxl_job \
  < /data/testagent/deploy/internal/xxl-job-readonly-check.sql
```

Redis 章节只能说明确认 TCP 可达和由应用摘要判断配置一致；不得提供 `KEYS`、`SCAN`、`GETDEL`、ticket key 查询或任何真实 ticket/session marker 读取命令。浏览器章节禁止导出未脱敏 HAR，明确普通 HTTP 可能拒收 `Secure` Cookie且不得以删除安全属性作为现场修复。

- [ ] **Step 4: 更新文档索引与测试说明**

在 `docs/README.md` 的部署与数据库段新增：

```markdown
- `deploy/internal/XXL-JOB-TROUBLESHOOTING.md`：当前企业双后台 XXL-JOB 管理页、SSO、Admin、executor 和共享 MySQL 的只读排查手册。
```

在 `docs/testing/xxl-job-integration.md` 标准命令增加：

```bash
bash tools/verify-internal-xxl-job-diagnostics.sh
```

并注明验证完全使用临时夹具，不访问 `.2/.4/.114/.20/.148`。

- [ ] **Step 5: 运行文档与脚本验证**

Run:

```bash
bash tools/verify-internal-xxl-job-diagnostics.sh
bash tools/verify-ai-docs.sh
git diff --check
```

Expected: 三个命令均退出 `0`；诊断验证末行包含 `XXL-JOB enterprise diagnostics verified`。

- [ ] **Step 6: 提交正式手册**

```bash
git add deploy/internal/XXL-JOB-TROUBLESHOOTING.md docs/README.md docs/testing/xxl-job-integration.md tools/verify-internal-xxl-job-diagnostics.sh
git commit -m "docs: 新增企业 XXL-JOB 排查手册"
```

### Task 6: 全量安全自检、会话记录与交付提交

**Files:**
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: 全部已实现文件与 AGENTS 完成清单。
- Produces: 可追溯验证记录，且最终工作树只保留任务前已有的无关修改/未跟踪文件。

- [ ] **Step 1: 运行 Shell、行为和只读边界验证**

Run:

```bash
bash -n deploy/internal/diagnose-xxl-job-entry.sh
bash -n deploy/internal/diagnose-xxl-job-frontend.sh
bash -n deploy/internal/diagnose-xxl-job-backend.sh
bash -n tools/verify-internal-xxl-job-diagnostics.sh
bash tools/verify-internal-xxl-job-diagnostics.sh
```

Expected: 全部退出 `0`。

- [ ] **Step 2: 扫描危险操作、敏感字面量与冲突标记**

Run:

```bash
rg -n 'systemctl[[:space:]]+(start|stop|restart)|nginx[^\n]*-s[[:space:]]+reload|docker[[:space:]]+(restart|stop|rm)|redis-cli|curl[^\n]*/sso-tickets|platform-sso/login' \
  deploy/internal/diagnose-xxl-job-*.sh
rg -n '^(INSERT|UPDATE|DELETE|REPLACE|MERGE|TRUNCATE|ALTER|CREATE|DROP|CALL|GRANT|REVOKE|LOCK|UNLOCK|SET)[[:space:]]' \
  deploy/internal/xxl-job-readonly-check.sql
rg -n '^(<<<<<<<|=======|>>>>>>>)' \
  deploy/internal/XXL-JOB-TROUBLESHOOTING.md \
  deploy/internal/diagnose-xxl-job-*.sh \
  deploy/internal/xxl-job-readonly-check.sql \
  tools/verify-internal-xxl-job-diagnostics.sh \
  docs/README.md docs/testing/xxl-job-integration.md
```

Expected: 三次扫描均无输出。SQL 内嵌在 Markdown 代码块中的示例不参与 SQL 文件扫描。

- [ ] **Step 3: 回顾全部会话日志并更新本机日志**

Run:

```bash
git config user.name
find .agents -maxdepth 1 -type f -name 'session-log*.md' -print | sort
tail -n 220 .agents/session-log.md
tail -n 220 .agents/session-log.huangzhenren.md
tail -n 220 .agents/session-log.rkk222.md
```

在 `.agents/session-log.huangzhenren.md` 新增一条 `2026-07-22 - 新增企业 XXL-JOB 只读排查手册`，按 `Why / What / How / Result` 记录固定拓扑、三个脚本、只读 SQL、脱敏与实际验证结果，不写真实 secret 或原始 token 摘要。

- [ ] **Step 4: 检查精确暂存范围并提交收尾记录**

Run:

```bash
git status --short
git diff --check
git add .agents/session-log.huangzhenren.md
git diff --cached --check
git diff --cached --name-only
git commit -m "docs: 记录 XXL-JOB 排查手册交付"
```

Expected: cached 文件只有 `.agents/session-log.huangzhenren.md`；不包含任务前已有的 `openapitools.json` 或其它无关文件。

- [ ] **Step 5: 最终确认提交和工作树边界**

Run:

```bash
git log -n 7 --oneline
git status --short
```

Expected: 本计划对应提交均在当前 `main`，未推送；工作树中只有任务开始前已有且未纳入任务的文件。

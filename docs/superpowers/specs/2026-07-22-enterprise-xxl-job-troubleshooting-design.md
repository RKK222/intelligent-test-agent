# 企业 XXL-JOB 只读排查手册设计

## 背景与目标

企业内 XXL-JOB 管理页经过浏览器入口、企业网关、实体 Nginx、Java 内嵌 Admin、共享 Redis/MySQL 和 executor 多层组件。平台 `8080` readiness 不包含 `xxlJobAdmin`，因此平台其它页面正常不能证明 Admin、SSO 或调度链路正常；前端“管理服务暂不可用”还是签票异常、Admin 错误和 15 秒 iframe 握手超时的统一兜底文案，不能直接作为根因。

本次新增一份面向当前固定企业现场的完整只读排查手册及配套诊断脚本，让现场人员按照同一顺序收集证据、定位故障边界并把脱敏结果升级给对应责任方。手册不执行修复，不启动、停止或重启服务，不 reload Nginx，不修改配置，不签发或消费真实 SSO 票据，不触发任务，也不写 Redis/MySQL。

## 固定现场范围

手册只面向当前企业拓扑，不抽象为通用模板：

| 角色 | 固定地址 |
|---|---|
| 浏览器域名入口 | 域名 `http://mimo.sdc.cs.icbc:9996` → 企业入口/网关 → 实体 Nginx `122.233.30.2:80` |
| 浏览器 IP 入口 | IP `http://122.233.30.2:9996` → 实体 Nginx `122.233.30.2:9996` |
| 实体 Nginx | 实体 Nginx 同时监听 `80` 与 `9996` |
| 后台 A | `122.233.30.4`，平台 `8080`、Admin `18080`、executor `9999` |
| 后台 B | `122.233.30.114`，平台 `8080`、Admin `18080`、executor `9999` |
| Redis | `122.233.30.20:6379` |
| 外部 XXL MySQL | `122.210.106.43:3306/xxl_job`（现网外部服务，不在平台节点部署容器） |

`TEST_AGENT_NGINX_XXL_JOB_ADMINS` 仍是前端服务器 `nginx.env` 的有效渲染输入，用于生成中央 Nginx Admin upstream；它不是 Java 环境变量。已删除的 Java 地址变量是 `TEST_AGENT_XXL_JOB_ADMIN_ADDRESSES`、`TEST_AGENT_XXL_JOB_EXECUTOR_ADDRESS` 和 `TEST_AGENT_XXL_JOB_EXECUTOR_IP`。

当前入口为普通 HTTP，而 Admin 会话 Cookie 被安全过滤器强制设置为 `Secure`。手册必须把浏览器是否拒收 Cookie 列为高优先级检查项，但只负责取证和判定，不通过删除 `Secure`、放宽 iframe 策略或现场改配置规避安全边界。

## 文档结构

正式手册新增为 `deploy/internal/XXL-JOB-TROUBLESHOOTING.md`，并从 `docs/README.md` 增加入口。手册按端到端数据流组织，在开头提供按现象查找的速查表：

1. 适用范围与只读约束。
2. 当前固定拓扑、端口和请求数据流。
3. “管理服务暂不可用”、会话失效、502、执行器离线、任务不触发等现象速查。
4. 排查前准备：故障时间、实际入口、账号角色、浏览器版本和脱敏规则。
5. 浏览器 SSO 请求链与 Cookie/CSP/`postMessage`。
6. 企业入口、DNS 和域名 `9996 -> 企业入口/网关 -> .2:80` 转发，并与 IP `.2:9996` 直连分层判断。
7. `.2` 实体 Nginx、有效配置和 Admin upstream。
8. `.4` 后台平台/Admin/executor。
9. `.114` 后台平台/Admin/executor。
10. `.20` Redis 票据和 session marker 边界。
11. 外部 MySQL `122.210.106.43`、Flyway、JIT 用户、执行器组、任务和 registry。
12. 多 Admin 间歇性故障及节点配置一致性。
13. executor 在线但任务不触发、互斥跳过和调度日志。
14. HTTP 状态码、日志关键字和故障责任边界决策树。
15. 证据收集模板、脱敏复核和升级路径。
16. 固定地址、有效配置变量、命令和只读 SQL 附录。

手册不复制完整部署步骤；Mac 打包、外层 U 盘包、节点包和 `.4 -> .114 -> .2` 部署顺序继续以 `deploy/internal/README.md`、`SINGLE-BACKEND.md` 和 `MULTI-BACKEND.md` 为准。若诊断确认版本混用，手册只记录需要核对的 JAR、前端包和配置摘要，不提供替换、传输或部署命令。

## 配套脚本

### 浏览器网段入口脚本

新增 `deploy/internal/diagnose-xxl-job-entry.sh`，在实际浏览器网段的 Linux 终端执行。它只执行 DNS 查询、TCP/HTTP GET/HEAD 探测和响应摘要检查：

- 在任何网络探测前读取本机全局 IPv4；命中 `.2/.4/.114/.20` 或外部 MySQL `122.210.106.43` 已知基础设施地址，或 `ip` 缺失/失败/无法得到地址时，明确返回误用/关键前提退出码 `2`。
- 检查 `mimo.sdc.cs.icbc` DNS。
- 分别检查域名入口和 `.2:9996` IP 入口。
- 检查同源 `/xxl-job-admin/actuator/health/readiness`。
- 区分连接失败、DNS 失败、`404`、`502/504`、JSON health 和误返回 SPA HTML。
- 当实际入口为 HTTP 时输出 `Secure` Cookie 兼容风险提示。
- 不调用签票 API，不提交 `/platform-sso/login`，不读取浏览器 Cookie、Token 或存储。

### 前端 Nginx 脚本

新增 `deploy/internal/diagnose-xxl-job-frontend.sh`，只允许在 `122.233.30.2` 执行：

- 验证本机地址包含 `.2`，不符时退出 `2`。
- 读取 `/data/testagent/config/nginx.env` 的非敏感键。
- 使用 `/data/apps/nginx/sbin/nginx -p /data/apps/nginx/ -c /data/apps/nginx/conf/nginx.conf -T` 读取有效配置，不使用 PATH 中其它 Nginx。
- 验证已加载配置中的有效指令包含 `test_agent_xxl_job_admin`、`.4:18080`、`.114:18080`、`location /xxl-job-admin/` 和对应 `proxy_pass`；正则锚定到首个非空白字符，注释行不能满足合同。
- 分别从 `.2` GET 两个 Admin readiness，识别单节点故障。
- 有界读取 `/data/apps/nginx/logs/access.log` 和 `error.log` 中的 XXL 路径及 upstream 错误，并在输出前去除 query 和敏感字段。
- 不执行 `nginx -s reload`、`systemctl reload`、配置生成或任何文件覆盖。

### 后台脚本

新增 `deploy/internal/diagnose-xxl-job-backend.sh`，分别在 `.4`、`.114` 执行并强制传入 `--expected-host`。参数只接受 `122.233.30.4` 或 `122.233.30.114`；脚本验证本机地址与参数一致，防止在错误节点生成误导结果。

脚本检查：

- `test-agent-backend` systemd 的 `ExecStart`、`EnvironmentFiles`、`MainPID` 和启动时间。
- `8080/18080/9999` 监听者及其与 systemd 主 PID 的关系。
- 平台 `8080` readiness 和本机 Admin `18080/xxl-job-admin/actuator/health/readiness`。
- 到 `.20:6379`、外部 MySQL `122.210.106.43:3306`、对端 `8080/18080/9999` 的只读网络连通性。
- `/data/testagent/config/backend.env` 中允许展示的地址、开关、用户名和端口；数据库/Redis/XXL MySQL password、普通 API token、manager token 和内部代理 key 等低熵凭据只输出 `SET/UNSET`，不输出长度或摘要。
- 只有生产规范要求强随机且需要跨节点比对的 `TEST_AGENT_XXL_JOB_ACCESS_TOKEN` 输出长度和 SHA-256 前缀；两个后台的 Redis、XXL MySQL、该 access token 摘要和端口应当一致，本机 advertised host 与稳定 ID 应符合本节点。
- 最近 5～120 分钟的 Admin、Flyway、Hikari、MySQL、executor readiness、注册和调度日志，默认 15 分钟。
- `.serverid/.serverhost` 只用于关联 Java 身份；manager、worker 和 `4096-4115` 故障明确标记为非管理页首要链路。

脚本不调用 `systemctl start/stop/restart`，不修改 systemd、env、身份文件、日志或网络。

### 只读 SQL

新增 `deploy/internal/xxl-job-readonly-check.sql`，由运维使用专门的 MySQL 只读账号人工执行。SQL 只允许 `SELECT` 和 `SHOW`，检查：

- 数据库版本和 Flyway 已应用版本。
- JIT 用户数量、`platform_user_id`、用户名、session digest 长度和过期时间；不读取密码、token 或 digest 原文。
- `test-agent-backend` 执行器组配置。
- 七条 `platform_task_key`、Cron、启停、路由、阻塞、失败策略和重试次数；不输出完整 executor 参数。
- registry 地址及最近更新时间。
- 调度日志状态、时间和计数摘要，不输出可能包含参数的完整结果。

SQL 不包含 `INSERT/UPDATE/DELETE/REPLACE/MERGE/TRUNCATE/ALTER/CREATE/DROP/CALL`，也不获取锁或触发存储过程。静态边界还显式拒绝 `INTO OUTFILE`、`INTO DUMPFILE`、`GET_LOCK`、`RELEASE_LOCK`、`IS_FREE_LOCK`、`IS_USED_LOCK` 和用户变量赋值 `:=`。

## 输出、退出码与脱敏

三个 Shell 脚本使用统一前缀：

```text
[PASS] 正常
[WARN] 风险、非关键缺失或需要人工确认
[FAIL] 已定位的异常边界
[INFO] 脱敏上下文
```

统一退出码：

- `0`：关键检查全部通过；`WARN` 不一定改变退出码。
- `1`：至少一个关键检查失败或发现高风险不一致。
- `2`：用法错误、非法参数、执行机器不符或关键前提文件缺失。

脚本只写标准输出/标准错误。手册允许操作员用 `tee` 把输出保存为 `/data/0709/xxl-job-diagnostics-<节点>.log`，但脚本本身不自动创建、打包、传输或删除证据文件。

脱敏规则覆盖大小写变体的 `ticket`、`cookie`、`authorization`、`token`、`password`、`secret`、MySQL/Redis 凭据和 session digest。URL 输出去掉 query/hash；不启用 `set -x`；不输出完整 env、请求体、数据库任务参数或异常堆栈。只有固定生产强随机 XXL access token 的 SHA-256 前缀可用于节点一致性比较；其它凭据不输出长度或无盐摘要。

## 端到端决策规则

手册必须明确以下判断：

| 证据 | 故障边界 |
|---|---|
| 域名入口失败，`.2:9996` 成功 | 域名链路中的企业 DNS、入口/网关或 `9996 -> .2:80` 转发 |
| `.2` 入口失败，但 `.2` 直连两个 Admin 成功 | Nginx 有效配置、location 或 upstream |
| `.2` 只能访问一个 Admin | 单后台 Admin、防火墙或网络；可能间歇失败 |
| 后台 `8080` 正常、`18080` 失败 | Admin 子上下文或 XXL MySQL；平台 readiness 不能排除该故障 |
| `18080` 正常、`9999` 失败 | executor 链路，不是管理页面本身 |
| 签票 API `401/403/5xx` | 平台会话 / `SUPER_ADMIN` 权限 / Redis 或签票服务 |
| `/platform-sso/login` 为 `403/503/502` | 票据消费 / JIT 或 MySQL / Nginx upstream |
| SSO `200` 后立即失效 | Cookie、session marker，或 HTTP 入口拒收 `Secure` Cookie |
| HTTP 全部 `200`，15 秒后仍不可用 | 同源、CSP、iframe 脚本或 `postMessage` |
| 页面正常、执行器离线 | advertised host、`9999` 网络或共享 access token |
| executor 在线、任务不触发 | 任务启停/Cron、调度线程、registry 或调度日志 |
| 任务结果为 `SKIPPED_LOCK_HELD` | `GLOBAL_MUTEX` 正常互斥，不作为失败 |

脚本不模拟浏览器登录。SSO 被动证据顺序固定为：票据签发 POST → 登录 POST → iframe ready `postMessage` → Admin GET。全过程只检查事故时已经保留的证据，不得为补证主动刷新、重试或重放；任何截图或 HAR 在外传前必须删除票据、Cookie、Authorization 和请求体。

## 验证设计

新增 `tools/verify-internal-xxl-job-diagnostics.sh`，使用临时 fixture 和假命令环境验证脚本，不访问真实企业地址：

- 三个 Shell 脚本通过 `bash -n`。
- 入口健康、前端双 Admin 健康、后台健康路径返回 `0`。
- Admin 不可达、缺失 upstream、配置摘要不一致返回 `1`。
- 入口脚本在五个已知基础设施节点上均拒绝执行，地址检测工具缺失/失败也返回 `2` 且不调用 curl；后台错误机器、非法 `--expected-host`、超出 5～120 分钟范围返回 `2`。
- Nginx 要求全部只存在于注释或任一必须指令被注释时返回 `1`；合法活跃配置与附加 `server` 参数继续通过。
- fixture 中的密码、token、Cookie、ticket、Authorization 和 digest 不出现在输出。
- 普通 password/token/key 只输出 `SET/UNSET`；只有 XXL access token 的不同长度/SHA-256 前缀可被识别，原值均不回显。
- 三个现场脚本走到正常结束路径时都由 `finish()` 输出最终 PASS/FAIL 摘要；误用、错误机器和关键前提继续直接返回 `2`。
- 静态扫描确认脚本不含服务启停/reload、配置覆盖和数据库写命令。
- SQL 静态检查只允许注释、空行、`SELECT`、`SHOW` 及只读 CTE，不含 DML、DDL、`CALL` 或显式锁。

`docs/testing/xxl-job-integration.md` 增加验证命令：

```bash
bash tools/verify-internal-xxl-job-diagnostics.sh
```

实现完成后还运行 `git diff --check`、冲突标记扫描，并人工复核手册中的每条命令均为只读。

## 文档与工作区边界

实现只新增正式手册、三个 Shell 脚本、一个只读 SQL、一个验证脚本，并修改干净的 `docs/README.md` 与 `docs/testing/xxl-job-integration.md`。当前工作区的 `deploy/internal/README.md`、`SINGLE-BACKEND.md`、`MULTI-BACKEND.md` 及大量运行管理文件已有他人未提交改动，本任务不修改或暂存这些文件。

本次不修改 Java、前端运行时代码、HTTP API、RunEvent/SSE、数据库结构、Flyway、Redis 数据、安全协议、环境配置或部署脚本；不新建分支、不拉取、不 rebase、不推送。

## 验收标准

1. 现场人员可以按“浏览器网段 -> `.2` -> `.4` -> `.114` -> 外部 MySQL 只读证据 -> 浏览器 SSO”的固定顺序完成只读取证。
2. 每个脚本在错误机器上拒绝执行，在目标机器上输出统一、可判定且已脱敏的结果。
3. 平台健康、Admin 健康、executor 健康三层不会互相混淆。
4. 单节点 Admin 故障、Nginx upstream 缺失、共享配置不一致、HTTP `Secure` Cookie 风险和 SSO 状态码均有明确判断路径。
5. MySQL 检查只暴露必要元数据，不读取或输出密码、token、digest 原文和完整任务参数。
6. 所有脚本和 SQL 均无运行时写操作；验证脚本覆盖健康、故障、误用、脱敏和只读边界。
7. 正式手册、文档索引、测试说明和脚本行为一致，且本任务提交不包含工作区既有无关改动。

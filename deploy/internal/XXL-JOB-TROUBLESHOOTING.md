# 企业双后台 XXL-JOB 只读排查手册

## 1. 适用范围、固定拓扑与只读红线

本手册只适用于当前固定企业现场，不是通用部署教程：

| 层级 | 固定地址与端口 |
|---|---|
| 浏览器域名入口 | `http://mimo.sdc.cs.icbc:9996` |
| 浏览器 IP 入口、实体 Nginx | `http://122.233.30.2:9996`、`122.233.30.2` |
| 后台 A | `122.233.30.4:8080`，Admin `18080`，executor `9999` |
| 后台 B | `122.233.30.114:8080`，Admin `18080`，executor `9999` |
| 共享 Redis | `122.233.30.20:6379` |
| 共享 XXL MySQL | `122.233.30.148:3306/xxl_job` |

请求链路固定为“浏览器网段 → 企业入口 → `.2:9996` Nginx → `.4/.114:18080` Admin”；调度链路再由共享 MySQL 中的 Admin 调度到 `.4/.114:9999` executor。平台 `8080`、Admin `18080` 和 executor `9999` 是三个独立检查层，平台 readiness 正常不能证明 Admin 或 executor 正常。

本手册及配套脚本只读：不得启动、停止或重启服务，不得 reload 或生成 Nginx 配置，不得修改 env/身份文件，不得签发或消费真实 SSO 票据，不得触发任务，不得读写 Redis 业务数据，不得写 MySQL。诊断出版本或配置混用时，只记录 JAR、配置摘要和时间，转交部署负责人按现有部署文档处理；这里不复制发布、传输、替换或重启流程。

排查顺序固定如下。任一层出现 `[FAIL]` 或命令非零退出，先保存该层证据并停止向后推断；只有确认故障不在本层时才继续下一层。

1. 浏览器所在网段执行入口脚本。
2. `122.233.30.2` 执行前端 Nginx 脚本。
3. `122.233.30.4` 执行后台脚本。
4. `122.233.30.114` 执行后台脚本。
5. 核对 `.20` Redis 的 TCP 与两后台摘要边界。
6. 由 DBA 在 `.148` MySQL 执行只读 SQL。
7. 回到浏览器检查 SSO、Cookie、CSP 与 `postMessage`。
8. 最后判断 executor、任务调度或 `GLOBAL_MUTEX` 结果。

## 2. 五分钟现象速查表

| 现场现象 | 首要证据 | 优先边界 | 下一步 |
|---|---|---|---|
| 域名打不开，IP 入口正常 | 入口脚本中域名与 IP 的结果不同 | 企业 DNS、入口或端口转发 | 保存入口日志后交企业网络负责人 |
| 域名和 IP 都是 `502/504` | 入口脚本与 `.2` 脚本 | Nginx Admin upstream 或后台 Admin | 继续分别看 `.4/.114:18080` |
| “管理服务暂不可用” | Browser Network 的签票、登录、Admin 请求 | 只是统一前端兜底，不能直接定根因 | 按实际 HTTP 状态分层 |
| 平台页面正常，Admin 不可用 | 后台 `8080` PASS、`18080` FAIL | Admin 子上下文或 XXL MySQL | 保存相应后台日志并查 `.148` |
| Admin 偶发成功、偶发失败 | `.2` 对两个 Admin 的独立 readiness | 某一个 Admin、节点网络或配置不一致 | 两后台脚本与摘要逐项比对 |
| SSO `200` 后马上失效 | Application Cookie 与 Network | Cookie/session marker；HTTP 入口的 `Secure` Cookie 风险 | 只取证，不删除安全属性 |
| 全部 HTTP `200`，15 秒后仍失败 | Console、CSP/X-Frame-Options、iframe 消息 | 同源、iframe 脚本或 `postMessage` ready 握手 | 保存脱敏 Console 与响应头 |
| 页面正常但 executor 离线 | 后台 `9999`、registry 结果 | advertised host、端口网络或共享 access token | 比对后台摘要和 `.148` registry |
| executor 在线但任务不触发 | 任务启停/Cron、registry、近 7 日统计 | Admin 调度线程、任务配置或调度记录 | DBA SQL + 两后台日志 |
| 结果为 `SKIPPED_LOCK_HELD` | handler 结构化结果 | `GLOBAL_MUTEX` 正常互斥 | 不作为失败；核对另一次执行是否实际运行 |

## 3. 排查前记录项与脱敏规则

开始前记录：故障开始/结束时间（精确到秒、含时区）、实际访问入口、账号是否为平台 `SUPER_ADMIN`、浏览器名称与版本、本次失败是否已经保留被动证据、最近一次成功时间、相关任务 key、执行每条命令的机器和命令退出码。不要记录或要求他人回传真实密码、Token、Cookie、票据、Authorization、RSA 私钥、请求体或完整任务参数。

三个脚本使用统一前缀：`[PASS]` 为正常，`[WARN]` 为风险或人工确认项，`[FAIL]` 为异常边界，`[INFO]` 为脱敏上下文。退出码 0 表示关键检查通过，WARN 不一定改变退出码；退出码 1 表示至少一个关键检查失败或高风险不一致；退出码 2 表示用法错误、非法参数、执行机器不符或关键命令/文件缺失。退出码 2 先修正执行前提再重跑，不能解释成业务故障。

脚本会将 secret 归纳为 `SET/UNSET`、长度和 SHA-256 前 16 位，只用于两节点一致性比较。保存证据时不得启用 shell 调试输出，不得保存完整 env；URL query/hash、请求头和浏览器截图必须先脱敏。严禁外传未脱敏 HAR。

## 4. 浏览器网段入口（执行入口脚本）

**操作机器：实际浏览器网段内、已放置标准发布目录的 Linux 诊断终端。工作目录：`/data/testagent`。**

```bash
cd /data/testagent
set -o pipefail
bash /data/testagent/deploy/internal/diagnose-xxl-job-entry.sh \
  | tee /data/0709/xxl-job-diagnostics-entry.log
```

成功条件：域名解析到 `122.233.30.2`；域名入口与 `122.233.30.2:9996` 均返回 HTTP 200 且非空；两个同源 Admin readiness 都返回 HTTP 200/UP；命令退出码为 `0`。当前入口是 HTTP，因此出现 `Secure` Cookie 风险 WARN 是预期的人工检查提示。

失败停止点：域名失败而 IP 成功时停在企业 DNS/入口边界；两个入口都失败时先交企业入口/网络负责人；`404` 停在 Nginx location；`502/504` 继续到 `.2` 仅用于区分 upstream 节点，但不要先判断为 Java 平台故障。脚本不请求签票 API，也不提交 SSO 登录。

## 5. 122.233.30.2 前端 Nginx（执行前端脚本）

**操作机器：`122.233.30.2` 前端。工作目录：`/data/testagent`。**

```bash
cd /data/testagent
set -o pipefail
bash /data/testagent/deploy/internal/diagnose-xxl-job-frontend.sh \
  | tee /data/0709/xxl-job-diagnostics-frontend-122.233.30.2.log
```

成功条件：脚本确认本机为 `.2`；读取 `/data/testagent/config/nginx.env`；用 `/data/apps/nginx/sbin/nginx` 读取有效配置；有效配置包含 `test_agent_xxl_job_admin`、`.4:18080`、`.114:18080`、`location /xxl-job-admin/` 和对应 `proxy_pass`；从 `.2` 直连两个 Admin readiness 均为 HTTP 200/UP；退出码为 `0`。

失败停止点：退出码 `2` 表示机器、Nginx binary、env 或日志等关键前提不符，修正执行位置/交付前提后重跑；缺失 upstream/location 或任一 readiness 失败返回 `1`。只有一个 Admin 失败时先记录为单节点 Admin、节点网络或防火墙边界，这会造成负载均衡下的间歇故障。脚本只执行只读 `nginx -T` 解析并 dump 有效配置，同时读取最近 200 行相关日志；不 reload Nginx，也不写配置。

## 6. 122.233.30.4 后台（expected-host=.4）

**操作机器：`122.233.30.4` 后台。工作目录：`/data/testagent`。**

```bash
cd /data/testagent
set -o pipefail
bash /data/testagent/deploy/internal/diagnose-xxl-job-backend.sh \
  --expected-host 122.233.30.4 --minutes 15 \
  | tee /data/0709/xxl-job-diagnostics-backend-122.233.30.4.log
```

成功条件：本机地址、`TEST_AGENT_SERVER_ADVERTISED_HOST`、`.serverhost/.serverid` 一致；systemd 只运行固定 JAR 且读取固定 backend.env；`8080/18080/9999` 由同一 MainPID 监听；平台和 Admin readiness 都为 UP；`.20:6379`、`.148:3306` 及 `.114` 的 `8080/18080/9999` TCP 可达；固定端口和配置摘要通过；退出码为 `0`。

失败停止点：机器不匹配、参数非法、关键文件或命令缺失返回 `2`，不得改用另一 expected-host 绕过；`8080` 正常而 `18080` 失败时定位 Admin/MySQL；`18080` 正常而 `9999` 失败时定位 executor；systemd、身份、端口、共享拓扑或对端网络不一致返回 `1`。保存脱敏日志后再检查 `.114`，不要在现场重启服务或修改 env。

## 7. 122.233.30.114 后台（expected-host=.114）

**操作机器：`122.233.30.114` 后台。工作目录：`/data/testagent`。**

```bash
cd /data/testagent
set -o pipefail
bash /data/testagent/deploy/internal/diagnose-xxl-job-backend.sh \
  --expected-host 122.233.30.114 --minutes 15 \
  | tee /data/0709/xxl-job-diagnostics-backend-122.233.30.114.log
```

成功条件：本机地址为 `.114`，advertised host、`.serverhost/.serverid` 与 `.114` 身份一致；systemd 只运行固定 JAR 且读取固定 backend.env；`8080/18080/9999` 由同一 MainPID 监听；平台与 Admin readiness 均为 UP；`.20:6379`、`.148:3306` 及 `.4` 的 `8080/18080/9999` TCP 可达；固定端口和共享配置摘要通过；退出码为 `0`。

失败停止点：退出码 `2` 表示当前机器不是 `.114`、参数非法、关键文件或命令缺失，必须停止并修正执行前提，不得换用 `.4` expected-host 绕过。`8080` 正常而 `18080` 失败时，停止在 `.114` 的 Admin 子上下文或其到 XXL MySQL 的链路；`18080` 正常而 `9999` 失败时，停止在 `.114` executor；到 `.4` 的任一端口失败时交节点网络/防火墙；共享 Redis/MySQL 端点或摘要不符时交 `.114` 应用配置负责人。任何 `[FAIL]` 都先保存 `/data/0709/xxl-job-diagnostics-backend-122.233.30.114.log` 并停止该节点推断，不复用 `.4` 结论。`4096-4115` 是 opencode 用户进程端口池，不是管理页首要链路，本排查不操作 worker/manager/Docker。

## 8. 122.233.30.20 Redis 的人工只读边界

Redis 在此链路只由应用使用。现场人员不得读取任何票据、会话标记或业务键，不得在证据中暴露键名、值或原始 session 信息。人工仅接受两类证据：两个后台脚本给出的 `122.233.30.20:6379` TCP 可达结果，以及同一固定端点的应用摘要。

**操作机器：`122.233.30.4` 后台。工作目录：`/data/0709`。**

```bash
cd /data/0709
grep -E '^\[(PASS|INFO)\] (Redis |REDIS_ENDPOINT=)' \
  /data/0709/xxl-job-diagnostics-backend-122.233.30.4.log
```

**操作机器：`122.233.30.114` 后台。工作目录：`/data/0709`。**

```bash
cd /data/0709
grep -E '^\[(PASS|INFO)\] (Redis |REDIS_ENDPOINT=)' \
  /data/0709/xxl-job-diagnostics-backend-122.233.30.114.log
```

成功条件：两份证据都是 `REDIS_ENDPOINT=122.233.30.20:6379` 且 TCP PASS。失败停止点：任一节点不可达交网络/Redis 运维；端点摘要不一致交应用配置负责人。若签票或会话仍异常，使用应用/浏览器状态码继续分层，禁止通过直接读取 Redis 内部数据“验证”真实票据。

## 9. 122.233.30.148 MySQL（DBA 执行只读 SQL）

执行前由 DBA 确认 `/data/testagent/deploy/internal/xxl-job-readonly-check.sql` 来自本次受控发布，并使用专门的 MySQL 只读账号。密码必须交互输入，不得写在命令行、脚本、聊天或证据中。

**操作机器：`122.233.30.148` MySQL DBA 终端。工作目录：`/data/testagent`。**

```bash
cd /data/testagent
set -o pipefail
mysql --host=122.233.30.148 --port=3306 --user='<只读账号>' --password \
  --database=xxl_job \
  < /data/testagent/deploy/internal/xxl-job-readonly-check.sql \
  | tee /data/0709/xxl-job-diagnostics-mysql-122.233.30.148.log
```

成功条件：命令成功结束；Flyway 记录均成功；JIT 用户只显示平台 ID、用户名、角色、digest 长度和过期时间；`test-agent-backend` 组存在；任务元数据包含预期七个 `platform_task_key`；registry 能看到 `.4:9999` 与 `.114:9999` 的近期注册；近 7 日调度统计和两个索引结果可读。SQL 不输出 password、token、digest 原文、`executor_param`、`trigger_msg` 或 `handle_msg`。

失败停止点：连接/权限失败交 DBA；表或 Flyway 版本缺失、migration `success` 异常停止业务推断并交发布/数据库负责人；只有一个 registry 地址时回到对应后台 `9999`、advertised host、网络和共享 access token；任务缺失、停用或 Cron 异常交 XXL 配置负责人。不要用写 SQL 现场修复。

## 10. 浏览器 SSO Network/Console/Cookie/CSP/postMessage

**操作机器：发生故障的用户浏览器。证据来源：本次事故/当前失败尝试已经保留的 DevTools Network、Console 与 Application 状态。** 只允许点击已存在的请求行、Headers/Cookies 面板、已有 Console 行和当前 Cookie 记录；这些是被动 UI 查看动作。禁止为了诊断主动刷新、重试、重放或重新进入页面，也不得点击“Replay/Resend”。如果 Network 未保留本次失败请求，立即停止并升级，不得为补证再次发起 SSO。不得导出未脱敏 HAR。

按如下顺序核对本次事故已经保留的被动证据：

1. 已保留的 Network：`POST /api/internal/platform/xxl-job/sso-tickets`。
2. 已保留的 Network：`POST /xxl-job-admin/platform-sso/login` 完成响应。
3. Network 之外的被动证据：父页面已显示 connected/ready，或已有 instrumentation 记录同源 ready `postMessage`。
4. ready 之后的已保留 Network：重定向及 Admin `GET /xxl-job-admin/`、静态资源。

DevTools Network 不会记录 `postMessage`；第 3 步只能使用事故时已观察到的父页面状态或已存在的 instrumentation，不得为捕获消息新增代码或重放流程。如果这两类 ready 证据都未保留，立即停止并升级，不得重放。只记录路径、方法、状态码、时间与安全响应头；不得打开或复制请求体、票据、Cookie、Authorization 或响应中的敏感字段。

签票请求 `401` 表示平台会话无效，`403` 表示不是 `SUPER_ADMIN` 或权限边界拒绝，`5xx` 指向签票服务/Redis；iframe 登录 `403` 指向票据消费失败，`503` 指向 JIT/MySQL/Admin，`502/504` 指向 Nginx upstream。只从已保留的请求读取这些状态，不用任何 HTTP 客户端补发请求。

Application/Cookie 只查看当前是否已落 Cookie 及属性：Path 应为 `/xxl-job-admin/`，并检查 `HttpOnly`、`Secure`、`SameSite=Lax`。当前普通 HTTP 入口可能被浏览器拒收带 `Secure` 属性的 Admin Cookie，表现为已保留证据中登录 POST 成功后立即失效；这是需要升级的入口安全兼容风险，不得以删除 `Secure`、放宽 Cookie、改用不安全脚本或关闭浏览器安全策略作为现场修复。

在已保留的 Admin 文档响应头中核对 `Content-Security-Policy: frame-ancestors 'self'` 与 `X-Frame-Options: SAMEORIGIN`。Console 只查看已存在的同源、CSP、frame、Cookie 和资源加载错误，不粘贴带凭据的对象。普通 iframe `load` 不等于登录成功，平台只在同源 iframe 收到登录成功页的 `postMessage` ready 握手后进入就绪态；已保留证据显示 HTTP 全部 200 但约 15 秒后仍不可用时，边界是 CSP、实际 iframe origin、脚本/静态资源或 ready 消息。

成功条件：已保留证据完整呈现上述顺序；前两个 POST 成功；父页面或已有 instrumentation 证实同源 ready；此后的重定向、Admin GET 与静态资源成功；Cookie 已被接受且四个属性完整；CSP/X-Frame-Options 保持同源；Console 无相关错误。失败停止点：任一状态码或安全属性不符就停在对应边界并升级；ready 或 Network 证据序列不完整同样停止，不主动补发，也不继续猜测任务调度。

## 11. executor 在线、任务不触发与 SKIPPED_LOCK_HELD

只检查第 6、7、9 节已经收集的证据，禁止手动触发任务、修改任务策略、清锁或执行额外 SQL。

**操作机器：`122.233.30.4` 后台。证据目录：`/data/0709`。**

```bash
cd /data/0709
grep -Ei 'XXL executor 端口 9999|ExecutorRegistryThread|registry error|Connection refused|SKIPPED_LOCK_HELD|schedule|trigger|handle' \
  /data/0709/xxl-job-diagnostics-backend-122.233.30.4.log
```

成功证据：存在 `.4` 的 executor `9999` 由 MainPID 监听的 PASS，且注册/调度相关行没有 `registry error` 或 `Connection refused`。停止点：grep 无输出、端口 FAIL 或出现连接/注册错误时，停止并把该绝对路径交 `.4` executor/网络负责人。

**操作机器：`122.233.30.114` 后台。证据目录：`/data/0709`。**

```bash
cd /data/0709
grep -Ei 'XXL executor 端口 9999|ExecutorRegistryThread|registry error|Connection refused|SKIPPED_LOCK_HELD|schedule|trigger|handle' \
  /data/0709/xxl-job-diagnostics-backend-122.233.30.114.log
```

成功证据：存在 `.114` 的 executor `9999` 由 MainPID 监听的 PASS，且注册/调度相关行没有错误。停止点：grep 无输出、端口 FAIL 或出现连接/注册错误时，停止并把该绝对路径交 `.114` executor/网络负责人；不得用 `.4` 结果代替。

**操作机器：`122.233.30.148` MySQL DBA 终端。证据目录：`/data/0709`。**

```bash
cd /data/0709
grep -E '^(platform_task_key|opencode-runtime\.|scheduler\.|registry_group|EXECUTOR[[:space:]]|trigger_day)' \
  /data/0709/xxl-job-diagnostics-mysql-122.233.30.148.log
```

成功证据：`test-agent-backend` registry 包含 `.4:9999` 和 `.114:9999` 的近期行；预期七个 task key 都有只读元数据；近 7 日统计可判断 trigger/handle 状态。停止点：任一 registry 节点或任务元数据缺失时停止并交 Admin/任务配置负责人；有 trigger 无 handle 时交 executor 网络/access token；handle 失败时按脱敏 task key 交业务 handler 负责人，不执行任务验证。

`GLOBAL_MUTEX` 未取得锁时会正常结束并记录 `SKIPPED_LOCK_HELD`，表示另一节点或另一轮已持锁，是防重正常结果，不作为 executor 失败。只在上述已收集日志和 SQL 摘要中核对相邻记录，不能为补证触发任务。

## 12. 多 Admin 间歇故障与共享配置摘要比对

两台 Admin 共享同一 XXL MySQL，Nginx 在两者之间转发。单节点不健康、网络不通或共享配置不一致都会表现为间歇性页面/SSO/executor 故障。只比较脚本已脱敏的字段，禁止直接汇总完整 env。

**操作机器：`122.233.30.4` 后台。工作目录：`/data/0709`。**

```bash
cd /data/0709
grep -E '^\[INFO\] (REDIS_ENDPOINT|XXL_MYSQL_ENDPOINT|TEST_AGENT_XXL_JOB_ACCESS_TOKEN|ADMIN_PORT|EXECUTOR_PORT)=' \
  /data/0709/xxl-job-diagnostics-backend-122.233.30.4.log
```

**操作机器：`122.233.30.114` 后台。工作目录：`/data/0709`。**

```bash
cd /data/0709
grep -E '^\[INFO\] (REDIS_ENDPOINT|XXL_MYSQL_ENDPOINT|TEST_AGENT_XXL_JOB_ACCESS_TOKEN|ADMIN_PORT|EXECUTOR_PORT)=' \
  /data/0709/xxl-job-diagnostics-backend-122.233.30.114.log
```

成功条件：Redis 为 `.20:6379`、MySQL 为 `.148:3306/xxl_job`、access token 的 SET/UNSET 状态、长度和摘要完全相同，Admin 为 `18080`，executor 为 `9999`；advertised host 则应分别为 `.4` 和 `.114`，不要求相同。摘要不一致或单节点 readiness/registry 异常时停止并把两份脱敏证据交应用配置负责人；不得回传原始 token 进行人工比对。

## 13. HTTP 状态码、日志关键字、责任边界决策树

```text
域名失败、IP 成功
└─ 企业 DNS / 入口 / 9996 转发

域名和 IP 都失败
└─ .2 脚本
   ├─ 有效 location/upstream 缺失 → Nginx 配置责任方
   ├─ 仅 .4 或仅 .114 readiness 失败 → 对应后台/节点网络
   └─ 两个 Admin 都失败 → 分别执行两个后台脚本
      ├─ 8080 正常、18080 失败 → Admin 子上下文 / XXL MySQL
      ├─ 18080 正常、9999 失败 → executor
      └─ 共享端点或摘要不一致 → 应用配置责任方

入口、Nginx、Admin 均正常
└─ 浏览器 SSO
   ├─ 签票 401/403 → 平台会话 / SUPER_ADMIN 权限
   ├─ 签票 5xx → 平台签票服务 / Redis
   ├─ 登录 403 → 票据消费
   ├─ 登录 503 → JIT / MySQL / Admin
   ├─ 登录 502/504 → Nginx upstream
   ├─ 200 后立即失效 → Cookie / session marker / HTTP + Secure 风险
   └─ 全 200、15 秒超时 → 同源 / CSP / iframe / postMessage

管理页正常、调度异常
└─ MySQL 只读结果 + 后台日志
   ├─ registry 缺节点 → advertised host / 9999 / 网络 / access token
   ├─ executor 在线无 trigger → 任务启停/Cron/Admin 调度
   ├─ trigger 有、handle 无 → executor 链路
   ├─ handle 失败 → 业务 handler
   └─ SKIPPED_LOCK_HELD → GLOBAL_MUTEX 正常互斥
```

常见 HTTP：`404` 看 location/路径，`502/504` 看 upstream/后台网络，`503` 看 Admin/MySQL/JIT，`401/403` 按签票或登录阶段判断，`200` 仍需检查响应类型、Cookie 和 ready 握手。常见日志关键字：`Flyway`、`Hikari`、`MySQL`、`Admin readiness`、`ExecutorRegistryThread`、`registry error`、`Connection refused`、`schedule`、`trigger`、`handle`、`SKIPPED_LOCK_HELD`。只使用脚本输出的脱敏片段。

## 14. 证据保存、脱敏复核与升级模板

四份 Shell 证据与一份 DBA 证据固定保存在 `/data/0709/`：入口、`.2`、`.4`、`.114` 和 `.148`。脚本自身不会创建、打包、传输或删除证据；上面的 `tee` 才负责保存。外传前逐份搜索并人工复核 ticket、Cookie、Authorization、token、password、secret、URL query/hash、session digest、请求体和任务参数，任何命中都先脱敏。浏览器只提供裁剪后的截图、响应状态/安全头和脱敏 Console 文本，不提供原始 HAR。

**操作机器：持有五份脱敏日志的受控取证终端。证据目录：`/data/0709`。** 以下扫描只以退出状态报告结果，不打印疑似敏感行：

```bash
cd /data/0709
if awk '
  {
    line=tolower($0)
    gsub(/(ticket|cookie|token|password|secret|authorization|platform_session_digest)[[:alnum:]_"'\''-]*[[:space:]]*[=:][[:space:]]*["'\'']*\[redacted(_query|_fragment)?\]["'\'']*/, "", line)
    gsub(/[[:alnum:]_]*(token|password|secret|digest)[[:alnum:]_]*=(set length=[0-9]+ sha256=[0-9a-f]+|unset)/, "", line)
    gsub(/\?\[redacted_query\]|#\[redacted_fragment\]/, "", line)
    if (line ~ /authorization:[[:space:]]*bearer/) leaked=1
    if (line ~ /(ticket|cookie|token|password|secret|authorization|platform_session_digest)[[:alnum:]_"'\''-]*[[:space:]]*[=:][[:space:]]*["'\'']*[^,;[:space:]"'\''}]/) leaked=1
    if (line ~ /[^[:space:]?#][?#][^[:space:]]+/ || line ~ /(^|[[:space:]])[?#][^[:space:]]+/) leaked=1
  }
  END { exit leaked ? 1 : 0 }
' \
  /data/0709/xxl-job-diagnostics-entry.log \
  /data/0709/xxl-job-diagnostics-frontend-122.233.30.2.log \
  /data/0709/xxl-job-diagnostics-backend-122.233.30.4.log \
  /data/0709/xxl-job-diagnostics-backend-122.233.30.114.log \
  /data/0709/xxl-job-diagnostics-mysql-122.233.30.148.log \
  >/dev/null; then
  printf '[PASS] 五份证据未命中疑似敏感值\n'
else
  printf '[STOP] 证据疑似仍含敏感值；禁止显示命中内容，停止外传并交安全负责人复核\n' >&2
  exit 1
fi
```

成功条件：只输出 `[PASS]` 且退出 `0`，再由人工检查浏览器截图/Console 已裁剪且没有请求体、Cookie、票据或 Authorization。失败停止点：出现 `[STOP]` 或退出非零时不得用 grep/编辑器打印命中行，不得打包或外传，直接交安全负责人在受控终端复核。

升级单使用以下模板：

```text
故障时间（含时区）：
实际入口：域名 / IP（不含 query）
账号角色：SUPER_ADMIN / 非 SUPER_ADMIN（不写账号凭据）
浏览器与版本：
本次失败被动证据已保留：是 / 否；最近成功时间：
入口脚本退出码与首个 FAIL：
.2 脚本退出码与首个 FAIL：
.4 脚本退出码与首个 FAIL：
.114 脚本退出码与首个 FAIL：
MySQL 只读检查结论：
浏览器请求阶段、HTTP 状态与 Cookie/CSP/postMessage 结论：
任务 key 与脱敏调度结论：
已完成脱敏复核：是 / 否
建议责任边界：企业网络 / Nginx / 平台会话权限 / Redis / Admin-MySQL / executor 网络 / 任务配置 / handler
```

## 15. 固定命令与有效/废弃变量附录

固定只读入口如下：

| 用途 | 文件/命令 |
|---|---|
| 浏览器网段入口 | `/data/testagent/deploy/internal/diagnose-xxl-job-entry.sh`，不接受参数 |
| `.2` Nginx | `/data/testagent/deploy/internal/diagnose-xxl-job-frontend.sh`，不接受参数 |
| `.4` 后台 | `/data/testagent/deploy/internal/diagnose-xxl-job-backend.sh --expected-host 122.233.30.4 --minutes 15` |
| `.114` 后台 | `/data/testagent/deploy/internal/diagnose-xxl-job-backend.sh --expected-host 122.233.30.114 --minutes 15` |
| `.148` DBA SQL | `/data/testagent/deploy/internal/xxl-job-readonly-check.sql`，用只读账号和交互式密码执行 |

`TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080` 只在 `122.233.30.2` 的 `/data/testagent/config/nginx.env` 中有效，用于渲染中央 Nginx Admin upstream；它不是 Java 环境变量。

以下三个 Java 地址变量已经删除，任何节点都不得恢复或配置：`TEST_AGENT_XXL_JOB_ADMIN_ADDRESSES`、`TEST_AGENT_XXL_JOB_EXECUTOR_ADDRESS`、`TEST_AGENT_XXL_JOB_EXECUTOR_IP`。Java 内部 Admin 地址和 executor 注册地址由当前公共程序按本机身份派生；排查只核对派生结果，不以废弃变量现场覆盖。

本手册到此只完成诊断与责任分层。需要实际部署、升级、替换配置或重启时，停止本执行单并转企业内部署的权威文档和变更流程。

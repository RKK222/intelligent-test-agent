# XXL-JOB 集成架构

本文档是平台周期任务迁移至 XXL-JOB 后的架构单一事实源。HTTP 契约见 `docs/api/http-api.md`，数据库与部署细节分别见 `docs/deployment/database.md`、`docs/deployment/backend.md` 和 `docs/deployment/frontend.md`。

## 版本与许可证

- 固定上游版本：XXL-JOB `3.4.2`。
- 固定上游 commit：`c2bbb46c9a3af8e2a69246728a452c606240b80e`。
- 许可证：GPL-3.0；完整文本随源码模块和离线发布包交付。
- `test-agent-xxl-job-admin-upstream` 原样保存上游 Admin Java 源码与资源，禁止直接打平台补丁。
- `test-agent-xxl-job-integration` 承载全部平台适配。升级时整体替换上游目录，再只调整 integration 模块，降低后续合并成本。

## 进程与上下文

每个 Java 进程仍只交付并启动一个平台应用 JAR，但进程内包含三个互相隔离的运行部分：

```text
同一 Java 进程
├── 平台主上下文：Spring WebFlux / Netty
├── XXL Admin 子上下文：Spring MVC / Tomcat / 独立端口
└── XXL executor：test-agent-backend / 独立端口
```

- `TestAgentApplication` 强制使用 `WebApplicationType.REACTIVE`，避免引入 Servlet 依赖后把平台主上下文切换为 MVC。
- Admin 使用 `WebApplicationType.SERVLET` 在独立子上下文启动，隔离 Tomcat、MySQL DataSource、MyBatis、Flyway 和安全自动配置。
- 上游源码目录保留原始 `application.properties`，但依赖 JAR 将它重定位到 `META-INF/xxl-job-admin-upstream/`。Admin launcher 只向 Servlet 子上下文加载这份低优先级默认配置，再用平台 MySQL、端口、access token、SSO 和 Flyway 配置覆盖；WebFlux 主上下文不会加载其中的 Hikari/MySQL 默认值。
- Admin 启动失败不会关闭平台主上下文。生命周期组件按 5、10、20、40、60 秒上限指数退避重试。
- executor 覆盖上游 `SmartInitializingSingleton` 自动启动入口，Spring 单例初始化阶段不创建监听端口或注册线程。独立 daemon 生命周期在 Admin 生命周期之后启动，以 250 毫秒～5 秒退避探测同 JVM 本机 Admin 的 `/actuator/health/readiness`；返回 HTTP 200 后才启动 executor，且同一 Java 进程最多启动一次。
- 本机 Admin 未就绪时 executor 端口保持关闭并持续等待，平台 Netty/8080、主 readiness 和其它业务不受影响；Admin/MySQL 恢复后无需重启平台进程即可启动 executor。该门控不使用固定 sleep，也不新增部署配置。
- `xxlJobAdmin` health component 在 Admin/MySQL 不可用时返回 `DOWN`，但平台 readiness group 只包含平台必需依赖，不包含 XXL health。
- 每台 Linux 只运行一个 Java，Admin 与 executor 固定本机配对。Admin 地址始终派生为 loopback；executor 注册地址从 `BackendInstanceIdentity.listenUrl()` 提取 advertised host，并拼接 executor 端口。平台未配置 `TEST_AGENT_SERVER_ADVERTISED_HOST` 时，该监听地址沿用 `LinuxServerIpResolver` 的内网 IPv4 自动探测。
- 所有 Admin 共享同一个 XXL MySQL，因此每个 Admin 都能看到全部 executor 注册；注册地址不携带 `linuxServerId`，调度不引入 Linux 服务器亲和。
- 生产入口通过 Nginx 对多个 Admin 子端口做同源代理。Java 内部地址派生与 Nginx 的 `TEST_AGENT_NGINX_XXL_JOB_ADMINS` 是两套边界：新增节点只需启动新 Java、将其普通 backend/Admin 地址加入中央 Nginx upstream 并无停机 reload，不需要修改或重启旧 Java。

## 认证与账号

管理页不使用 XXL 原生用户名密码登录：

1. 只有平台 `SUPER_ADMIN` 可以调用 `POST /api/internal/platform/xxl-job/sso-tickets`。
2. 后端生成 32 字节安全随机数，编码为 43 字符 URL-safe 票据；票据最长 60 秒有效，并受平台 Token 剩余有效期约束。
3. Redis 只保存一次性票据载荷，消费使用一段 `GET` 后立即 `DEL` 的原子 Lua 脚本。该实现兼容 Redis 5、Redis 6.0 及更新版本，不依赖 Redis 6.2 才增加的 `GETDEL`；Redis ACL 必须允许应用执行 `EVAL`。载荷保存平台用户 ID、显示名、平台 session digest 和过期时间，不保存原始 Token。
4. 前端用隐藏表单把票据 `POST` 到同源 `/xxl-job-admin/platform-sso/login` iframe；票据不进入 URL、浏览器历史或访问日志。
5. 首次登录按 `platform_user_id` JIT upsert XXL 管理员账号。显示名冲突时追加平台用户 ID SHA-256 的稳定 8 位短 hash；平台改名会同步更新 XXL 显示账号。
6. XXL Cookie 会话每次请求都通过自定义 `LoginStore` 校验平台 SHA-256 session marker。平台登出、刷新 Token 或 Token 过期后 marker 失效，XXL 会话同步失效。

不初始化 XXL 默认管理员。原生登录、改密及账号新增/修改/删除入口由 integration Filter 禁止，账号列表保持只读可查看。平台删除账号后不删除 XXL 行，保留历史审计；没有有效平台会话 marker 时无法继续访问。

Admin 响应固定设置 `Content-Security-Policy: frame-ancestors 'self'` 和 `X-Frame-Options: SAMEORIGIN`；会话 Cookie 固定使用 `HttpOnly`、`SameSite=Lax` 和 `/xxl-job-admin/` Path，`Secure` 默认开启。只有受控内网入口明确无法提供 HTTPS 时，部署配置才可显式设置 `TEST_AGENT_XXL_JOB_COOKIE_SECURE=false`，入口升级 HTTPS 后必须恢复 `true`。票据消费、JIT 或 XXL 登录的运行时异常统一返回 integration 维护的 `503 unavailable` 状态页，避免上游通用错误页在嵌入态访问不存在的父窗口 AdminLTE 对象。

## 嵌入页面边界

- 上游模块完整保留 XXL-JOB 3.4.2 引用的 AdminLTE `dist` CSS/JS，根忽略规则对该目录设置精确例外；平台不修改这些上游文件。
- integration 只提供作用域为 `test-agent-xxl-embedded` 的平台样式资源。前端在同源 iframe 确认为 XXL shell 后添加根 class 和样式链接，把左侧菜单转为紧凑横向导航，并把 JIT 映射账号收敛为只读文本。
- 装饰器不把普通 iframe `load` 当作 SSO 成功，不处理登录中转、错误或不可访问文档；直接访问 Admin 没有根 class，继续使用上游原生纵向布局。任务页面、原生页签和 SSO/会话失效协议不变。

## 数据库边界

平台 PostgreSQL 与 XXL MySQL 完全分离：

- 平台 Flyway 仍扫描 `classpath:db/migration`，不扫描 XXL migration。
- Admin 子上下文只连接 MySQL，并扫描独立顶层 location `classpath:xxl-job/db/migration`；平台 PostgreSQL 仍只扫描 `classpath:db/migration`。
- `V1` 是 XXL-JOB 3.4.2 基础表，不写示例任务或默认管理员。
- `V2` 增加平台用户/session 字段、`platform_task_key` 唯一键并扩展用户名长度。
- `V3` 新增自动注册执行器组 `test-agent-backend` 和首批六个周期任务。
- `V4` 注册夜间数据库分发任务 `opencode-runtime.night-execution-dispatch`。
- 后续新增任务必须新建 `V5`、`V6` 等版本 SQL，按 `platform_task_key` 插入；禁止启动时 upsert/覆盖页面已调整的启停或 Cron。

旧 PostgreSQL 的任务定义和运行历史只做保留，不复制到 MySQL，也不再被 runner 调度。短暂停机升级 migration 将旧夜间 `PENDING/RUNNING/STOPPING USER_PLAN` 全部标记为 `SKIPPED`，避免 runner 删除后残留永久活动记录；不删除历史审计，新夜间任务只写 `night_execution_tasks`。

## 执行器与任务契约

所有 Java 进程注册同一个自动注册地址执行器组 `test-agent-backend`。executor 地址由平台 advertised host 与 executor 端口自动生成，必须能被所有 Admin 节点访问；注册和路由不携带 `linuxServerId`，不与稳定 Linux 服务器亲和。

所有平台周期任务使用统一 handler `testAgentScheduledTaskHandler`，参数固定为：

```json
{
  "taskKey": "opencode-runtime.analytics-rollup",
  "concurrencyPolicy": "GLOBAL_MUTEX",
  "payload": {}
}
```

- `GLOBAL_MUTEX` 复用旧 scheduler 的 Redis key `test-agent:scheduler:lock:{taskKey}`，并按锁 TTL 的三分之一续租。滚动发布期间新旧入口最多只有一个真正执行。
- 未取得锁时 XXL 任务正常结束，并在结构化结果中记录 `SKIPPED_LOCK_HELD`。
- `ALLOW_OVERLAP` 不申请全局锁；结合 XXL `ROUND` 路由可以在不同 Java 节点并发。单个 Java 内由 XXL `DISCARD_LATER` 保持同一 handler 串行。
- 未知任务、非法参数或策略、锁续租丢失、handler 异常均明确失败，错误响应不包含原始参数、Token、Cookie 或数据库凭据。
- XXL 停止请求通过线程中断传给 `ScheduledTaskContext.stopRequested()`；长任务仍须在安全检查点主动检查停止状态。

首批任务统一使用 `ROUND + DISCARD_LATER + DO_NOTHING + retry=0`：

| 任务 key | XXL cron | Redis 锁 TTL |
|---|---|---|
| `opencode-runtime.analytics-rollup` | `0 0/5 * * * ? *` | 5 分钟 |
| `opencode-runtime.night-execution-dispatch` | `0 0/15 * * * ? *` | 15 分钟 |
| `opencode-runtime.night-execution-reconcile` | `0 0/5 * * * ? *` | 5 分钟 |
| `opencode-runtime.stale-active-run-reconcile` | `0 0/5 * * * ? *` | 5 分钟 |
| `opencode-runtime.terminal-projection-retry` | `0/5 * * * * ? *` | 30 秒 |
| `opencode-runtime.side-question-orphan-cleanup` | `0 0/5 * * * ? *` | 5 分钟 |
| `scheduler.run-retention-cleanup` | `0 0 8 * * ? *` | 5 分钟 |

JVM 和 XXL 统一使用 `Asia/Shanghai`。运行记录清理在北京时间 08:00 执行，对应原 UTC 00:00；XXL 日志保留 30 天，旧 PostgreSQL scheduler 历史仍由任务清理 7 天。

## 夜间分发与旧 scheduler 边界

应用内已移除 `ScheduledTaskRunner`、`ScheduledUserPlanService`、亲和 provider 和对应运行配置。`test-agent-scheduler` 只保留 handler/context/result、Redis 全局锁与历史运行清理；`ScheduledTaskRegistry` 只维护内存 handler 映射，不同步 PostgreSQL 定义。

`opencode-runtime.night-execution-dispatch` 每 15 分钟按 `slot_start, created_at` 扫描最多 500 条 `status=SCHEDULED AND slot_start<=now AND window_end>now` 的任务，按提交时固化的 `target_linux_server_id` 分组，每批最多 50 条、同时最多调用 8 台服务器。同一目标服务器的批次串行，单服务器或单任务失败只进入本轮统计并留待下轮；数据库扫描等全局故障才使 XXL 本轮失败。任务不自动顺延，最晚只重试到北京时间 07:00。

executor 的 XXL 注册和 ROUND 路由仍不携带 Linux 亲和；取得全局扫描锁的任意 Java 在业务层复用 `BackendJavaRouteResolver` 和 `BackendHttpForwarder`，先选出目标服务器上的精确 backendProcessId，再把目标服务器与任务 ID 交给该 Java；同服务器多 JVM 不得按 linuxServerId 直接本机短路。内部请求不携带 prompt、附件或用户信息。目标 Java 以 attemptId、精确 backendProcessId 和 5 分钟租约认领后，只调用普通 `startScheduledRun`；批次最多并发 4 个同步受理调用，不等待 Run 完成，也没有夜间专属队列。

普通 Run 锚点受理后夜间任务进入 `DISPATCHED` 并释放输入、时段容量和会话锁。`DISPATCHED` 只表示已交给 Run；最终成功、失败或取消仍由现有会话、Run 状态和 RunEvent SSE 展示。默认 `LEGACY_FULL` Scheduled Run 也先把稳定 clientRequestId 写入 `runs` 唯一锚点，并用 Run 级 attempt、5 分钟租约和 handoff 受理时间封住“锚点已写但 prompt 未交付”的崩溃窗口：未受理锚点只能由有效 attempt CAS 认领并复用原 runId/messageId；恢复重投前按 remoteSessionId + dispatchMessageId 探测远端，ACCEPTED 只补 handoff 标记，NOT_ACCEPTED 才发送，UNKNOWN 不发送，避免 prompt 已受理但本地标记未落库时重复进入执行 loop。恢复时必须校验来源类型、taskId、owner、Session 和 Workspace，客户端复用历史 ID 不得命中旧 Run。5 分钟补偿先查已受理锚点，再检查任务租约和精确 owner Java 心跳；owner 仍在线时不跨进程抢占，由 owner 本机每分钟 watchdog 在 in-flight handle 消失后先查锚点再恢复。Run 创建副作用前还要以 attempt 和 `window_end > now` 最终续租 CAS；达到 07:00 时仍在本机 handle/在线 owner 保护中的调用先等待收敛，避免先写 `FAILED` 后旧调用又创建 Run。所有续租、完成和恢复均匹配 attemptId，通用 stale legacy Run 扫描排除新协议下尚未 handoff 的 Scheduled 锚点。传输语义为至少一次，数据库/Redis 唯一锚点与远端接收探测保证同一夜间任务只创建一个 Run，恢复时不重复执行。

旧 `/api/internal/platform/scheduler-management/**` 统一返回 `410 API_GONE`。周期任务的启停、Cron 修改、手动触发、停止和日志查看均在嵌入的 XXL 页面完成；不新增 RunEvent/SSE 事件。

## 本地与生产配置

- 本地 `deploy/local/docker-compose.yml` 提供 MySQL 8.4、`xxl_job` 库、健康检查和持久卷，默认主机端口 `13306`；表和首批任务由 Admin 子上下文 Flyway 初始化。
- 只通过 `.env.local.example` 提供示例，禁止自动修改开发者的 `.env.local`。
- 本地 Vite 和生产 Nginx 都把 `/xxl-job-admin/` 代理到 Admin 子端口，保持 iframe 同源。
- 生产使用外部共享 MySQL。夜间迁移采用短暂停机：先停止全部旧 Java，再执行 PostgreSQL/XXL MySQL migration 并启动新版本，避免旧 `USER_PLAN` 与 XXL 分发并行。随后检查 `.serverid/.serverhost`、派生 executor 地址、15 分钟分发和 5 分钟补偿均启用，确认没有旧 runner 线程；开放 Admin 到 executor 网络并更新/reload Nginx。
- 离线包仍只有一个平台应用 JAR，同时包含上游源码、LICENSE、UPSTREAM 元数据、版本文件和配置示例。

完整变量和网络要求见 `docs/deployment/backend.md`，验证清单见 `docs/testing/xxl-job-integration.md`。

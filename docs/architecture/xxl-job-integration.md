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
- executor 覆盖上游 `SmartInitializingSingleton` 自动启动入口，Spring 单例初始化阶段不创建监听端口或注册线程。独立 daemon 生命周期在 Admin 生命周期之后启动，以 250 毫秒～5 秒退避探测配置列表中各 Admin 的 `/actuator/health/readiness`；任意一个返回 HTTP 200 后才启动 executor，且同一 Java 进程最多启动一次。
- 全部 Admin 未就绪时 executor 端口保持关闭并持续等待，平台 Netty/8080、主 readiness 和其它业务不受影响；Admin/MySQL 恢复后无需重启平台进程即可启动 executor。该门控不使用固定 sleep，也不新增部署配置。
- `xxlJobAdmin` health component 在 Admin/MySQL 不可用时返回 `DOWN`，但平台 readiness group 只包含平台必需依赖，不包含 XXL health。
- 同机多 Java 进程必须配置不同的 Admin 端口和 executor 端口。生产入口通过 Nginx 对多个 Admin 子端口做同源代理。

## 认证与账号

管理页不使用 XXL 原生用户名密码登录：

1. 只有平台 `SUPER_ADMIN` 可以调用 `POST /api/internal/platform/xxl-job/sso-tickets`。
2. 后端生成 32 字节安全随机数，编码为 43 字符 URL-safe 票据；票据最长 60 秒有效，并受平台 Token 剩余有效期约束。
3. Redis 只保存一次性票据载荷，消费使用原子 `GETDEL`。载荷保存平台用户 ID、显示名、平台 session digest 和过期时间，不保存原始 Token。
4. 前端用隐藏表单把票据 `POST` 到同源 `/xxl-job-admin/platform-sso/login` iframe；票据不进入 URL、浏览器历史或访问日志。
5. 首次登录按 `platform_user_id` JIT upsert XXL 管理员账号。显示名冲突时追加平台用户 ID SHA-256 的稳定 8 位短 hash；平台改名会同步更新 XXL 显示账号。
6. XXL Cookie 会话每次请求都通过自定义 `LoginStore` 校验平台 SHA-256 session marker。平台登出、刷新 Token 或 Token 过期后 marker 失效，XXL 会话同步失效。

不初始化 XXL 默认管理员。原生登录、改密及账号新增/修改/删除入口由 integration Filter 禁止，账号列表保持只读可查看。平台删除账号后不删除 XXL 行，保留历史审计；没有有效平台会话 marker 时无法继续访问。

Admin 响应固定设置 `Content-Security-Policy: frame-ancestors 'self'` 和 `X-Frame-Options: SAMEORIGIN`；会话 Cookie 使用 `HttpOnly`、`Secure`、`SameSite=Lax` 和 `/xxl-job-admin/` Path。

## 数据库边界

平台 PostgreSQL 与 XXL MySQL 完全分离：

- 平台 Flyway 仍扫描 `classpath:db/migration`，不扫描 XXL migration。
- Admin 子上下文只连接 MySQL，并扫描独立顶层 location `classpath:xxl-job/db/migration`；平台 PostgreSQL 仍只扫描 `classpath:db/migration`。
- `V1` 是 XXL-JOB 3.4.2 基础表，不写示例任务或默认管理员。
- `V2` 增加平台用户/session 字段、`platform_task_key` 唯一键并扩展用户名长度。
- `V3` 新增自动注册执行器组 `test-agent-backend` 和首批六个周期任务。
- 后续新增任务必须新建 `V4`、`V5` 等版本 SQL，按 `platform_task_key` 插入；禁止启动时 upsert/覆盖页面已调整的启停或 Cron。

旧 PostgreSQL 的周期任务定义和运行历史只做保留，不复制到 MySQL，也不再被旧 runner 调度。`USER_PLAN`、`executionAffinity` 和夜间一次性计划仍使用旧 PostgreSQL scheduler。

## 执行器与任务契约

所有 Java 进程注册同一个自动注册地址执行器组 `test-agent-backend`。executor 地址必须能被所有 Admin 节点访问，但注册和路由不携带 `linuxServerId`，不与稳定 Linux 服务器亲和。

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
| `opencode-runtime.night-execution-reconcile` | `0 0/5 * * * ? *` | 5 分钟 |
| `opencode-runtime.stale-active-run-reconcile` | `0 0/5 * * * ? *` | 5 分钟 |
| `opencode-runtime.terminal-projection-retry` | `0/5 * * * * ? *` | 30 秒 |
| `opencode-runtime.side-question-orphan-cleanup` | `0 0/5 * * * ? *` | 5 分钟 |
| `scheduler.run-retention-cleanup` | `0 0 8 * * ? *` | 5 分钟 |

JVM 和 XXL 统一使用 `Asia/Shanghai`。运行记录清理在北京时间 08:00 执行，对应原 UTC 00:00；XXL 日志保留 30 天，旧 PostgreSQL scheduler 历史仍由任务清理 7 天。

## 旧 scheduler 边界

旧 `ScheduledTaskRunner` 启动时只同步包含 `USER_PLAN` 能力的 handler，每轮只扫描匹配当前 `executionAffinity` 的 `USER_PLAN`。它不再扫描 Cron 到期任务或管理员手工任务。旧本地终态投影重试 ticker 已移除，改由 XXL 中的 `opencode-runtime.terminal-projection-retry` 调度；其它心跳、租约、恢复和配置轮询不变。

夜间一次性计划仍按当前用户 opencode binding 的稳定 Linux 服务器执行，这个亲和只属于 `USER_PLAN`，不得复制到 XXL executor 注册、任务参数或路由策略中。

旧 `/api/internal/platform/scheduler-management/**` 统一返回 `410 API_GONE`。周期任务的启停、Cron 修改、手动触发、停止和日志查看均在嵌入的 XXL 页面完成；不新增 RunEvent/SSE 事件。

## 本地与生产配置

- 本地 `deploy/local/docker-compose.yml` 提供 MySQL 8.4、`xxl_job` 库、健康检查和持久卷，默认主机端口 `13306`；表和首批任务由 Admin 子上下文 Flyway 初始化。
- 只通过 `.env.local.example` 提供示例，禁止自动修改开发者的 `.env.local`。
- 本地 Vite 和生产 Nginx 都把 `/xxl-job-admin/` 代理到 Admin 子端口，保持 iframe 同源。
- 生产使用外部共享 MySQL。部署顺序：创建库与最小权限账号；配置凭据、XXL access token、每进程唯一端口和可达 executor 地址；部署 Java；开放 Admin 到 executor 网络；配置 Nginx。
- 离线包仍只有一个平台应用 JAR，同时包含上游源码、LICENSE、UPSTREAM 元数据、版本文件和配置示例。

完整变量和网络要求见 `docs/deployment/backend.md`，验证清单见 `docs/testing/xxl-job-integration.md`。

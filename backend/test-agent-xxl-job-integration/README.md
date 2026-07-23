# test-agent-xxl-job-integration

## 工程定位

平台周期任务接入 XXL-JOB 的适配模块。它在平台 WebFlux 主上下文中运行 executor、票据服务和健康状态，并在独立端口异步启动 Servlet Admin 子上下文；XXL Admin/MySQL 故障不会终止平台主服务。

## 依赖边界

- 上游调用方：`test-agent-api`（签发 SSO 票据）、`test-agent-app`（运行装配）。
- 下游依赖：`test-agent-scheduler` 的 handler/Redis 锁契约、`test-agent-domain` 的认证 marker 端口、原样上游 Admin 模块、XXL Core、Redis、MySQL/Flyway。
- 允许依赖：common、domain、observability、scheduler、upstream Admin 与基础 Spring/XXL 运行库。
- 禁止依赖：`test-agent-app`、`test-agent-api`、persistence 实现和 generated SDK。
- SQL 边界：平台扩展查询只能写在本模块 MyBatis XML；MySQL 结构和基础任务写在独立 Flyway location。

## 运行原则

- 所有 Java 进程注册到同一自动注册执行器组，不携带 `linuxServerId` 或稳定 Linux 服务器亲和。
- 周期任务由统一 `testAgentScheduledTaskHandler` 进入；`GLOBAL_MUTEX` 复用旧 Redis 锁键，`ALLOW_OVERLAP` 不申请全局锁。
- 夜间一次性任务由 XXL 的 `opencode-runtime.night-execution-dispatch` 每 15 分钟扫描 PostgreSQL，并由业务层按任务固化的 `linuxServerId` 路由到目标 Java；executor 注册仍不携带服务器亲和。
- 上游源码不在本模块复制或修改，所有登录禁用、平台 SSO、响应安全头都通过扩展 Bean/Filter 实现。
- 平台嵌入态横向导航样式由本模块以 `/static/platform/xxl-job-embedded-shell.css` 提供；只有同源父页面显式添加 `test-agent-xxl-embedded` 根 class 后生效，直接访问 Admin 仍使用上游原生布局。

## 配置与故障边界

- `TEST_AGENT_XXL_JOB_ENABLED` 控制 Admin/executor，默认开启；测试 profile 默认关闭，真实集成测试显式开启。
- `TEST_AGENT_XXL_JOB_MYSQL_URL/USERNAME/PASSWORD` 只供 Admin 子上下文使用，Flyway location 固定为顶层独立路径 `xxl-job/db/migration`，不会被平台 PostgreSQL 的 `db/migration` 扫描。
- `TEST_AGENT_XXL_JOB_ACCESS_TOKEN` 必须在生产使用强随机值并由全部 Admin/executor 共享。
- SSO 票据通过 Redis Lua 原子读删，兼容 Redis 5 及未提供 `GETDEL` 的 Redis 6.0；Redis 必须允许应用执行 `EVAL`。票据消费、JIT 或登录异常统一返回平台 `503 unavailable` 状态页，不进入上游通用错误页。
- Admin Cookie 默认保持 `Secure`。只有受控内网明确无法提供 HTTPS 时才设置 `TEST_AGENT_XXL_JOB_COOKIE_SECURE=false`；仍强制 `HttpOnly`、`SameSite=Lax` 和限定 Path，入口升级 HTTPS 后必须恢复 `true`。
- 每台 Linux 只部署一个 Java。executor 固定注册到同 JVM 的 `127.0.0.1:${TEST_AGENT_XXL_JOB_ADMIN_PORT}` Admin；注册地址复用 `BackendInstanceIdentity.listenUrl()` 的 advertised host 和 `TEST_AGENT_XXL_JOB_EXECUTOR_PORT`，因此所有 Admin 节点必须能访问该 host/port。
- Admin 启动失败按 5～60 秒指数退避；`xxlJobAdmin` health 独立上报，不属于平台 readiness；Admin 子上下文的 readiness 只检查其独立 MySQL，不继承平台 Redis 成员。
- executor 不在 Spring 单例初始化回调中立即启动。独立 daemon 协调器以 250 毫秒～5 秒退避探测本机 Admin 的 `/actuator/health/readiness`；返回 HTTP 200 后才启动 executor 和注册线程，且每个进程只启动一次。本机 Admin 不可用时平台 8080 仍正常启动，executor 端口保持未监听并等待恢复。
- 上游 `application.properties` 在依赖 JAR 中位于 `META-INF/xxl-job-admin-upstream/`，launcher 只把它作为 Admin 子上下文的低优先级默认配置加载；MySQL、端口、access token、SSO 和 Flyway 等平台运行配置以高优先级覆盖，平台主上下文不会读取上游的 Hikari/MySQL 默认值。

## 测试

- 单元测试覆盖 ticket 原子一次消费/过期、Redis 异常状态页、Cookie 安全模式、session marker、JIT 幂等/改名、原生入口禁用、参数校验、锁/续租/停止和异常脱敏；真实 Redis 5 容器验证不依赖 `GETDEL`。
- MySQL 8.4 Testcontainers 覆盖 V1-V4 全新初始化、重复 migration、一个 executor 组、七条任务和无默认管理员；夜间分发任务固定使用 ROUND、DISCARD_LATER、DO_NOTHING、GLOBAL_MUTEX 和零 XXL 重试。
- `DefaultXxlJobAdminContextLauncherTest` 启动真实 Servlet/Tomcat 子上下文，验证 Flyway 先于 scheduler、原生登录 403、表单 SSO/JIT、安全 Cookie、上游 AdminLTE 与平台嵌入样式资源可访问，以及两个先注册节点在另一 Admin 新增第三节点后仍保留于共享 MySQL registry。
- endpoint/readiness/lifecycle 测试验证 advertised IPv4/DNS 地址派生、本机 Admin context path 规整、非法监听地址安全拒绝、非 200 不启动、恢复后只启动一次，以及 Spring 自动装配使用派生地址且不会提前创建 executor 注册线程。
- `TestAgentRuntimePropertiesBindingTest` 验证上游通用 `spring.datasource.*` 不会进入平台主上下文，launcher 集成测试同时验证重定位后的上游默认项仍在 Admin 子上下文生效。
- 全部架构与多节点人工验收见 `docs/testing/xxl-job-integration.md`。

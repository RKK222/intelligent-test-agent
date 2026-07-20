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
- `USER_PLAN` 与带 `executionAffinity` 的夜间一次性计划继续由旧 scheduler 执行。
- 上游源码不在本模块复制或修改，所有登录禁用、平台 SSO、响应安全头都通过扩展 Bean/Filter 实现。

## 配置与故障边界

- `TEST_AGENT_XXL_JOB_ENABLED` 控制 Admin/executor，默认开启；测试 profile 默认关闭，真实集成测试显式开启。
- `TEST_AGENT_XXL_JOB_MYSQL_URL/USERNAME/PASSWORD` 只供 Admin 子上下文使用，Flyway location 固定为顶层独立路径 `xxl-job/db/migration`，不会被平台 PostgreSQL 的 `db/migration` 扫描。
- `TEST_AGENT_XXL_JOB_ACCESS_TOKEN` 必须在生产使用强随机值并由全部 Admin/executor 共享。
- `TEST_AGENT_XXL_JOB_ADMIN_PORT` 与 `TEST_AGENT_XXL_JOB_EXECUTOR_PORT` 在同机多进程间必须唯一；executor address 必须可被所有 Admin 节点访问。
- Admin 启动失败按 5～60 秒指数退避；`xxlJobAdmin` health 独立上报，不属于平台 readiness。

## 测试

- 单元测试覆盖 ticket 一次消费/过期、session marker、JIT 幂等/改名、原生入口禁用、参数校验、锁/续租/停止和异常脱敏。
- MySQL 8.4 Testcontainers 覆盖 V1-V3 全新初始化、重复 migration、一个 executor 组、六条任务和无默认管理员。
- `DefaultXxlJobAdminContextLauncherTest` 启动真实 Servlet/Tomcat 子上下文，验证 Flyway 先于 scheduler、原生登录 403、表单 SSO/JIT 和安全 Cookie。
- 全部架构与双节点人工验收见 `docs/testing/xxl-job-integration.md`。

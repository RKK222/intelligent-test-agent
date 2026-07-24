# test-agent-app

## 工程定位

唯一 Spring Boot 启动入口，最终只打这一个可运行后端服务包。本模块不承载业务逻辑，不定义 Controller，不保存 API DTO。

## 技术栈

- Java 21、Spring Boot 4.1.0、Maven multi-module。
- Spring WebFlux 主上下文、独立 Spring MVC/Tomcat XXL Admin 子上下文、Actuator、Flyway、Log4j2、Micrometer。
- 通过 `test-agent-api` 和各业务 library jar 装配运行态能力。

## 主要职责

- 启动 `TestAgentApplication`，扫描 `com.enterprise.testagent` 下的后端组件。
- 承载运行时 profile、配置绑定、日志配置、Actuator health、Flyway migration 入口、opencode execution node seed、Spring Scheduling，以及 Java/opencode 运行心跳和统一定时任务装配。
- 组装 `test-agent-api`、业务模块、persistence、event、opencode-client 等 library jar，形成单一部署包；persistence 装配 Redis 唯一 `RunRuntimeStore`，负责 manifest、durable/runtime 双 Stream、Hash/ZSET 物化 snapshot 和 active 索引，event/runtime 只依赖领域端口；多服务器部署时由 event 模块装配 Redis 服务器广播，workspace-management 模块执行应用版本工作区副本补偿。
- Maven package 通过 Spring Boot `build-info` 把产物构建时刻写入 jar；运行时按北京时间格式化为 `VyyyyMMdd.HHmmss` 并随 Java Redis 心跳快照上报。IDE 直接启动或旧 jar 缺少构建元数据时版本为空，不使用启动时间替代。
- 强制主应用使用 `WebApplicationType.REACTIVE`，装配 `test-agent-xxl-job-integration` 在独立端口异步启动 Admin 并注册 executor；Admin/MySQL 故障不关闭主服务。
- 装配 `test-agent-scheduler` 的公共 handler/Redis 锁以及 `test-agent-xxl-job-integration`；应用内不再启动 PostgreSQL scheduler 扫描线程。
- 保持生产容器只运行一个 Java 应用进程；PostgreSQL、XXL MySQL、Redis 和 opencode server 均由外部配置注入。

## 不负责

- 不放业务服务、Controller、请求/响应 DTO、WebFilter 或 WebSocket handler。
- 不直接暴露新的 HTTP/SSE/WebSocket URL。
- 不直接依赖 generated SDK。
- 不把 workspace、session、run、runtime、terminal 等业务包放回本模块。

## 运行入口能力

- `TestAgentApplication`：Spring Boot 启动类，强制 Reactive 并把 JVM 默认时区统一为 `Asia/Shanghai`。
- XXL Admin lifecycle/health、Servlet 子上下文和 executor 由 `test-agent-xxl-job-integration` 装配；app 只提供配置与最终包依赖。
- `config.TestAgentRuntimeProperties`：运行时配置绑定。
- `config.DatabaseMigrationRunner`：启动时执行 `classpath:db/migration`，确保空库先完成 Flyway migration；`CommonParameterMemoryStartupRunner` 紧随迁移严格加载显式 JVM 内存通用参数，再进入 scheduler 等业务 Runner。
- `config.OpencodeManagerControlConfig`：绑定 manager 控制面 token，解析稳定服务器身份和 advertised host，按 advertised host 与 `server.port` 派生后端实例直连地址，提供 `SYS_DATA_ROOT_DIR/.serverid/.serverhost` 路径解析器、5 秒 Java 心跳、10 秒 Redis 快照 TTL 和命令超时；启动时注册后端实例心跳，并把服务器身份与可访问地址写入 `.serverid/.serverhost` 供 Go manager 读取，本地和生产都走 manager WebSocket 控制面。
- `config.RedisHealthIndicator`：基于 Spring 标准 `spring.data.redis.*` 的运行态 Redis 健康检查。
- `config.RuntimeJsonConfig`：应用运行态共享 Jackson 配置。
- `config.WebClientConfig`：提供运行态共享 `WebClient.Builder`，供后端 Java 间 SSE 转发等基础设施注入。
- `log4j2-spring.xml`：Log4j2 控制台和文件日志配置，默认输出 `key=value` 结构化字段并对 message、thread 和 traceId 做 CRLF 编码；`logs/backend.log` 保存后端全量运行日志，`logs/sse.log` 额外保存 SSE 相关 logger，`logs/error.log` 额外保存 `ERROR` 及以上日志。
- `backend/Dockerfile`：只构建并运行 `test-agent-app` Java 进程，不包含 PostgreSQL、Redis 或 opencode server。
- `tools/dev-backend-run.sh`：本地后端启动入口，默认读取仓库根目录未跟踪的 `.env.local`，`--profile test` 读取 `.env.test`。

## 运行配置

- `application.yml` 是默认、本地和企业生产的唯一运行配置；所有差异由 dotenv 或系统环境变量提供，不再保留 `local`、`guo`、`prod` profile。
- 根目录 `restart-dev-services.sh` 默认读取 `.env.test` 并以 `test` profile 一键重启；本地直接使用 `tools/dev-backend-run.sh --env-file .env.local`，不设置 Spring profile。
- `application-test.yml`：数据库使用 `TEST_AGENT_TEST_DB_*`；为避免共享测试库中的占位/跨机器 Git 地址被本机后台反复 clone，应用版本工作区副本补偿器在 test profile 默认关闭。
- 企业 Java 运行时使用外置 `dist/backend/lib/` 加载全部依赖；PostgreSQL JDBC 驱动类使用 `TEST_AGENT_DB_DRIVER_CLASS_NAME`，默认 `org.postgresql.Driver`。
- `application.yml`：`test-agent.xxl-job.enabled` 默认 `true`；MySQL、access token、Admin/executor 端口和地址使用 `TEST_AGENT_XXL_JOB_*` 注入。readiness group 明确不包含 `xxlJobAdmin`。
- 标准夜间执行每个 15 分钟时段容量不绑定环境变量，由全局通用参数 `NIGHT_EXECUTION_SLOT_CAPACITY` 提供；该显式内存参数在运行态 Flyway 完成后严格加载，缺失或非法会让应用启动失败。支持精确分钟测试定时后，分发改由 XXL 每分钟触发，补偿仍每 5 分钟触发。
- 运营分析等周期 handler 不再由旧 runner 注册；任务定义由 XXL MySQL 版本 SQL 初始化，启停、Cron、手动触发和日志在 XXL 页面维护。
- 应用版本工作区物理根目录由 `common_parameters` 中的 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT` 决定（数据库唯一来源，缺失抛业务异常），不在 yaml 预留 fallback；副本补偿器除 test profile 外默认开启，可用 `test-agent.managed-workspace.replica-reconciler.enabled=false` 关闭，扫描间隔默认 60 秒。
- 用户进程运行管理和 manager 控制面在线状态强依赖 Redis；多服务器应用版本工作区副本实时同步也需要共享 Redis，并显式开启 `test-agent.server-broadcast.enabled=true`；默认 channel 为 `test-agent:server-broadcast`。
- Run 运行数据面同样强依赖 Redis；`REDIS_SUMMARY` 的 manifest/input/Stream/snapshot/scope/active 索引不提供 PostgreSQL 或 JVM 内存降级。默认 `test-agent.redis-summary.enabled=false`、rollout `0`，生产完成 Redis `noeviction`、AOF `everysec`、ACL/TLS、容量告警与故障演练后，可按 userId 稳定哈希逐步提高新 Run 比例；活动 Run 不切换模式，回滚只把后续新 Run 比例调回 `0`。部署要求见 `docs/deployment/backend.md`。
- `com.h2database:h2` 仅以 test scope 存在，用于 Docker 不可用时的无持久化启动冒烟；正式 local profile 仍以 PostgreSQL/Flyway 为准。

## 测试覆盖

- `AppModuleBoundaryTest` 保证 app 模块不回流 workspace、session、run、runtime、terminal、web 等业务包。
- `ConversationMemberRevocationIntegrationTest` 跨 configuration/workspace/runtime 验证成员移除会使旧 token 失效，并让同一托管 Workspace 的后续上下文签发返回 `FORBIDDEN`。
- `TestAgentRuntimePropertiesBindingTest` 覆盖默认值、local/test/prod profile 配置绑定、终端安全阈值、Spring Redis 配置入口、XXL 配置、废弃 scheduler/opencode 固定节点/普通工作区目录配置缺失，以及 manager 控制面 5 秒心跳和 10 秒 TTL。
- `ServerIdentityFilePathResolverTest` / `ServerIdentityFileWriterTest` 覆盖 `SYS_DATA_ROOT_DIR/.serverid/.serverhost` 派生、参数缺失失败、父目录创建、旧内容覆盖和单行身份/地址写入。
- `OpencodeManagerControlConfigTest` 覆盖稳定服务器身份按环境变量优先、主机名兜底，advertised host 按环境变量优先、探测 IPv4 兜底，以及后端直连地址由 advertised host 和 `server.port` 自动派生。
- `RedisHealthIndicatorTest` 覆盖 Redis 必需依赖的 TCP 健康检查。
- `LoggingFrameworkBindingTest` 覆盖运行态使用 Log4j2 作为 SLF4J 实际绑定。
- `WebClientConfigTest` 覆盖运行态提供可构建的 `WebClient.Builder`。
- `TestAgentApplicationTest` 覆盖即使 classpath 含 Servlet 依赖，平台主应用仍强制为 Reactive 并使用北京时间；integration 模块覆盖 Admin 独立端口、真实 MySQL Flyway、SSO 与故障退避。

## 允许依赖

- `test-agent-api`。
- `test-agent-system-management`、`test-agent-scheduler`、`test-agent-integration`、`test-agent-xxl-job-integration` 运行装配骨架。
- `test-agent-common`、`test-agent-domain`、`test-agent-observability`。
- `test-agent-persistence`、`test-agent-event`、`test-agent-opencode-client`，仅用于运行装配、migration、health 和 seed。
- Flyway Core、PostgreSQL database support、Spring Boot starters、Log4j2 starter；XXL 的 Servlet/MySQL 依赖由 integration 隔离提供。

## 禁止依赖

- `test-agent-opencode-sdk-generated`。
- 新增业务流程、API 协议逻辑或文件系统操作。
- workspace、session、run、runtime、terminal、web 等业务源码包。

## 后续 AI 编码指引

新增可部署启动、profile、migration runner、health contributor、日志或运行装配时才改本模块。新增 API 先放 `test-agent-api`；新增业务逻辑先判断归属到 workspace-management、opencode-runtime、system-management 或 integration，没有合适工程时按业务新建 Maven module。

运行态安全、鉴权、限流、CORS、API URL 和事件流变化必须同步 `docs/standards/security.md`、`docs/api/http-api.md`、`docs/api/event-stream.md` 和相关模块 README/PACKAGE。

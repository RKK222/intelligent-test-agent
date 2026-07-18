# 后端规范

本规范适用于 `backend/` 下所有人工维护代码，合并编码、测试、性能、错误处理、可观测性和数据变更规则。模块职责与依赖边界见 `docs/architecture/dependency-rules.md`，模块速查见 `docs/architecture/module-map.md`，技术栈见 `backend/README.md`。

## 工程原则

1. 后端是 Maven multi-module 工程，Java 21、Spring Boot 4.1.0、Spring WebFlux、Log4j2、Micrometer、Druid 连接池、MyBatis XML mapper。
2. 只有 `test-agent-app` 是可运行 Spring Boot 服务包，其余 `test-agent-*` 模块只产出 library jar。
3. 新增后端文件前必须先按 `docs/architecture/dependency-rules.md` 列出现有合适工程；没有合适工程时按业务边界新建 Maven module。
4. 业务代码优先遵守模块 README 和 `docs/architecture/dependency-rules.md` 的边界。

## 配置与环境变量

1. 不允许为临时绕过配置、适配个人环境或规避通用参数/数据库配置而随意新增环境变量。
2. 新增环境变量前必须先评估能否复用 `common_parameters`、Spring 配置项、数据库配置或既有 dotenv 变量；只有部署期密钥、外部端点、进程身份、启动引导路径或资源容量这类必须由运行环境注入的值，才允许新增环境变量。
3. 确需新增环境变量时，必须同步说明用途、默认值、适用 profile、是否敏感、配置缺失时的失败语义，并更新 `backend/README.md`、`docs/deployment/backend.md`、相关模块 README、启动脚本或 dotenv 示例以及配置绑定/启动测试。

## 分层规则

详细依赖方向与禁止关系见 `docs/architecture/dependency-rules.md`。核心红线：

1. Controller 只负责协议适配、参数校验和调用业务模块 service，不得直接访问 Repository 或 generated SDK。
2. `test-agent-api` 不放业务规则，不依赖 `test-agent-persistence` 或 `test-agent-app`。
3. 只有 `test-agent-opencode-client` 可以直接依赖 generated SDK；generated SDK DTO 不得进入 domain，不得直接返回前端。
4. `test-agent-agent-runtime` 负责 agentId 选择、运行时接口、日志/指标包装和具体 agent 适配；opencode facade 对外只暴露平台 command/result 和 `RunEventDraft`，不返回 generated SDK DTO。
5. `test-agent-domain` 不依赖 Spring Web、Persistence、generated SDK。
6. `test-agent-app` 不得新增 Controller、WebFilter、WebSocket handler 或业务源码包。
7. 工作区文件和 Agent 配置文件的跨服务器目录列表、读取、写入必须走平台文件 WebSocket route/ticket/RPC；不得为文件操作新增后端到后端 HTTP 代理，Git、初始化、进度查询、公共 worktree 元数据列表等非文件操作除外。公共 Agent 直接目录模式必须通过 route/ticket 绑定 `linuxServerId`，公共 worktree 模式必须通过落库 `worktreeId -> linuxServerId` 绑定目标服务器。
8. `workspace.move` 保持既有文件 WebSocket RPC 的 `workspaceId/sourcePath/targetPath` 请求与 `null` 成功响应，不新增 HTTP API 或 RunEvent SSE。实现必须在同一工作区内以一次原子文件系统重命名整体移动普通文件或普通目录（包括非空目录），禁止递归拆分与目标覆盖；同路径幂等成功，缺失源映射 `NOT_FOUND`，目标存在映射 `CONFLICT`，根、符号链接/特殊文件和目录自身后代目标映射 `VALIDATION_ERROR`，路径越界映射 `FORBIDDEN`。移动前必须固定真实 root/source/目标父目录；Linux 必须从 `/` 逐段打开目录句柄并直接调用内核 `renameat2(RENAME_NOREPLACE)`，macOS 必须使用逐段目录句柄和 `renameatx_np(RENAME_EXCL | RENAME_NOFOLLOW_ANY)`，Windows 必须固定源条目和目标父目录句柄、核对最终路径后使用不替换的 `SetFileInformationByHandle`，以原子方式阻断路径替换和目标并发创建；平台缺少等价能力时必须失败关闭，禁止退回仅校验字符串后直接移动的实现。原子重命名成功后，句柄关闭异常只能记录，不能把已完成操作反转为失败响应。
9. 涉及 opencode-manager 路由、Java 到 manager 控制、用户 opencode 进程服务器归属、运行管理 `containerId` 路由、Agent 配置或文件 WebSocket 目标后端选择时，必须复用统一公共程序：`BackendJavaRouteResolver` 做目标 Java 选择，普通 Java->Java HTTP 转发走 `BackendHttpForwarder`，RunEvent SSE 长连接转发走 API 层 `BackendSseForwarder`，目标 Java 再通过 `OpencodeProcessManagerGateway` 和本服务器 manager WebSocket 控制 manager。`BackendSseForwarder` 仅用于 `text/event-stream` 流式响应，必须复用同一个 `X-Test-Agent-Backend-Routed` 防循环头并透传认证、trace 和续传游标；禁止在业务入口自行扫描 Redis 快照、手写 HTTP 转发器、防循环 header 变体、本机降级、跨服务器直接控制 manager 或恢复 `local-direct` / `gateway-mode=local` 等本地绕过。
10. 涉及 opencode server 启动、重启后拉起、端口复用或启动成功状态回写时，必须复用 `test-agent-opencode-runtime` 的 `OpencodeProcessStartupService`。业务入口不得直接调用 `OpencodeProcessManagerGateway.startProcess()` 后自行保存进程、用户 binding、Redis heartbeat 或兼容 `ExecutionNode`；启动成功必须以公共启动服务完成 manager state/PID 与 opencode HTTP health 检查为准。
11. 涉及 opencode server 停止、停止后状态回写或运行管理停止命令时，必须复用 `test-agent-opencode-runtime` 的 `OpencodeProcessStopService`。业务入口不得直接调用 `OpencodeProcessManagerGateway.stopProcess()` 后自行判定成功或保存 `STOPPED`；平台已有进程记录时，停止成功必须以公共停止服务完成 manager stop 和停止后 health 不健康确认为准。
12. 涉及 opencode server 状态查询、健康探测、进程状态回写或 Redis heartbeat 刷新时，必须复用 `test-agent-opencode-runtime` 的 `OpencodeProcessStatusQueryService`。业务入口不得直接调用 `OpencodeProcessManagerGateway.checkHealth()` 后自行判断 `RUNNING/STOPPED/UNHEALTHY/FAILED`；进程不存在、运行中和健康检查异常的查询语义由公共查询服务统一映射。

## DTO 与模型

1. 对外 API 使用平台 DTO，不直接暴露 generated SDK 模型或数据库 surrogate PK。
2. 领域对象表达业务概念，不携带 HTTP、数据库或 SDK 注解。
3. Persistence 映射对象只在持久化模块内部使用。
4. API 请求和响应 DTO 变更必须更新 `docs/api/http-api.md`。

## 错误处理

所有后端错误必须转换为平台统一格式，不把任意 Java 异常直接返回给前端，不泄露堆栈、SQL、密钥、token、内部路径和第三方原始敏感错误。generated SDK 异常必须在 `test-agent-opencode-client` 转换为平台异常。

错误响应 `ApiErrorResponse` 至少包含 `code`（稳定错误码）、`message`（安全说明）、`traceId`、`details`（可选安全结构化详情）；成功响应对应 `ApiResponse<T>`。业务代码优先抛出 `PlatformException` 并携带 `ErrorCode`，入口层由 `GlobalExceptionHandler` 统一转换。`ApiTokenWebFilter` 和 `InMemoryRateLimitWebFilter` 在拦截链中直接写出统一 `ApiErrorResponse`，必须保持 `traceId`、HTTP 状态和错误码一致。路径穿越、非法 ID、非法分页和非法 `Last-Event-ID` 都必须映射为稳定平台错误码。

| code | HTTP 状态 | 默认说明 |
|---|---:|---|
| `VALIDATION_ERROR` | 400 | 请求参数无效 |
| `UNAUTHENTICATED` | 401 | 未认证 |
| `FORBIDDEN` | 403 | 无权限 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `CONFLICT` | 409 | 状态冲突 |
| `RATE_LIMITED` | 429 | 请求过于频繁 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |
| `OPENCODE_BAD_GATEWAY` | 502 | opencode 服务响应异常 |
| `OPENCODE_UNAVAILABLE` | 503 | opencode 服务不可用 |
| `OPENCODE_TIMEOUT` | 504 | opencode 服务超时 |

新增或修改错误码必须同步 `docs/api/http-api.md`、相关模块 README 和对应测试。

## 可观测性

### TraceId

1. 所有入口请求必须携带或生成 traceId，贯穿 Controller、application service、agent runtime、opencode client、persistence 和 event。
2. SSE、异步任务、重试和回放流程必须保留 traceId 或生成关联 ID；错误响应必须包含 traceId。
3. HTTP 入口使用请求/响应头 `X-Trace-Id`。合法 traceId 以 `trace_` 开头，只含字母、数字、下划线和短横线；缺失或非法值由 `TraceIdSupport` 生成新值。`TraceIdWebFilter` 将 traceId 写入 WebExchange attribute、响应头、Reactor context 和 SLF4J MDC。
4. `ApiTokenWebFilter`、`InMemoryRateLimitWebFilter` 的错误响应也必须返回同一 `X-Trace-Id`。
5. Run 启动、取消、routing decision、RunEvent 追加和 agent runtime 调用必须携带 traceId；SSE 回放使用事件自身 traceId，请求 traceId 只用于当前 HTTP 连接观测。

### 日志与指标

1. 使用正式日志框架，禁止 `System.out.println`。后端运行态使用 Log4j2 作为 SLF4J 实际绑定，业务代码使用 SLF4J API。
2. 日志必须结构化，至少包含 traceId、模块、关键业务 ID 和结果；`test-agent-app` 默认控制台日志为 `key=value` 格式。
3. Log4j2 PatternLayout 必须对可变 message、thread 和 traceId 做 CRLF 编码，避免日志换行注入。
4. 不记录密钥、token、认证头、个人敏感信息和大段用户输入；热路径避免高频 info 日志，调试细节用 debug。
5. 关键 API、agent runtime 调用、SSE 连接、事件处理、数据库访问应有 Micrometer 指标，标签必须低基数（不得用完整 message、token、用户输入）。

## 性能

### API

1. 列表接口必须分页或设置明确上限，查询参数必须限制最大 page size。
2. 大对象下载、日志、事件回放不得一次性全部加载到内存；同步 API 不执行不可控耗时任务，长任务应异步化或流式返回。
3. Workspace 文件目录列表必须单层读取并设置上限，默认不超过 1000 项；文件内容读写默认上限 1MB。

### 数据库

1. 避免 N+1 查询，高频查询必须有索引，批量写入使用批处理或明确事务边界，查询只取需要字段。
2. JDBC 连接池统一使用 Druid，通过 `TEST_AGENT_DB_POOL_INITIAL_SIZE`、`TEST_AGENT_DB_POOL_MIN_IDLE`、`TEST_AGENT_DB_POOL_MAX_ACTIVE`、`TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS` 配置；默认保留 `validation-query=SELECT 1` 和借出连接校验，`TEST_AGENT_DB_POOL_TEST_ON_BORROW` 只允许在明确评估数据库稳定性后关闭；不得在代码中硬编码环境容量。
3. 新增或修改关系型数据库 SQL 必须通过 MyBatis XML mapper 实现，mapper 接口只能声明方法和 `@Param`，禁止使用 MyBatis 注解 SQL；存量 `Jdbc*Repository` 仅作为迁移窗口保留，触及其 SQL 时迁移到 MyBatis XML。

### opencode 调用

1. 外部调用必须有连接、读取和整体超时；重试必须有上限，只对可重试错误执行。
2. 取消 Run、SSE 断开、请求超时必须释放资源；opencode server 节点选择不得每次全量扫描大表。
3. 新增 agent 必须实现 `AgentRuntime` 并复用 registry 的日志、指标和统一错误处理；未注册 agent 不得在 Controller 中特殊分支，应由 registry 返回统一错误。
4. opencode server 启动成功不能只信任 manager `STARTED` 回包；所有启动入口必须走 `OpencodeProcessStartupService`，由它在目标 Java 上写入候选进程快照、调用 manager health 同时确认本地 state/PID 和 opencode HTTP health，健康后才回写 `RUNNING`、ACTIVE binding、Redis heartbeat 和兼容节点投影。
5. opencode server 停止成功不能只信任 manager `STOPPED` 回包；所有停止入口必须走 `OpencodeProcessStopService`，由它通过 manager stop 发起停止，对平台已有进程记录的端口继续调用 manager health，确认 health 不健康后才回写 `STOPPED`。
6. opencode server 状态查询不能在业务入口直接调用 manager health；所有强状态查询必须走 `OpencodeProcessStatusQueryService`，先确认平台进程记录是否存在，再通过目标 Java 的本机 manager health 归一为未启动、运行中或健康检查异常，并统一刷新 DB 状态与 Redis heartbeat。

### SSE 与事件

1. SSE 输出必须考虑背压和客户端断开；事件回放必须基于 runId 和 seq 增量查询。
2. 高频事件可以合并或节流，但不能丢失必要状态；`Last-Event-ID` 续传必须避免重复发送不可幂等事件。
3. `LEGACY_FULL` SSE polling 默认批量读取 100 条事件，轮询间隔默认 500ms；调整必须同时评估数据库读压和前端渲染频率。`REDIS_SUMMARY` 禁止创建数据库 polling：首帧总发送完整物化 `run.snapshot.reset`，然后由最短 5 秒的 Redis 安全扫描和生产 Java 本机 live bus 只唤醒按 `runtimeVersion` 分页读 Redis 尾流；live 事件仍即时唤醒，但帧本身不得直接输出。
4. Redis durable `events` Stream ID 必须使用 `${seq}-0`，durable/transient 全事件 `runtime-events` Stream ID 必须使用 `${runtimeVersion}-0`。seq/runtimeVersion 分配、`XADD`、Hash/ZSET snapshot 投影、manifest 容量计数和动态 key TTL 刷新必须由同 slot Lua 原子完成。容量换代使 runtime 游标落后时必须再次发送 transient `run.snapshot.reset`；该事件不设置 SSE `id`，前端应用物化 snapshot 后仍只用后续 durable seq 推进 `Last-Event-ID`。
5. 单 Run 详情上限为 20,000 条 durable 事件、20,000 条 runtime 事件或 20,000 个 snapshot 投影项，总规范化详情上限为 32 MiB；任一阈值超限都必须显式删除旧 Stream、更新 manifest/reset generation 并保留物化状态，禁止依赖 Redis eviction、`MAXLEN` 静默裁剪或 PostgreSQL 原始事件降级。

## 数据变更规则

1. 数据库表结构变更必须有 Flyway migration，不允许只改实体类、Repository 或 SQL 映射。
2. migration 文件命名必须稳定、递增、可重复执行，必须能从空库执行到最新版本。V18 及以前保留既有数字版本；V18 之后新增 migration 统一使用 `VyyyyMMddHHmmss__description.sql`，时间戳按开发者创建迁移时的本地时间确定，禁止继续使用顺序数字版本以免多人并行开发冲突；已应用到任何共享或本地库的 migration 禁止删除、重命名或改写。
3. Flyway migration 只能承载表结构变更、历史数据兼容迁移和生产必需的基础字典/系统参数；禁止在 migration 中写入测试数据、演示数据、个人开发账号、样例应用/工作区、默认本地进程绑定或其他环境专属数据。此类数据必须放在测试 fixture、`test-agent-test-support`、mock 数据、显式本地开发脚本或人工初始化流程中。
4. 新字段优先允许空值或提供默认值；删除字段必须先完成读取兼容和数据迁移，再分阶段删除；枚举值、状态值、唯一约束和索引变更必须评估历史数据。若为兼容历史脏数据在持久化映射层做归一化，必须补充 Repository 测试并在模块 README 或数据库文档记录边界。
5. 新增表、字段、索引、约束必须有 migration 测试或集成验证；数据迁移脚本必须验证成功路径和关键失败场景；Repository 变更必须验证映射字段、查询条件、分页和排序。
6. 当前 migration 清单与表结构见 `docs/deployment/database.md`。
7. MyBatis mapper、行模型和 Repository 实现属于 `test-agent-persistence` 内部细节，业务模块只能依赖 `test-agent-domain` 的 Repository 端口。

## 测试

遵循“改什么补什么测试”。测试数据优先放在 `test-agent-test-support`。

### 分层测试

- Controller/API：验证参数校验、状态码、统一错误格式、traceId、鉴权和限流。
- Domain：验证状态机、领域规则、值对象约束和边界条件。
- Persistence：验证 Repository 映射、唯一约束、事务、Flyway migration。
- MyBatis：验证 XML mapper 查询、更新、动态条件和分页；源码约束测试必须阻止新增 JDBC SQL 和 MyBatis 注解 SQL。
- Event/SSE：验证事件类型、seq 单调递增、`Last-Event-ID` 续传、断线重连。
- Agent runtime：验证 agentId 规范化、默认 opencode 命中、未知 agent 统一错误和运行时调用指标。
- Opencode client facade：使用 mock opencode server 验证错误转换、超时、重试和事件映射；只有 `GeneratedOpencodeSdkGateway` 允许直接依赖 generated SDK。
- Observability：验证 traceId 传播、日志字段、关键指标注册。
- Application service：使用 fake repository/facade 验证 workspace、session、run、cancel 编排和错误映射。
- File service：验证路径穿越拒绝、单层目录列表、UTF-8 读写和超大文件拒绝。
- Health/config：验证 local/prod properties binding、废弃 opencode 固定节点配置缺失、Redis disabled/enabled health。

### 模块测试命令

```bash
cd backend
mvn -pl test-agent-workspace-management -am test
mvn -pl test-agent-agent-runtime -am test
mvn -pl test-agent-opencode-runtime -am test
mvn -pl test-agent-api -am test
```

`test-agent-app` 只保留启动装配、profile、migration、health 和模块边界测试；Controller/API 测试放在 `test-agent-api`，workspace/file 业务测试放在 `test-agent-workspace-management`，Session/Run/runtime/terminal 业务测试放在 `test-agent-opencode-runtime`。模块边界变更必须用 `rg` 校验依赖方向（见 `docs/architecture/dependency-rules.md`）。

### 数据库测试

1. 当前 persistence 集成测试使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Repository 映射、RunEvent append-only、增量读取和唯一约束。
2. 唯一约束、外键约束、状态字段约束必须有失败场景。
3. 后续使用 PostgreSQL 专有能力（JSONB、锁、advisory lock）必须补 Testcontainers 或等价 PostgreSQL 集成测试。
4. 测试环境 PostgreSQL 连通验证启用 `test` profile，通过 `TEST_AGENT_TEST_DB_*` 注入凭据，禁止写入仓库配置或测试源码；测试环境 opencode 连通通过 `TEST_AGENT_OPENCODE_BASE_URL` 注入，不得要求测试环境用 Docker Compose 启动数据库、Redis 或 opencode。
5. 数据库连接池使用 Druid，配置测试必须验证 `spring.datasource.druid.*` 绑定且 Druid Web 控制台默认关闭。

### 构建命令

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests
```

有测试代码后优先运行目标模块测试，再运行全量必要测试。

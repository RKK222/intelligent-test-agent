# test-agent-app

## 工程定位

唯一 Spring Boot 启动入口，最终只打这一个可运行后端服务包。

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Spring WebFlux
- Spring Security
- Spring Boot Actuator
- Log4j2
- Maven executable jar

## 主要职责

- 启动 `TestAgentApplication`。
- 承载入口层、控制面、代理层、路由、认证、限流等单后端职责。
- 提供 HTTP API、认证、限流、TraceId 注入、API 转发、SSE/WebSocket 代理入口。
- 编排 workspace、session、run、routing、event、opencode client facade。

## 已有入口能力

- `TraceIdWebFilter`：处理 `X-Trace-Id` 透传、生成和响应头写入。
- `GlobalExceptionHandler`：把 `PlatformException`、参数异常和未知异常转换为统一错误响应。
- `ApiTokenWebFilter`：未配置 `TEST_AGENT_API_TOKEN` 时放行；配置后要求 Bearer token。
- `InMemoryRateLimitWebFilter`：可配置内存限流占位，超限返回 `RATE_LIMITED`。
- Workspace API：工作区注册、分页查询、单层文件列表、UTF-8 文件读写和文件状态。
- Session API：会话创建、workspace 分页、全局搜索、标题/置顶更新、软删除、消息追加和消息分页读取。
- Run API：启动、查询、取消、RunEvent SSE 和 Run 级 Diff；首次 Run 懒创建远端 opencode session 并保存内部映射，后续 Run 复用原 session 和 execution node。
- `POST /api/runs` 已接收 Phase 11 可选字段；旧 `prompt` 继续兼容，`parts` 会转换为 opencode `text/file/agent` parts，`reference` part 会转为可读 text part，`agent/model/variant/messageId` 会随 `prompt_async` 下沉到 opencode facade。
- Phase 11 Runtime API：`/api/agents|models|providers|commands|references`、`/api/sessions/{id}/children|todo|diff|abort|fork|compact|revert|unrevert|command|shell`、permission/question、fs/vcs/lsp/mcp status/resources/tools 运行态接口，统一通过 `OpencodeRuntimeApplicationService` 和 opencode-client runtime facade 转发，不直返 generated DTO。
- Diff API：查询 Run Diff、接受保留当前工作区变更、拒绝时通过 opencode `sessionRevert` 回滚本次 Run 对应消息。
- RunController 在 WebFlux 下必须把阻塞式 Run/Diff 应用服务调用 offload 到 `boundedElastic`，避免在 event-loop 上触发 `.block()` 造成 `INTERNAL_ERROR`；RunEvent SSE 保持 `Flux` 流式返回。
- RunEvent SSE 续传时 header `Last-Event-ID` 优先；浏览器原生 `EventSource` 首次续传可使用 query `lastEventId`，由 RunController 传给 event 模块统一解析。
- RunApplicationService 订阅 opencode stream 后，事件持久化必须串行 offload 到 `boundedElastic`；本地 RunEvent 落库异常只记录告警，不能误判为 opencode Run 失败。
- Health：opencode nodes health、Redis optional health、数据库 health。

## 本地与生产 profile

- `application-local.yml`：默认连接 `127.0.0.1:15432/test_agent`，用于个人离线开发备用的 `deploy/local/docker-compose.yml`。
- `application-test.yml`：数据库使用 `TEST_AGENT_TEST_DB_*`，opencode node 使用 `TEST_AGENT_OPENCODE_*`，均指向外部研发测试服务。
- `application.yml` / `application-local.yml`：本地 CORS 默认允许 `localhost:3000` 和 `127.0.0.1:3000`，便于两种 loopback 地址联调。
- `application-prod.yml`：数据库、API token、CORS、Redis 和 opencode baseUrl 均通过环境变量注入，不提供真实密钥默认值。
- `log4j2-spring.xml`：Log4j2 控制台日志配置，默认输出 `key=value` 结构化字段并对 message、thread 和 traceId 做 CRLF 编码。
- `backend/Dockerfile`：只构建并运行 `test-agent-app` Java 进程，不包含 PostgreSQL、Redis 或 opencode server。
- `tools/dev-backend-run.sh`：本地后端启动入口，默认读取仓库根目录未跟踪的 `.env.local`，`--profile test` 读取 `.env.test`，先打包再运行 `test-agent-app` executable jar。
- `DatabaseMigrationRunner` 会在启动时执行 `classpath:db/migration`，确保空库先完成 Flyway migration 再 seed opencode node。
- `test-agent-app` 直接声明 Flyway Core 和 PostgreSQL database support，因为运行态 migration 入口由本模块承载。
- `test-agent.opencode.nodes` 会在启动时 seed 到 `execution_nodes`，node 默认来自 `TEST_AGENT_OPENCODE_BASE_URL`。
- `com.h2database:h2` 仅以 test scope 存在，用于 Docker 不可用时的无持久化启动冒烟；正式 local profile 仍以 PostgreSQL/Flyway 为准。

## 测试环境配置

- `application.yml`：所有环境统一使用 Druid 管理 JDBC 连接池，默认关闭 Druid Web 控制台和 WebStatFilter；默认借出连接前执行 `SELECT 1` 校验，避免远端 PostgreSQL idle 连接断开后首个业务请求失败。
- `application-test.yml`：`test` profile 使用 PostgreSQL 数据源，并启用 `test-agent-persistence` 的 Flyway migration。
- 数据库连接信息通过 `TEST_AGENT_TEST_DB_HOST`、`TEST_AGENT_TEST_DB_PORT`、`TEST_AGENT_TEST_DB_NAME`、`TEST_AGENT_TEST_DB_USERNAME`、`TEST_AGENT_TEST_DB_PASSWORD` 注入。
- opencode 测试节点通过 `TEST_AGENT_OPENCODE_BASE_URL` 注入；可用 `TEST_AGENT_OPENCODE_NODE_ID`、`TEST_AGENT_OPENCODE_MAX_RUNS`、`TEST_AGENT_OPENCODE_WEIGHT` 覆盖节点属性。
- 连接池大小可通过 `TEST_AGENT_DB_POOL_INITIAL_SIZE`、`TEST_AGENT_DB_POOL_MIN_IDLE`、`TEST_AGENT_DB_POOL_MAX_ACTIVE`、`TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS` 覆盖；`TEST_AGENT_DB_POOL_TEST_ON_BORROW` 可覆盖借出连接校验，默认 `true`。
- 真实账号、密码和测试库地址不得写入仓库配置；本地验证使用仓库根目录 `.env.local` 和 `.env.test`，二者已被 `.gitignore` 排除。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-observability`。
- `test-agent-opencode-client`。
- `test-agent-persistence`。
- `test-agent-event`。
- Flyway Core 和 PostgreSQL database support。
- Log4j2 starter，作为可运行后端服务的 SLF4J 实际绑定。
- Spring Boot starters。

## 禁止依赖

- 直接依赖 `test-agent-opencode-sdk-generated`。
- Controller 直接访问数据库实现。
- Controller 直接调用 generated SDK。

## 后续 AI 编码指引

新增对外 API、请求鉴权、代理入口、应用服务编排和启动配置时改这里。业务规则优先下沉到 domain，opencode 调用优先经过 `test-agent-opencode-client`。
Run 编排不得把平台 `workspaceId` 当作 opencode `workspace` query 传入；本地集成默认只传 workspace rootPath 对应的 `directory`。
Phase 11 Runtime API 同样不得把平台 `workspaceId` 当作 opencode `workspace` query；workspace 接口只用其 rootPath 作为 `directory`，session 接口必须通过平台 session 的内部远端映射定位 opencode session。MCP resources/tools 只读查询可以映射 opencode experimental API，但不得扩展到 MCP 安装、认证或配置页面。
Diff 编排必须留在 application service，Controller 不直接调用 opencode facade；缺少 opencode `messageID` 时拒绝 Diff 返回 `CONFLICT`。
Run/Diff HTTP Controller 返回 `Mono<ApiResponse<...>>` 时必须继续使用 `boundedElastic` 承载阻塞式应用服务，不能把 Repository 或 opencode 同步调用放回 WebFlux event-loop。
opencode stream 订阅错误和本地事件持久化错误必须区分：只有 opencode 终态事件或启动编排失败可以推进 Run 到终态，本地 RunEvent 写入抖动不能追加 `run.failed`。

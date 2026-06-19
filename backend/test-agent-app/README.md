# test-agent-app

## 工程定位

唯一 Spring Boot 启动入口，最终只打这一个可运行后端服务包。

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Spring WebFlux
- Spring Security
- Spring Boot Actuator
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
- Session API：会话创建、分页查询、消息追加和消息分页读取。
- Run API：启动、查询、取消、RunEvent SSE 和 Run 级 Diff；首次 Run 懒创建远端 opencode session 并保存内部映射，后续 Run 复用原 session 和 execution node。
- Diff API：查询 Run Diff、接受保留当前工作区变更、拒绝时通过 opencode `sessionRevert` 回滚本次 Run 对应消息。
- Health：opencode nodes health、Redis optional health、数据库 health。

## 本地与生产 profile

- `application-local.yml`：默认连接 `127.0.0.1:15432/test_agent`，用于 `deploy/local/docker-compose.yml`。
- `application-prod.yml`：数据库、API token、CORS 和 opencode baseUrl 均通过环境变量注入，不提供真实密钥默认值。
- `DatabaseMigrationRunner` 会在启动时执行 `classpath:db/migration`，确保空库先完成 Flyway migration 再 seed opencode node。
- `test-agent.opencode.nodes` 会在启动时 seed 到 `execution_nodes`，默认本地 node 来自 `TEST_AGENT_OPENCODE_BASE_URL`。
- `com.h2database:h2` 仅以 test scope 存在，用于 Docker 不可用时的无持久化启动冒烟；正式 local profile 仍以 PostgreSQL/Flyway 为准。

## 测试环境配置

- `application.yml`：所有环境统一使用 Druid 管理 JDBC 连接池，默认关闭 Druid Web 控制台和 WebStatFilter。
- `application-test.yml`：`test` profile 使用 PostgreSQL 数据源，并启用 `test-agent-persistence` 的 Flyway migration。
- 数据库连接信息通过 `TEST_AGENT_TEST_DB_HOST`、`TEST_AGENT_TEST_DB_PORT`、`TEST_AGENT_TEST_DB_NAME`、`TEST_AGENT_TEST_DB_USERNAME`、`TEST_AGENT_TEST_DB_PASSWORD` 注入。
- 连接池大小可通过 `TEST_AGENT_DB_POOL_INITIAL_SIZE`、`TEST_AGENT_DB_POOL_MIN_IDLE`、`TEST_AGENT_DB_POOL_MAX_ACTIVE`、`TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS` 覆盖。
- 真实账号、密码和测试库地址不得写入仓库配置；本地验证可使用 `.env.test` 等已被 `.gitignore` 排除的文件承载。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-observability`。
- `test-agent-opencode-client`。
- `test-agent-persistence`。
- `test-agent-event`。
- Spring Boot starters。

## 禁止依赖

- 直接依赖 `test-agent-opencode-sdk-generated`。
- Controller 直接访问数据库实现。
- Controller 直接调用 generated SDK。

## 后续 AI 编码指引

新增对外 API、请求鉴权、代理入口、应用服务编排和启动配置时改这里。业务规则优先下沉到 domain，opencode 调用优先经过 `test-agent-opencode-client`。
Run 编排不得把平台 `workspaceId` 当作 opencode `workspace` query 传入；本地集成默认只传 workspace rootPath 对应的 `directory`。
Diff 编排必须留在 application service，Controller 不直接调用 opencode facade；缺少 opencode `messageID` 时拒绝 Diff 返回 `CONFLICT`。

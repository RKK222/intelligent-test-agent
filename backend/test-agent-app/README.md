# test-agent-app

## 工程定位

唯一 Spring Boot 启动入口，最终只打这一个可运行后端服务包。本模块不承载业务逻辑，不定义 Controller，不保存 API DTO。

## 主要职责

- 启动 `TestAgentApplication`，扫描 `com.example.testagent` 下的后端组件。
- 承载运行时 profile、配置绑定、日志配置、Actuator health、Flyway migration 入口和 opencode execution node seed。
- 组装 `test-agent-api`、业务模块、persistence、event、opencode-client 等 library jar，形成单一部署包。
- 保持生产容器只运行 Java 进程；PostgreSQL、Redis 和 opencode server 均由外部配置注入。

## 不负责

- 不放业务服务、Controller、请求/响应 DTO、WebFilter 或 WebSocket handler。
- 不直接暴露新的 HTTP/SSE/WebSocket URL。
- 不直接依赖 generated SDK。
- 不把 workspace、session、run、runtime、terminal 等业务包放回本模块。

## 运行入口能力

- `TestAgentApplication`：Spring Boot 启动类。
- `config.TestAgentRuntimeProperties`：运行时配置绑定。
- `config.DatabaseMigrationRunner`：启动时执行 `classpath:db/migration`，确保空库先完成 Flyway migration。
- `config.ExecutionNodeSeeder`：从配置 seed opencode execution node。
- `config.OpencodeNodesHealthIndicator`、`config.RedisOptionalHealthIndicator`：运行态健康检查。
- `config.RuntimeJsonConfig`：应用运行态共享 Jackson 配置。
- `log4j2-spring.xml`：Log4j2 控制台日志配置，默认输出 `key=value` 结构化字段并对 message、thread 和 traceId 做 CRLF 编码。
- `backend/Dockerfile`：只构建并运行 `test-agent-app` Java 进程，不包含 PostgreSQL、Redis 或 opencode server。
- `tools/dev-backend-run.sh`：本地后端启动入口，默认读取仓库根目录未跟踪的 `.env.local`，`--profile test` 读取 `.env.test`。

## 本地与生产 profile

- `application-local.yml`：默认连接 `127.0.0.1:15432/test_agent`，用于个人离线开发备用的 `deploy/local/docker-compose.yml`。
- `application-test.yml`：数据库使用 `TEST_AGENT_TEST_DB_*`，opencode node 使用 `TEST_AGENT_OPENCODE_*`，均指向外部研发测试服务。
- `application-prod.yml`：数据库、API token、CORS、Redis 和 opencode baseUrl 均通过环境变量注入，不提供真实密钥默认值。
- `com.h2database:h2` 仅以 test scope 存在，用于 Docker 不可用时的无持久化启动冒烟；正式 local profile 仍以 PostgreSQL/Flyway 为准。

## 允许依赖

- `test-agent-api`。
- `test-agent-system-management`、`test-agent-integration` 运行装配骨架。
- `test-agent-common`、`test-agent-domain`、`test-agent-observability`。
- `test-agent-persistence`、`test-agent-event`、`test-agent-opencode-client`，仅用于运行装配、migration、health 和 seed。
- Flyway Core、PostgreSQL database support、Spring Boot starters、Log4j2 starter。

## 禁止依赖

- `test-agent-opencode-sdk-generated`。
- 新增业务流程、API 协议逻辑或文件系统操作。
- workspace、session、run、runtime、terminal、web 等业务源码包。

## 后续 AI 编码指引

新增可部署启动、profile、migration runner、health contributor、日志或运行装配时才改本模块。新增 API 先放 `test-agent-api`；新增业务逻辑先判断归属到 workspace-management、opencode-runtime、system-management 或 integration，没有合适工程时按业务新建 Maven module。

运行态安全、鉴权、限流、CORS、API URL 和事件流变化必须同步 `docs/standards/security.md`、`docs/api/http-api.md`、`docs/api/event-stream.md` 和相关模块 README/PACKAGE。

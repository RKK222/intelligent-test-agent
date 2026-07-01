# test-agent-app

## 工程定位

唯一 Spring Boot 启动入口，最终只打这一个可运行后端服务包。本模块不承载业务逻辑，不定义 Controller，不保存 API DTO。

## 技术栈

- Java 21、Spring Boot 4.1.0、Maven multi-module。
- Spring WebFlux、Actuator、Flyway、Log4j2、Micrometer。
- 通过 `test-agent-api` 和各业务 library jar 装配运行态能力。

## 主要职责

- 启动 `TestAgentApplication`，扫描 `com.icbc.testagent` 下的后端组件。
- 承载运行时 profile、配置绑定、日志配置、Actuator health、Flyway migration 入口、opencode execution node seed、Spring Scheduling，以及 Java/opencode 运行心跳和运营分析 rollup 周期任务装配。
- 组装 `test-agent-api`、业务模块、persistence、event、opencode-client 等 library jar，形成单一部署包；多服务器部署时由 event 模块装配 Redis 服务器广播，workspace-management 模块执行应用版本工作区副本补偿。
- 装配 `test-agent-scheduler`，默认关闭后台扫描；启用后由 scheduler 模块校验 Redis 必需。
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
- `config.OpencodeManagerControlConfig`：绑定 manager 控制面 token、后端实例直连地址、`SYS_DATA_ROOT_DIR/.serverip` 路径解析器、5 秒 Java 心跳、10 秒 Redis 快照 TTL 和命令超时；Redis 心跳启用时启动后端实例注册/心跳，未启用时跳过该 runner，运行管理和 manager 控制面仍在业务入口 fail fast。启动时会把当前服务器 IPv4 写入 `.serverip` 供 Go manager 读取，本地和生产都走 manager WebSocket 控制面。
- `config.OpencodeNodesHealthIndicator`、`config.RedisHealthIndicator`：运行态健康检查；manager/socket 用户进程模式下跳过旧固定 opencode node 探测，避免旧 IP 或空端口把整体 Actuator health 误判为 DOWN。
- `config.RuntimeJsonConfig`：应用运行态共享 Jackson 配置。
- `log4j2-spring.xml`：Log4j2 控制台日志配置，默认输出 `key=value` 结构化字段并对 message、thread 和 traceId 做 CRLF 编码。
- `backend/Dockerfile`：只构建并运行 `test-agent-app` Java 进程，不包含 PostgreSQL、Redis 或 opencode server。
- `tools/dev-backend-run.sh`：本地后端启动入口，默认读取仓库根目录未跟踪的 `.env.local`，`--profile test` 读取 `.env.test`。

## 本地与生产 profile

- `application-local.yml`：默认连接 `127.0.0.1:15432/test_agent`，用于个人离线开发备用的 `deploy/local/docker-compose.yml`。
- `local` / `guo` profile 不再支持 `gateway-mode=local` 或 `local-direct` 短路；本地调试用户 opencode 进程时也需要启动 Go manager，并让 manager 连接 `.serverip` 指向的本服务器 Java。
- 根目录 `restart-dev-services.sh` 默认读取 `.env.test` 并以 `test` profile 一键重启后端、opencode-manager 和前端；test profile 下默认启动本机 Go manager，不再受 `.env.test` 中共享测试 `TEST_AGENT_OPENCODE_BASE_URL` 影响；若未配置 `TEST_AGENT_BACKEND_LISTEN_URL`，会自动使用默认路由网卡 IPv4 补全后端直连地址；脚本会导出 `TEST_AGENT_ROOT` 和兼容别名 `TESTAGENT` 供通用参数路径展开；`.serverip` 固定由 `SYS_DATA_ROOT_DIR/.serverip` 约定，不再通过环境变量改写。使用本地离线配置时显式传入 `--profile local --env-file .env.local`。
- `application-guo.yml`：连接个人调试环境，CORS 默认继承本地端口白名单，并允许通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 覆盖；配合根目录 `restart-dev-services.sh` 用局域网 IP 启动时，脚本会自动追加实际前端 origin。
- IDEA 运行配置 `.idea/runConfigurations/TestAgentApplication_guo.xml` 直接启动 `TestAgentApplication`，通过 `-Dspring.profiles.active=guo` 读取 `application-guo.yml`。该 yml 已内置原 `.env.local` 中 Java 进程需要的数据库、Redis、opencode、manager token、模型来源和模型 key 配置，Windows 开发人员不需要执行 shell 启动脚本。
- `application-test.yml`：数据库使用 `TEST_AGENT_TEST_DB_*`，opencode node 使用 `TEST_AGENT_OPENCODE_*`，均指向外部研发测试服务；为避免共享测试库中的占位/跨机器 Git 地址被本机后台反复 clone，应用版本工作区副本补偿器在 test profile 默认关闭。
- `application-prod.yml`：数据库、API token、CORS、Redis 和 opencode baseUrl 均通过环境变量注入，不提供真实密钥默认值。
- `application.yml`：`test-agent.scheduler.enabled` 默认 `false`，可通过 `TEST_AGENT_SCHEDULER_ENABLED` 显式启用。
- Workspace 目录选择器允许根目录通过 `test-agent.workspace-picker.allowed-roots` / `TEST_AGENT_WORKSPACE_PICKER_ROOTS` 配置，逗号分隔，默认 `${user.home}/workspace`。
- 应用版本工作区物理根目录由 `common_parameters` 中的 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT` 决定（数据库唯一来源，缺失抛业务异常），不在 yaml 预留 fallback；副本补偿器除 test profile 外默认开启，可用 `test-agent.managed-workspace.replica-reconciler.enabled=false` 关闭，扫描间隔默认 60 秒。
- 用户进程运行管理和 manager 控制面在线状态强依赖 Redis；多服务器应用版本工作区副本实时同步也需要共享 Redis，并显式开启 `test-agent.server-broadcast.enabled=true`；默认 channel 为 `test-agent:server-broadcast`。
- `com.h2database:h2` 仅以 test scope 存在，用于 Docker 不可用时的无持久化启动冒烟；正式 local profile 仍以 PostgreSQL/Flyway 为准。

## 测试覆盖

- `AppModuleBoundaryTest` 保证 app 模块不回流 workspace、session、run、runtime、terminal、web 等业务包。
- `TestAgentRuntimePropertiesBindingTest` 覆盖默认值、local/test/prod profile 配置绑定、目录选择根、终端安全阈值、Redis、scheduler 默认关闭、opencode 节点、manager 控制面 5 秒心跳和 10 秒 TTL。
- `ServerIpFilePathResolverTest` / `ServerIpFileWriterTest` 覆盖 `SYS_DATA_ROOT_DIR/.serverip` 派生、参数缺失失败、父目录创建、旧内容覆盖和单行 IPv4 写入。
- `ExecutionNodeSeederTest` 覆盖启动时从配置 seed opencode execution node。
- `OpencodeNodesHealthIndicatorTest` 覆盖 manager/socket 模式跳过旧固定节点探测。
- `RedisHealthIndicatorTest` 覆盖 Redis 必需依赖的 TCP 健康检查。
- `LoggingFrameworkBindingTest` 覆盖运行态使用 Log4j2 作为 SLF4J 实际绑定。

## 允许依赖

- `test-agent-api`。
- `test-agent-system-management`、`test-agent-scheduler`、`test-agent-integration` 运行装配骨架。
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

# Phase 05 本地集成和开发环境详细设计

## 目标

Phase 05 提供可重复的本地开发、联调和后端容器部署入口。研发测试和生产部署只将 `test-agent-app` Java 进程放入 Docker，PostgreSQL、可选 Redis 和 opencode server 节点都通过外部地址注入。仓库不保存真实密钥、token、数据库密码或生产地址。

## 配置分层

- `application.yml` 保存跨环境默认值：服务端口、Druid 默认池大小、Actuator 暴露范围、Runtime API 默认限制。
- `application-local.yml` 面向个人本地开发：默认连接本机 `15432/test_agent`，Redis 默认关闭，opencode 节点从 `TEST_AGENT_OPENCODE_BASE_URL` 读取。
- `application-test.yml` 使用 `TEST_AGENT_TEST_DB_*` 注入外部测试 PostgreSQL，并通过 `TEST_AGENT_OPENCODE_*` 注入外部 opencode 节点。
- `application-prod.yml` 不提供真实默认 secret；生产 token、CORS allow origins、数据库连接、Redis 地址和 opencode 节点都必须来自环境变量或配置中心。

## 执行节点 Seed 与健康检查

- 新增 `TestAgentRuntimeProperties` 绑定 `test-agent.*` 配置，包括文件限制、安全、限流、Redis 和 opencode node 列表。
- 启动时由 `ExecutionNodeSeeder` 把配置中的 opencode nodes upsert 到 `execution_nodes`，默认节点 ID 为 `node_local_opencode`。
- Actuator health 扩展：
  - DB 使用 Spring Boot 原生 db health。
  - Redis 未启用时返回 disabled；启用时只做轻量 TCP 连通性检查。
  - opencode health 遍历已配置执行节点并调用 `OpencodeClientFacade.health`，只返回 nodeId、baseUrl、status 和安全错误码。

## 本地 Compose 与脚本

- `backend/Dockerfile` 提供后端专用 multi-stage 镜像构建，只打包并运行 `test-agent-app` executable jar，不包含 PostgreSQL、Redis 或 opencode server。
- `deploy/local/docker-compose.yml` 保留为个人离线开发备用，可提供 Postgres 和 Redis 服务。Redis 默认通过 compose profile 启用，避免强制依赖。
- `tools/dev-local-up.sh` 只启动个人本地备用依赖，默认只启动 Postgres，可通过参数启用 Redis。
- `tools/dev-backend-check.sh` 统一执行后端测试、打包和依赖边界检查。
- `tools/dev-health-check.sh` 调用本地 Actuator health 和核心 Runtime API 探针，支持通过环境变量传入 base URL 和 Bearer token。
- 脚本必须支持 `--help`，不得写死密钥；失败时输出可排查的命令和环境变量名。

## 文档同步

- `backend/README.md` 记录 local/test/prod profile、端口、环境变量和启动顺序。
- `docs/deployment/backend-docker-deployment.md` 记录后端镜像构建、生产必填环境变量和外部依赖边界。
- `docs/api/backend-api.md` 记录 Phase 04 Runtime API 路径、请求、响应、错误码、traceId 和鉴权行为。
- `docs/api/event-stream-api.md` 记录 Runtime SSE 入口和 `Last-Event-ID` 行为。
- `docs/security/security-standards.md` 记录 token 占位、CORS 和限流策略。
- `docs/database/migrations.md` 记录 V2 `session_messages` 表和兼容策略。

## 验收

- 空库启动时 Flyway 可执行 V1/V2 migration。
- test/prod profile 可连接外部 PostgreSQL 并 seed opencode node。
- 后端 Docker 镜像构建成功，容器运行层只启动 Java 进程。
- health 能反映 DB、Redis disabled/enabled 和 opencode node 状态。
- 新增脚本 `--help` 可运行，后端检查脚本覆盖测试、打包和 generated SDK 边界。
- 文档和 README 中的环境变量、端口、命令与代码保持一致。

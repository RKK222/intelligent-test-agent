# Phase 05 本地集成和开发环境

## 阶段目标

提供可重复的本地开发和部署配置入口，让后端 Java 进程可在本地或 Docker 中启动，并通过外部 PostgreSQL、可选 Redis 和 opencode server 节点完成集成验证。

## 可验收功能清单

1. 提供后端 Dockerfile 和本地启动检查脚本。
2. 外部 PostgreSQL 可初始化并执行 Flyway migration。
3. Redis 可选能力有配置开关。
4. opencode server 节点地址可配置。
5. 提供健康检查和基础联调脚本。

## 修改项目

- `backend/test-agent-app`
- `backend/test-agent-persistence`
- `backend/test-agent-opencode-client`
- `tools/*`
- `docs/implementation-plan.md`
- `docs/backend/*`

## 实现功能

- 配置文件区分 local、test、prod。
- 当前已提供 `test-agent-app` 的 `test` profile，用 `TEST_AGENT_TEST_DB_*` 环境变量连接外部 PostgreSQL 测试库并启用 Flyway。
- `prod` profile 用 `TEST_AGENT_DB_*`、`TEST_AGENT_OPENCODE_*` 和可选 `TEST_AGENT_REDIS_*` 连接外部依赖。
- 密钥、token、数据库密码和 opencode server 地址从环境变量或配置文件读取。
- 健康检查包含数据库、可选 Redis、执行节点连通性。
- 工具脚本能一键检查文档、后端骨架和后端编译。
- `deploy/local/docker-compose.yml` 只作为个人离线开发备用，不作为研发测试或生产部署主路径。

## 验收方式

- 新环境按文档可启动。
- Flyway 可在空库执行。
- 健康检查能返回关键依赖状态。
- 不存在硬编码 secret。
- 部署文档说明后端镜像构建、外部依赖环境变量、端口和常见检查命令。

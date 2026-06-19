# 文档索引

本文档说明 Codex、Claude 和开发者在不同任务中应优先阅读哪些稳定文档。

## 通用入口

- `AGENTS.md`：AI 编码总规范。
- `CLAUDE.md`：Claude 入口说明。
- `docs/development/ai-coding-rules.md`：AI 编码红线。
- `docs/development/task-workflow.md`：任务执行流程。
- `docs/development/ai-self-checklist.md`：完成前自检清单。
- `docs/implementation-plan.md`：平台总体方案。
- `docs/design/00-roadmap-design.md`：Phase 00 总体路线图详细设计。
- `docs/design/01-backend-domain-and-contracts-design.md`：Phase 01 后端领域模型和契约详细设计。
- `docs/design/02-persistence-and-routing-design.md`：Phase 02 持久化和执行节点路由详细设计。
- `docs/design/03-opencode-client-and-events-design.md`：Phase 03 opencode client 与事件体系详细设计。
- `docs/design/04-backend-api-runtime-design.md`：Phase 04 后端 Runtime API 详细设计。
- `docs/design/05-local-integration-and-devops-design.md`：Phase 05 本地集成和开发运维详细设计。
- `docs/plan/00-roadmap.md`：分阶段实施路线图。

## 后端任务

- `backend/README.md`：后端多模块总览。
- `backend/test-agent-*/README.md`：模块职责和依赖边界。
- `backend/**/PACKAGE.md`：源码包职责、主要程序和访问边界。
- `docs/backend/backend-coding-standards.md`：后端编码规范。
- `docs/backend/backend-testing-standards.md`：后端测试规范。
- `docs/backend/backend-performance-standards.md`：后端性能规范。
- `docs/backend/error-handling-standards.md`：错误处理规范。
- `docs/backend/observability-standards.md`：日志与可观测性规范。
- `docs/backend/data-change-standards.md`：数据库变更规范。

## API 与架构任务

- `docs/api/backend-api.md`：HTTP API 文档入口。
- `docs/api/event-stream-api.md`：SSE 和事件流文档入口。
- `docs/architecture/dependency-rules.md`：分层依赖和访问关系。
- `docs/architecture/pty-websocket-design.md`：Phase 11 P2 交互式 PTY WebSocket 的受控例外、安全和协议设计。
- `docs/deployment/backend-docker-deployment.md`：后端 Java 进程容器部署说明。
- `docs/security/security-standards.md`：安全规范。
- `docs/database/migrations.md`：数据库 migration、核心表和兼容策略。

## 分阶段实施任务

- `docs/plan/00-roadmap.md`：总体路线图，明确前端完全自研。
- `docs/plan/01-backend-domain-and-contracts.md`：领域模型、统一响应、错误和 traceId。
- `docs/plan/02-persistence-and-routing.md`：持久化、Flyway 和执行节点路由。
- `docs/plan/03-opencode-client-and-events.md`：opencode client、错误映射和 RunEvent。
- `docs/plan/04-backend-api-runtime.md`：Workspace、Session、Run、Cancel 和 Event API。
- `docs/plan/05-local-integration-and-devops.md`：本地集成、配置和开发脚本。
- `docs/plan/06-frontend-foundation.md`：前端基础工程。
- `docs/plan/07-workbench-shell-and-files.md`：工作台、文件树和编辑器。
- `docs/plan/08-agent-chat-diff-and-run-mvp.md`：Agent 对话、Diff 和运行 MVP。
- `docs/plan/09-test-reports-and-skill-studio.md`：测试报告和 Skill Studio。
- `docs/plan/10-e2e-hardening-and-release.md`：端到端加固和发布验收。
- `docs/plan/11-opencode-web-feature-replica.md`：opencode Web 交互功能复刻需求（会话、对话、工具、Diff、权限、提问、Agent、上下文、斜杠命令、终端、MCP 等）。

## 前端任务

当前仓库尚未创建真实 `frontend/` 工程，前端规范先放在 `docs/frontend/`。前端目标是完全自研测试智能体 Web IDE。

- `docs/frontend/frontend-requirements.md`：前端要求总结与产品形态。
- `docs/frontend/frontend-architecture.md`：前端 workspace 和包职责规划。
- `docs/frontend/frontend-coding-standards.md`：前端编码规范。
- `docs/frontend/frontend-testing-standards.md`：前端测试规范。
- `docs/frontend/frontend-performance-standards.md`：前端性能规范。
- `docs/frontend/frontend-backend-contract.md`：自研前端与 `test-agent-app` 的 HTTP API 和 RunEvent SSE 契约。

未来创建 `frontend/` 后，每个 app、package 和关键源码包必须补 README 或 PACKAGE.md。

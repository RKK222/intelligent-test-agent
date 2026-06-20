# 文档索引

本索引按研发场景组织，是 Codex、Claude 和开发者的唯一文档入口。入口规范见 `AGENTS.md`。

历史设计、需求草案和阶段计划已移至根目录 `requirements/`，**不作为编码依据**，需要时仅供追溯。

## 研发流程（先读这里）

- `docs/guides/ai-workflow.md`：AI 编码工作流（读文档→定位→修改→测试→文档→自检→提交）。
- `docs/guides/self-checklist.md`：完成前自检清单。
- `docs/architecture/dependency-rules.md`：分层依赖与访问边界。

## 技术栈与编码规范

- `backend/README.md`：后端多模块总览与技术栈。
- `frontend/README.md`：前端工程总览与技术栈（技术栈版本以本文件为单一来源）。
- `docs/standards/backend.md`：后端编码、测试、性能、错误处理、可观测性、数据变更规范。
- `docs/standards/frontend.md`：前端编码、性能、测试规范。
- `docs/standards/security.md`：安全、日志脱敏、PTY 安全例外。

## 找功能（模块/包定位）

- `docs/architecture/module-map.md`：后端模块与前端包职责速查，按功能定位代码位置。
- `docs/architecture/dependency-rules.md`：模块归属与依赖方向，新增文件前必读。
- `backend/test-agent-*/README.md`：各后端模块职责与依赖边界。
- `frontend/packages/*/README.md`、`frontend/apps/agent-web/README.md`：各前端包职责。

## 前后端 API 约定

- `docs/api/http-api.md`：HTTP API 路径、方法、请求/响应、错误码、traceId。
- `docs/api/event-stream.md`：RunEvent SSE 事件类型、字段、续传规则（单一事实源）。

## 部署与数据库

- `docs/deployment/backend.md`：后端 Java 进程容器部署。
- `docs/deployment/frontend.md`：前端 Next.js 生产构建与部署。
- `docs/deployment/database.md`：Flyway migration、核心表与兼容策略。

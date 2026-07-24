# 文档索引

本索引按研发场景组织，是 Codex、Claude 和开发者的唯一文档入口。入口规范见 `AGENTS.md`。

历史设计、需求草案和阶段计划已移至根目录 `requirements/`，**不作为编码依据**，需要时仅供追溯。

## 研发流程（先读这里）

- `docs/guides/ai-workflow.md`：AI 编码工作流（读文档→定位→修改→测试→文档→自检→提交）。
- `docs/guides/self-checklist.md`：完成前自检清单。
- `docs/architecture/dependency-rules.md`：分层依赖与访问边界。
- `docs/standards/opencode.md`：OpenCode 只读源码快照、平台适配和 generated SDK 边界。

## 技术栈与编码规范

- `backend/README.md`：后端多模块总览与技术栈。
- `frontend/README.md`：前端工程总览与技术栈（技术栈版本以本文件为单一来源）。
- `docs/standards/backend.md`：后端编码、测试、性能、错误处理、可观测性、数据变更规范。
- `docs/standards/frontend.md`：前端编码、性能、测试规范。
- `docs/standards/security.md`：安全、日志脱敏、PTY 安全例外。

## 找功能（模块/包定位）

- `docs/architecture/module-map.md`：后端模块与前端包职责速查，按功能定位代码位置。
- `docs/architecture/dependency-rules.md`：模块归属与依赖方向，新增文件前必读。
- `docs/architecture/xxl-job-integration.md`：XXL-JOB 3.4.2 集成、SSO、数据库、executor 与夜间任务分发边界的单一事实源。
- `backend/test-agent-*/README.md`：各后端模块职责与依赖边界。
- `frontend/packages/*/README.md`、`frontend/apps/agent-web/README.md`：各前端包职责。

## 前后端 API 约定

- `docs/api/http-api.md`：HTTP API 路径、方法、请求/响应、错误码、traceId。
- `docs/api/event-stream.md`：RunEvent SSE 事件类型、字段、续传规则（单一事实源）。

## 对话场景造数

- `docs/testing/conversation-scenes.md`：直接对话、历史运行中、Todo、ask、permission、subagent、宠物旁路成功/失败等可重复 fixture 入口。
- `docs/testing/application-worktree-feature-cases.md`：应用 worktree、feature、角色写权限、发布投影和固定 UI 测试数据案例。
- `docs/testing/xxl-job-integration.md`：XXL-JOB 自动化、双 Java、故障隔离和安全验收清单。

## 部署与数据库

- `docs/deployment/backend.md`：后端 Java 进程容器部署。
- `docs/deployment/opencode-upgrade-1.18.4.md`：OpenCode 1.18.4 / OpenAPI Generator 7.24.0 差异、影响、验证与回滚基线。
- `docs/deployment/frontend.md`：前端 Vue + Vite 生产构建与部署。
- `deploy/internal/SINGLE-BACKEND.md`：企业内单 Java 后台 + 单 worker 离线部署。
- `deploy/internal/MULTI-BACKEND.md`：企业内两个或更多 Java/worker 节点部署与跨节点验收。
- `deploy/internal/REDIS-OFFLINE.md`：当前本地 Redis 7.4.9 的独立 linux/amd64 离线封包、企业 Redis 5.0 停写备份、升级验证与回滚。
- `deploy/internal/FULL-UPGRADE-RUNBOOK.md`：当前企业 Redis 5 升级至 7.4.9，再按 `.4 → .114 → .2` 发布平台的完整逐机执行手册；中转机固定使用 `~/Desktop/mimoagent/0709`。
- `deploy/internal/XXL-JOB-TROUBLESHOOTING.md`：当前企业双后台 XXL-JOB 管理页、SSO、Admin、executor 和共享 MySQL 的只读排查手册。
- `docs/deployment/database.md`：Flyway migration、核心表与兼容策略。

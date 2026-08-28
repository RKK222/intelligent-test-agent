# 文档索引

本索引按研发场景组织，是 Codex、Claude 和开发者的唯一文档入口。入口规范见 `AGENTS.md`。

历史设计、需求草案和阶段计划已移至根目录 `requirements/`，**不作为编码依据**，需要时仅供追溯。

## 研发流程（先读这里）

- `docs/guides/ai-workflow.md`：AI 编码工作流与长期分支策略（仅新增部署节点使用 dev，其余使用 release，再读文档→定位→修改→测试→文档→自检→提交）。
- `docs/guides/self-checklist.md`：完成前自检清单。
- `docs/architecture/dependency-rules.md`：分层依赖与访问边界。
- `docs/standards/opencode.md`：OpenCode 只读源码快照、平台适配和 generated SDK 边界。
- `docs/architecture/local-opencode-client.md`：本地 OpenCode 反向隧道、fencing、文件安全和运行目标冻结。

## 技术栈与编码规范

- `backend/README.md`：后端多模块总览与技术栈。
- `frontend/README.md`：前端工程总览与技术栈（技术栈版本以本文件为单一来源）。
- `docs/standards/backend.md`：后端编码、测试、性能、错误处理、可观测性、数据变更规范。
- `docs/standards/frontend.md`：前端编码、性能、测试规范。
- `docs/standards/metrics-glossary.md`：NVIDIA AIPerf / AI Perf Metrics Reference 英文缩写与性能指标对照指南。
- `docs/standards/security.md`：安全、日志脱敏、PTY 安全例外。

## 找功能（模块/包定位）

- `docs/architecture/module-map.md`：后端模块与前端包职责速查，按功能定位代码位置。
- `docs/architecture/dependency-rules.md`：模块归属与依赖方向，新增文件前必读。
- `docs/architecture/xxl-job-integration.md`：XXL-JOB 3.4.2 集成、SSO、数据库、executor 与夜间任务分发边界的单一事实源。
- `docs/architecture/lobehub-integration.md`：LobeHub 平台票据、部门 Workspace JIT、企业模型适配和独立 fork 交付契约。
- `docs/architecture/domain-models.md`：核心领域模型说明，Session/Run 等模型概念与关系。
- `backend/test-agent-*/README.md`：各后端模块职责与依赖边界。
- `frontend/packages/*/README.md`、`frontend/apps/agent-web/README.md`：各前端包职责。

## 前后端 API 约定

- `docs/api/http-api.md`：HTTP API 路径、方法、请求/响应、错误码、traceId。
- `docs/api/external-api.md`：外部 API Key 管理、用户 SSH Key 查询及 TAEK1 跨语言解密契约。
- `docs/api/event-stream.md`：RunEvent SSE 事件类型、字段、续传规则（单一事实源）。

## 对话场景造数

- `docs/testing/conversation-scenes.md`：直接对话、历史运行中、Todo、ask、permission、subagent、宠物旁路成功/失败等可重复 fixture 入口。
- `docs/testing/application-worktree-feature-cases.md`：应用 worktree、feature、角色写权限、发布投影和固定 UI 测试数据案例。
- `docs/testing/app-source-snapshot.md`：应用源码固定提交、多服务器物化、独立进度 WebSocket、文件能力和到期清理的自动化与人工验收。
- `docs/testing/xxl-job-integration.md`：XXL-JOB 自动化、双 Java、故障隔离和安全验收清单。
- `docs/testing/internal-model-observability-local.md`：企业内部模型调用可观测性的本地验证——用 `tools/mock-model-server.py` 在不部署/不连真实企业端点时复现成功、上游错误、超时、连接失败并核对明细/探活/查询 API。
- `docs/testing/opencode-observability-acceptance.md`：OpenCode 1.18.4 插件化运营指标、DSH Trace、本地低优先级上传、集中归档和企业离线交付的八组验收记录。

## 产品介绍与宣传素材

- `docs/assets/marketing/mimo-recent-features-announcement.md`：可直接用于群公告或邮件的近期功能简短介绍。
- `docs/assets/marketing/deep-space/mimo-product-intro-email.md`：面向跨部门使用与共建邀请的完整邮件正文。

## 部署与数据库

- `docs/deployment/backend.md`：后端 Java 进程容器部署。
- `docs/deployment/opencode-upgrade-1.18.4.md`：OpenCode 1.18.4 / OpenAPI Generator 7.24.0 差异、影响、验证与回滚基线。
- `docs/deployment/local-opencode-client.md`：ARM64 客户端签名打包、Nginx HTTP 分发、用户服务安装和风险。
- `docs/deployment/codex-whitebox-mcp.md`：官方 Codex 0.145.0 MCP、企业 DeepSeek 路由、无审批夜间分析、原生参数风险、Linux 4.19 / Docker 18.09.7 预检与回滚。
- `docs/deployment/frontend.md`：前端 Vue + Vite 生产构建与部署。
- `deploy/internal/CLICKHOUSE-ANALYTICS.md`：运营分析 ClickHouse 专机的离线打包、部署、回填、验收、清理和回滚。
- `docs/deployment/toolbox.md`：IT-Tools + OmniTools 的 193 项离线目录、派生源码、双后台共置容器、Nginx、增量发布与回滚。
- `docs/deployment/lobehub-offline.md`：LobeHub/ParadeDB/RustFS 独立离线制品、安装、Redis ACL、启动、验收和回滚。
- `docs/deployment/qa-memory.md`：通用长期记忆、多节点 Mem0、独立 CPU BGE/pgvector、离线交付、端到端验收与回滚。
- `docs/deployment/lobehub-client-build.md`：LobeHub Windows/Linux 原生客户端构建、Authenticode、Linux 双人审批与证据汇集。
- `docs/deployment/lobehub-fork-transfer.md`：LobeHub 独立 fork 的最小 ref Git Bundle、企业 Git 导入、校验和回滚。
- `deploy/internal/SINGLE-BACKEND.md`：企业内单 Java 后台 + 单 worker 离线部署。
- `deploy/internal/MULTI-BACKEND.md`：企业内两个或更多 Java/worker 节点部署与跨节点验收。
- `deploy/internal/REDIS-OFFLINE.md`：当前本地 Redis 7.4.9 的独立 linux/amd64 离线封包、企业 Redis 5.0 停写备份、升级验证与回滚。
- `deploy/internal/FULL-UPGRADE-RUNBOOK.md`：当前企业 Redis 5 升级至 7.4.9，再按 `.4 → .114 → .2` 发布平台的完整逐机执行手册；中转机固定使用 `~/Desktop/mimoagent/0709`。
- `deploy/local/README.md`：`192.168.8.100` 测试机 Jenkins 的 `release` 分支构建、真实 PostgreSQL 克隆升级门禁、首次宿主接管、不可变发布与回滚说明。
- `deploy/internal/collect-recent-process-logs.sh`：不依赖 JSON/高速搜索专用工具的最近 `1..14` 天进程日志只读采集、脱敏、限量归档与初步诊断摘要。
- `deploy/internal/EMPTY-RESPONSE-BODY-TROUBLESHOOTING.md`：企业部署后 HTTP/SSE“空报文体”的逐层只读采证、节点对比、模型链路定位与重部署停止条件。
- `deploy/internal/XXL-JOB-TROUBLESHOOTING.md`：当前企业双后台 XXL-JOB 管理页、SSO、Admin、executor 和共享 MySQL 的只读排查手册。
- `docs/deployment/database.md`：Flyway migration、核心表与兼容策略。

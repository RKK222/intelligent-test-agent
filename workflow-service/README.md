# workflow-service

## 工程定位

独立 Python 3.12 长程任务控制面。浏览器经 Nginx 同源直连本服务；Java 不代理、不保存也不查询本服务的对话、消息、任务、运行、事件或报告。

本服务负责：

- FastAPI HTTP 与原生 AG-UI SSE。
- 平台 Redis Bearer Token 精确键认证。
- AgentScope 白名单意图识别、报告综合和报告问答。
- LangGraph 固定拓扑、checkpoint、恢复和节点幂等。
- PostgreSQL 业务持久化、带 fencing 的 Worker 租约与 48 小时工作区清理。
- 调用 Java 窄平台能力接口与独立 Analysis Runner；本进程不持有 Docker Socket、个人 SSH 私钥或供应商 Token。

AG-UI durable 投影在 `RUN_STARTED/RUN_FINISHED/RUN_ERROR` 时清理旧的待输入状态；因此实时消费和断线快照都不会在 run 已前进或终止后继续暴露失效的恢复卡片。

首个且唯一注册的工作流是 `code-change-impact-analysis`。场景 2～4 只能通过新增 `WorkflowDefinition` 和对应图实现扩展，当前不注册、不暴露可执行入口。

## 进程

- `testagent-workflow api`：HTTP/AG-UI 服务。
- `testagent-workflow worker`：PostgreSQL `FOR UPDATE SKIP LOCKED` 任务认领、LangGraph 执行和工作区到期清理。
- `testagent-workflow migrate`：显式执行 Alembic 业务表迁移。
- `testagent-workflow checkpoint-setup`：显式初始化 LangGraph checkpoint 表。

API 与 Worker 使用同一镜像、不同容器。二者都不能挂载 Docker Socket。

Worker 的状态收敛、事件发布和工作区停止都必须在续租成功后执行；运行终态由
PostgreSQL 同时校验 owner token、租约期限、`RUNNING` 和未取消状态后原子写入。旧 Worker
丢失租约时只精确撤销自己持有的模型 grant，不得停止、保留或改写已由新 Worker
接管的共享工作区。部署配置的 Worker ID 只作逻辑前缀，每个进程会追加随机 owner
token；进程重启不能复用旧进程的租约身份。
Runner 失败保留被拒绝时，Worker 必须先把工作区租约写成带立即到期时间的
`CLEANUP_FAILED`，再把 run 写入失败终态。控制库写入会做有界短暂重试；仍不可用时保持
`RUNNING` 且停止心跳，由租约到期后的 Worker 接管依靠节点幂等再次收敛，禁止留下
`ACTIVE + expiresAt=NULL` 后仍终态化 run。

## 数据边界

- 业务表由 `migrations/` 中的 Alembic 管理，位于独立 `test_agent_workflow` 数据库。
- LangGraph checkpoint 使用官方 PostgreSQL saver 的独立初始化命令。
- 不使用平台 Flyway、不写平台业务表，也不使用平台 RunEvent。
- 对话和 Markdown/结构化报告长期保留；源码、工具原始日志与临时输出由 Runner 按最后一次分析、局部重分析或有效期内报告追问结束后 48 小时清理，已过期工作区不会被追问复活。
- 主动取消对 `CANCELED` 幂等，但不能把成功或失败终态改写为取消；Runner 删除失败会把工作区立即标记为 `CLEANUP_FAILED`，交给数据库清理循环持久重试。

## 认证与共享能力

- 普通 HTTP/SSE 请求只读取 `test-agent:token:<完整Bearer>` 与其 `PTTL`，每次请求都重新校验；SSE 每 30 秒复核。
  平台 Jackson 当前把 `AuthPrincipal.issuedAt/expiresAt` 写为 Unix 秒数，认证器同时兼容该格式和历史 ISO 时间文本。
- Python 调 Java 时只发送用户 ID、Bearer SHA-256 摘要、请求体摘要、时间戳、nonce 和 HMAC；不发送原始 Bearer。
- Java 只提供仓库授权/分支、一次性 checkout ticket、模型 grant 和超级管理员角色复核。
- `TEST_AGENT_WORKFLOW_ANALYSIS_MODEL_NAME` 是平台模型网关公开模型 ID。Worker 将它随受签名 Runner 请求下发；Runner把真实grant隔离在任务容器内独立UID的回环relay，Codex/OpenCode只使用本地token并固定到该模型，不能使用工具默认 provider 或公网端点。
- 至少一个代码智能体成功但 AgentScope 综合连续失败时，使用带 analyzer 来源的确定性降级报告并把 run 标记为 `PARTIAL_FAILED`；只有全部代码智能体失败才不发布报告。

稳定协议见：

- `docs/api/workflow-api.md`
- `docs/api/workflow-ag-ui.md`
- `docs/deployment/workflow-offline.md`

## 开发与测试

```bash
uv sync --frozen
PYTHONPATH=src uv run pytest tests
uv run testagent-workflow migrate
uv run testagent-workflow checkpoint-setup
```

涉及 PostgreSQL 锁、唯一约束、Alembic 或清理状态时必须运行 `tests/test_postgres_store.py` 的真实 PostgreSQL Testcontainers 测试。依赖版本与哈希以 `uv.lock` 为准。

## 禁止事项

- 不得调用 Java 的对话、任务、报告或事件接口，也不得新增此类 Java 接口。
- 不得读取个人 SSH 私钥或供应商真实 Token。
- 不得访问 Docker Socket、宿主机工作区或 OpenCode 源码快照。
- 不得让模型决定图节点、任意工具名或未注册 `intentId`。

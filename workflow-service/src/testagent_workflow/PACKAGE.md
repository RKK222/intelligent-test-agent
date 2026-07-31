# testagent_workflow 包边界

- `api.py` / `agui.py`：独立 HTTP、SSE、快照与回放协议。
- `auth.py`：平台 Token Redis 精确读取，不暴露扫描或写入接口。
- `application.py` / `intent.py` / `registry.py`：对话输入、AgentScope 结构化分类和代码注册白名单。
- `impact_engine.py` / `workflows/`：场景 1 的固定 LangGraph 图和外部能力端口。
- `platform.py`：Python 到 Java 窄能力 HMAC 客户端。
- `runner_client.py`：Worker 到 Runner HMAC 客户端及场景 1 适配器。
- `database.py` / `store.py`：独立 PostgreSQL 与测试内存存储；不得连接平台数据库。
- `worker.py`：任务租约、checkpoint 执行及工作区清理状态收敛。
- `reports.py`：结构化报告、Markdown、版本合并和仅报告问答。

依赖方向固定为 API/Worker → 应用层/注册表/图 → 存储与外部端口。Runner、Java 和模型网关都只能通过显式客户端调用，不能在图节点中临时拼 URL 或读取密钥。

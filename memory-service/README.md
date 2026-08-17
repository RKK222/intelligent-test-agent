# testagent-memory-service

独立、无状态的通用 Mem0 REST 数据面，锁定 `mem0ai==2.0.17`。所有副本共享独立 PostgreSQL/pgvector；Java 只访问 REST，不连接该数据库。本镜像不包含 BGE 权重、torch 或本地 history 卷。

## 接口

除 `/health` 外均要求 `X-Memory-Service-Key`：

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/health` | 仅进程存活，不代表数据库/profile 可用 |
| `GET` | `/ready` | 共享 collection、profile、投影 outbox 和 `rawMessageCount=0` |
| `POST` | `/memories` | 官方风格 add；学习使用 `messages + infer=true`，投影/手工使用 `infer=false` |
| `GET/PUT/DELETE` | `/memories/{logicalMemoryId}` | 读取、更新、删除逻辑记忆 |
| `GET` | `/memories/{logicalMemoryId}/history` | 共享逻辑版本历史 |
| `POST` | `/search` | 最多三个 scope；多 profile RRF 合并 |

旧 `/memory-api/v1/**` 返回 `410 API_GONE`。

原生学习不传自定义 prompt/custom instructions，不做 QA 分类、显式/隐式/临时判定或置信度规则。当前 Run 的 USER/ASSISTANT 只作为一次请求交给 `Mem0.add(..., infer=true)`；服务不保存 Mem0 message history，`rawMessageCount()` 固定为 0。metadata 任意层级禁止原始消息、prompt、answer 和 transcript 字段。

## 多 profile 与共享控制面

- 无企业 embedding：只使用固定 CPU profile。
- 有企业 embedding：企业 profile 为原生抽取首选；不可用时 CPU profile 执行唯一一次抽取。
- `infer=true` 前在共享操作表写 at-most-once 标记；重试优先按 operationId 恢复已写向量，LLM 已开始但无法恢复时返回空结果，绝不再次抽取。幂等绑定请求摘要，不能用同一 key 改写内容或跨分区复用。
- 原始结果使用稳定 `logicalMemoryId`，以 `infer=false` 投影另一个 collection；不同维度/模型永不混入同一 collection。
- 更新、删除和 scope 提升按逻辑版本同步到全部 profile；失败进入 PostgreSQL outbox，由各副本竞争领取并补偿。后台巡检还会对共享逻辑版本与当前 profile 清单做差异扫描，自动补齐“逻辑提交后、outbox 写入前退出”的崩溃窗口，以及后来新增企业 profile 的历史投影。单任务连续 12 次失败标记为 `DEAD`，冷却 5 分钟后若版本差异仍存在会自动重新入队，避免 provider 恢复后永久搁置。
- 检索并行查询可用 profile，按 `logicalMemoryId` 去重并用 Reciprocal Rank Fusion 合并，不比较跨模型 score。单 profile embedding 默认 1.5 秒超时、硬上限 1.8 秒，为 Java 2 秒总预算预留 RRF 和 HTTP 返回时间。
- 同一 owner/Application 分区使用 PostgreSQL advisory lock 串行写；history、幂等、逻辑版本、投影状态和 outbox 全部位于共享库。
- Application 个人记忆同时使用由 Application ID 单向摘要得到的 Mem0 `run_id` 作为原生去重分区；这是内部作用域键，不是平台 Run ID，也不保存对话。它避免 Mem0 2.0.17 仅按 user/session entity 去重时让一个 Application 的事实抑制另一个 Application。

独立记忆库只由 `alembic upgrade head` 管理。不要让每个 Web 副本自动迁移，也不要引入 Java Flyway。

## 模型访问

Mem0 的 CHAT 与 embedding provider 均回调 Java 固定 base path `/api/internal/platform/model-gateway/v1`。每次请求使用 HMAC-SHA256，签名覆盖正文摘要、平台用户、Run、Session、operation、时间、nonce、能力和 `query/document` 类型。服务不持有供应商 URL 或供应商 Token。

CPU profile 的模型实际运行在相邻的独立 `embedding-service`；模型身份、revision、512 维、L2 和查询前缀见其 README。memory-service 镜像运行时不访问 Hugging Face，Mem0 telemetry 关闭。

## 配置

所有变量使用 `TEST_AGENT_MEMORY_SERVICE_` 前缀，主要配置：

```text
API_KEY
DATABASE_URL
MODEL_GATEWAY_URL
MODEL_GATEWAY_CLIENT_ID
MODEL_GATEWAY_HMAC_SECRET
CHAT_MODEL_ID
CPU_EMBEDDING_MODEL_ID
ENTERPRISE_EMBEDDING_MODEL_ID             # 三项同时为空或同时配置
ENTERPRISE_EMBEDDING_DIMENSION
ENTERPRISE_EMBEDDING_FINGERPRINT
POSTGRES_POOL_MIN_SIZE
POSTGRES_POOL_MAX_SIZE
PROJECTION_BATCH_SIZE
PROJECTION_POLL_SECONDS
SEARCH_PROFILE_TIMEOUT_SECONDS             # 默认 1.5，硬上限 1.8 秒
```

API key/HMAC secret 至少 32 字节。配置及正文不能写日志。运行容器采用非 root、只读根文件系统和临时 `/tmp`，不挂载本地数据卷。

## 测试

```bash
uv sync --frozen --dev
uv run pytest
```

真实 Mem0 2.0.17 + 双 collection + PostgreSQL/pgvector 集成测试（embedding 经内存模型网关桩，不下载权重）：

```bash
TESTCONTAINERS_RYUK_DISABLED=true TEST_AGENT_MEMORY_RUN_INTEGRATION=true \
  uv run pytest tests/test_pgvector_integration.py
```

本地三副本数据面：

```bash
tools/memory-dev-services.sh prepare
tools/memory-dev-services.sh build
tools/memory-dev-services.sh start
tools/memory-dev-services.sh status
```

完整架构、限值、企业离线包、发布顺序、真实浏览器 E2E 和回滚见 `docs/deployment/qa-memory.md`。

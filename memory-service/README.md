# testagent-memory-service

QA Agent 长期记忆的独立数据面。服务固定使用 `mem0ai==2.0.3`，只暴露平台需要的健康、Embedding profile、派生记忆 CRUD/search/history 和候选抽取窄接口；不提供 Mem0 上游通用 API。

## 数据边界

- `documents` 中的正文必须已经是“稳定测试工作习惯”候选或生效记忆，不允许传入整段聊天。
- 原始聊天只由 OpenCode Session/现有恢复链路保存。候选抽取请求在内存中短暂使用本轮用户消息与最终回答，响应后不在本服务保存。
- `NoRawMessageHistoryManager.save_messages()` 永远为空操作，`get_last_messages()` 永远为空；合同测试同时检查 SQLite `messages` 为 0 行。Mem0 的派生记忆增删改历史仍写入持久化 `/data/mem0-history.db`。
- 向量正文、metadata 和 512 维向量写入独立 PostgreSQL + pgvector。平台 PostgreSQL 只保存治理状态和安全摘要。

## 固定 Embedding profile

- Provider：`LOCAL_BGE`
- 模型：`BAAI/bge-small-zh-v1.5`
- revision：`7999e1d3359715c523056ef9478215996d62a620`
- 维度：512，CPU，L2 归一化
- 搜索文本前缀：`为这个句子生成表示以用于检索相关文章：`
- 权重在镜像构建期下载到 `/models/BAAI__bge-small-zh-v1.5` 并写入身份清单。运行期设置 HuggingFace/Transformers offline，代码也要求 `local_files_only=True`。

集合名由 provider、模型、revision 前缀、维度、collection version 和完整 profile SHA-256 摘要构成。未来接入企业 Embedding 必须新增 Provider 和集合版本，不能覆盖 V1 集合。

## 本地测试

```bash
cd memory-service
uv sync --dev
PYTHONPATH=src uv run pytest tests
```

真实 pgvector/BGE smoke 使用 `TEST_AGENT_MEMORY_RUN_INTEGRATION=true` 显式开启；需要固定 revision 模型目录以及 Docker。

## 运行配置

所有配置使用 `TEST_AGENT_MEMORY_SERVICE_` 前缀。`API_KEY` 至少 32 字节；`DATABASE_URL`、`API_KEY` 不得输出日志。抽取模型通过 `EXTRACTION_GATEWAY_URL=/api/internal/platform/model-gateway/v1` 固定路径和短期 `mfg_` grant 调用，不接受供应商 URL 或长期密钥。

生产容器默认以 UID/GID `10004` 运行，`/models` 只读，只有 `/data` 需要持久化写权限。Mem0 telemetry、PostHog 和 HuggingFace 运行期网络访问均关闭。

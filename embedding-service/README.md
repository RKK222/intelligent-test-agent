# CPU Embedding 服务

本模块独立部署固定的 `BAAI/bge-small-zh-v1.5` CPU 模型，revision 锁定为
`7999e1d3359715c523056ef9478215996d62a620`。服务只暴露 OpenAI-compatible
`POST /v1/embeddings`、`GET /health` 和 `GET /ready`，不包含 Mem0，也不连接记忆库。

`X-Embedding-Input-Type` 必须显式为 `query` 或 `document`；仅查询向量增加中文 BGE
查询前缀。所有向量固定 512 维并执行 L2 归一化。模型权重在 Docker 构建期下载并写入
身份清单，运行时启用 Hugging Face 离线开关。

本地测试：

```bash
uv sync --frozen --dev
uv run pytest
```

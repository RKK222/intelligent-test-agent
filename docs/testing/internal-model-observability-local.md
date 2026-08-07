# 企业内部模型调用可观测性 — 本地验证指南

本文档说明如何**在不部署、不连接真实企业模型端点**的情况下，端到端验证「企业内部模型调用可观测性」：代理插桩明细落库、失败分类、定时/手动探活、观测查询 API。真实企业端点（如 `ai-code.sdc.icbc:9070`）只能内网访问，本指南用本地 mock 服务替代，覆盖成功、上游错误、超时、连接失败等全部故障模式。

## 前置条件

- 已按 `deploy/local/docker-compose.yml` 启动本地 PostgreSQL/Redis/MySQL。
- 后端可本地启动（`test-agent-app`），前端可访问系统管理页。
- `python3` 可用（macOS 自带，无第三方依赖）。

## 1. 启动 mock 模型服务

`tools/mock-model-server.py` 是纯标准库的 OpenAI-compatible mock，按故障模式返回不同响应：

```bash
# 成功（按请求 stream 参数返回 JSON 或完整 SSE）
python3 tools/mock-model-server.py --port 19070 --mode ok

# 上游 400 / 500（非 SSE 错误正文）
python3 tools/mock-model-server.py --port 19070 --mode http400
python3 tools/mock-model-server.py --port 19070 --mode http500

# 首响应、首有效输出、输出空闲三种不同超时
python3 tools/mock-model-server.py --port 19070 --mode header-timeout
python3 tools/mock-model-server.py --port 19070 --mode first-output-timeout
python3 tools/mock-model-server.py --port 19070 --mode idle-timeout

# 强制非流式 200 / SSE 响应头后立即 EOF
python3 tools/mock-model-server.py --port 19070 --mode nonstream-200
python3 tools/mock-model-server.py --port 19070 --mode empty
```

验证 mock 可用：

```bash
curl -N -X POST http://127.0.0.1:19070/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"mock-model","messages":[{"role":"user","content":"hi"}],"stream":true}'
```

## 2. 配置内部模型 provider 指向 mock

通过系统管理 → 内部模型供应商，新增/修改一个 provider：

| 字段 | 值 |
|---|---|
| providerId | `local-mock`（任意唯一 ID） |
| baseUrl | `http://127.0.0.1:19070`（**不要**带 `/chat/completions`，平台会拼接） |
| 关联 Token | 新增任意 Token（值任意，mock 不校验） |
| enabled | 勾选 |

并在该 provider 的模型目录中启用至少一个模型（探活会取第一个 enabled 模型的 `upstreamModelId` 作为请求 model）。

## 3. 触发一次真实转发（验证代理插桩明细）

用内部代理 API key 直接打代理端点（等价于 opencode 子进程的调用），`X-Enterprise-Model-Provider` 指定上面配置的 providerId：

```bash
# 从 backend.env 读取代理 key（本地默认空则省略 Authorization）
curl -N -X POST http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions \
  -H "Authorization: Bearer ${TEST_AGENT_INTERNAL_PROXY_API_KEY}" \
  -H "X-Enterprise-Model-Provider: local-mock" \
  -H "Content-Type: application/json" \
  -d '{"model":"mock-model","messages":[{"role":"user","content":"hi"}],"stream":true}'
```

用不同 `--mode` 重启 mock 后重复调用，可分别复现并核对以下分类：

| mock 模式 | 真实流式代理 outcome | 流式探活 outcome | httpStatus |
|---|---|---|---|
| `ok` / `sse` | `SUCCESS` | `SUCCESS` | 200 |
| `nonstream-200` | `SUCCESS`（非 SSE 原样透传） | `UPSTREAM_STREAM_INTERRUPTED` | 200 |
| `http400` / `http500` | `UPSTREAM_HTTP_ERROR` | `UPSTREAM_HTTP_ERROR` | 400 / 500 |
| `header-timeout` | `UPSTREAM_FIRST_RESPONSE_TIMEOUT` | `UPSTREAM_FIRST_RESPONSE_TIMEOUT` | 空（响应头未到） |
| `first-output-timeout` | `UPSTREAM_FIRST_EVENT_TIMEOUT` | `UPSTREAM_FIRST_EVENT_TIMEOUT` | 200 |
| `idle-timeout` | `UPSTREAM_STREAM_IDLE_TIMEOUT` | `UPSTREAM_STREAM_IDLE_TIMEOUT` | 200 |
| `empty` | `UPSTREAM_STREAM_INTERRUPTED` | `UPSTREAM_STREAM_INTERRUPTED` | 200 |

`first-output-timeout` 会持续发送 SSE 注释和 `data: ping`，`idle-timeout` 会在首个真实输出后持续发送注释；它们用于确认伪心跳不会延后对应截止时间。`timeout` 仍作为 `header-timeout` 的兼容别名。

> 连接失败：把 provider 的 baseUrl 指向未监听端口（如 `http://127.0.0.1:19999`），预期 `UPSTREAM_CONNECT_FAILED`。

## 4. 手动触发探活（验证探活状态）

系统管理 → 内部模型可观测 → 顶部探活状态卡片，或直接调用：

```bash
# 全量探活
curl -X POST http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/probe \
  -H "Authorization: Bearer <超管token>" -H "Content-Type: application/json" -d '{}'
```

探活固定发送 `stream=true,max_tokens=1`，必须收到至少一个真实输出 chunk 和 `[DONE]` 才成功。把 mock 切到 `http500` 或 `nonstream-200` 后再探活，观察探活状态卡片变红、`consecutiveFailures` 递增；切回 `ok` 后探活，连续失败清零。

## 5. 核对观测查询 API

```bash
# 明细（按 provider/outcome 过滤）
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/call-records?providerId=local-mock&page=1&size=20" \
  -H "Authorization: Bearer <超管token>"

# 小时聚合
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/stats?providerId=local-mock" \
  -H "Authorization: Bearer <超管token>"

# 仅真实用户调用（页面默认口径；探活不会混入业务指标）
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/stats?providerId=local-mock&source=USER_CALL" \
  -H "Authorization: Bearer <超管token>"

# 探活状态
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/probe-status" \
  -H "Authorization: Bearer <超管token>"
```

## 验证结论

- **插桩→分类→落库**：由 `InternalModelSseStreamObserverTest` 固化真实输出与伪心跳语义；`InternalModelProxyForwardingServiceTest` 覆盖首 token、`[DONE]` 流完成和流中断；`InternalModelProviderProbeServiceTest` 用本地 HttpServer 覆盖完整 SSE、空 200、超时、500 与连接拒绝。
- **查询/探活 API**：由 `InternalModelObservabilityControllerTest`、持久化集成测试覆盖。
- **本指南**用真实 HTTP 链路串起上述各层，作为部署前的人工交互复现，不替代真实企业端点验收。

## 已知边界

- mock 不校验 Token/鉴权，仅用于链路验证；真实环境仍走代理 key 与 provider Token。
- 三种 timeout 模式最长挂起 120 秒，客户端达到平台截止时间后会主动断开；需要切换模式时可直接重启 mock。`empty` 与 `nonstream-200` 用于验证 2xx 不会让流式探活误报健康。
- 明细的 `firstTokenMillis` 是首个真实输出到达耗时，`streamCompleteMillis` 是收到 `[DONE]` 的上游完整流耗时；`durationMillis` 还包含代理向下游写出和终态处理，不应拿来替代前两者。
- 真实企业端点的网络时延、真实 token 计数、TLS 与证书等，仍需部署后按 `deploy/internal/EMPTY-RESPONSE-BODY-TROUBLESHOOTING.md` 现场验收。

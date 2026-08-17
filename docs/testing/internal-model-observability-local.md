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

# 企业网关兼容：返回 finish_reason=stop 后直接 EOF，不另发 [DONE]
python3 tools/mock-model-server.py --port 19070 --mode finish-reason-eof
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

用内部代理 API key 直接打代理端点（等价于 opencode 子进程的调用），`X-Enterprise-Model-Provider` 指定上面配置的 providerId。这里的 `Authorization: Bearer` 只鉴权 Java 内部代理，不是上游供应商鉴权；真实转发时 Java 会删除它并改用上游 `Auth-Token`，因为企业上游的供应商 Bearer 模式不会让 `ucid` 生效：

```bash
# 从 backend.env 读取代理 key（本地默认空则省略 Authorization）
curl -N -X POST http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions \
  -H "Authorization: Bearer ${TEST_AGENT_INTERNAL_PROXY_API_KEY}" \
  -H "X-Enterprise-Model-Provider: local-mock" \
  -H "ucid: local-observability-user" \
  -H "Content-Type: application/json" \
  -d '{"model":"mock-model","messages":[{"role":"user","content":"hi"}],"stream":true}'
```

用不同 `--mode` 重启 mock 后重复调用，可分别复现并核对以下分类：

| mock 模式 | 真实流式代理 outcome | 流式探活 outcome | httpStatus |
|---|---|---|---|
| `ok` / `sse` | `SUCCESS` | `SUCCESS` | 200 |
| `finish-reason-eof` | `SUCCESS` | `SUCCESS` | 200 |
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

探活固定发送 `stream=true,max_tokens=1`，必须收到至少一个真实输出 chunk，并收到 `[DONE]` 或非空 `finish_reason` 才成功。把 mock 切到 `http500` 或 `nonstream-200` 后再探活，观察探活状态卡片变红、`consecutiveFailures` 递增；切回 `ok` 后探活，连续失败清零。

## 5. 核对观测查询 API

```bash
# 明细（按 provider 和看板结果大类过滤）
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/call-records?providerId=local-mock&outcomeGroup=UPSTREAM_FAILURE&page=1&size=20" \
  -H "Authorization: Bearer <超管token>"

# 明细按统一认证用户过滤；items 与 total 必须使用同一服务端条件
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/call-records?source=USER_CALL&ucid=AUTH_1&page=1&size=20" \
  -H "Authorization: Bearer <超管token>"

# 小时聚合
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/stats?providerId=local-mock" \
  -H "Authorization: Bearer <超管token>"

# 仅真实用户调用（页面默认口径；探活不会混入业务指标）
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/stats?providerId=local-mock&source=USER_CALL" \
  -H "Authorization: Bearer <超管token>"

# TTFT 箱线图五数概括；只统计确实开始返回回答的调用
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/ttft-distribution?providerId=local-mock&source=USER_CALL" \
  -H "Authorization: Bearer <超管token>"

# ITL / TPOT 箱线图五数概括；只统计至少 2 个输出 Token 且有准确 usage 的调用
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/itl-distribution?providerId=local-mock&source=USER_CALL" \
  -H "Authorization: Bearer <超管token>"

# Output TPS 平均值与五数概括；逐条计算 tokens/s 后再聚合
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/tps-distribution?providerId=local-mock&source=USER_CALL" \
  -H "Authorization: Bearer <超管token>"

# 探活状态
curl "http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-observability/probe-status" \
  -H "Authorization: Bearer <超管token>"
```

`outcomeGroup` 可选 `SUCCESS/REQUEST_OR_CONFIGURATION/UPSTREAM_FAILURE/CALLER_INTERRUPTED/OTHER`。返回项仍保留精确 `outcome` 供排障；用户调用的 `ucid` 会在页面“来源 / 用户 ID”列直接展示，明细接口支持忽略大小写的 UCID 子串过滤，过滤后的 `total` 应随之变化，探活记录则显示“探活”。如需核对单一底层原因，仍可使用兼容参数 `outcome=UPSTREAM_HTTP_ERROR`。

## 6. 独立复算，判断看板有没有算错

验证时先固定与页面完全相同的 `providerId/outcomeGroup/source/from/to`，再按下面的方法核对。不要只下载第 1 页：明细接口单页最多 100 条，必须翻页直到累计条数等于返回的 `total`。页面的明细分页只用于展示，Overview、供应商卡片和图表都使用全量数据。

把所有页的 `items` 合并到一个 JSON 数组文件（例如 `all-call-records.json`），然后运行下面的纯 Python 复算。它没有复用后端 SQL，可作为独立交叉检查：

```bash
python3 - all-call-records.json <<'PY'
import json, math, statistics, sys

rows = json.load(open(sys.argv[1], encoding="utf-8"))

def percentile_cont(values, p):
    values = sorted(values)
    if not values:
        return None
    rank = (len(values) - 1) * p
    low, high = math.floor(rank), math.ceil(rank)
    return values[low] + (values[high] - values[low]) * (rank - low)

def summary(values):
    return {
        "sampleCount": len(values),
        "averageMillis": statistics.fmean(values) if values else None,
        "minimumMillis": min(values) if values else None,
        "firstQuartileMillis": percentile_cont(values, 0.25),
        "medianMillis": percentile_cont(values, 0.50),
        "thirdQuartileMillis": percentile_cont(values, 0.75),
        "maximumMillis": max(values) if values else None,
    }

for provider_id in sorted({row["providerId"] for row in rows}):
    provider_rows = [row for row in rows if row["providerId"] == provider_id]
    ttft = [row["firstTokenMillis"] for row in provider_rows
            if isinstance(row.get("firstTokenMillis"), (int, float))]
    itl = []
    for row in provider_rows:
        first, last, count = (row.get("firstTokenMillis"), row.get("lastTokenMillis"),
                              row.get("outputTokenCount"))
        if (isinstance(first, (int, float)) and isinstance(last, (int, float))
                and isinstance(count, int) and count >= 2 and last >= first):
            itl.append((last - first) / (count - 1))
    print(json.dumps({"providerId": provider_id, "TTFT": summary(ttft), "ITL_TPOT": summary(itl)},
                     ensure_ascii=False, indent=2))
PY
```

核对标准：

- `REQ` 应等于全量数组长度，而不是当前页条数；`SR/FR` 分别按 `outcome == SUCCESS` 与非成功条数计算。
- E2E 使用每条 `durationMillis`；TTFT 只取非空 `firstTokenMillis`；SCT 只取非空 `streamCompleteMillis`。
- ITL/TPOT 只接受首末输出时间完整且 `outputTokenCount >= 2` 的记录，单条按 `(last-first)/(count-1)` 计算，单位为毫秒。
- Output TPS 还要求 `last > first`，单条按 `(count-1)*1000/(last-first)` 计算；必须先逐条计算后再取平均和分位数，不能用 `1000 / 平均 ITL` 反推。
- 脚本结果应与同一筛选条件下每个厂商分布接口的 `sampleCount/average/min/P25/P50/P75/max` 一致；浮点数允许极小的显示舍入差异。
- 最强的回归检查是准备两个厂商、每个厂商至少 4 个确定样本，再确认页面出现两个独立箱体。前端自动化测试还会在“当前页只有 1 条、服务端总量为 41 条”的情况下断言 Overview 仍读取全量聚合结果。

页面交互还需核对：打开时间筛选后点击自定义日期框并只选择开始端点，父弹层和日期面板都应继续显示且不发查询；选择完整区间后才收起并刷新。公开性能参考的“方法说明（离线）”和每张卡片的“数据源（离线快照）”应直接打开页面内弹窗，不依赖企业网络访问外站。调用结果分布与供应商请求量对比应各自独占整行，桌面端图表高度为 340px，窄屏不少于 300px。

## 验证结论

- **插桩→分类→落库**：由 `InternalModelSseStreamObserverTest` 固化真实输出、伪心跳与两种收尾信号语义；`InternalModelProxyForwardingServiceTest` 覆盖首 token、`[DONE]`、`finish_reason` 后 EOF 和无收尾信号的流中断；`InternalModelProviderProbeServiceTest` 用本地 HttpServer 覆盖两种完整 SSE、空 200、超时、500 与连接拒绝。
- **查询/探活 API**：由 `InternalModelObservabilityControllerTest` 固化 UCID、TTFT、ITL/TPOT 和 Output TPS 的筛选与返回字段；H2 持久化集成测试验证用户过滤后的分页总量、平均值、四分位数、准确用量筛选与空样本，PostgreSQL Testcontainers 测试验证生产数据库的 `avg` 与 `percentile_cont` 结果。
- **本指南**用真实 HTTP 链路串起上述各层，作为部署前的人工交互复现，不替代真实企业端点验收。

## 已知边界

- mock 不校验 Token/鉴权，仅用于链路验证；`ok/sse/finish-reason-eof` 会返回两段模型输出和准确 usage，便于形成 ITL/TPOT 与 Output TPS 样本。真实环境仍走代理 key 与 provider Token。
- 三种 timeout 模式最长挂起 120 秒，客户端达到平台截止时间后会主动断开；需要切换模式时可直接重启 mock。`empty` 与 `nonstream-200` 用于验证 2xx 不会让流式探活误报健康。
- 明细的 `firstTokenMillis`/`lastTokenMillis` 是首末真实输出到达耗时，`outputTokenCount` 是上游准确用量，三者计算 ITL/TPOT 与 Output TPS；`streamCompleteMillis` 是收到 `[DONE]` 或非空 `finish_reason` 的上游完整流耗时，`durationMillis` 还包含代理向下游写出和终态处理。
- 真实企业端点的网络时延、真实 token 计数、TLS 与证书等，仍需部署后按 `deploy/internal/EMPTY-RESPONSE-BODY-TROUBLESHOOTING.md` 现场验收。

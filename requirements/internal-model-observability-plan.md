# 企业内部模型 API 调用可观测性 — 工程落地方案

## Context

企业内部部署后，opencode/codex 调用企业内部模型端点总是失败，当前排查手段只有人工 grep manager 日志 + 手工 curl 代理端点（`deploy/internal/EMPTY-RESPONSE-BODY-TROUBLESHOOTING.md` 第 9 节），没有任何自动化的调用记录、统计或探活。

调用链：opencode 子进程 → 同节点 Java `InternalModelProxyController`（`/api/internal/platform/opencode-runtime/internal-model-proxy/v1/**`）→ `InternalModelProxyForwardingService` WebClient 转发 → 企业模型端点（如 `ai-code.sdc.icbc:9070`）。Java 代理层是唯一同时知道 provider、model、上游状态码、耗时的插桩黄金位置。

已确认需求范围：
- 只覆盖内部模型代理链路（不覆盖 ModelGateway/LobeHub 链路）
- 能力：明细调用记录落库 + 小时级聚合统计 + XXL-JOB 定时主动探活
- 失败只存结构化字段（不存任何请求/响应体内容，含错误正文）
- 入口：超级管理员管理 API + 前端管理页面

## 模块归属

| 层 | 位置 |
|---|---|
| 领域枚举/记录/端口 | `test-agent-domain` 新包 `internalmodelobservability` |
| SQL（MyBatis XML 三段式） | `test-agent-persistence` |
| 记录服务、失败分类器、探活、查询服务、XXL handler | `test-agent-opencode-runtime` 新包 `internalmodel.observability` |
| 插桩挂钩（仅信号采集） | `test-agent-api` 现有 `InternalModelProxyForwardingService`/`Controller` |
| 管理 Controller/DTO | `test-agent-api` |
| 前端 | `frontend/apps/agent-web` 系统管理面板 |

依赖方向：api → opencode-runtime 已存在（Registry 即如此引用），合法。分类/落库逻辑不留在 api 层。不复用 `ModelCapabilityProbeService`（opencode-runtime 不得依赖 model-gateway，且其语义是模型能力探测非 provider 连通性探活）。

## 1. Flyway migration（PostgreSQL 16）

新文件 `backend/test-agent-persistence/src/main/resources/db/migration/V20260807HHMMSS__create_internal_model_observability.sql`（时间戳取编写时刻，当前最新 `V20260806190500`），三张表一次建齐：

**`internal_model_call_records`（明细，append-only，保留 30 天）**
`id / provider_id / model / endpoint / source(USER_CALL|PROBE) / outcome / http_status / error_class / streaming / duration_ms / first_byte_ms / trace_id / ucid / started_at`
索引：`(provider_id, started_at desc)`、`(outcome, started_at desc)`、`(started_at)`（retention 用）。

**`internal_model_call_stats_hourly`（小时聚合，upsert-on-write，保留 180 天）**
主键 `(stat_hour, provider_id, model, endpoint, source, outcome)`，累加 `request_count / duration_ms_sum / duration_ms_max`。
选 upsert-on-write 而非 rollup job：与 `model_gateway_usage_daily` 先例一致，单条原子累加、近实时。

**`internal_model_probe_status`（每 provider 一行的最新探活态）**
`provider_id(pk) / last_outcome / last_http_status / last_error_class / last_duration_ms / last_probed_at / last_success_at / consecutive_failures / trace_id / updated_at`

## 2. 域层（test-agent-domain）

```java
enum InternalModelCallOutcome {
  SUCCESS, PROXY_AUTH_FAILED, PROVIDER_UNAVAILABLE, REQUEST_INVALID,
  UPSTREAM_CONNECT_FAILED, UPSTREAM_FIRST_RESPONSE_TIMEOUT,
  UPSTREAM_FIRST_EVENT_TIMEOUT, UPSTREAM_STREAM_IDLE_TIMEOUT,
  UPSTREAM_HTTP_ERROR, UPSTREAM_STREAM_INTERRUPTED, UPSTREAM_STREAM_FAILED,
  CLIENT_DISCONNECTED, UNKNOWN_ERROR }
enum InternalModelCallSource { USER_CALL, PROBE }
record InternalModelCallRecord(...)      // 上表字段
record InternalModelProbeStatus(...)
interface InternalModelCallRecordRepository {
  void record(r);                        // 明细 insert + 聚合 upsert 同事务
  PageResponse<...> query(q); List<...> queryHourlyStats(q);
  int deleteRecordsBefore(cutoff); int deleteHourlyStatsBefore(cutoff); }
interface InternalModelProbeStatusRepository { void upsert(s); List<...> findAll(); }
```

## 3. 持久化层（test-agent-persistence）

- `mybatis/InternalModelObservabilityMapper.xml`：`insertCallRecord`、`incrementHourlyStat`（postgresql `on conflict do update` + 默认 merge 双方言，照抄 `ModelGatewayUsageDailyMapper.xml`）、`findCallRecords`/`countCallRecords`（动态过滤 + 分页，照抄 `SupportAccessMapper.xml`）、`findHourlyStats`、两个 `delete*Before`、`upsertProbeStatus`（`consecutive_failures` 成功归零、失败 +1）、`findAllProbeStatus`
- Mapper 接口 + `MyBatisInternalModelCallRecordRepository` / `MyBatisInternalModelProbeStatusRepository`（`record()` 加 `@Transactional`）

## 4. 插桩（核心）

### 4.1 记录服务（opencode-runtime `internalmodel.observability`）

- `InternalModelCallRecorder`：`record(r)` = `Mono.fromRunnable(repo::record).subscribeOn(boundedElastic()).onErrorResume(log+empty)`；另有 fire-and-forget 同步入口。**异步策略选 boundedElastic 直插，不做内存队列批量**——低 QPS 场景，照抄 `ModelGatewayForwardingService.recordUsage` 模式，宕机丢个别记录可容忍。
- `InternalModelCallOutcomeClassifier`：异常/信号 → outcome 的唯一映射点；`error_class` 存 `Exceptions.unwrap` 后的类简名，**不存 message**（可能含 URL，遵守 security.md:206）。

### 4.2 forwarding 改造（`test-agent-api/.../platform/InternalModelProxyForwardingService.java`）

1. `validateModel(byte[])` → `validateAndExtractModel(byte[])`（解析循环已拿到 model 局部变量，返回即可）；`/responses` 分支从 `ConvertedRequest.model()` 拿。
2. `forward()` 建可变 `CallObservation`（startedNanos、providerId、model、endpoint、streaming、firstByte、firstEvent、httpStatus、streamOutcome、AtomicBoolean 防重）。
3. 首字节：`exchangeToMono` 回调里（`responseHeadersReady.tryEmitEmpty()` 处）`markFirstByte()` + 记状态码。
4. 首事件：`writeResponse` 对 upstream SSE flux 加 `doOnNext(markFirstEvent)`（幂等），用于区分三种 timeout。
5. 流中断/失败：responses 补偿分支 `convertResponsesFailure(...)` 里当 `failIfIncomplete` 实际产生补偿事件时 `markStreamOutcome(INTERRUPTED/FAILED)`。
6. 终态挂钩加在 **`Mono.firstWithSignal(...)` 的结果上**（不能挂内层 request，否则 header timeout 会误记为 CLIENT_DISCONNECTED）：
   `doOnError(分类) . doOnCancel(CLIENT_DISCONNECTED) . doFinally(record，lambda 内 try-catch)`。
   终态优先级：streamOutcome > 异常分类 > httpStatus 非 2xx → UPSTREAM_HTTP_ERROR > SUCCESS。
7. `InternalModelProxyController.proxy` 的 `Mono.defer` 内 catch `prepareRequest` 的同步 `PlatformException`（UNAUTHENTICATED → PROXY_AUTH_FAILED；provider 解析失败 → PROVIDER_UNAVAILABLE）记录后 rethrow。

分类映射表：`WebClientRequestException`+Connect/UnknownHost/ConnectTimeout → CONNECT_FAILED；`TimeoutException` 按 firstByte/firstEvent 标记区分 FIRST_RESPONSE / FIRST_EVENT / STREAM_IDLE 三种 timeout；VALIDATION_ERROR/PAYLOAD_TOO_LARGE → REQUEST_INVALID；其余 → UNKNOWN_ERROR。

**铁律：观测绝不影响转发主链路**——recorder 内 onErrorResume + doFinally lambda try-catch 双保险。

## 5. 定时探活 + retention（opencode-runtime）

- `InternalModelProviderProbeService`：遍历 registry 内存快照（与真实转发同源，语义正确）中启用的 provider，取其首个 enabled model，POST `{baseUrl}/chat/completions` 最小 body（`max_tokens:1, stream:false`，同 `ModelCapabilityProbeService.CHAT` 样本），connect 10s / response 30s；结果落明细表（`source=PROBE`，ucid=`platform-probe`）+ upsert probe_status；失败分类复用 Classifier。提供 `probeAll` / `probeProvider`。
- `InternalModelProbeTaskHandler implements ScheduledTaskHandler`：taskKey `opencode-runtime.internal-model-probe`，每 5 分钟，GLOBAL_MUTEX 锁 TTL 4 分钟（照抄 `AnalyticsRollupTaskHandler`）。
- `InternalModelObservabilityRetentionTaskHandler`：taskKey `opencode-runtime.internal-model-observability-retention`，每日 03:30，删 30 天前明细 + 180 天前聚合。
- XXL 独立 MySQL migration：`backend/test-agent-xxl-job-integration/src/main/resources/xxl-job/db/migration/V10__register_internal_model_probe_task.sql`、`V11__register_internal_model_observability_retention_task.sql`（当前最新 V9，照抄 V9 模板）。

## 6. 管理 API（test-agent-api）

新 `InternalModelObservabilityController` + DTO，`@RequestMapping("/api/internal/platform/opencode-runtime/internal-model-observability")`，超管鉴权 + boundedElastic（照抄 `InternalModelProviderManagementController`）；查询经 opencode-runtime 的 `InternalModelObservabilityQueryService`（页大小 ≤100、时间范围默认 24h/上限 31 天），Controller 不直调 Repository。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/call-records?providerId&outcome&source&from&to&page&size` | 明细分页 |
| GET | `/stats?providerId&from&to` | 小时序列 + provider×outcome 汇总（成功率/均值/最大耗时） |
| GET | `/probe-status` | 逐 provider 最近探活状态 |
| POST | `/probe`（body 可选 providerId） | 手动触发探活，同步返回结果 |

## 7. 前端（frontend/apps/agent-web）

- `packages/shared-types`：新增 4 个类型；`packages/backend-api`：新增 4 个 client 方法。
- 新组件 `src/components/system/InternalModelObservabilityPanel.vue`（骨架照抄 `InternalModelProviderPanel.vue`）：顶部 provider 探活状态卡片（红绿点、consecutive_failures、"立即探活"按钮）；下方 tabs："调用记录"（el-table 分页 + provider/outcome/source/时间范围过滤）与"聚合统计"（provider×outcome 汇总表，不引入图表库）。
- `SystemManagementPanel.vue`：菜单增加 `internalModelObservability` 项（"内部模型供应商"后）+ 内容分支。

## 8. 文档同步

- `docs/api/http-api.md`：4 个新端点
- `docs/deployment/database.md`：三张新表 + 保留期
- `docs/architecture/xxl-job-integration.md`：两个新 taskKey
- `backend/test-agent-opencode-runtime/README.md`、persistence README
- `deploy/internal/EMPTY-RESPONSE-BODY-TROUBLESHOOTING.md` 第 9 节前加"先看内部模型可观测页面"引导

## 实施顺序

- **P1 明细 + 查询 API**：domain → migration → persistence + 集成测试 → recorder/classifier + 单测（覆盖全部分类映射）→ forwarding 插桩 + 既有测试扩展 → `/call-records` `/stats` API + WebFlux 测试 → http-api.md
- **P2 探活 + retention**：ProbeService + MockWebServer 单测（200/500/超时/拒绝连接）→ 两个 TaskHandler + XXL V10/V11 → `/probe-status` `/probe` API → 文档
- **P3 前端**：types/client → Panel 组件 + 菜单接线 → 前端测试 → troubleshooting 文档引导

按 AGENTS.md 要求每阶段完成后自检、更新 session-log 并用中文 commit 提交。

## 验证

- 后端：`mvn -pl test-agent-persistence,test-agent-opencode-runtime,test-agent-api test`
- 端到端失败分类验证（本地起服务后逐一 curl 代理端点）：错 apiKey → PROXY_AUTH_FAILED；不存在 provider → PROVIDER_UNAVAILABLE；缺 model → REQUEST_INVALID；provider baseUrl 指向死端口 → UPSTREAM_CONNECT_FAILED；指向 `nc -l` 不回包 → FIRST_RESPONSE_TIMEOUT；指向本地 mock 500 → UPSTREAM_HTTP_ERROR；随后查 `/call-records` 核对
- 探活：改坏某 provider baseUrl，5 分钟后 probe_status 变红且 consecutive_failures 递增；XXL admin 确认任务注册
- 前端：超管菜单可见/非超管不可见、过滤分页、手动探活交互

## 风险点

1. **SSE 补偿分支与 doFinally 终态竞争**：responses 分支把上游异常吞成补偿事件后主链路正常 complete——用固定优先级归并 + AtomicBoolean 防双写，单测覆盖"interrupted 但 signal 是 ON_COMPLETE"。
2. **timeout 挂钩位置**：必须挂 `firstWithSignal` 结果，否则 FIRST_RESPONSE_TIMEOUT 被误记为 CLIENT_DISCONNECTED。
3. **H2 兼容**：聚合 upsert 双方言（databaseId 先例已有）；`greatest()` 在 H2 PG MODE 行为需集成测试确认。
4. **XXL V10 版本冲突**：提交前再次确认 migration 目录最新版本。
5. **探活流量**：5 分钟 × provider 数 × 1 请求（max_tokens=1），量级极小，文档写明可在 XXL admin 调整或停用。

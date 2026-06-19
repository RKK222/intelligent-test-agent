# 包说明：com.example.testagent.event

## 职责

平台事件包，负责 RunEvent 模型、SSE 输出、事件转换、事件回放和断线续传边界。

## 不负责

- 不直接暴露 generated SDK DTO 给前端。
- 不实现数据库细节。
- 不承载 Controller 入口。

## 主要程序清单

- `package-info.java`：说明 event 包是平台事件模型、SSE 和回放边界。
- `RunEventAppender`：通过 domain 端口追加事件草稿。
- `RunEventReplayService`：处理 Last-Event-ID 和事件增量回放。
- `RunEventSseMapper`：将 RunEvent 映射为 `ServerSentEvent`。
- `RunEventSseStreamService`：基于 polling 的 RunEvent SSE 输出服务；阻塞式回放查询 offload 到 `boundedElastic`，单次回放失败跳过本轮轮询并保持订阅。
- `RunEventSsePayload`：SSE body 的稳定平台事件载荷。
- 后续可新增内存广播、跨实例订阅恢复和订阅管理器。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Reactor。
- Spring WebFlux 的事件流类型。

## 禁止依赖

- generated SDK 直接返回给前端。
- `test-agent-app` Controller。
- persistence 实现细节。

## 上游调用方

- `test-agent-app` 的事件 API。
- opencode client facade 的事件转换流程。
- 前端 SSE 客户端。

## 下游依赖

- domain 事件模型。
- persistence 提供的事件查询和追加接口。
- observability 提供的 trace 和指标能力。

## 测试位置

- event 模块单元测试。
- SSE 集成测试。
- Last-Event-ID、seq 单调递增、断线续传和事件映射测试。

## 修改时必须同步更新

- `backend/test-agent-event/README.md`。
- `docs/api/event-stream-api.md`。
- `docs/backend/backend-testing-standards.md`，如果事件测试策略变化。
- 前端 adapter 契约文档，如果事件形态影响前端。

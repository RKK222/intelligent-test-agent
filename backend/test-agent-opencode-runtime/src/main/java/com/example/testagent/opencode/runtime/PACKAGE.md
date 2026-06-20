# 包说明：com.example.testagent.opencode.runtime

## 职责

opencode 运行态业务根包，负责平台 Session/Run 与远端 opencode 能力之间的业务编排。

## 不负责

- 不定义 HTTP Controller 或 API URL。
- 不直接访问 generated SDK。
- 不直接依赖 JDBC Repository 实现。

## 主要程序清单

- `session.SessionApplicationService`：会话创建、查询、消息和归档。
- `run.RunApplicationService`：Run 启动、路由、远端 session 创建/复用、事件订阅和取消。
- `run.RunDiffApplicationService`：Run 级 Diff 查询、接受和拒绝。
- `run.RunEventPersistencePolicy`：区分 durable RunEvent 与 transient live output，并清洗 tool 大字段。
- `run.RunMessageRecoveryService`：SSE 建连时从 opencode session projected messages 生成 transient message snapshot。
- `runtime.OpencodeRuntimeApplicationService`：Phase 11 opencode Web App runtime API 到 facade 的映射。
- `terminal.*`：PTY ticket、限流、WebSocket 背后的业务状态和本地进程适配。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-event`。
- `test-agent-opencode-client`。
- Reactor、Jackson、Spring Context。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。
- `test-agent-persistence` 实现细节。

## 修改时必须同步更新

- `backend/test-agent-opencode-runtime/README.md`。
- `docs/api/http-api.md` 或 `docs/api/event-stream.md`，如果影响 API、DTO 或事件。
- `docs/architecture/dependency-rules.md`，如果依赖边界变化。

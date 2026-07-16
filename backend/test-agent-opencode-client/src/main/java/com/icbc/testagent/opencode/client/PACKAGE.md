# 包说明：com.icbc.testagent.opencode.client

## 职责

业务侧 opencode client facade，隔离 generated SDK、统一 traceId 透传、错误映射、超时重试和 raw event 到平台 RunEvent 的转换。

## 不负责

- 不承载 Controller。
- 不访问数据库或 Repository。
- 不返回 generated SDK DTO 给 domain、app 或前端 API。

## 主要程序清单

- `package-info.java`：说明本包是 generated opencode SDK 之上的稳定 facade。
- `OpencodeClientFacade`、`DefaultOpencodeClientFacade`：业务门面和默认实现。
- `OpencodeSdkGateway`、`GeneratedOpencodeSdkGateway`：内部 gateway 端口与唯一 generated SDK 适配器。
- `OpencodeMessageIdGenerator`：生成与 OpenCode `MessageID.ascending()` 字典序兼容的时序消息 ID；同毫秒并发和 JVM 时钟回退时保持单调。
- `OpencodeCreateSessionCommand`、`OpencodeCreateSessionResult`：创建远端 opencode session。
- `OpencodeStartRunCommand`、`OpencodePromptPart`、`OpencodeStartRunResult`：使用远端 opencode session id 调用 `prompt_async`，以稳定 facade 模型承载 text/file/agent parts 和可选工具启停映射。
- `OpencodeCancelCommand`、`OpencodeCancelResult`：使用远端 opencode session id 调用 abort。
- `OpencodeStreamEventsCommand`、`OpencodeRunEventMapper`：订阅 opencode event 并映射为平台 RunEventDraft；不在 client 层决定当前 Run root/child scope。
- `OpencodeDiffCommand`、`OpencodeDiffResult`、`OpencodeDiffFile`：查询 opencode session Diff 并输出平台稳定 DTO。
- `OpencodeRejectDiffCommand`、`OpencodeRejectDiffResult`：通过 opencode `sessionRevert` 拒绝 Run 级 Diff。
- `OpencodeRuntimeCommand`、`OpencodeRuntimeResult`：Web App 运行态通用 facade 命令和结果，使用 Jackson `JsonNode` 承载 opencode HTTP 响应，支持受控访问 MCP resources/tools 等只读 runtime 目录，避免 generated DTO 穿透业务模块、domain 或 API。
- `OpencodeSessionMessagesCommand`、`OpencodeSessionMessage`、`OpencodeSessionMessagesResult`：读取 opencode session projected messages 的稳定 facade DTO，用于消息内容恢复。
- `OpencodeHealthCommand`、`OpencodeHealthResult`：执行节点健康检查。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-observability`。
- `test-agent-opencode-sdk-generated`。
- Reactor、Jackson。

## 禁止依赖

- `test-agent-app`。
- `test-agent-persistence`。
- Web Controller。
- 前端 DTO。

## 上游调用方

- `test-agent-opencode-runtime` 业务模块。
- `test-agent-app` health contributor。

## 下游依赖

- generated opencode SDK。
- opencode server HTTP API。

## 测试位置

- opencode-client 模块单元测试和 gateway 级 HTTP 测试。
- facade 测试覆盖 traceId、错误映射、超时重试、create/start/cancel/event/diff/revert/runtime/session messages；消息 ID 测试覆盖格式、单调性、并发唯一性和真实跨轮故障样本。

## 修改时必须同步更新

- `backend/test-agent-opencode-client/README.md`。
- 本文件。
- `docs/api/http-api.md`，如果影响平台 API 语义或错误码。
- `docs/architecture/dependency-rules.md`，如果依赖边界变化。

## 事件映射注意事项

`OpencodeRunEventMapper` 必须兼容 opencode raw event 演进。opencode 1.17.8 中 `session.status` 的 `idle` 状态和 `session.idle` 代表本次 prompt 已结束，应在 root scope 下额外派生平台 `run.succeeded`；child scope 下只保留 `session.status`。缺失 raw event id 时不能写 `"unknown"`，派生的 `run.succeeded/run.failed` 不复用原始 `rawEventId`，但要带 `derived=true` 和 `derivedFromRaw*` 来源字段。

Web App 复刻时，mapper 需要优先识别 opencode App 实际消费的运行态事件：`message.updated`、`message.part.updated`、`message.part.delta`、`todo.updated`、`permission.*`、`question.*`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`、`reference.updated`、`file.edited`、`file.watcher.updated`。旧 `assistant.message.delta`、`tool.*`、`diff.*` 兼容事件必须保留，不能破坏现有前端。
mapper 必须保留 opencode 原始 `sessionID/messageID/partID/callID/requestID` 字段，并补充 `sessionId/messageId/partId/callId/requestId` alias，方便前端和历史 DTO 统一读取。
`session.diff` 必须保持同名平台事件，不得在 client mapper 中直接改写为 `diff.proposed`。

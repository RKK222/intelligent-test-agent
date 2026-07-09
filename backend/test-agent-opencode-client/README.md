# test-agent-opencode-client

## 工程定位

generated SDK 的业务封装层，后端其他模块只应通过这里调用 opencode server。

## 技术栈

- Java 21
- Reactor
- generated opencode SDK
- Maven library jar

## 主要职责

- 提供 `OpencodeClientFacade`。
- 统一 opencode SDK 调用、错误转换、事件转换、超时、重试和 trace。
- 隔离 generated DTO，避免其进入 domain 或前端 API。

## 已有实现

- `OpencodeClientFacade` / `DefaultOpencodeClientFacade`：提供 health、createSession、sessionExists、startRun、startCommand、cancelSession、streamRunEvents、getDiff、rejectDiff 能力；`sessionExists` 调用远端 v2 session get，404 映射为 `false` 供上层重建历史绑定，其它错误仍按统一 opencode 错误码抛出；原生 command 由平台 Run 后台持有，不使用普通 30 秒超时或自动重试。
- `GeneratedOpencodeSdkGateway`：唯一直接调用 generated SDK 的内部适配器；`prompt_async` 发送前后输出 `opencode_prompt_async_request_prepared` / `opencode_prompt_async_request_accepted` 摘要日志，用于和 opencode `/global/event` 的原生 `text/file/agent` parts 对照，日志只记录类型、文件名、mime、URL 类型、source 路径和字符数，不记录正文、data URL 原文或 token；读取 `/session/{sessionID}/message` 响应头 `X-Next-Cursor` 作为分页 cursor，并使用自定义 generated `ApiClient` WebClient 单页缓冲上限，避免大量 tool/read/write parts 让历史消息恢复退回全量大响应；远端 session 存在性校验使用 generated `SessionsApi.v2SessionGet`，不手改 generated SDK。
- `OpencodeCreateSessionCommand`、`OpencodeCreateSessionResult`：创建远端 opencode session 并只返回远端 session id。
- `OpencodeSessionExistsCommand`：校验远端 opencode session 是否仍存在，只返回布尔值，不暴露 v2 session DTO。
- `OpencodeStartRunCommand`、`OpencodeStartCommand`、`OpencodePromptPart`、`OpencodeStartRunResult`：平台 Run 启动命令、原生 slash command、稳定 prompt part 模型和结果，分别映射到 opencode `prompt_async` 与 `/session/{sessionID}/command`，不向 app/domain 暴露 generated DTO。
- `OpencodeRunEventMapper`：把 opencode raw JSON event 映射为平台 `RunEventDraft`，未知事件降级为 `opencode.event.unknown`；支持按 `RunEventScopeContext` 生成规范化 session 事件和 root 终态派生事件，并为常见 `*ID` 字段补充 lower camel alias。mapper 会把 opencode `payload.type=sync`、`payload.syncEvent.type=*.1` 包装还原为内层事件 type/id/data，保证 runtime router 能用同一 raw event id 去重 direct 与 sync 事件。workspace 级全局事件流不在 client 层按 root session 过滤，当前 Run 的 root/child scope 由 runtime router 判定。
- `OpencodeDiffCommand` / `OpencodeDiffResult`：封装 opencode `sessionDiff`，不泄露 `SnapshotFileDiff`。
- `OpencodeRejectDiffCommand` / `OpencodeRejectDiffResult`：封装 opencode `sessionRevert`，用于 Run 级拒绝 Diff。
- `OpencodeRuntimeCommand` / `OpencodeRuntimeResult`：运行态通用 facade 命令，用于受控访问 opencode Web App 需要的 agent/model/provider/command/reference、session、permission、question、fs/vcs/lsp/mcp status/resources/tools 等 HTTP API；返回 Jackson `JsonNode`，不泄露 generated DTO。
- `OpencodeSessionMessagesCommand` / `OpencodeSessionMessagesResult`：通过 generated `ApiClient` 分页读取 opencode 标准 `/session/{sessionID}/message` 消息 envelope，供断线、完成态快照和历史刷新恢复完整 user/assistant 消息；generated `Message` union 无法稳定反序列化 user 时在本适配器内保留原始 JSON，不向业务模块暴露 generated DTO。

## 测试覆盖

- `DefaultOpencodeClientFacadeTest` 覆盖 traceId 透传、health/create/sessionExists/start/cancel/event/diff/reject/messages facade 编排，以及超时、远端 404/503 和有限重试映射。
- `OpencodeRunEventMapperTest` 覆盖旧版 `session.next.*` 事件、opencode 1.17.8 `session.status`/`session.idle` 终态、message/permission/question/todo/vcs/lsp/mcp/reference/file 等运行态事件、公共 ID alias、派生终态来源字段和未知事件透传。
- `GeneratedOpencodeSdkGatewayTest` 使用本地 HTTP server 覆盖 create/start/cancel/event/messages/diff/revert/runtime 的真实请求路径、query、请求体和 `X-Trace-Id` header，确保 generated SDK DTO 不外泄。
- `OpencodeRuntimeFacadeTest` 覆盖 runtime facade 的 GET/POST 调用透传和 JSON projection 返回。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-observability`。
- `test-agent-opencode-sdk-generated`。
- Reactor。

## 禁止依赖

- `test-agent-app`。
- `test-agent-persistence`。
- Web Controller。

## 后续 AI 编码指引

新增 opencode server 调用、错误映射、事件映射时改这里。除本模块外，不要让其他业务模块直接 import `com.example.opencode.sdk.*`。
opencode 1.17.8 的 Run 成功终态只由 root `session.status` 的 `idle` 状态或 root `session.idle` 派生；`session.next.step.ended` 只保留为兼容未知事件，不能再直接映射为 `run.succeeded`。child session idle/error 只产生 session 级事件，不改变 Run 终态。
opencode raw event 缺失 `id` 时 payload 不写 `rawEventId`，不能补 `"unknown"`；由 root session 事件派生的 `run.succeeded/run.failed` 不复用原 raw event id，避免持久化层把派生终态与 session 事件误去重。
Web App 复刻新增识别 `message.updated`、`message.part.updated`、`message.part.delta`、`todo.updated`、`permission.*`、`question.*`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`、`reference.updated`、`file.edited`、`file.watcher.updated` 等 opencode App 运行态事件，同时保留旧 `assistant.message.delta`、`tool.*`、`diff.*` 兼容事件。
mapper 会保留 opencode 原始 `sessionID/messageID/partID/callID/requestID` 字段，并补充 `sessionId/messageId/partId/callId/requestId` alias；派生 `run.succeeded/run.failed` payload 带 `derived=true` 和 `derivedFromRaw*` 来源字段，但不复用原始 `rawEventId`。
opencode `session.diff` 保持映射为平台 `session.diff`，不在 client 层改写为 `diff.proposed`；写入类 tool 完成后是否派生 diff proposal 属于 runtime/persistence 契约。
Facade 对外方法不得返回 generated SDK DTO，新增方法必须同步测试成功、超时、远端错误和 traceId 透传。`workspace` query 仅在后续接入真实 opencode workspace/control-plane 时传入；本地模式默认只传 `directory`。`prompt_async` 的 `text/file/agent` parts 必须通过 `OpencodePromptPart` 传入，generated union DTO 只允许留在本模块内部或用稳定 JSON 请求体替代。session messages 只能读取标准 `/session/{sessionID}/message`，不能改用只含切换事件的 `/api/session/{sessionID}/message`，并只能通过 facade 平台 DTO 输出；session 存在性校验只能读取 v2 `/api/session/{sessionID}` 并把 404 作为可恢复缺失处理。
generated `SessionApi` 中存在和 model 同名的参数包装类时，必须在本模块内用 `ApiClient.invokeAPI` 做安全适配，不能手改 generated SDK。
runtime facade 是 `test-agent-opencode-runtime` 访问 opencode 的唯一入口；`test-agent-api` Controller 和业务模块仍不得 import generated SDK。

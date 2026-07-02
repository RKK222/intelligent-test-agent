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

- `OpencodeClientFacade` / `DefaultOpencodeClientFacade`：提供 health、createSession、startRun、startCommand、cancelSession、streamRunEvents、getDiff、rejectDiff 能力；原生 command 由平台 Run 后台持有，不使用普通 30 秒超时或自动重试。
- `GeneratedOpencodeSdkGateway`：唯一直接调用 generated SDK 的内部适配器。
- `OpencodeCreateSessionCommand`、`OpencodeCreateSessionResult`：创建远端 opencode session 并只返回远端 session id。
- `OpencodeStartRunCommand`、`OpencodeStartCommand`、`OpencodePromptPart`、`OpencodeStartRunResult`：平台 Run 启动命令、原生 slash command、稳定 prompt part 模型和结果，分别映射到 opencode `prompt_async` 与 `/session/{sessionID}/command`，不向 app/domain 暴露 generated DTO。
- `OpencodeRunEventMapper`：把 opencode raw JSON event 映射为平台 `RunEventDraft`，未知事件降级为 `opencode.event.unknown`；workspace 级全局事件流中显式携带 sessionID 的事件只允许进入匹配 remote session 的 Run。
- `OpencodeDiffCommand` / `OpencodeDiffResult`：封装 opencode `sessionDiff`，不泄露 `SnapshotFileDiff`。
- `OpencodeRejectDiffCommand` / `OpencodeRejectDiffResult`：封装 opencode `sessionRevert`，用于 Run 级拒绝 Diff。
- `OpencodeRuntimeCommand` / `OpencodeRuntimeResult`：运行态通用 facade 命令，用于受控访问 opencode Web App 需要的 agent/model/provider/command/reference、session、permission、question、fs/vcs/lsp/mcp status/resources/tools 等 HTTP API；返回 Jackson `JsonNode`，不泄露 generated DTO。
- `OpencodeSessionMessagesCommand` / `OpencodeSessionMessagesResult`：通过 generated `ApiClient` 读取 opencode 标准 `/session/{sessionID}/message` 消息 envelope，供断线、完成态快照和历史刷新恢复完整 user/assistant 消息；generated `Message` union 无法稳定反序列化 user 时在本适配器内保留原始 JSON，不向业务模块暴露 generated DTO。

## 测试覆盖

- `DefaultOpencodeClientFacadeTest` 覆盖 traceId 透传、health/create/start/cancel/event/diff/reject/messages facade 编排，以及超时、远端 503 和有限重试映射。
- `OpencodeRunEventMapperTest` 覆盖旧版 `session.next.*` 事件、opencode 1.17.8 `session.status`/`session.idle` 终态、message/permission/question/todo/vcs/lsp/mcp 等运行态事件和未知事件透传。
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
opencode 1.17.8 的 Run 终态既可能来自旧的 `session.next.step.ended`，也可能来自 `session.status` 的 `idle` 状态或 `session.idle`；这些 raw event 必须统一映射为平台 `run.succeeded`，避免前端 Run 长时间停在 `RUNNING`。
Web App 复刻新增识别 `message.updated`、`message.part.updated`、`message.part.delta`、`todo.updated`、`permission.*`、`question.*`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed` 等 opencode App 运行态事件，同时保留旧 `assistant.message.delta`、`tool.*`、`diff.*` 兼容事件。
Facade 对外方法不得返回 generated SDK DTO，新增方法必须同步测试成功、超时、远端错误和 traceId 透传。`workspace` query 仅在后续接入真实 opencode workspace/control-plane 时传入；本地模式默认只传 `directory`。`prompt_async` 的 `text/file/agent` parts 必须通过 `OpencodePromptPart` 传入，generated union DTO 只允许留在本模块内部或用稳定 JSON 请求体替代。session messages 只能读取标准 `/session/{sessionID}/message`，不能改用只含切换事件的 `/api/session/{sessionID}/message`，并只能通过 facade 平台 DTO 输出。
generated `SessionApi` 中存在和 model 同名的参数包装类时，必须在本模块内用 `ApiClient.invokeAPI` 做安全适配，不能手改 generated SDK。
runtime facade 是 `test-agent-opencode-runtime` 访问 opencode 的唯一入口；`test-agent-api` Controller 和业务模块仍不得 import generated SDK。

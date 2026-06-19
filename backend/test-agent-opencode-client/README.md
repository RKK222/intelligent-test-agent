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

- `OpencodeClientFacade` / `DefaultOpencodeClientFacade`：提供 health、createSession、startRun、cancelSession、streamRunEvents 初版能力。
- `GeneratedOpencodeSdkGateway`：唯一直接调用 generated SDK 的内部适配器。
- `OpencodeCreateSessionCommand`、`OpencodeCreateSessionResult`：创建远端 opencode session 并只返回远端 session id。
- `OpencodeStartRunCommand`、`OpencodeStartRunResult`：平台 Run 启动命令和结果，使用远端 opencode session id 映射到 opencode `prompt_async`。
- `OpencodeRunEventMapper`：把 opencode raw JSON event 映射为平台 `RunEventDraft`，未知事件降级为 `opencode.event.unknown`。

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
Facade 对外方法不得返回 generated SDK DTO，新增方法必须同步测试成功、超时、远端错误和 traceId 透传。`workspace` query 仅在后续接入真实 opencode workspace/control-plane 时传入；本地模式默认只传 `directory`。

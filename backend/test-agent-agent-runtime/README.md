# test-agent-agent-runtime

## 工程定位

多 agent 运行时抽象模块，负责根据 agent 标志选择运行时实现，并为不同 agent 复用统一日志、指标和平台错误格式。

## 主要职责

- 定义 `AgentRuntime` 通用 Java 接口。
- `AgentRuntime.createDispatchMessageId()` 为远端 USER 派发锚点提供 agent 专属 ID；默认保持平台 UUID，观测包装必须委托真实 runtime，opencode 实现使用原生时序 ID。
- 提供 `AgentRuntimeRegistry`，按 URL 中的 agentId 查找运行时，未知或未注册 agent 统一返回平台 `NOT_FOUND` 错误。
- 提供 `OpencodeAgentRuntime`，把普通 prompt 与 slash 原生命令统一适配到 `OpencodeClientFacade` 的可恢复 Run 调用；普通 prompt 可通过 `AgentStartRunCommand.tools` 显式覆盖远端工具开关，空映射保持原有默认行为。
- `AgentCreateSessionCommand.title` 为可选值；根会话创建不传标题时保留 OpenCode 默认标题，使其内置 title agent 能在首条用户消息后生成会话名。
- `AgentRuntime.sessionExists` 用于在复用历史 binding 前校验远端会话是否仍存在；opencode 实现会把远端 404 转成 `false`，由上层 resolver 决定是否重建。
- `AgentSessionMessagesResult` 保留远端 projected messages 的 `previousCursor/nextCursor`，供 runtime 快照恢复按页拉取，不把具体 opencode SDK DTO 暴露给业务层。
- 提供 `OtherAgentRuntime` 抽象占位类，供后续其他 agent 实现继承；本次不注册为可调用 Spring Bean。

## 测试覆盖

- `AgentRuntimeRegistryTest` 覆盖默认 `opencode` 命中、agentId 规范化、未知 agent 统一错误、默认 UUID dispatch ID 和观测包装委托。
- `OpencodeAgentRuntimeTest` 覆盖 OpenCode 时序 dispatch ID 与普通 prompt 参数映射。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-opencode-client`。
- Reactor、Jackson、Micrometer、Spring Context。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-persistence`。
- generated SDK。

## 后续 AI 编码指引

新增真实 agent 时优先新增本模块内的 `AgentRuntime` 实现，输出必须适配为平台稳定 DTO、RunEvent 和错误码；如果该 agent 支持可复用远端 session，必须实现 `sessionExists`，让历史 binding 缺失能被上层恢复。Controller、Repository 和 generated SDK 仍不得穿透到业务模块。

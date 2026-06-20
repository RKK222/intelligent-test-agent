# test-agent-opencode-runtime

## 工程定位

与 opencode 相关的后端业务模块，承载 Session、Run、RunEvent 编排、opencode runtime 映射、Diff/revert 和受控 PTY terminal 业务。

## 主要职责

- Session 创建、查询、消息追加和归档。
- Run 启动、取消、远端 opencode session 懒创建/复用、事件订阅和终态处理。
- RunEvent 持久化策略、实时发布和 opencode projected messages 恢复。
- Run Diff 查询、接受和拒绝。
- Phase 11 opencode runtime 能力映射，包括 catalog/fs/vcs/lsp/mcp、config、provider auth/OAuth、worktree、session share、permission/question 和 MCP auth。
- PTY terminal ticket、限流、active session registry、进程适配和审计。

## 测试覆盖

- `RunApplicationServiceTest` 覆盖 Run 创建、远端 session 懒创建/复用、sticky node、prompt parts、终态事件、瞬态消息事件和取消编排。
- `RunDiffApplicationServiceTest` 覆盖 Diff 事件优先读取、opencode Diff fallback、接受/拒绝动作和缺失 messageID 冲突。
- `RunEventPersistencePolicyTest` 覆盖消息投影只走实时通道、关键状态事件持久化、tool payload 清洗和 rawPayload 移除。
- `RunMessageRecoveryServiceTest` 覆盖 opencode projected messages 恢复为 transient SSE snapshot，以及未绑定/远端失败时降级为空。
- `SessionApplicationServiceTest` 覆盖 Session 创建前 Workspace 校验、归档隐藏、标题/置顶更新和消息追加默认 role。
- `OpencodeRuntimeApplicationServiceTest` 覆盖 agent/provider/MCP runtime path、config/provider OAuth/worktree/share/MCP auth、workspace directory 透传和 permission reply body 兼容。
- `Terminal*Test` 覆盖 ticket 签发/消费/过期、active session 互斥、输入/输出限流、WebSocket envelope 编解码和本地进程适配。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-event`。
- `test-agent-opencode-client`。
- Reactor、Jackson、Spring Context。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-persistence` 实现类。
- `test-agent-opencode-sdk-generated`。

## 后续 AI 编码指引

新增与 opencode 会话、运行、事件、Diff、permission/question、runtime catalog、terminal 相关业务时改这里。Controller 和 URL 映射必须放在 `test-agent-api`。
高频文本 delta、message projection 和大段 tool/bash 输出不应写入 `run_events`；消息内容刷新恢复只从 opencode session projected messages 拉取，Run 状态、Diff、permission/question 等平台关键事件继续依赖 durable RunEvent。

# OpenCode 源码边界

## 只读快照

本项目严格禁止直接修改 OpenCode 源码。`opencode-source/opencode-1.18.4/` 是按上游 tag 和 commit 固定的只读源码快照，仅用于源码审计、行为对照和 OpenAPI 兼容性分析。

禁止在该目录中提交以下变更：

- OpenCode 源码、测试、配置、构建脚本或资源的补丁、功能修改和缺陷修复。
- 为平台适配临时加入的日志、接口、兼容分支或构建产物。
- 直接编辑快照文件后继续生成 worker、OpenAPI 或 Java SDK。

如果平台行为需要调整，必须在本项目的后端适配层、前端复刻工程、worker 启动器或受控配置层实现，不得通过修改 OpenCode 源码绕过项目边界。需要升级 OpenCode 时，只能按 `docs/deployment/opencode-upgrade-1.18.4.md` 的流程重新获取干净上游快照，并同步评估 API、SDK 和运行时兼容性；不得在快照上做本地补丁。

## Codex MCP 适配边界

Codex 白盒分析使用 worker 内固定的官方 Codex CLI，启动器直接执行官方 `codex mcp-server`，
不修改 `opencode-source/opencode-1.18.4/`，也不增加协议门面、工具改名或参数过滤。公共
`opencode/opencode.jsonc` 只注册本地 MCP 和企业模型路由环境变量；官方 `codex` /
`codex-reply` 的 cwd、模型、配置、沙箱和指令参数保持原生可用。管理员级 requirements 仅固定
`approval_policy=never`，夜间分析 Agent 默认请求官方 `sandbox=read-only`。具体配置、风险、
企业兼容预检和回滚见
`docs/deployment/codex-whitebox-mcp.md`。

## 生成 SDK 边界

`backend/test-agent-opencode-sdk-generated/` 同样禁止手工修改 generated Java 源码。SDK 变更必须通过 `tools/generate-opencode-java-sdk.sh` 重新生成，再按模块文档同步。

## 最后一条消息原生撤销重发语义

OpenCode 1.18.4 的界面重发不是独立 resend API：界面先对最后一条 user message 调用
`POST /session/{sessionID}/revert`，OpenCode 立即把该消息及后续 assistant/tool 内容排除出消息查询和下一轮模型上下文，
并恢复该轮文件改动；下一次投递使用新的 message ID。`session.status.type=retry` 是供应商调用内部重试，平台只展示该状态，
不得据此取消 Run 或重复发送 prompt。

平台实现只能通过 `AgentRuntime` 中立端口调用既有消息查询、revert/unrevert 和 prompt/command 适配，禁止修改只读快照或
generated SDK。重发前必须确认目标仍是远端最后一条 user message；人工入口只能由会话所属人发起，可在服务端取得可信远端轮次后用所属人确认的可选新文本替换第一个 text/subtask，file/agent 等其它 part、model、agent、variant 必须继续沿用原轮次，
使用服务端稳定的新 message ID。投递结果未知时只能继续探测该稳定 ID；只有明确不存在时才能尝试 unrevert，unrevert 回包未知时
仍保留锁并在恢复轮重新确认。替代消息确认受理后才清理源 Run 的正文、工具事件和 scope 投影，`runs`、反馈、用量、traceId 与
重发关系继续作为最小审计数据保留。

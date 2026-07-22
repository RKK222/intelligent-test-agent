# Session Log — guojq

## 2026-07-22 新增 agents/tools/opencode REST 工具智能体

### Why
用户需要在工作空间 Agent 配置树的 agents 目录下新增一个 tools 子文件夹，其中定义一个 opencode 工具，用于调用第三方 REST API 并返回信息。

### What
1. 在 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config/opencode/agents/tools/` 下创建 `opencode.md`，定义了名为 "OpenCode REST 工具" 的 subagent，支持通过 bash/curl 调用第三方 REST API。
2. 在前端 `tool-registry.ts` 中新增 `"tool"` family 类型，并添加对 `tools/opencode`、`opencode_tool`、`opencode-tool` 工具名的识别，显示为 "REST 工具"。

### How
- Agent 定义采用与现有 agent 一致的 YAML frontmatter + Markdown body 格式，走 legacy 解析路径。
- 前端 ToolInfo.family 联合类型新增 `"tool"` 分支；getToolInfo 新增 tools/opencode 识别规则。
- AgentConfigPanel 现有的 `canCreateInDirectory` 规则（`path.startsWith("agents/")`）已覆盖 agents/tools/ 子目录，无需额外修改。

### Result
- agents/tools/opencode.md 已创建，格式与现有 agent 文件一致。
- 前端 tool-registry.ts 已更新，新增 REST 工具识别。
- 无 API、事件、数据库、安全或兼容性变更。

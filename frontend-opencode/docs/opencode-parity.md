# opencode App parity 验收记录

## 参考范围

- 参考源：`opencode-source/opencode-1.17.8/packages/app`。
- 不包含：`opencode-source/opencode-1.17.8/packages/web` 官网/文档/公开分享页。

## 当前覆盖

| 区域 | 状态 |
|---|---|
| App shell/topbar/search/new session/settings/command palette | 已实现 Vue 版本；`/new-session` 草稿页支持 `workspaceId` 目标 workspace、`prompt` deep link 预填并提交后提升为真实 session；Settings dialog 支持 toolbar 打开、Close、背景关闭和 Escape 关闭；command palette 支持 toolbar trigger、`Ctrl/Cmd+Shift+P` 与 `Ctrl/Cmd+K` 打开、搜索过滤、方向键高亮、Enter 选择、Escape/背景关闭，并把平台 command catalog 选项写入 composer slash 文本。 |
| Home workspace/session/provider/model/agent 展示 | 已实现，数据来自平台 API。 |
| Session layout 左侧会话、中间 timeline/composer、右侧 review/files/terminal/status | 已实现首版结构；`/w/:workspaceId/session` 省略 sessionId 时会在 workspace 会话列表加载后补齐首个会话并渲染 timeline；桌面 session rail 支持点击切换会话并重新加载目标 timeline，并跟随全局 search query 过滤当前 workspace 会话；移动端 toolbar Panel 按钮会打开右侧面板抽屉并保留 Review/Files/Terminal/Status tabs，右侧 tabs 已具备标准 tab/tabpanel 与 selected 状态；Timeline 已按 opencode message part 分块展示 text/reasoning/tool/file/event，reasoning/tool 支持折叠展开，completed 默认收起，running/pending/error 默认展开，并保留旧纯文本 fallback；Files tab 已接入 runtime fs 只读文件树、目录进入、搜索、刷新、空态和错误态。 |
| Session toolbar：share/fork/compact/revert/abort | 已接入 Vue toolbar；fork 可选择用户消息并跳转子会话，revert 回滚最新用户消息，compact 在当前 session 带 model/provider 时调用 summarize，abort 仅在 active run 为 pending/queued/running 时启用并经平台 API。 |
| Composer 上方待处理 dock：permission/question/todo/follow-up/revert | 已实现 Vue 版本，操作统一经 `backend-api`，并覆盖组件/状态单测。 |
| Prompt parts 与运行态选择：text/file/image/agent/reference/slash command、Agent/Model/Variant | 已实现构造与单测；composer 附件/@ 文件选择走平台 fs catalog，图片支持选择、粘贴和拖拽并以 `file` part 发送，context chip 支持移除，Agent/Model/Variant 选择透传运行态 API，切换模型会清理失效 variant，slash 菜单从平台 command catalog 选择命令并写入 `/command` 文本，支持按钮和 textarea `/query` 触发、方向键、Enter、Escape、参数模板插入和参数表单补全；textarea Enter 提交、Shift+Enter 换行、边界 ArrowUp/ArrowDown 浏览历史；普通 prompt 走 `startRun`，shell mode 走 `runSessionShell`，slash command 走 `runSessionCommand`；active run 执行中发送按钮切换为 Stop，点击后立即关闭本地输出订阅并调用平台 cancel。 |
| RunEvent reducer：message part delta、todo、permission、question、diff/status | 已实现核心 reducer 与单测；session timeline 投影会保留 message parts 供 Vue timeline 分块渲染，并按当前 active session 过滤 RunEvent 消息，避免切换会话时串入其它 session 的流式输出；会话加载时并行读取 messages 与 active run，非终态 Run 自动恢复 SSE，timeline 按 messageId 合并数据库快照和 SSE projection。 |
| Terminal | 已接入后端 ticket 获取、平台 WebSocket JSON envelope、xterm 输出渲染、原始终端输入、warning/error、input、resize、clear、关闭/重连操作。 |
| Diff review | 已接入 `DiffReviewPanel`，支持文件聚焦、unified/split 样式切换、hunk 统计/导航、空态，以及 `MonacoDiffEditor` 懒加载只读 diff editor；Monaco 不可用时保留 hunk 预览 fallback。 |
| Session share | 已接入 toolbar share popover，支持 publish、显示/复制/打开公开 URL、unpublish，操作统一经 `backend-api`。 |
| Provider auth/config/worktree/MCP auth API | 后端平台代理已补齐；Settings 已接入 provider auth 状态、API key 保存/移除、provider OAuth methods/prompt inputs/authorize URL/code 与 auto callback payload、worktree 列表/创建/重置/删除，以及 MCP status/connect/disconnect/auth/remove auth 入口；Settings 弹窗具备内部滚动，Worktree/MCP 操作在小视口内可达。 |
| Mock E2E 验收 | Playwright 已覆盖桌面/移动端 App shell、首页会话列表、`/new-session?prompt=...` 草稿提升、会话页加载、prompt submit 请求体、toolbar abort/compact/fork/revert、Session share publish/unpublish、Settings provider API key/OAuth/remove auth、worktree create/reset/remove、MCP connect/disconnect/auth/remove auth mock 流程，以及 fake RunEvent SSE 驱动 assistant delta 渲染到 timeline；状态/组件单测覆盖刷新运行中会话恢复、等价事件不重复和 Stop 按钮取消。 |
| Real E2E 入口 | 已新增 `playwright.real.config.ts` 与 `tests/e2e-real/session-stream.real-spec.ts`，仅在 `TEST_AGENT_RUN_REAL_E2E=1` 下运行单个桌面 smoke，通过真实平台 API 查找/创建 workspace、UI 发送 prompt，并等待 RunEvent SSE 渲染 assistant 文本。 |

## 待真实三服务验收

- 在本机真实启动 `test-agent-app`、opencode server、`frontend-opencode` 后执行 `corepack pnpm e2e:real`，确认发送 prompt -> RunEvent SSE -> timeline 渲染闭环；当前真实套件已落地，但仍依赖外部服务可用性。
- 桌面 1440x900 与移动 Pixel 7 截图对比原 opencode App。
- 真实 provider OAuth、MCP auth、worktree、share 依赖可用 opencode runtime 环境；provider OAuth methods、prompt inputs、authorize URL、code/auto callback payload 已走平台代理，仍需真实 provider 验证外部浏览器授权闭环；worktree UI 已走平台代理，仍需真实 Git runtime 验证创建/重置/删除结果；share UI 已走平台代理，仍需真实公开分享服务验证 URL 可访问性。
- MCP status/connect/disconnect/auth UI 已走平台代理；仍需真实 MCP server 验证连接切换、认证链接和错误状态刷新。
- Provider API key 管理和 OAuth methods/prompt inputs/code/auto callback payload 已可用，完整外部浏览器 OAuth 完成状态仍需真实 provider 环境验收。
- Session toolbar fork/revert/compact 已走平台 API；仍需真实三服务验证 fork 返回子会话路由、summarize 模型参数和 revert 后远端消息边界。
- Prompt composer 附件/@ 文件选择、图片选择/粘贴/拖拽、Agent/Model/Variant 运行态选择、shell mode、slash command runtime 分流、`/query` 触发、参数表单补全、Enter/Shift+Enter、历史上下键和运行中 Stop 取消已走 Vue 版；更完整编辑器级键盘导航仍需继续补齐。
- Terminal 已具备 ticket/WebSocket JSON envelope、xterm 渲染、原始输入、resize、warning/error 和输出截断基础交互；后续需用真实三服务验证 PTY 协议闭环和终端视觉一致性。

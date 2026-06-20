# opencode App parity 验收记录

## 参考范围

- 参考源：`opencode-source/opencode-1.17.8/packages/app`。
- 不包含：`opencode-source/opencode-1.17.8/packages/web` 官网/文档/公开分享页。

## 当前覆盖

| 区域 | 状态 |
|---|---|
| App shell/topbar/search/new session/settings/command palette | 已实现 Vue 版本。 |
| Home workspace/session/provider/model/agent 展示 | 已实现，数据来自平台 API。 |
| Session layout 左侧会话、中间 timeline/composer、右侧 review/files/terminal/status | 已实现首版结构；Files tab 已接入 runtime fs 只读文件树、目录进入、搜索、刷新、空态和错误态。 |
| Session toolbar：share/fork/compact/revert/abort | 已接入 Vue toolbar；fork 可选择用户消息并跳转子会话，revert 回滚最新用户消息，compact 在当前 session 带 model/provider 时调用 summarize，abort 经平台 API。 |
| Composer 上方待处理 dock：permission/question/todo/follow-up/revert | 已实现 Vue 版本，操作统一经 `backend-api`，并覆盖组件/状态单测。 |
| Prompt parts 与运行态选择：text/file/image/agent/reference/slash command、Agent/Model/Variant | 已实现构造与单测；composer 附件/@ 文件选择走平台 fs catalog，图片支持选择、粘贴和拖拽并以 `file` part 发送，context chip 支持移除，Agent/Model/Variant 选择透传 `startRun`，切换模型会清理失效 variant，slash 菜单从平台 command catalog 选择命令并写入 `/command` 文本，支持方向键、Enter、Escape 和参数模板插入。 |
| RunEvent reducer：message part delta、todo、permission、question、diff/status | 已实现核心 reducer 与单测。 |
| Terminal | 已接入后端 ticket 获取、平台 WebSocket JSON envelope、输出展示、warning/error、input、resize、clear、关闭/重连操作；完整 xterm 渲染仍是后续增强。 |
| Diff review | 已接入 `DiffReviewPanel`，支持文件聚焦、unified/split 样式切换、hunk 统计/导航和空态；完整 Monaco diff 后续懒加载。 |
| Session share | 已接入 toolbar share popover，支持 publish、显示/复制/打开公开 URL、unpublish，操作统一经 `backend-api`。 |
| Provider auth/config/worktree/MCP auth API | 后端平台代理已补齐；Settings 已接入 provider auth 状态、API key 保存/移除、provider OAuth authorize URL/code callback、worktree 列表/创建/重置/删除，以及 MCP status/auth/remove auth 入口。 |
| Mock E2E 验收 | Playwright 已覆盖桌面/移动端 App shell、首页会话列表、会话页加载、prompt submit 请求体，以及 fake RunEvent SSE 驱动 assistant delta 渲染到 timeline。 |

## 待真实三服务验收

- 启动 `test-agent-app`、opencode server、`frontend-opencode` 后执行发送 prompt -> RunEvent SSE -> timeline 渲染闭环。
- 桌面 1440x900 与移动 Pixel 7 截图对比原 opencode App。
- 真实 provider OAuth、MCP auth、worktree、share 依赖可用 opencode runtime 环境；provider OAuth authorize/code callback UI 已走平台代理，仍需真实 provider 验证授权 URL、prompt inputs、多 method 和 auto callback；worktree UI 已走平台代理，仍需真实 Git runtime 验证创建/重置/删除结果；share UI 已走平台代理，仍需真实公开分享服务验证 URL 可访问性。
- MCP status/auth UI 已走平台代理；opencode App 的 MCP toggle/connect/disconnect 仍需平台后端补齐接口后接入。
- Provider API key 管理和 OAuth authorize/code callback 已可用，完整多 method、prompt inputs 和 auto callback flow 仍需真实 provider 环境验收后补齐。
- Session toolbar fork/revert/compact 已走平台 API；仍需真实三服务验证 fork 返回子会话路由、summarize 模型参数和 revert 后远端消息边界。
- Prompt composer 附件/@ 文件选择、图片选择/粘贴/拖拽、Agent/Model/Variant 运行态选择和 slash command 已走平台目录；完整 opencode 参数表单、命令参数表单化补全和更完整编辑器级键盘导航仍需继续补齐。
- Terminal 已具备 ticket/WebSocket JSON envelope、input、resize、warning/error 和输出截断基础交互；后续需用真实三服务验证 PTY 协议闭环和 xterm 视觉一致性。

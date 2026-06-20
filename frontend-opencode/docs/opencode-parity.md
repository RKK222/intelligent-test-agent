# opencode App parity 验收记录

## 参考范围

- 参考源：`opencode-source/opencode-1.17.8/packages/app`。
- 不包含：`opencode-source/opencode-1.17.8/packages/web` 官网/文档/公开分享页。

## 当前覆盖

| 区域 | 状态 |
|---|---|
| App shell/topbar/search/new session/settings/command palette | 已实现 Vue 版本。 |
| Home workspace/session/provider/model/agent 展示 | 已实现，数据来自平台 API。 |
| Session layout 左侧会话、中间 timeline/composer、右侧 review/files/terminal/status | 已实现首版结构。 |
| Composer 上方待处理 dock：permission/question/todo/follow-up/revert | 已实现 Vue 版本，操作统一经 `backend-api`，并覆盖组件/状态单测。 |
| Prompt parts：text/file/image/agent/reference | 已实现构造与单测。 |
| RunEvent reducer：message part delta、todo、permission、question、diff/status | 已实现核心 reducer 与单测。 |
| Terminal | 已接入后端 ticket 获取、WebSocket 连接、输出展示、命令发送、关闭/重连操作；完整 xterm 渲染仍是后续增强。 |
| Diff review | 已接入 `DiffReviewPanel`，支持文件聚焦、unified/split 样式切换、hunk 统计/导航和空态；完整 Monaco diff 后续懒加载。 |
| Session share | 已接入 toolbar share popover，支持 publish、显示/复制/打开公开 URL、unpublish，操作统一经 `backend-api`。 |
| Provider auth/config/worktree/MCP auth API | 后端平台代理已补齐；Settings 已接入 provider auth 状态、API key 保存/移除、worktree 列表/创建/重置/删除，以及 MCP status/auth/remove auth 入口。 |

## 待真实三服务验收

- 启动 `test-agent-app`、opencode server、`frontend-opencode` 后执行发送 prompt -> RunEvent SSE -> timeline 渲染闭环。
- 桌面 1440x900 与移动 Pixel 7 截图对比原 opencode App。
- 真实 provider OAuth、MCP auth、worktree、share 依赖可用 opencode runtime 环境；worktree UI 已走平台代理，仍需真实 Git runtime 验证创建/重置/删除结果；share UI 已走平台代理，仍需真实公开分享服务验证 URL 可访问性。
- MCP status/auth UI 已走平台代理；opencode App 的 MCP toggle/connect/disconnect 仍需平台后端补齐接口后接入。
- Provider API key 管理已可用，OAuth authorize/callback 入口仍需真实 provider 环境验收后补齐外跳体验。
- Terminal 已具备 ticket/WebSocket 基础交互，后续需用真实三服务验证 PTY 协议、resize 和 xterm 视觉一致性。

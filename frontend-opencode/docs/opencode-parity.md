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
| Terminal | 已接入后端 ticket 获取，真实 xterm 渲染后续可替换当前占位屏。 |
| Diff review | 已展示平台 `SessionDiff` 文件和 patch，占位 Monaco diff 后续懒加载。 |
| Provider auth/config/worktree/share/MCP auth API | 后端平台代理已补齐，UI 首版以 settings/API adapter 为入口。 |

## 待真实三服务验收

- 启动 `test-agent-app`、opencode server、`frontend-opencode` 后执行发送 prompt -> RunEvent SSE -> timeline 渲染闭环。
- 桌面 1440x900 与移动 Pixel 7 截图对比原 opencode App。
- 真实 provider OAuth、MCP auth、worktree、share 依赖可用 opencode runtime 环境。
- Terminal 仍是平台 ticket/WebSocket 接入占位屏，后续需替换为完整 xterm 会话渲染与 resize 交互。

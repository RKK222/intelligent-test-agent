# frontend-opencode 文档索引

`frontend-opencode` 是 opencode App 的 Vue 3 + TypeScript + Vite 复刻项目，运行、构建和测试命令以根目录 `README.md` 为准。

## 文档

- `dependencies.md`：新增前端运行库、测试库和组件库用途。
- `api-mapping.md`：opencode 原 App 调用到平台后端 API 的映射。
- `opencode-parity.md`：页面、交互、视觉和真实环境验收清单。

## 组件记录

- `src/components/DiffReviewPanel.vue`：复刻 opencode Review 面板的文件聚焦、unified/split 样式切换和 hunk 导航；数据来自平台 `SessionDiff`，不在浏览器端直连 opencode。
- `src/components/FileTreePanel.vue`：复刻 opencode Files 侧栏的只读文件树入口，支持 runtime fs 列目录、进入目录、搜索、刷新、空态和错误态；数据统一来自平台 `/api/fs/*`。
- `src/components/PromptComposer.vue`：复刻 opencode composer 的文本、附件、图片选择/粘贴/拖拽、@ 上下文、Agent/Model/Variant 运行态选择、shell mode 和 slash command 入口；附件/@ 文件选择通过平台 fs catalog，context chip 可移除，图片以平台 `file` part 契约发送，slash 菜单通过平台命令目录写入 `/command` 文本，并支持方向键、Enter、Escape 键盘操作；textarea Enter 提交、Shift+Enter 换行；提交时 shell mode 走 `runSessionShell`，slash command 走 `runSessionCommand`。
- `src/components/SessionToolbarActions.vue`：复刻 opencode session toolbar 的 share、fork、compact、revert、abort 入口；fork/revert 按 opencode `messageID` 请求体经平台代理。
- `src/components/SessionForkDialog.vue`：复刻 opencode fork dialog 的用户消息选择列表，选择后通过 `backend-api.forkSession` 创建子会话并跳转。
- `src/components/SessionShareButton.vue`：复刻 opencode session share publish/view/copy/unpublish 入口；publish/unpublish 通过平台 `backend-api`，公开 URL 只保存在前端会话状态。
- `src/components/SettingsDialog.vue`：承载 opencode 设置入口，已接入 provider auth 状态、API key 保存/移除、provider OAuth methods、prompt inputs、授权 URL、code/auto callback payload。
- `src/components/SettingsWorktreePanel.vue`：复刻 opencode workspace/worktree 管理入口，支持 experimental worktree 列表、创建、重置和删除；所有操作经平台 `backend-api` 代理。
- `src/components/SettingsMcpPanel.vue`：复刻 opencode MCP 状态/认证/开关入口，展示 connected/failed/needs_auth 等状态，并通过平台 API connect/disconnect、发起或移除 MCP auth。
- `src/components/TerminalPanel.vue`：复刻 opencode Terminal 侧栏基础交互，ticket 经 `backend-api` 获取，WebSocket 使用平台 JSON envelope 发送 input/resize/close，使用 xterm 渲染输出并保留 warning/error、输出片段上限和可访问 transcript。

## 测试记录

- Playwright mock E2E 覆盖桌面/移动端 shell、首页会话列表、会话详情加载、prompt 提交请求构造，以及通过 fake EventSource 注入 RunEvent 后的 timeline 流式渲染。

## 当前边界

- 浏览器端只调用平台 `backend-api`，不直接连接 opencode server。
- 实时消息只通过 RunEvent SSE 合并到前端状态。
- `packages/web` 官网、文档站和公开分享页不属于当前 v1 复刻范围。

# frontend-opencode 文档索引

`frontend-opencode` 是 opencode App 的 Vue 3 + TypeScript + Vite 复刻项目，运行、构建和测试命令以根目录 `README.md` 为准。

## 文档

- `dependencies.md`：新增前端运行库、测试库和组件库用途。
- `api-mapping.md`：opencode 原 App 调用到平台后端 API 的映射。
- `opencode-parity.md`：页面、交互、视觉和真实环境验收清单。

## 组件记录

- `src/components/DiffReviewPanel.vue`：复刻 opencode Review 面板的文件聚焦、unified/split 样式切换和 hunk 导航；数据来自平台 `SessionDiff`，不在浏览器端直连 opencode。
- `src/components/SessionShareButton.vue`：复刻 opencode session share publish/view/copy/unpublish 入口；publish/unpublish 通过平台 `backend-api`，公开 URL 只保存在前端会话状态。
- `src/components/SettingsDialog.vue`：承载 opencode 设置入口，已接入 provider auth 状态、API key 保存/移除、provider OAuth authorize URL 展示和基础 code callback。
- `src/components/SettingsWorktreePanel.vue`：复刻 opencode workspace/worktree 管理入口，支持 experimental worktree 列表、创建、重置和删除；所有操作经平台 `backend-api` 代理。
- `src/components/SettingsMcpPanel.vue`：复刻 opencode MCP 状态/认证入口，展示 connected/failed/needs_auth 等状态，并通过平台 API 发起或移除 MCP auth。

## 当前边界

- 浏览器端只调用平台 `backend-api`，不直接连接 opencode server。
- 实时消息只通过 RunEvent SSE 合并到前端状态。
- `packages/web` 官网、文档站和公开分享页不属于当前 v1 复刻范围。

# frontend-opencode 文档索引

`frontend-opencode` 是 opencode App 的 Vue 3 + TypeScript + Vite 复刻项目，运行、构建和测试命令以根目录 `README.md` 为准。

## 文档

- `dependencies.md`：新增前端运行库、测试库和组件库用途。
- `api-mapping.md`：opencode 原 App 调用到平台后端 API 的映射。
- `opencode-parity.md`：页面、交互、视觉和真实环境验收清单。

## 组件记录

- `src/components/DiffReviewPanel.vue`：复刻 opencode Review 面板的文件聚焦、unified/split 样式切换和 hunk 导航；数据来自平台 `SessionDiff`，不在浏览器端直连 opencode。

## 当前边界

- 浏览器端只调用平台 `backend-api`，不直接连接 opencode server。
- 实时消息只通过 RunEvent SSE 合并到前端状态。
- `packages/web` 官网、文档站和公开分享页不属于当前 v1 复刻范围。

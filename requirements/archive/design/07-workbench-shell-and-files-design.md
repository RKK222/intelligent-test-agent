# Phase 07 工作台、文件树和编辑器详细设计

## 目标

Phase 07 实现 Web IDE 的基础文件工作流：工作台布局、文件树、文件名搜索、多 tab、Monaco 编辑、保存、脏状态和错误提示。布局和交互参考 `frontend/interaction-visual-demo` 的顶部栏、左侧工作空间/搜索/变更 tab、中间编辑区和右侧 Agent 区，但用 Dockview 和 React package 边界实现。

## 工作台布局

- `packages/workbench-shell` 提供默认 Dockview 布局：左侧文件面板、主编辑区、右侧 Agent 面板、底部运行/日志面板。
- 面板 id 使用稳定字符串，布局状态保存到浏览器本地状态；恢复失败时回退默认布局。
- 顶部栏显示当前 workspace、后端连接状态、Run 状态和常用操作入口。
- 移动或窄屏场景采用可收起面板，不让编辑器、文件树和对话区互相覆盖。

## 文件能力

- `packages/file-explorer` 通过 `backend-api` 调用现有单层目录接口，目录展开时懒加载子目录。
- 文件搜索首版只基于已加载文件树做文件名过滤，不新增后端内容搜索 API。
- 文件状态首版显示存在性、大小、目录/文件类型和本地脏状态；Git 状态和内容搜索留到后续阶段。
- 打开文件时由 `packages/editor` 读取内容并创建或激活 tab。

## 编辑器能力

- `packages/editor` 封装 Monaco Editor，支持只读/编辑模式、语言推断、保存、脏状态和保存错误展示。
- 文件大小限制沿用后端 Workspace 文件 API，超过限制时显示统一错误和 traceId。
- 保存通过 `PUT /api/workspaces/{workspaceId}/files/content`，成功后刷新文件状态并清理脏标记。
- 保存失败不丢弃用户编辑内容，错误提示必须包含安全 message、code 和 traceId。

## 验收

- 文件树可展开、刷新、选中文件并打开到编辑区。
- 多文件 tab 可切换和关闭，编辑后 tab 和状态栏显示脏状态。
- 保存成功后文件内容在后端工作区落盘；保存失败显示统一错误。
- 文件名搜索可过滤已加载文件并打开匹配项。
- 组件测试覆盖文件树、打开文件、保存成功、保存失败、空状态和搜索。

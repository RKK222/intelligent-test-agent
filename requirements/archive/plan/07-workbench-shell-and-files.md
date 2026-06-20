# Phase 07 工作台、文件树和编辑器

## 阶段目标

实现 Web IDE 的基础工作流：工作台布局、文件树、文件打开、Monaco 编辑器、保存、文件状态和基础搜索。

## 可验收功能清单

1. Dockview 工作台支持侧边栏、主编辑区、右侧对话区和底部面板。
2. 文件树可展示工作区目录。
3. 文件可打开到 Monaco 编辑器。
4. 可编辑文件支持保存和脏状态。
5. 文件搜索和文件状态可用。

## 修改项目

- `frontend/apps/agent-web`
- `frontend/packages/workbench-shell`
- `frontend/packages/file-explorer`
- `frontend/packages/editor`
- `frontend/packages/backend-api`
- `frontend/packages/ui-kit`
- `docs/frontend/*`
- `docs/api/backend-api.md`

## 实现功能

- 工作台布局可恢复上次打开面板。
- 文件树支持展开、收起、刷新、选中文件。
- 编辑器支持只读和编辑模式。
- 保存失败展示统一错误和 traceId。
- 大文件打开有只读或阻断策略。

## 验收方式

- 前端 lint、typecheck、unit test 通过。
- 组件测试覆盖文件树、打开文件、保存、错误和空状态。
- 浏览器验证工作台布局、文件树和编辑器不重叠。
- API 文档包含文件树、读取文件、保存文件接口。

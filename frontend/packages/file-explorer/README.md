# @test-agent/file-explorer

## 工程定位

文件树、已加载文件名搜索和 Changed Files 面板。

## 主要职责

- 展示单层加载的目录树。
- 使用 VS Code Workbench 风格的 30px icon tabbar 承载文件树、搜索和变更视图，文件浏览列表行保持 22px 高、13px 字号。
- 文件/目录、chevron 和 loading 图标使用 `@vscode/codicons`；`getVsCodeFileIconClass(entry)` 从包入口导出，供 `agent-web` 的 Agent 配置树复用。
- 展开目录时通过回调交给 app 调用后端。
- 文件树标题行提供刷新事件按钮，本包不直接调用后端。
- 搜索默认使用 app 层传入的服务端搜索结果（`searchResults`/`searchLoading`/`searchKeyword` props），通过 `search` 事件把关键字回传给 app 由其发起工作空间递归搜索；app 未提供结果时回退到本地已加载文件名/路径过滤。
- 搜索结果在文件名中高亮匹配关键字（`highlightKeyword` 分段渲染），并在同一紧凑行内展示父目录路径。
- 展示 Diff 文件列表并触发打开 Diff。
- 文件树行支持在文件名后展示变更行数 `+N -N`（绿/红，与 Changed Files 面板一致）：由 app 层传入 `changedFiles` 与 `workspaceRootPath`，本包把 diff 路径归一化为 workspace 相对路径后按文件匹配，行数来自 `RunDiffFile.additions/deletions`。
- 保持文件树、Changed Files 和搜索结果的紧凑列表视觉，不因长路径、状态徽标或选中态改变行高。
- `@` context 和 prompt file context 只消费 app 层传入的选择回调；本包不读取文件内容。

## 禁止事项

- 不直接调用后端。
- 不实现内容搜索 API。
- 不直接构造 `PromptPart`，不访问 opencode server。

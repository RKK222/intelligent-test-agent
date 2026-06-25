# @test-agent/file-explorer

## 工程定位

文件树、已加载文件名搜索和 Changed Files 面板。

## 主要职责

- 展示单层加载的目录树。
- 使用 Figma Web IDE 风格的 45px icon tabbar 承载文件树、搜索和变更视图，列表行保持 28px 紧凑高度。
- 展开目录时通过回调交给 app 调用后端。
- 文件树标题行提供刷新事件按钮，本包不直接调用后端。
- 搜索只过滤已加载文件树中的文件名或路径。
- 展示 Diff 文件列表并触发打开 Diff。
- 文件树行支持在文件名后展示变更行数 `+N -N`（绿/红，与 Changed Files 面板一致）：由 app 层传入 `changedFiles` 与 `workspaceRootPath`，本包把 diff 路径归一化为 workspace 相对路径后按文件匹配，行数来自 `RunDiffFile.additions/deletions`。
- 保持文件树、Changed Files 和搜索结果的紧凑列表视觉，不因长路径、状态徽标或选中态改变行高。
- `@` context 和 prompt file context 只消费 app 层传入的选择回调；本包不读取文件内容。

## 禁止事项

- 不直接调用后端。
- 不实现内容搜索 API。
- 不直接构造 `PromptPart`，不访问 opencode server。

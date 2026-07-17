# @test-agent/file-explorer

## 工程定位

文件树、已加载文件名搜索和 Changed Files 面板。

## 主要职责

- 展示单层加载的目录树。
- 文件树中的文件和目录支持双击进入行内改名；改名请求由 app 层提交，本包不直接访问后端。
- 普通文件支持右键或 Ctrl/Cmd+C、X、V 复制/剪切/粘贴，并可拖放到目录或工作区根目录；`Ctrl/Cmd+Z` 请求撤销当前个人 worktree 最近一次复制、移动或上传。本包只维护剪贴板和交互事件，实际文件操作与撤销由 app 层提交。
- 文件树标题和目录行的 `+` 统一打开“新建/上传”面板，面板明确展示目标目录并支持选择一个或多个本机文件；本包只上报浏览器 `File[]`，二进制编码、大小限制、冲突和落盘由 app/backend-api 处理。
- 文件拖放在全局 `drop/dragend` 后统一清除根目录和递归目录高亮，避免蓝色目标框残留。
- `canWrite=false` 时保留展开、读取、搜索和“添加文件到对话”，隐藏并在组件内部阻断新增、删除、重命名、复制移动和上传入口，避免只读 feature 副本出现伪写操作。
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
- 文件行和搜索结果行可通过右键 emit “添加文件到对话”事件；文件读取、二进制/超大文件拦截和上下文 store 写入仍由 app 层完成。

## 禁止事项

- 不直接调用后端。
- 不实现内容搜索 API。
- 不直接构造 `PromptPart`，不访问 opencode server。

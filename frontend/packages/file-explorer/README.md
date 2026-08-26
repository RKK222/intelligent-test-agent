# @test-agent/file-explorer

## 工程定位

文件树、已加载文件名搜索和 Changed Files 面板。

## 主要职责

- 展示单层加载的目录树。
- 组合文件树以节点 `id`（而不是可能重复的展示 `path`）作为展开、loading、剪贴板和 Vue key 身份；app 层定位已打开的引用文件时同样以稳定节点 ID 作为 `activePath`，并根据父子缓存展开祖先，本包不解析引用展示路径。应用资产引用 `source=REFERENCE` 且 `merged=true` 的节点显示蓝色，自动化只读引用 `source=AUTOMATION_REFERENCE` 显示紫色，工作区已有同名目录返回 `source=MIXED` 并保持普通颜色，文件冲突显示红色，`merge=false` 的普通参考别名根保持普通目录颜色。
- 文件树中的文件和目录通过右键菜单进入行内改名，双击不再触发改名；改名请求由 app 层提交，本包不直接访问后端。条目当前没有任何可用右键操作时不渲染空白菜单。
- 可写工作区条目支持 Ctrl/Cmd+单击增量多选；普通点击建立选择锚点，随后 Shift+单击按当前页面从上到下的可见顺序连续选择文件和目录，展开目录的后代参与、折叠目录的后代不参与，只读、纯 `REFERENCE` 和 `MIXED` 条目会跳过。右键可对当前选择执行删除、复制、剪切和粘贴，多文件复制与剪切复用既有 app 层文件操作；选择同时包含父目录与后代时，写操作只提交顶层目标，避免重复处理同一子树。拖动任一已选行会整体移动当前选择到目录或工作区根目录；只读、纯 `REFERENCE` 和 `MIXED` 条目不可拖。合法目录和根空白区显示蓝色落点；当前父目录、自身、被拖目录的后代、文件行、纯引用目录和只读目录均拒绝落入，带 `workspacePath` 的 `MIXED` 目录可作为工作区侧落点接收工作区条目。`Ctrl/Cmd+Z` 请求撤销当前个人 worktree 最近一次复制、移动或上传。本包只维护选择、剪贴板和交互事件，实际文件操作与撤销由 app 层提交。
- 文件树标题和目录行的 `+` 统一复用 `FileEntryCreateDialog` 打开“新建/上传”面板，面板明确展示目标目录；工作区既可一次选择多个本机文件，也可选择整个文件夹并通过 `webkitRelativePath` 保留内部层级，Agents 配置树默认不开放目录选择。该组件也从包入口导出，供 Agents 配置树复用同一套文件/文件夹交互与名称校验。启用 Agent 模板时，Agent/Skill 中文名称必填、英文名称选填，组件只透传两种名称并明确提示英文留空会生成完整拼音；技术标识和文件内容仍由 app 层生成。本包只上报浏览器 `File[]` 或创建事件，二进制编码、大小限制、冲突和落盘由 app/backend-api 处理。
- 文件和目录行都常驻低强调的 `−` 删除入口，聚焦行按 `Delete/Del` 使用同一 `FileEntryDeleteDialog` 确认面板；目录确认会明确提示其中全部内容将递归删除。该组件从包入口导出，供 Agents 配置树复用同一确认语义；新建/上传与删除面板统一使用工作台紧凑样式、路径块和危险状态。
- 文件和目录行在悬停或键盘聚焦时提供下载按钮；本包只 emit `downloadEntry`，文件内容读取、文件夹递归打包 ZIP 和浏览器下载均由 app 层按当前工作区权限完成。
- 文件拖放在全局 `drop/dragend` 后统一清除根目录和递归目录高亮，避免蓝色目标框残留。
- `canWrite=false` 时保留展开、读取和搜索，隐藏并在组件内部阻断新增、删除、重命名、复制移动和上传入口；`canAttach=false` 独立隐藏并阻断“添加文件到对话”，`canDownload=false` 独立隐藏并阻断文件/目录下载。排查页面同时关闭三项能力，避免伪只读入口；节点级 `readonly=true` 同样阻断所有变更，`source=MIXED` 的目录只允许通过 `workspacePath` 向工作区侧新增、上传、粘贴或拖入，不能重命名、删除或移动整棵混合目录。
- 使用 VS Code Workbench 风格的 30px icon tabbar 承载文件树、搜索和变更视图，文件浏览列表行保持 22px 高、13px 字号。
- 文件/目录、chevron 和 loading 图标使用 `@vscode/codicons`；`getVsCodeFileIconClass(entry)` 从包入口导出，供 `agent-web` 的 Agent 配置树复用。
- 独立思维导图文件扩展名 `.mind` 映射到现有 Material Icon Theme `Tree` 图标；本包只负责文件类型视觉识别，不解析或编辑思维导图正文。
- 展开目录时通过回调交给 app 调用后端。
- 文件树标题行提供刷新事件按钮，本包不直接调用后端。
- 搜索可使用 app 层传入的服务端结果；`file-explorer` 在渲染前仍按当前关键字对 `name` 做不区分大小写的子串过滤，父目录路径命中不会展示文件名未命中的文件。app 未提供结果时，本地回退同样只过滤已加载物理工作区的文件名。
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

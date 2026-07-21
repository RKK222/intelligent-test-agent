# 包说明：@test-agent/file-explorer/src

## 职责

实现文件树 UI、基础搜索和变更文件入口，并保持 context 选择场景下的稳定列表布局。

## 主要程序清单

- `FileExplorer.vue`：三 tab 文件面板、Changed Files 入口、VS Code 风格紧凑状态徽标和选择回调；以组合视图节点稳定 `id` 维护工作区文件内部复制/剪切状态、根目录拖放、全局拖放高亮清理和浏览器文件选择器；搜索 tab 接收 app 层传入的服务端工作区搜索结果并高亮关键字，不把引用节点混入搜索。
- `DirectoryRows.vue`：递归渲染 22px 文件树行、目录展开状态、变更行数、引用来源颜色和加载状态；纯引用节点只读，混合目录只把新增/上传/粘贴/拖入映射到 `workspacePath`。仅可写纯 `WORKSPACE` 文件和目录可作为拖动源，源行显示抓取/半透明反馈；只读、纯 `REFERENCE` 和 `MIXED` 条目不可拖。合法目录和根空白区显示蓝色落点；当前父目录、自身、被拖目录的后代、文件行、纯引用目录和只读目录均拒绝落入，带 `workspacePath` 的 `MIXED` 目录可作为工作区侧落点接收工作区条目。普通文件和目录双击后提供行内改名，行尾 `−` 或聚焦后按 Delete/Del 共用删除确认；普通工作区文件支持 Ctrl/Cmd+C/X/V/Z、右键复制/剪切/粘贴/撤销和拖放到目录，目录 `+` 复用共享新建面板按明确目标路径新建或上传；删除确认继续使用统一紧凑工作台弹框，最终操作均 emit 给 app 层。
- `FileEntryCreateDialog.vue`：工作空间根、目录行和 Agents 配置树共享的新建/上传面板；统一文件/文件夹名称校验、目标目录展示和可选上传入口，不直接访问后端。Agent 配置根通过 `allowAgentTemplates` 追加 Agent/Skill 标准模板选项与普通条目差异说明，公共/应用及目录上传仍由调用方按 scope 路由。
- `FileEntryDeleteDialog.vue`：工作空间与 Agents 配置树共享的文件/目录删除确认面板；目录明确提示递归删除全部内容，只上报确认事件，不直接访问后端。
- `fileIcons.ts`：按文件类型返回 VS Code codicon class，包入口导出 `getVsCodeFileIconClass` 供应用侧 Agent 树复用。
- `filterLoadedFiles.ts`：已加载文件名过滤，作为服务端搜索结果未提供时的本地回退。
- `highlightKeyword.ts`：把文件名按关键字分段，供搜索结果高亮渲染。

## 允许依赖

- Vue。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。
- `@vscode/codicons`。

## 禁止依赖

- 直接访问后端 API。
- 内容搜索或 Git 操作实现。
- 直接构造 `PromptPart` 或读取文件内容。

## 修改时必须同步更新

- `docs/standards/frontend.md`。
- 本包 README 和测试。

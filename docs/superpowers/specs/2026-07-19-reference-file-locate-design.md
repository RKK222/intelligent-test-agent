# 引用文件定位到当前文件设计

## 背景与根因

编辑器页脚的“定位到当前文件”和标签双击都会把当前 `tab.path` 传给 `AgentWorkbench.handleLocateFile`。普通工作区文件的 `tab.path` 是工作区相对路径，现有逻辑可以按路径片段逐层加载并展开目录。

引用文件为了区分工作区、引用别名、引用内路径和合并后的逻辑路径，使用 `workspace-reference:` 开头的合成 tab 身份。现有定位逻辑仍把该值当作工作区相对路径传给 `expandPathToFile`，无法解析出文件树的引用目录节点，因此虽然当前 tab 仍处于激活状态，折叠目录不会展开，也无法滚动到对应文件。

打开引用文件时，前端已经把合成 tab 身份映射到后端返回的稳定文件树节点 `id`；文件树缓存也按父目录节点 `id` 保存子节点列表。修复应复用这些现有身份，不能根据展示路径猜测引用来源。

## 方案决策

采用稳定节点 ID 反向恢复祖先目录链：

- 普通工作区文件保持现有相对路径展开逻辑，不改变行为。
- 引用文件先从 `workspaceViewNodeIdByTabPath` 取得精确叶子节点 ID，再根据 `entriesByDirectory` 的“父目录 ID → 子节点”关系反向查找祖先目录。
- 从根到叶依次展开祖先目录；如果某层缓存尚未加载，则复用该目录在 `workspaceViewDirectoryById` 中的 locator 调用现有 `loadDirectory`。
- 目录展开完成后，继续使用现有 active node ID 高亮和滚动逻辑，确保合并引用、非合并引用以及同名工作区/引用节点都定位到实际打开的引用文件。
- 如果引用 tab 缺少节点映射或祖先链已失效，不错误定位到同名工作区文件；只保留当前 tab，并安全结束定位。普通工作区路径仍可走现有兼容回退。

不采用按 `logicalPath` 重新匹配文件树，因为合并目录或同名冲突时无法唯一确定引用来源。不新增全局 reveal service 或修改后端接口，因为现有节点映射和目录缓存已经足以完成精确定位。

## 代码边界

- 在 `workspaceViewState.ts` 增加纯函数，根据目标节点 ID 与当前目录缓存计算有序祖先目录 ID，便于独立测试并避免把树搜索细节堆入 Vue 组件。
- `AgentWorkbench.vue` 的定位处理根据普通路径/引用合成路径选择对应的展开方法；事件签名、`WorkbenchFooter`、`FigmaEditorArea` 和 `EditorTab` 数据结构保持不变。
- 不修改 backend、HTTP API、RunEvent、数据库、OpenCode manager、generated SDK 或环境配置。

## 测试与文档

先在 `workspaceViewState.test.ts` 增加失败回归，覆盖：

- 合并引用文件可按稳定节点 ID 得到从根到叶的祖先目录顺序。
- 非合并引用别名目录可完整展开。
- 同名工作区与引用文件共存时只使用实际节点关系，不按展示路径误选。
- 目标不存在、父链不完整或出现异常循环时安全返回，不造成无限循环。

再增加或调整工作台组件回归，确认引用 tab 的定位调用稳定节点展开逻辑并在目录展开后滚动；普通文件定位保持兼容。同步更新 agent-web 与文件树相关说明，执行定向 Vitest、agent-web typecheck、前端 lint/build 和 `git diff --check`。

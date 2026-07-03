# 工作区 Git 原生冲突流程设计

## 背景

个人工作区的“提交并推送”当前先提交用户选中的本地文件，再拉取应用版本分支并合入个人分支。若应用分支已经积累大量目录调整，用户会从“只有一个本地 Diff”突然进入“几十个自动合并结果和多个冲突”的状态。现有页面只支持逐文件解决冲突，冲突路径还可能保留 Git 的八进制转义；发布冲突后，应用副本数据库记录也可能继续停留在 pull 前的旧 commit。

本设计保留 Git 的原生 merge 语义，不自动覆盖任一侧内容，增加发布前同步预览和冲突批量处理。

## 目标

1. 远程/应用分支存在待合入变更时，发布前先展示变更规模，用户确认后才提交并 merge。
2. 冲突支持一键“全部保留个人版本”“全部采用应用版本”和“取消整次合并”，不要求逐文件处理。
3. 只有需要混合选择或手工编辑内容时，用户才进入单文件冲突编辑器。
4. 正确展示 `AU`、`UD`、`UA`、`DU` 等文件存在性冲突，不把缺失侧伪装成空文本。
5. 中文冲突路径使用真实 UTF-8 路径，不向前端暴露 Git 八进制转义。
6. 应用副本 pull 成功后，即使个人分支 merge 冲突，也要保持数据库 replica/target commit 与应用 worktree HEAD 一致。
7. 首次进入工作台或恢复最近工作区时自动加载 Diff 文件数量和角标，不要求用户打开变更页后手工刷新。

## 非目标

- 不自动决定冲突应保留个人版本还是应用版本。
- 不改变个人分支最终合入应用版本分支、只推送应用版本特性分支的既有发布模型。
- 不新增数据库表或字段。
- 不清理或改写当前正在进行的 54+8 merge；实现和自动化测试使用独立临时 Git 仓库。

## 交互设计

### 发布前同步预览

用户点击“提交并推送”后，前端先请求发布预览：

- 如果应用分支没有个人分支尚未包含的提交，直接执行现有发布。
- 如果存在待同步提交，弹出确认对话框，展示：
  - 应用分支待合入提交数；
  - 变更文件总数；
  - 新增、修改、删除、重命名数量；
  - 最多若干条代表性路径；
  - “继续会先提交当前已暂存文件，再把这些应用变更合入个人工作区”的说明。

用户选择“继续同步并提交”后，发布请求携带预览得到的应用分支 HEAD。后端在实际发布前再次 pull 并校验 HEAD；若远程在预览后又变化，返回需要重新预览的统一冲突错误，不基于过期确认继续 merge。

### 冲突批量操作

冲突列表顶部提供三个操作：

- **全部保留个人版本**：对每个 unmerged path 选择 Git stage 2；stage 2 不存在时删除该路径，然后统一 stage。
- **全部采用应用版本**：对每个 unmerged path 选择 Git stage 3；stage 3 不存在时删除该路径，然后统一 stage。
- **取消整次合并**：执行现有 `merge --abort`。

操作前显示确认提示，明确将处理的冲突文件数量。批量解决后刷新 Git Diff；Git 原生自动合并产生的 staged 文件继续保留。只要仍有 unmerged path，提交和推送继续禁用；全部解决后，用户再次点击“提交并推送”完成 merge commit 和 push。

当前 8 个 `AU` 冲突只有 stage 2：

- “全部保留个人版本”会保留并 stage 8 个文件；
- “全部采用应用版本”会删除并 stage 8 个文件。

### 单文件冲突

单文件编辑器继续支持当前、应用、两者、手工内容和删除，但根据 stage 是否存在调整文案：

- stage 2 缺失：显示“个人版本已删除此文件”；
- stage 3 缺失：显示“应用版本已删除此文件”；
- 两侧都存在：显示普通双栏内容；
- 缺失任一侧时禁用“保留两者”。

## 后端设计

### 复用边界

- `GitWorkspaceService` 继续作为 Git 原子操作唯一入口。
- `ManagedWorkspaceApplicationService` 继续负责用户归属、工作区路径映射、发布编排和 DTO 组装。
- `ManagedWorkspaceController` 只暴露 HTTP 路由，不直接运行 Git 命令。
- 前端继续只通过 `backend-api` 调用平台后端。

### Git 原子能力

扩展现有 `GitWorkspaceService`：

1. `conflictPaths` 复用 `gitNoQuotedPath`，关闭 `core.quotepath`，保证中文路径可直接使用。
2. 增加按 current/incoming 批量解决冲突的原子方法：
   - 先读取所有 unmerged path 和 stage；
   - 对存在目标 stage 的路径执行原生 ours/theirs checkout；
   - 对目标 stage 不存在的路径执行 `git rm`；
   - 对保留路径执行定点 `git add`。
3. 不提供“无条件 git add -A 全部标记已解决”，避免把仍含冲突标记的工作树文本误提交。

### 发布预览

新增个人工作区发布预览服务：

1. 校验 personal workspace 归属。
2. 确保应用版本副本存在且干净。
3. fetch/pull 应用版本特性分支。
4. 立即把应用版本 target commit 和当前服务器 replica commit 更新为实际 HEAD。
5. 比较个人分支 HEAD 与应用分支 HEAD，返回待合入提交数和 name-status 汇总。

发布请求增加可选 `expectedApplicationHead`：

- 新前端预览后传入；
- 旧调用方不传时保持兼容，继续执行原流程；
- 传入后若实际应用 HEAD 不一致，返回 `CONFLICT`，要求重新预览。

### 冲突批量 API

新增：

`POST /workspaces/{workspaceId}/git-conflict/resolve-all`

请求体：

```json
{
  "resolution": "CURRENT"
}
```

`resolution` 只支持 `CURRENT`、`INCOMING`。接口只允许 personal workspace 且必须存在未解决 merge 冲突。

## 前端设计

### backend-api

增加：

- `previewPersonalWorkspacePublish(personalWorkspaceId)`；
- `resolveAllWorkspaceGitConflicts(workspaceId, resolution)`；
- publish payload 的 `expectedApplicationHead`。

### GitChangesPanel

1. 发布按钮先调用 preview。
2. 有待同步变更时展示确认对话框；用户确认后才调用 publish。
3. 冲突区域增加批量操作工具条和二次确认。
4. 批量操作、单文件操作和取消合并共用现有 refresh/错误处理。
5. 发布返回冲突时只显示可读摘要，不再把几十条完整路径拼成超长 banner；详细路径由冲突列表展示。

### Diff 数量自动同步

`AgentWorkbench` 当前只在 `selectedWorkspaceIdRef` 后续发生变化时刷新 `vcsDiffFiles`，watch 没有 immediate；当最近工作区在 watcher 建立前已经恢复，左侧“变更”角标会保持空值，直到用户手工刷新并由 `changes-refreshed` 触发第二次查询。

修复后：

1. 工作区 watcher 首次挂载立即执行，已有 workspaceId 时自动查询 Git Diff。
2. 切换工作区时先清空旧工作区角标，再以请求序号保护新结果，避免慢响应覆盖。
3. `GitChangesPanel` 完成真实 Git 查询后把已加载文件摘要随 `changes-refreshed` 传给父组件，父组件直接更新 `vcsDiffFiles`，不再为了角标重复查询一次。
4. stage、unstage、单文件/批量冲突解决、取消 merge 和发布结果都复用同一刷新出口。

## 错误处理

- 应用版本副本不干净：保持现有 `CONFLICT`。
- 预览后远程 HEAD 变化：返回明确的“应用分支已更新，请重新确认同步内容”。
- 批量操作中 Git 命令失败：返回统一 `GIT_UNAVAILABLE`，刷新后以真实 index 状态为准。
- 批量操作请求时不存在 merge/unmerged path：返回 `CONFLICT`。
- 中文路径不做前端二次解码，后端统一返回 UTF-8。

## 测试

### 后端

- 真实临时 Git 仓库覆盖中文冲突路径不转义。
- 覆盖 `UU`、`AU`、`UD` 等 stage 组合的批量 current/incoming 语义。
- 覆盖发布预览的提交数、状态统计、样例路径和应用 HEAD。
- 覆盖 expected HEAD 一致时继续发布、不一致时拒绝。
- 覆盖 pull 成功但个人 merge 冲突时 replica/target commit 已更新。
- Controller 覆盖新增路由和请求映射。

### 前端

- 有远程变更时先展示确认，不立即 publish。
- 无远程变更时直接 publish。
- 批量保留当前/采用应用调用正确 API 并刷新。
- 冲突 banner 不拼接完整路径列表。
- `AU` 缺失应用侧时展示“应用版本已删除此文件”。
- 最近工作区在组件挂载前已恢复时，变更角标仍会自动显示；快速切换工作区时旧请求不能覆盖新数量。
- 全量 Vitest、typecheck 和 production build。

## 运行验证

实现后使用 `.env.test` / `test` profile 重启服务。自动化测试使用独立临时 Git 仓库，不修改当前 888 账户正在进行的 merge。页面验收至少覆盖发布预览对话框、批量操作按钮可见性和中文路径展示；是否实际批量解决当前 8 个冲突由用户另行确认。

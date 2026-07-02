# 工作区 Git 发布与冲突处理设计

## 目标

修复应用工作空间 Git 变更面板的三个一致性问题：

1. 普通发布只提交用户明确暂存的文件，不把索引中残留的其他文件一起提交。
2. 合并冲突在工作台中显示为可操作的三方合并视图，支持当前版本、应用版本、两者合并、手工编辑和取消整次合并。
3. 只有后端确认远端 `push` 完成后，前端才显示“提交并推送成功”；失败时清除进行中或成功文案并展示错误。

## 根因

- `publishPersonalWorkspace` 先 `git add` 选中文件，再执行无 pathspec 的 `git commit`。如果索引中已有其他 staged 文件，Git 会把它们一并提交。
- 后端已保留个人 worktree 中的 merge 冲突并通过 `git-diff` 返回 `status=conflict`，但前端只展示冲突行，没有读取 Git index 的 base/ours/theirs 内容，也没有标记解决或 abort merge 的 API。
- 前端发布流程会在中间步骤写入成功进度，异常分支只写错误而不清空进度；个人工作区发布响应也没有独立的远端推送确认字段。

## 方案

### 文件白名单提交

普通发布时，后端先把索引恢复到 `HEAD`，再只 stage 请求 `files` 中的路径并提交。未选择文件保留在工作树中。

如果个人 worktree 已处于 merge 流程，则不重置索引：冲突全部解决后必须提交完整 merge 结果，包括 Git 自动合并的文件。后端先确认不存在 unmerged 文件，再提交现有 merge index。

### 三方冲突编辑器

新增工作区 Git 冲突读取 API，内容来自 Git index：

- stage 1：共同基线；
- stage 2：当前个人版本；
- stage 3：应用版本；
- working tree：Git 生成的待解决结果。

前端复用 `@test-agent/diff-viewer` 已有 Monaco 懒加载能力，在中间工作区展示“当前个人版本 / 应用版本 / 合并结果”三块。合并结果可编辑，支持：

- 保留当前版本；
- 采用应用版本；
- 保留两者；
- 手工编辑后标记已解决；
- 当前或应用版本为“文件不存在”时，将结果标记为删除；
- 取消整次 merge。

标记解决由后端写入或删除工作树文件后执行定点 `git add -- <path>`。取消合并调用既有 `GitWorkspaceService.abortMerge`。

### 推送结果语义

`PersonalWorkspacePublishResponse` 增加向后兼容字段：

- `remotePushed`：只有 `git push` 返回成功才为 `true`；
- `headCommit`：已推送的应用版本分支 HEAD。

前端必须同时满足 `status=MERGED` 和 `remotePushed=true` 才显示成功。任一步骤失败时清空进度文案，不显示成功；Agent 配置 Git 操作同时校验返回的 operation status。

## 边界

- Git 业务仍属于 `test-agent-workspace-management`，原子 Git 命令继续复用 `test-agent-common/GitWorkspaceService`。
- HTTP DTO 位于 `test-agent-api`，前端请求统一由 `packages/backend-api` 发出。
- Monaco 合并组件位于 `packages/diff-viewer`，只呈现和 emit 用户决策，不直接访问后端。
- 不新增数据库、Redis、RunEvent 或 generated SDK 变更。

## 验证

- 后端单元测试覆盖残留 staged 文件隔离、merge 重试、冲突 stage 读取、解决和 abort、push 失败不返回成功。
- 前端组件测试覆盖只发布一个选中文件、冲突编辑器打开与解决、push 未确认/异常时不显示成功。
- 构造本地 bare remote、应用分支和个人 worktree，制造真实 Git 冲突，验证三方内容、解决、重试和远端 HEAD。
- 使用 `.env.test` / `test` profile 启动真实前后端并通过浏览器直连验证。

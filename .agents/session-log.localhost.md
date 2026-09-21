# 2026-09-21 修复提交推送进度弹窗红色错误说明被后台刷新刷没

## Why

- 用户反馈：应用 Agent「提交并推送」失败时，进度弹窗里的红色错误说明刚显示就消失，来不及看清失败原因。
- 排查结论：弹窗错误文本绑定 `errorMessage`；提交流程 catch 中虽然用了 `refreshChanges({ preserveError: true })`，
  但 `GitChangesPanel.vue` 中两个后台 watch（`workbench.publicWorktree?.worktreeId` 变化、`agentConfigRevision` 变化）
  调用 `refreshChanges()` 时不带 `preserveError`，会无条件清空 `errorMessage`；同时后台刷新自身失败还会用
  「刷新变更列表失败」覆盖弹窗里的提交失败说明。

## What

- `frontend/apps/agent-web/src/components/GitChangesPanel.vue`：
  `refreshChanges` 增加 `keepError = options.preserveError || showCommitProgressDialog.value`，
  进度弹框打开期间既不清空也不覆盖 `errorMessage`（含自身失败分支）。
- `frontend/apps/agent-web/tests/git-changes-panel.test.ts`：新增回归用例
  「keeps the failed publish error visible when a background revision refresh runs」——
  发布失败弹出错误说明后，rerender 递增 `agentConfigRevision` 触发后台刷新，断言错误说明与 FAILED 状态仍在。

## How

- 先全链路定位：弹窗模板（`提交并推送进度`）、`applyPublishProgressEvent`/`publishErrorExecution`、
  `refreshChanges` 的清空逻辑、`AgentWorkbench.vue` 中 `agentConfigRevision` 的 5 处递增点，
  确认后台自动刷新是唯一能在弹窗打开期间清空 `errorMessage` 的路径。
- 反向验证：临时把 `keepError` 退回 `options.preserveError`，新用例失败；恢复修复后通过，证明用例确实覆盖该 bug。
- 验证命令：`npx vitest run apps/agent-web/tests/git-changes-panel.test.ts`（65 passed，在 frontend 根目录跑，
  注意 vitest 配置在 `frontend/vitest.config.ts`，进 apps/agent-web 子目录跑会缺 jsdom 环境报 `window is not defined`）；
  `npx vue-tsc --noEmit`（agent-web）通过。

## Result

- 弹窗打开期间红色错误说明不会再被后台 watch 刷新刷掉；用户关闭弹窗后行为不变。
- 不涉及 API、事件、数据库、安全、兼容性；纯前端组件行为修复 + 回归测试。

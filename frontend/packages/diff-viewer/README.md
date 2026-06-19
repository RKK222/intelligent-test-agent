# @test-agent/diff-viewer

## 工程定位

Run/Session/VCS Diff 查看和 Run 级动作入口包。

## 主要职责

- 使用 Monaco Diff Editor 展示当前文件 Diff。
- 展示 Changed Files 列表。
- 支持 Run、Session/message、VCS 三种 Diff 来源切换。
- 支持 split/unified 视图切换。
- 支持 hunk 列表、上一处/下一处 hunk 导航，以及把当前 hunk 引用为下一条 Prompt 的 file context。
- 提供 Run 级接受/拒绝回调。
- 当前文件操作只作为选择和反馈，不承诺后端 per-file 回滚。

## 禁止事项

- 不直连 opencode server。
- 不自行修改工作区文件。

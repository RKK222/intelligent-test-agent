# @test-agent/diff-viewer

## 工程定位

Run 级 Diff 查看和动作入口包。

## 主要职责

- 使用 Monaco Diff Editor 展示当前文件 Diff。
- 展示 Changed Files 列表。
- 提供 Run 级接受/拒绝回调。
- 当前文件操作只作为选择和反馈，不承诺后端 per-file 回滚。

## 禁止事项

- 不直连 opencode server。
- 不自行修改工作区文件。

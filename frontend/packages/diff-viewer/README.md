# @test-agent/diff-viewer

## 工程定位

Run/Session/VCS Diff 查看和 Run 级动作入口包。

## 主要职责

- 使用 Monaco Diff Editor 展示当前文件 Diff。
- 复用 Monaco 懒加载能力展示当前个人版本、应用版本和可编辑合并结果；组件只 emit 冲突解决/取消决策，不直接调用后端。
- 展示 Changed Files 列表。
- 支持 Run、Session/message、VCS 三种 Diff 来源切换。
- 支持 split/unified 视图切换。
- 支持 hunk 列表、上一处/下一处 hunk 导航，以及把当前 hunk 引用为下一条 Prompt 的 file context。
- 提供 Run 级接受/拒绝回调。
- 当前文件操作只作为选择和反馈，不承诺后端 per-file 回滚。
- Diff toolbar、hunk 导航和文件列表必须保持固定高度；长文件名、空 patch 和 loading/error 状态不能挤压 Monaco diff 区域。
- hunk context 只返回平台 `PromptPart` file context 给 app 层，不直接发送 prompt。

## 禁止事项

- 不直连 opencode server。
- 不自行修改工作区文件。
- 不开放 per-file/per-message 回滚 HTTP 调用，除非后端先明确语义并同步 API 文档。

# @test-agent/editor

## 工程定位

Monaco 文件编辑器包。

## 主要职责

- 按文件路径推断语言。
- 展示当前文件内容、脏状态、只读状态和保存按钮。
- 上报 Monaco 当前文本选区给 app 层，用于构造 Prompt file context。
- 保存动作通过回调交给 app 层调用 `backend-api`。
- Phase 11 选区上下文只上报文件路径、语言、选区范围和文本片段；是否转换为 `PromptPart` 由 app 层负责。
- 编辑器容器需要保持稳定高度和最小宽度，避免保存反馈、只读状态或长文件名挤压 Monaco 区域。

## 禁止事项

- 不启动 Run。
- 不直接调用后端。
- 不处理 Diff 接受拒绝。
- 不订阅 RunEvent，不直连 opencode server。

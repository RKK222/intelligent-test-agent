# @test-agent/workbench-shell

## 工程定位

Dockview 工作台布局和跨面板 UI 状态包。

## 主要职责

- 渲染顶部栏和 Dockview 左/中/右/底 panel。
- 提供打开文件 tab、活动文件、Diff 选择等 Zustand 状态。
- 保证固定布局区域有稳定尺寸。

## 禁止事项

- 不调用后端 API。
- 不处理文件内容保存、Run 启动或 Diff 接受拒绝。

# @test-agent/workbench-shell

## 工程定位

Dockview 工作台布局和跨面板 UI 状态包。

## 主要职责

- 渲染顶部栏和 Dockview 左/中/右/底 panel。
- 提供打开文件 tab、活动文件、Diff 选择等 Zustand 状态。
- 保证固定布局区域有稳定尺寸。
- 保持顶部栏、panel tab、底部运行区和状态徽标的视觉稳定性；Phase 11 streaming、terminal warning 或 Diff 状态变化不得改变整体布局尺寸。
- 只承接工作台级 UI 状态，不承接 session、prompt、permission/question 或 terminal WebSocket 业务状态。

## 禁止事项

- 不调用后端 API。
- 不处理文件内容保存、Run 启动或 Diff 接受拒绝。
- 不订阅 RunEvent，不创建 terminal ticket。

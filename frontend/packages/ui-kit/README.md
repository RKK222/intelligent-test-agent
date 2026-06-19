# @test-agent/ui-kit

## 工程定位

平台通用 UI 基础组件包。

## 主要职责

- Button、Badge、Input、Tabs、FeedbackBanner 等基础组件。
- 提供 `cn` 样式合并工具。
- 统一基础控件的 Phase 11 视觉状态：hover/focus/disabled、紧凑工具栏尺寸、状态徽标色阶和输入框边界。
- 组件样式必须消费 app 全局 theme token，不能在包内引入业务专用配色或运行态语义。

## 禁止事项

- 不调用后端 API。
- 不订阅 RunEvent。
- 不承载业务状态。
- 不实现 Agent/Model、permission/question、Diff、terminal 等业务控件逻辑。

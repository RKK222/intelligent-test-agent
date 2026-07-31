# 包说明：integration.uitest

## 职责

- 接收一行“四列测试案例”：案例名称、测试步骤、测试数据、预期结果。
- 通过带外部 Bearer Token 的短请求提交或查询独立 `uitest6` 平台。
- 将第三方状态和错误映射为平台稳定契约。

## 边界

- 一条 command 只对应一次 UI 自动化，`requestId` 用于外部幂等。
- “测试步骤”是唯一操作流程，其余三列提供标识、输入和验证上下文。
- 不包含 `uitest6` 源码、浏览器运行时或任务轮询编排。
- 外部 Token 只由 `UiTestExecutionSettings` 在 Java 进程中读取，不得进入 Tool、日志或响应。

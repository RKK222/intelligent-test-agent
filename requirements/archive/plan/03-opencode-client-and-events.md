# Phase 03 opencode client 与事件体系

## 阶段目标

通过 `OpencodeClientFacade` 收敛 generated SDK 调用，建立 opencode server 响应、错误和事件到平台 RunEvent 的转换机制。

## 可验收功能清单

1. `test-agent-opencode-client` 提供 `OpencodeClientFacade`。
2. 业务代码不直接依赖 `test-agent-opencode-sdk-generated`。
3. opencode server 错误映射为平台统一错误。
4. opencode server 执行输出转换为平台 RunEvent。
5. RunEvent 可写入持久化并可通过事件模块读取。

## 修改项目

- `backend/test-agent-opencode-client`
- `backend/test-agent-opencode-sdk-generated`
- `backend/test-agent-event`
- `backend/test-agent-persistence`
- `backend/test-agent-domain`
- `docs/api/event-stream-api.md`
- `docs/backend/backend-testing-standards.md`

## 实现功能

- Facade 隐藏 generated SDK 的 DTO 和 API 类。
- Facade 支持超时、取消、重试策略和 traceId 透传。
- 事件转换逻辑输出平台稳定事件类型，例如 run.started、assistant.message.delta、tool.started、tool.finished、diff.proposed、test.finished。
- 未知事件必须保留原始上下文或记录可观测错误，不能导致运行中断。

## 验收方式

- 后端编译通过。
- 依赖检查证明业务模块不直接依赖 generated SDK。
- Facade 测试覆盖成功、超时、远端错误和取消。
- 事件转换测试覆盖已知事件和未知事件。
- 事件文档同步新增事件类型和字段。

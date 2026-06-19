# 包说明：com.example.testagent.opencode.client

## 职责

opencode client facade 包，隔离 generated SDK，为业务侧提供稳定的 opencode server 调用边界。

## 不负责

- 不暴露 generated SDK DTO 给 domain 或前端。
- 不承载 Controller。
- 不访问数据库。

## 主要程序清单

- `package-info.java`：说明本包是 generated SDK 的稳定 facade 层。
- `OpencodeClientFacade`、`DefaultOpencodeClientFacade`：业务侧 opencode 调用门面。
- `OpencodeSdkGateway`、`GeneratedOpencodeSdkGateway`：generated SDK 内部适配端口和实现。
- `OpencodeHealthCommand`、`OpencodeCancelCommand`、`OpencodeStreamEventsCommand`：平台命令模型。
- `OpencodeHealthResult`、`OpencodeCancelResult`：平台结果模型。
- `OpencodeRunEventMapper`：opencode raw event 到 `RunEventDraft` 的转换器。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain` 中必要的平台模型。
- `test-agent-observability` 中 trace header 常量。
- `test-agent-opencode-sdk-generated`。
- Reactor。

## 禁止依赖

- `test-agent-app`。
- `test-agent-persistence`。
- Web Controller。

## 上游调用方

- `test-agent-app` 应用服务。
- event 模块的事件映射协作。

## 下游依赖

- generated SDK。
- observability trace 和指标能力。

## 测试位置

- opencode client 模块单元测试。
- 使用 mock opencode server 的集成测试。
- 错误转换、超时、重试、事件转换测试。

## 修改时必须同步更新

- `backend/test-agent-opencode-client/README.md`。
- `docs/backend/backend-coding-standards.md`，如果 facade 边界变化。
- `docs/api/event-stream-api.md`，如果事件映射变化。
- SDK 生成说明，若依赖 generated API 变更。

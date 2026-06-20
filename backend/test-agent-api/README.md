# test-agent-api

## 工程定位

后端 HTTP/SSE/WebSocket API 定义模块，只做协议入口、请求响应 DTO、统一响应、错误、traceId、鉴权、限流和受控 WebSocket 适配。

## 主要职责

- 暴露旧 `/api/...` 兼容 URL。
- 暴露新增 `/api/internal/platform/...`、`/api/internal/agent/opencode/...` 和预留 `/api/public/...` URL。
- Controller 只调用业务模块 service，不直接访问 Repository、generated SDK 或 JDBC 实现。
- 维护 `RuntimeDtos` 等平台 DTO，不返回 generated SDK DTO。
- RunEvent SSE 建连时先委托 runtime 恢复 opencode projected messages，再进入 durable replay 与 live bus 合流。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-observability`。
- `test-agent-event`。
- `test-agent-workspace-management`。
- `test-agent-opencode-runtime`。
- Spring WebFlux、Validation、Security。

## 禁止依赖

- `test-agent-persistence`。
- `test-agent-opencode-sdk-generated`。
- Repository 实现类。
- 业务规则、文件系统操作、opencode 调用编排。

## 后续 AI 编码指引

新增 API 时先确认业务实现应落在哪个业务模块；本模块只新增 Controller/DTO/协议转换。旧 URL 不删除，新 URL 必须同步记录到 `docs/api/http-api.md`。

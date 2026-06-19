# Phase 04 后端 API 运行时

## 阶段目标

在 `test-agent-app` 中提供前端所需的 Workspace、Session、Run、Cancel 和 Event API，并落地鉴权占位、限流占位、统一错误和 traceId。

## 可验收功能清单

1. Workspace API 可列目录、读文件、保存文件、查询文件状态。
2. Session API 可创建会话、查询列表、查询详情、追加消息。
3. Run API 可启动任务、查询状态、取消任务。
4. Event API 通过 RunEvent SSE 推送实时事件。
5. 全部 API 返回统一响应和统一错误格式。

## 修改项目

- `backend/test-agent-app`
- `backend/test-agent-common`
- `backend/test-agent-domain`
- `backend/test-agent-event`
- `backend/test-agent-persistence`
- `backend/test-agent-opencode-client`
- `docs/api/backend-api.md`
- `docs/api/event-stream-api.md`
- `docs/security/security-standards.md`

## 实现功能

- Controller 只做协议转换、参数校验和响应封装。
- 应用服务编排领域、Repository、事件和 opencode client。
- 鉴权、限流和 CORS 先实现可替换占位，不硬编码密钥。
- SSE 支持 `Last-Event-ID`，断线后可继续读取事件。

## 验收方式

- Controller 测试覆盖成功、参数错误、鉴权失败、限流占位和统一错误。
- SSE 测试覆盖连接、事件发送、断线续传和取消订阅。
- API 文档覆盖路径、方法、请求、响应、错误码和兼容说明。
- 后端 `mvn clean package -DskipTests` 通过。

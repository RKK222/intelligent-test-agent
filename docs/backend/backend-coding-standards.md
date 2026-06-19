# 后端编码规范

本规范适用于 `backend/` 下所有人工维护代码。

## 工程原则

1. 后端是 Maven multi-module 工程。
2. 只有 `test-agent-app` 是可运行 Spring Boot 服务包。
3. 其他 `test-agent-*` 模块只产出 library jar。
4. Java 版本为 21，Spring Boot 版本为 4.1.0。
5. 业务代码优先遵守模块 README 和源码包 PACKAGE.md 的边界。
6. 新增后端文件前必须先列出现有合适工程；没有合适工程时按业务边界新建 Maven module。

## 模块职责

- `test-agent-common`：公共异常、响应模型、TraceId、分页、校验、时间工具。
- `test-agent-domain`：纯领域模型、状态机、领域规则和值对象。
- `test-agent-observability`：日志、trace、Micrometer、观测性公共封装。
- `test-agent-opencode-sdk-generated`：生成的 opencode Java SDK，禁止手改源码。
- `test-agent-opencode-client`：generated SDK 的业务 facade，可依赖 domain 平台模型和 observability trace 常量，但只能在本模块内部直接使用 generated SDK。
- `test-agent-workspace-management`：workspace、文件查看/新增/修改/删除、后续 git/diff、agent 和 skill 管理业务。
- `test-agent-opencode-runtime`：Session、Run、RunEvent 编排、opencode runtime、Diff/revert、terminal ticket/PTY 业务。
- `test-agent-system-management`：用户、角色、权限等平台内部管理业务边界。
- `test-agent-integration`：非 opencode 外部系统联动业务边界。
- `test-agent-api`：Controller、WebSocket 入口适配、请求/响应 DTO、统一异常、鉴权、限流和 trace Web 入口。
- `test-agent-persistence`：数据库、Flyway、Repository、Redis 可选适配。
- `test-agent-event`：RunEvent、SSE、事件转换、事件回放。
- `test-agent-test-support`：测试 fixture、mock server、集成测试支撑。
- `test-agent-app`：唯一启动入口和可部署服务包，只放启动、装配、profile、migration、health 和日志等运行入口。

## 分层规则

1. Controller 只负责协议适配、参数校验和调用业务模块 service。
2. Controller 不得直接访问 Repository。
3. Controller 不得直接调用 generated SDK。
4. Domain 不依赖 Spring Web、Persistence、generated SDK。
5. Persistence 不反向依赖 `test-agent-app`。
6. 只有 `test-agent-opencode-client` 可以直接依赖 generated SDK。
7. generated SDK DTO 不得进入 domain，不得直接返回给前端。
8. opencode facade 对外只暴露平台 command/result 和 RunEventDraft，不返回 generated SDK DTO。
9. `test-agent-api` 不放业务规则，不依赖 `test-agent-persistence` 或 `test-agent-app`。
10. 业务模块不得依赖 `test-agent-api` 或 `test-agent-app`。
11. `test-agent-app` 不得新增 workspace、session、run、runtime、terminal、web 等业务源码包。

## DTO 与模型

1. 对外 API 使用平台 DTO，不直接暴露 generated SDK 模型。
2. 领域对象表达业务概念，不携带 HTTP、数据库或 SDK 注解。
3. Persistence 映射对象只在持久化模块内部使用。
4. API 请求和响应 DTO 变更必须更新 `docs/api/backend-api.md`。

## 中文注释

1. 新增人工维护类、接口、复杂算法、状态流转、异常分支必须有中文注释。
2. 人工维护的方法也必须有中文注释或中文 Javadoc，说明方法意图、关键入参、返回语义和边界条件。
3. 注释说明业务意图、边界和原因。
4. 不为简单 getter/setter、显而易见赋值写空洞注释。
5. generated SDK 不手工加注释。

## 配置

1. 配置项必须有统一前缀，避免散落硬编码。
2. 密钥、token、服务地址、超时时间、限流阈值必须通过配置注入。
3. 默认配置必须适合本地开发，不得包含真实密钥。
4. 新增配置必须同步 README 或相关规范文档。

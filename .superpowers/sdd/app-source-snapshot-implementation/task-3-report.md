# Task 3 实施报告：应用源码 API、持久化进度 WebSocket 与文件路由集成

## 结论

Task 3 已完成。应用源码仓库查询、分支/远端树、固定提交物化、同 generation 副本重试、打开、最近选择、操作快照和进度 ticket 已由 `test-agent-api` 暴露；前端 `shared-types` 与 `backend-api` 已提供对应安全 DTO 和 client。进度通道以数据库 operation/step/replica 为事实源，独立于 RunEvent；文件访问继续使用平台 route/ticket/RPC，并禁止 AppSource replica Workspace 因历史服务器信息发生本机回绑或降级。

本任务未修改 OpenCode 快照、generated SDK、数据库结构、MyBatis SQL、Flyway、`.env.local` 或其它环境配置。

## 后端实现

### HTTP 与业务投影

- `AppSourceController` 暴露 8 个应用源码生命周期入口：仓库列表、分支、远端树、物化、同 generation 副本重试、打开、最近选择查询和清除。
- `AppSourceOperationController` 暴露持久化操作快照查询与进度 WebSocket ticket 签发。
- Controller 只读取认证主体、`APP_ADMIN` 事实、traceId 和当前用户 READY opencode 进程服务器，再委托 `AppSourceApplicationService`；不访问 Repository、generated SDK、Git 或文件系统。
- 仓库列表严格返回 `NOT_DOWNLOADED/DOWNLOADED_ACTIVE/DOWNLOADED_EXPIRED/PERSONAL_OCCUPIED` 四态，并提供 owner 姓名/统一认证号、固定分支/commit、`selectedPaths`、到期时间、最新操作、安全步骤及逐服务器摘要。
- `openable` 按当前用户进程服务器上是否存在同 active generation 的 READY replica 计算；不能把其它服务器 READY 状态误投影为本机可打开。
- 物化选择请求和响应统一使用 `{ "path": "...", "type": "FILE|DIRECTORY" }`。物化、重试响应在登记后重新读取持久化 operation snapshot，不把内存受理对象当作最终进度。
- 操作查询每次重新校验启用应用、有效成员、当前代码库关联；TEAM 允许有效成员读取，PERSONAL 只允许 owner，或仍具签发角色事实且仍是有效成员的应用管理员读取。响应不含物理根、私钥或原始 Git 错误。

### 进度 WebSocket

- `AppSourceOperationTicketStore` 提供 60 秒、一次性、消费即删除的 JVM 本地 ticket；绑定 operation、用户、签发时管理员事实、签发 `backendProcessId`、traceId 和过期时间。不存在、过期、operation/JVM 不匹配或 Origin 缺失统一返回低敏 `FORBIDDEN`。
- `AppSourceOperationTicketService` 在签票前调用业务服务读取 operation 并鉴权，以当前 Java 身份返回绝对 WebSocket URL，保证多 Java 部署的 upgrade 回到签发 JVM。
- `AppSourceOperationWebSocketHandler` 在消费 ticket 前校验 Origin 白名单；成功连接首帧总为数据库权威 `snapshot`，之后按持久化变化发送 `step`，以 `completed` 表达 `SUCCEEDED/PARTIAL_FAILED`，以 `failed` 表达 `FAILED` 或安全读取错误。
- 每次轮询都重新调用业务服务，成员撤权会结束观察。客户端关闭只取消 Reactor 数据库观察，不取消、重试或修改后台物化 operation。
- 重连必须重新签票，新连接从最新持久化 snapshot 开始。跨服务器 worker 只写数据库步骤，签发 Java 从数据库安全汇聚全局和逐服务器时间线，不依赖 JVM 内存事件总线。
- payload 只包含逻辑 ID、固定 commit、状态、时间和 `safeSummary/safeError`；测试明确拒绝 `rootPath/repositoryPath/privateKey/stderr` 和物理 `/data/` 路径。

### 文件 route/ticket/RPC

- `ManagedConversationWorkspaceAccessAuthorizer` 在既有 replica READY、snapshot ACTIVE/未过期、应用启用、代码库关联、实时成员和 PERSONAL owner 校验前，新增 slot `activeGeneration == replica.generation` 约束；旧 generation 的 READY Workspace 也失败关闭。
- `WorkspaceFileRoutingService` 识别 AppSource Runtime Workspace。workspace 与当前用户进程服务器不一致时不执行普通历史 Workspace 的安全回绑，直接返回冲突；远端匹配服务器仍通过公共 `BackendJavaRouteResolver` 精确选择目标 Java。
- 应用源码 lifecycle/recent HTTP 入口沿用 `UserOpencodeBackendRoutingService` 和 `BackendHttpForwarder` 的用户绑定路由。文件 route/ticket HTTP 可跨 Java 转发，实际文件内容由浏览器直连目标 Java 的 WebSocket，不新增 Java→Java HTTP 文件代理。
- 既有文件 ticket 与每条 RPC 继续调用 workspace authorizer，因此 ticket 签发后 generation 到期、成员撤销、仓库解绑、应用停用或 replica 非 READY 都会在实际文件操作时被拒绝。

## 前端共享契约与 client

- `@test-agent/shared-types` 新增应用源码严格四态、purpose/path、operation/replica/step、安全仓库摘要、物化/重试/open、ticket 和 `snapshot/step/completed/failed` envelope。
- 响应新增字段按可空/可选策略兼容旧服务；下载四态和 `{path,type}` 请求字段保持精确。操作及副本已知枚举值与 Java 领域枚举一致，并继续允许服务端未来扩展值。
- `@test-agent/backend-api` 新增列表、分支、树、物化、重试、打开、最近选择、操作查询、ticket 和进度连接方法。
- `appId/repositoryId` 使用 URL 组件编码，`branch/path` 使用 `URLSearchParams` 查询编码；物化 body 保留精确 `{path,type}`。
- `connectAppSourceProgress` 每次调用都先申请新 ticket，直接使用后端返回的绝对 WebSocket URL，不二次编码、不自动重连。返回的 `close()` 只关闭观察连接，不调用任何 cancel API。

## TDD RED / GREEN 证据

1. **列表四态与当前服务器。** 新测试先要求业务列表返回严格四态、owner 身份和当前服务器 READY 可打开性；扩展 `RepositorySummary` 与业务映射后 GREEN。
2. **物化 wire 字段。** Controller 测试最初以 `selectedPaths[].type` 提交时，DTO 仍按旧命名读取，捕获到 path type 为 `null`；改为精确 `{path,type}` 后请求全部字段映射及响应序列化 GREEN。
3. **操作读取权限。** PERSONAL 非 owner、撤销成员的管理员访问先暴露授权缺口；补充 `getOperation` 的当前应用/仓库/成员与 owner/admin 规则后，TEAM 成员、PERSONAL owner/管理员正例及撤权反例全部 GREEN。
4. **旧 generation 文件访问。** READY replica 在 slot 已前进后最初仍可访问；加入 active generation 校验后 RED 用例转 GREEN，并补充已过期 snapshot 反例。
5. **AppSource Workspace 本机降级。** 新路由测试最初缺少能注入 `AppSourceRepository` 的构造器和识别逻辑；实现后，服务器不一致不回绑、远端精确目标不本机 fallback 均 GREEN。
6. **ticket 约束。** 一次消费、重复消费、过期、operation 不匹配、签发 JVM 不匹配和统一低敏失败由可注入 Clock/ID 的 store 测试驱动；签票前业务鉴权和绝对 JVM URL 由 service 测试覆盖。
7. **进度恢复和撤权。** handler 测试驱动首个持久化 snapshot、step→completed、`PARTIAL_FAILED -> completed`、新 ticket 重连、签票后成员撤权及敏感字段脱敏；不可信 Origin 在消费 ticket 前被拒绝。
8. **统一 HTTP 错误。** 业务 `FORBIDDEN` 经 WebFlux ControllerAdvice 返回 `success=false/code/message/traceId` 的统一 envelope。
9. **前端 client。** backend-api 测试先因方法缺失失败；实现后覆盖所有 URI/body 编码、新连接新 ticket、绝对 URL 不重复编码和 close 不发送 cancel，共 2/2 GREEN。
10. **Spring 装配。** `AppSourceApiContextTest` 与既有 `AppSourceContextTest` 分别以实际 API bean/config 和完整应用业务 bean 图验证，无 bean 循环或路由依赖缺失。

回归过程中出现的首个非生产失败是新增“过期 snapshot”测试 fixture 不满足领域对象“到期时间必须是受理时间后 1～72 个整小时”的构造约束，并连带留下 Mockito unfinished stubbing。fixture 改为受理后整 1 小时、且当前时刻已过期后，workspace 全组通过。另一次 `AppSourceContextTest` 启动失败来自命令中的 javaagent 路径重复了一层目录；使用下述正确路径后 1/1 通过，不是 Spring 装配失败。

Mockito/JDK 相关测试统一使用：

```text
-DargLine=-javaagent:/Users/huang/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar
```

## 最终验证

- API 定向：`AppSourceControllerTest, AppSourceOperationControllerTest, AppSourceOperationTicketServiceTest, AppSourceOperationTicketStoreTest, AppSourceOperationWebSocketHandlerTest, AppSourceWebSocketConfigTest, AppSourceApiContextTest, UserOpencodeBackendRoutingWebFilterTest`，通过。
- workspace 定向：`AppSourceApplicationServiceTest, AppSourceWorkspaceAccessTest`，通过。
- runtime 定向：`WorkspaceFileRoutingServiceTest`，通过。
- Spring：`AppSourceApiContextTest` 1/1、`AppSourceContextTest` 1/1，0 failures/errors。
- 前端：`vitest run packages/backend-api/tests/app-source.test.ts`，1 file / 2 tests 通过。
- 前端：`@test-agent/shared-types typecheck` 与 `@test-agent/backend-api typecheck` 均通过，无 TypeScript 诊断。
- API 完整依赖链：沙箱内首次运行到 `GeneratedOpencodeSdkGatewayTest` 时因测试临时 HTTP server 绑定端口被系统返回 `Operation not permitted`；按验证规范在沙箱外重跑 `mvn -q -DappLogDir=target/log -DargLine=... -pl test-agent-api -am test`，exit 0。
- `git diff --check`：通过。

## 文档同步

- 工程/模块：`backend/README.md`、`test-agent-api/README.md`、`test-agent-opencode-runtime/README.md`、`test-agent-workspace-management/README.md`。
- 前端：`frontend/README.md`、`packages/backend-api/src/PACKAGE.md`、`packages/shared-types/src/PACKAGE.md`。
- 稳定架构/契约：`docs/architecture/module-map.md`、`docs/api/http-api.md`、`docs/api/event-stream.md`、`docs/standards/security.md`。
- `event-stream.md` 明确应用源码使用独立 WebSocket，不新增 RunEvent/SSE wire name。

## 影响与兼容性

- **API：** 新增应用源码生命周期、operation/ticket HTTP 入口和独立进度 WebSocket；统一 `ApiResponse`/错误 envelope。新增响应字段可空/可追加，旧客户端可忽略。
- **事件：** 不新增 RunEvent、SSE 或内部广播类型。进度 envelope 是独立只读 WebSocket 协议。
- **数据库：** 无结构、migration 或 SQL 变化；只读取 Task 1/2 已有 operation/step/replica/snapshot 数据。
- **性能：** HTTP/Git/数据库阻塞工作使用既有 boundedElastic 边界；单个非终态进度连接默认每 500ms 串行读取一次持久化快照，不并发堆积。调用方负责只保留需要的连接并在离开页面时关闭。
- **安全：** ticket 短期、一次性、用户/operation/JVM/Origin 绑定；成员、仓库和 snapshot 关系实时复核；物理路径、凭据、原始 stderr 和堆栈不进入 DTO/WS。AppSource 文件路由禁止本机降级。
- **兼容性：** 未修改现有 URL、RunEvent、generated SDK 或数据库；业务列表保留旧三参数重载，新增当前服务器重载供 API 使用。

## 未完成事项与风险

- Task 3 只提供共享类型和 backend-api client；具体页面/交互接入属于后续任务。
- ticket 保存在签发 JVM 内存中，因此滚动重启会使未消费 ticket 失效；这是短期一次性凭据的安全失败模式，客户端重新申请即可。
- 高频同时观察大量 operation 会线性增加数据库轮询；当前串行 500ms 且连接由调用方显式关闭，后续若有大规模并发需求应基于持久化版本游标做批量查询，不能改用不可靠的本机内存事件作为事实源。
- 按 Task 3 协作约束未更新 `.agents/session-log.*.md`；最终汇总任务统一处理会话日志。本次提交前仍会回顾全部近期 session log。

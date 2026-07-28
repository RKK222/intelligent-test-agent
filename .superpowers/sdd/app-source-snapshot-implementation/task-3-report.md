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

## 独立复审修复（Round 1）

本轮基于 Task 3 初始提交 `936eef86c` 逐项修复独立复审提出的 4 个 Important 和 3 个 Minor 问题。没有修改 OpenCode 快照、generated SDK、数据库结构、MyBatis SQL、Flyway 或 `.env.local`；本节所在提交即本轮修复提交，最终 SHA 由 `git log -1` 确认。

### 四项 Important 证据

1. **ticket 未精确绑定签票 Origin。** 根因是旧 ticket 只判断 upgrade 请求存在 Origin，攻击者从另一白名单 Origin 获取泄露票后仍可消费。现在 HTTP ticket 入口强制读取并 canonicalize `Origin`，ticket 持久绑定 canonical Origin；upgrade 必须同时命中服务白名单和票内精确 Origin。canonical 规则只接受无 userinfo/path/query/fragment 的 `http(s)://host[:port]`，统一 scheme/host 大小写和默认端口。错误或缺失 Origin 返回低敏 `FORBIDDEN`，且错误 Origin 不烧毁正确来源后续要消费的票。RED：`ticketCannotBeConsumedFromAnotherAllowedOrigin` 期望拒绝但旧实现未抛异常；GREEN：`AppSourceWebSocketOriginTest`、ticket store/service/controller/handler Origin 用例全部通过。
2. **operationId 的业务、REST、ticket 与 WebSocket 规则不一致。** 根因是业务登记只要求非空，而 ticket 单独硬编码 `aso_` 正则，导致业务已接受的 `job_123` 永远无法签票。新增领域值对象 `AppSourceOperationId`，统一 trim、1–128 长度、拒绝控制字符和路径分隔符，但不要求 `aso_` 前缀；领域 operation、物化/重试命令、操作查询、ticket 和 WebSocket path 全部复用。ticket URL 对 ID path segment 显式编码。RED：`materializationAcceptedOperationIdCanAlwaysBeUsedToCreateATicket` 在旧实现得到 `VALIDATION_ERROR`；GREEN：领域 ID 2 项、ticket service 3 项和前端非 `aso_` ID 编码用例通过。
3. **AppSource 文件 route/ticket/RPC 没有把 replica server 与 generation 当作完整权威。** 根因是 route 仅在 Workspace 与用户进程服务器冲突时禁止回绑；若二者错误地一致、但 replica 指向另一服务器，仍会路由错误。现在 route 先反查 replica，并要求 replica `READY`、slot `activeGeneration == replica.generation`、Workspace 行服务器、用户 agent 服务器与 replica 服务器四者一致；后端仍只通过公共 `BackendJavaRouteResolver` 解析，跨 Java 仍由现有 `BackendHttpForwarder` 转发。authorizer 同时交叉校验 Workspace 行服务器，因此 ticket 签发和每条 RPC 都失败关闭。RED：`rejectsReplicaWhenWorkspaceAndAgentAgreeButReplicaServerDiffers` 旧实现未抛冲突；GREEN：route 9/9、显式 ticket/RPC 错服反例、以及公共 forwarder 用例全部通过。
4. **JVM 本地 ticket cache 无容量上限，消费也非并发原子。** 根因是无界 `ConcurrentHashMap` 加 `remove`/后置校验，未清理的票可持续增长，且没有明确验证并发唯一消费。现在默认硬上限 10,000，签发前清理过期票；达到上限显式返回 `RATE_LIMITED`，不静默驱逐有效票；`compute` 原子完成匹配与一次消费。RED：过期票未释放容量、10001 张有效票仍可签发、跨 Origin 仍可消费共 3 项失败；GREEN：store 8/8，包括 16 个并发消费者仅 1 个成功、过期回收和容量拒绝。

### 三项 Minor 证据

1. **进度事件类型和运行时校验过宽。** `AppSourceProgressEvent` 改为严格判别联合：`snapshot/step/completed` 必须携带一致的 `operationId/operation/traceId`，`failed` 必须携带 `status=FAILED` 与非空安全错误字段；服务端持久化 `FAILED` 终帧同步补齐这些字段。backend-api 运行时校验 operation、selected paths、steps 和 server summaries，不再只看 `type`。RED：`{"type":"snapshot"}` 与缺错误字段的 failed 帧被旧实现接受；GREEN：两者都归一为 `WEBSOCKET_MESSAGE_INVALID`，服务端 FAILED 终帧测试通过。
2. **调用方回调异常被误判为协议错误并二次调用。** 根因是 JSON 解析、协议校验和 `onEvent` 位于同一 `try/catch`。现在只捕获解析/校验异常，`onEvent` 在 catch 外调用；调用方异常原样抛出且只调用一次。RED：回调抛错后旧实现调用 2 次；GREEN：`does not reinterpret a caller callback exception as an invalid websocket message` 验证只调用 1 次并抛回原异常。
3. **稳定 API 文档示例与真实 wire 值不一致。** `docs/api/http-api.md` 将短提交示例改为完整 40 字符固定 commit，将不存在的 `MATERIALIZE` operation type 改为真实 `DOWNLOAD`，并记录统一 operationId 规则；`docs/api/event-stream.md` 补充 Origin 精确绑定、有界缓存和严格事件 envelope。模块 README 同步记录同一约束。

### Round 1 RED / GREEN 与分层验证

- RED 精确复现：Origin 越权 1 failure；业务已接受 ID 无法签票 1 failure；replica 错服 route 1 failure；ticket store 7 项中 3 failures；前端 4 项中不完整 payload、回调二次调用共 2 failures。
- domain：全量测试通过；新增 `AppSourceOperationIdTest` 2/2。
- persistence：沙箱外运行 `MyBatisAppSourcePostgresqlIntegrationTest,MyBatisAppSourceRepositoryIntegrationTest`，13/13，通过且 0 skipped。
- workspace-management：全量 337/337，通过；包含 app-source generation、Workspace/replica server 交叉授权。
- API：刷新当前 reactor 上游构件后单模块全量 417/417，通过；目标 13 类覆盖 Origin、ticket、FAILED envelope、context、文件 ticket/RPC 与公共 forwarder，全部通过。
- runtime：`WorkspaceFileRoutingServiceTest` 9/9；`RunRuntimeLossConvergenceSchedulerTest` 隔离 5/5，均通过。
- context：`AppSourceApiContextTest` 1/1、`AppSourceContextTest` 1/1，通过。
- frontend：backend-api `app-source.test.ts` 1 file / 4 tests；`@test-agent/backend-api` 与 `@test-agent/shared-types` typecheck，均通过。
- 完整 `test-agent-api -am` 在两次全链路高负载运行中均只出现既有 `RunRuntimeLossConvergenceSchedulerTest.keepsInMemoryRetryWhenNeitherDatabaseNorRedisAcceptedTerminal` 的 1 秒 Awaitility 时序波动（期望 converge 2、实际 1）；该类隔离 5/5 稳定通过。本轮未修改该调度器，按非任务范围风险保留，不以目标回归掩盖。
- `git diff --check` 通过；提交前按规范回顾全部 `.agents/session-log*.md`，并仅精确暂存本轮文件。

### Round 1 影响与剩余风险

- **API/事件：** 未新增 URL 或 RunEvent；ticket 请求现在强制要求浏览器 Origin，进度 envelope 收紧为可运行时验证的稳定契约。缺 Origin 的非浏览器调用会由原先签票成功变为安全拒绝，这是有意的安全收紧。
- **数据库：** 无结构、migration 或 SQL 变化；MyBatis PostgreSQL 集成回归通过。
- **性能：** ticket 缓存从无界改为最多 10,000 张有效票，过期票在签发/消费时惰性回收；达到容量时返回 429，不驱逐仍有效连接凭据。
- **安全：** 修复跨白名单 Origin 票据重放，文件 route/ticket/RPC 对 replica server/generation 错配全部失败关闭。
- **兼容性：** operationId 不再被 ticket 层额外限制为 `aso_`；既有合法 ID 继续可用，包含路径分隔符或控制字符的危险 ID 统一拒绝。严格前端 validator 会把旧式不完整进度帧转换为安全 `failed` 事件。
- **剩余风险：** 完整 reactor 的既有调度时序测试在高负载下仍可能波动；隔离回归通过，需由 runtime 后续任务单独放宽等待条件或改为确定性同步。本轮按任务边界未修改 `.agents/session-log.*.md`，由最终汇总任务统一更新。

## 独立复审修复（Round 2）

本轮基于 Round 1 提交 `0d126ccfb` 修复复审追加的 2 个 Important 和 1 个 Minor 问题。没有修改 OpenCode 快照、generated SDK、数据库结构、SQL、Flyway 或 `.env.local`；本节所在提交即 Round 2 修复提交。

### 两项 Important 证据

1. **operationId 仍允许精确点路径段。** `AppSourceOperationId` 现在统一拒绝 trim 后精确的 `.` 和 `..`，但保留普通内部双点 `release..1`；物化、重试、REST 查询、ticket、WebSocket 路径和前端 client 共享等价边界。前端在任何 fetch 或 socket 创建前先完成校验，后端 ticket 测试同时证明业务可接受的 `release..1` 能签票。RED：领域 `AppSourceOperationIdTest` 2 项中 1 failure；临时 mutation 验证物化/重试测试 20 项中 1 failure；前端精确点路径用例旧实现仍发起请求。GREEN：domain/workspace 全量、API ticket 和 backend-api 连接用例均通过。
2. **文件 WebSocket 只在建连/签票时固定用户服务器归属。** 在既有 `WorkspaceFileSocketTicketService` 增加每 RPC 动态授权，复用 `UserOpencodeProcessAssignmentService.fileRoutingAffinity`、`WorkspaceApplicationService.currentLinuxServerId()/requireWorkspaceOnCurrentServer` 和 `ConversationWorkspaceAccessAuthorizer`。每条 `workspace.*` RPC 都要求实时 affinity、ticket 目标/agent 服务器、Workspace/托管副本服务器和当前 JVM 完全一致；不扫描 Redis、不新增路由器、不做本机降级。RED：同一 socket 首次在 server-b 成功后把 assignment 改到 server-c，旧实现第二次仍返回 result；当前 JVM 与 ticket server 不一致时旧实现仍调用文件服务，`WorkspaceFileWebSocketHandlerTest` 25 项共 2 failures。GREEN：两项都返回 `FORBIDDEN`，前者文件服务仅调用 1 次、后者 0 次；ticket/handler/context 定向 34/34 通过。

### 一项 Minor 证据

1. **前端进度事件只校验结构、未校验事件语义和可选字段类型。** `AppSourceProgressEvent` 拆成严格状态判别联合：`snapshot` 可携带任一合法状态，`step` 只允许 `PENDING/RUNNING`，`completed` 只允许 `SUCCEEDED/PARTIAL_FAILED`，持久化 `failed` 的内外状态都必须是 `FAILED`。运行时 validator 还要求所有携带 operation 的 envelope 外内 `operationId/traceId` 一致，optional operation/step/server 字段存在时类型正确，`errorCode/errorMessage` trim 后非空；不含 operation 的连接失败仍允许合法可选 ID/trace。RED：旧 validator 接受状态错配、failed 身份错配、optional 数值/字符串错型和空白错误文本等 10 个 malformed frame，新增 7 项 Vitest 中 2 项失败。GREEN：所有 malformed frame 都归一为单次 `WEBSOCKET_MESSAGE_INVALID`，snapshot FAILED、step PENDING、completed PARTIAL_FAILED 和后端实际持久化 failed 帧继续通过，前端 7/7 与两个类型检查通过。

### Round 2 分层验证

- domain：全量 93/93，通过；精确 `.`/`..` 拒绝且 `release..1` 接受。
- workspace-management：全量 338/338，通过；物化与重试命令复用同一 operationId 规则。
- API：`WorkspaceFileSocketTicketServiceTest` 8/8、`WorkspaceFileWebSocketHandlerTest` 25/25、`AppSourceApiContextTest` 1/1、`AppSourceOperationTicketServiceTest` 3/3、`AppSourceOperationWebSocketHandlerTest` 6/6，共 43/43，通过；后端真实持久化 `FAILED` 终帧保持完整 operation/trace 与安全错误字段。
- runtime：`WorkspaceFileRoutingServiceTest` 9/9，通过。
- frontend：`app-source.test.ts` 1 file / 7 tests；`@test-agent/backend-api` 与 `@test-agent/shared-types` typecheck，均通过。
- 完整 `test-agent-api -am` 未重复执行；Round 1 已记录与本轮无关的 `RunRuntimeLossConvergenceSchedulerTest` 1 秒 Awaitility 既有波动，本轮用 file ticket/RPC/context 和 runtime route 定向回归验证改动边界。
- `git diff --check` 在提交前执行；提交前按规范回顾全部 `.agents/session-log*.md` 并精确暂存 Round 2 文件。

### Round 2 影响与剩余风险

- **API/事件：** 未新增 URL 或 RunEvent；operationId 新增精确点段拒绝，独立进度 WebSocket 的前端静态类型和运行时语义同步收紧。
- **数据库/性能：** 无数据库、migration、SQL 或新轮询变化；文件 WebSocket 每条 workspace RPC 增加一次现有轻量 affinity 读取和既有 workspace/成员/副本复核，以换取连接后迁移立即失效。
- **安全：** 旧 socket 不再保留签票时服务器归属；binding 迁移、错误 JVM、Workspace/副本错服均在调用文件服务前失败关闭。
- **兼容性：** 仅 trim 后精确 `.`/`..` 从历史可接受变为拒绝；普通合法 ID（包括 `release..1`）继续可查询、签票和连接。旧式语义错配或 optional 字段错型的进度帧会被客户端转换为安全失败。
- **剩余风险：** 无本轮新增未完成事项。按 Task 3 协作约束不修改 `.agents/session-log.*.md`，由最终汇总任务统一更新。

## 独立复审修复（Round 3）

本轮基于 Round 2 提交 `a690cba90` 修复 1 个 Important，并在同一文件 WebSocket 安全边界内修复 1 个复审追加的失败关闭问题。没有修改 OpenCode 快照、generated SDK、数据库结构、SQL、Flyway 或 `.env.local`；本节所在提交即 Round 3 修复提交。

### operationId ECMAScript 空白语义

后端原先使用 Java `String.trim()`，只移除 `U+0000–U+0020`，而前端 ECMAScript `trim()` 还移除 NBSP、Unicode Zs、行分隔符和 BOM。结果是后端可把 NBSP 包裹的 `..` 当作普通 ID 持久化，前端却先规范化成危险点段并拒绝查询、签票或连接。现在领域值对象显式固定 ECMAScript WhiteSpace + LineTerminator 的 BMP 集合：`U+0009–U+000D`、`U+0020`、`U+00A0`、`U+1680`、`U+2000–U+200A`、`U+2028`、`U+2029`、`U+202F`、`U+205F`、`U+3000`、`U+FEFF`；不依赖 locale、Java 版本或 `trim/strip` 的不同定义。前端继续使用规范规定的原生 ECMAScript `trim()`。

RED：`AppSourceOperationIdTest` 2 项均失败——NBSP 包裹的 `release..1` 没有规范化，NBSP 包裹的 `..` 没有拒绝。GREEN：领域测试覆盖上述完整集合、ASCII 空白、精确 `.`/`..` 和合法内部双点；物化/重试命令证明 NBSP 输入统一持久化为 `release..1`，API ticket 测试进一步证明同一规范值可用于后续 GET、签票和 consume/connect，前端测试覆盖 GET、ticket 与 WebSocket connect 的 NBSP/ASCII 边界。

实现时还捕获到 Java 会在词法分析前展开 Unicode 转义，直接写 `U+000A` 字符字面量会使源码断行并编译失败；最终使用带中文说明的显式数值 switch 常量，集合仍保持可审计且不受运行环境影响。

### APP_SOURCE 文件票防降级绑定

根因是文件 ticket 只保存 `superAdmin`，每条 RPC 调用 `requireFileAccess(..., allowUnmanaged=ticket.superAdmin)`。若签票时存在的 AppSource replica 映射在同一 socket 生命周期中消失，超级管理员的下一条 RPC 会被误判成真实非托管服务器工作区并继续进入文件服务。

现在 `ConversationWorkspaceAccessAuthorizer` 在同一次权威读取和授权判断中返回 `STANDARD/APP_SOURCE` 分类，避免分类与授权分两次读取产生 TOCTOU；JVM 本地 ticket 固定签票时的 `appSourceWorkspace` 事实。已绑定 APP_SOURCE 的票在每条 RPC 都关闭 unmanaged 兼容入口，并要求当前授权再次返回 APP_SOURCE；映射消失或变成其它工作区类型时统一在文件服务前 `FORBIDDEN`。真实未映射的服务器工作区仍可在签票和后续 RPC 中由 `SUPER_ADMIN` 按既有兼容路径访问。

RED：真实 authorizer 驱动的 handler 用例中，同一超级管理员 socket 首条 RPC 成功，删除 replica 映射后第二条仍返回 result，文件服务被调用两次。GREEN：第二条改为 `FORBIDDEN`，文件服务只调用一次；独立正例证明真正的非托管超级管理员 ticket 仍返回 result。接口新增默认分类方法并保留旧构造/签发重载，减少既有实现和包内调用的兼容影响。

### Round 3 分层验证

- domain/workspace 定向：`AppSourceOperationIdTest` 2/2、`AppSourceApplicationServiceTest` 20/20、`AppSourceWorkspaceAccessTest` 6/6，共 28/28，通过。
- API 定向：`WorkspaceFileWebSocketHandlerTest` 27/27、`WorkspaceFileSocketTicketServiceTest` 8/8、`AppSourceOperationTicketServiceTest` 3/3、`AppSourceApiContextTest` 1/1，共 39/39，通过。
- frontend：`app-source.test.ts` 1 file / 7 tests；`@test-agent/backend-api` 与 `@test-agent/shared-types` typecheck，均通过。
- 完整 `test-agent-api -am` 未重复执行；Round 1 已记录与本轮无关的 `RunRuntimeLossConvergenceSchedulerTest` 1 秒 Awaitility 既有波动，本轮按受影响领域、工作区、API 和前端边界执行定向回归。
- `git diff --check` 在提交前执行；提交前按规范回顾全部 `.agents/session-log*.md` 并精确暂存 Round 3 文件。

### Round 3 影响与剩余风险

- **API/事件：** 未新增或修改 URL、DTO、RunEvent 或 WebSocket envelope；只统一既有 operationId 规范化，并加强 JVM 本地 workspace ticket 的内部授权事实。
- **数据库：** 无结构、migration、SQL 或持久化模型变化。历史上若已存在 NBSP 包裹的 operationId，前端原本即无法按原值访问；本轮不新增自动数据迁移，后续新受理和重试统一写入规范值。
- **性能：** operationId 只做两端线性边界扫描；文件授权分类来自原有同一次 Repository 判断，不新增第二次分类查询。每条 workspace RPC 仍执行 Round 2 已建立的实时授权和 affinity 复核。
- **安全：** 修复超级管理员 APP_SOURCE 票在 replica 映射消失后降级为非托管工作区的授权绕过，且保留真正非托管服务器工作区的明确兼容入口。
- **兼容性：** ASCII 空白和普通合法 ID 行为不变；ECMAScript 额外空白现在与浏览器统一规范化，精确 `.`/`..` 仍拒绝，`release..1` 继续合法。新增接口方法、ticket 构造器和 store 签发参数均保留最小兼容重载。
- **剩余风险：** 无本轮新增未完成事项。按 Task 3 协作约束不修改 `.agents/session-log.*.md`，由最终汇总任务统一更新。

## Task 4 集成前契约修复（Round 4）

本轮修复 tree 查询只返回节点数组、导致新下载或新分支无法取得物化必填 `expectedTreeCommit` 的阻塞契约。没有修改 OpenCode 快照、generated SDK、数据库结构、SQL、Flyway、`.env.local`、物化分支移动冲突逻辑、RunEvent 或任何事件协议；本节所在提交即 Round 4 修复提交。

### 固定提交树快照与兼容入口

- `AppSourceApplicationService.getTreeSnapshot` 在一次业务调用内只执行一次远端分支 commit 解析，并以该同一 commit 列树，返回低敏 `TreeSnapshot(targetCommit,nodes)`；指定目录为空时 `nodes` 为空，但完整 40 字符 commit 仍保留。已有 `listTree` 入口继续存在并委托 snapshot 的 `nodes`。
- 既有 `GET .../tree?branch=&path=` URL 不变；不传 `includeCommit` 或传 `false` 时 wire `data` 仍是原节点数组，显式 `includeCommit=true` 时才返回 `{targetCommit,nodes}`。Controller 只在两种模式间委托 workspace-management 服务并映射 DTO，不接触 Git、Repository 或文件系统。
- `@test-agent/shared-types` 新增 `AppSourceTreeSnapshot`；`@test-agent/backend-api` 保留 `listAppSourceTree()` 的数组语义，并新增 `getAppSourceTreeSnapshot()` 通过同一 URL 发送 `includeCommit=true`。调用方可把响应 `targetCommit` 原样作为物化 `expectedTreeCommit`。
- `targetCommit/nodes` 不包含物理路径、凭据或 Git 错误；没有新增 endpoint、数据库字段、事件、WebSocket envelope 或后端轮询。

### Round 4 RED / GREEN 与验证

- RED：workspace/API testCompile 因缺少 `TreeSnapshot/getTreeSnapshot` 失败；frontend 7 项中 1 项以 `TypeError: client.getAppSourceTreeSnapshot is not a function` 失败，其余 6 项通过。
- GREEN：服务 snapshot 测试证明分支只解析一次且节点来自同一固定 commit，空目录仍返回完整 commit；已有 `listTree` 通过委托该 snapshot 保留节点语义。Controller 测试同时证明默认数组与 `includeCommit=true` envelope；client 测试证明旧 URL/数组和新增查询参数/envelope 两种契约并存，并使用显式 40 字符 commit fixture。
- workspace-management 全量：340/340，通过，0 failure/error/skip。
- API 定向：`AppSourceControllerTest` 与 `AppSourceApiContextTest` 共 7/7，通过。
- frontend：`app-source.test.ts` 1 file / 7 tests；`@test-agent/backend-api` 与 `@test-agent/shared-types` typecheck 均通过，无诊断。
- 独立只读复审未发现 Critical、Important 或 Minor 问题，结论 Ready；`git diff --check` 通过。

### Round 4 文档、影响与剩余风险

- **文档：** 同步 workspace-management/API 模块 README、frontend README、backend-api/shared-types `PACKAGE.md`、HTTP API 和模块图；`docs/api/event-stream.md` 不变，因为本轮没有事件或 WebSocket 协议变化。
- **API/兼容性：** 仅在原 URL 上增加可选查询参数和 opt-in envelope；旧 wire、旧 `listTree`、旧 `listAppSourceTree()` 均保持。物化继续独立解析当前分支并与 `expectedTreeCommit` 比较，分支移动仍返回 `CONFLICT`。
- **数据库/性能/安全：** 无数据库、migration 或 SQL 变化；snapshot 模式与旧树查询一样各执行一次 commit 解析和一次列树，没有新增远端 Git 往返；响应只增加固定 commit 和已有逻辑节点，不暴露敏感数据。
- **剩余风险：** 本轮契约已具备后续页面提交固定 `expectedTreeCommit` 的条件，页面接入由后续任务使用新增 client 方法完成。按协作约束未修改 `.agents/session-log.*.md`；提交前已回顾全部 session log 的近期条目并确认无覆盖、冲突或遗留合并标记。

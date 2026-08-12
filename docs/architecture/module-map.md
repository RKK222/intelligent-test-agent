# 模块与包速查

本文件是“按功能找模块/包”的速查表，合并原前端架构、前后端契约和总体方案的模块职责。依赖边界与禁止关系见 `docs/architecture/dependency-rules.md`；HTTP/SSE 契约见 `docs/api/`。

## 总体架构

```text
Browser
  -> frontend/apps/agent-web
      -> packages/backend-api
      -> packages/event-stream-client
      -> /xxl-job-admin/ iframe (same origin)
  -> frontend-opencode
      -> packages/backend-api (source alias)
      -> packages/event-stream-client (source alias)
  -> test-agent-app
      -> test-agent-api
          -> notification / workspace-management / opencode-runtime / system-management / configuration-management / scheduler / integration
              -> agent-runtime
      -> persistence / event / observability
      -> test-agent-scheduler
      -> test-agent-agent-runtime
          -> test-agent-opencode-client
              -> test-agent-opencode-sdk-generated
                  -> opencode server pool
      -> local client reverse-tunnel gateway
          -> test-agent-local-client-protocol
          -> user-owned test-agent-local-client -> loopback OpenCode / local filesystem
      -> test-agent-xxl-job-integration
          -> test-agent-xxl-job-admin-upstream
          -> isolated MySQL / XXL executors
```

关键边界：

- `test-agent-api` 统一承载 API、鉴权、限流、traceId、任务入口、事件出口和错误处理。
- `test-agent-app` 只承载启动、装配、profile、migration、health 和日志等运行入口，不承载业务逻辑。
- `test-agent-agent-runtime` 是多 agent 选择、统一日志/指标包装和具体 agent 适配器边界。
- `test-agent-opencode-client` 是业务代码访问 opencode server 的唯一门面。
- 本地目标仍通过 `test-agent-opencode-client` 门面，但传输由 `OpencodeWebClientTransport` 切换为反向隧道；浏览器和业务层不直连客户端。
- `test-agent-opencode-sdk-generated` 只保存生成代码，不承载业务逻辑。
- XXL executor 注册不使用稳定 Linux 服务器亲和；夜间扫描取得任务后，由业务层按任务提交时固化的目标服务器通过公共 Java 路由分发。
- Workspace 级 OpenCode 运行态目录由 `test-agent-opencode-runtime` 在目标解析前复用 `ConversationWorkspaceAccessAuthorizer` 校验应用成员与个人工作区 owner，不能只凭 workspaceId 读取其它应用的 `.opencode` 能力。

## 超级管理员问题排查只读访问边界

- `test-agent-domain` 只定义短期 grant、当前登录会话摘要、审计事件和目标用户会话/工作区查询端口；不包含 HTTP、SQL、Redis key 或明文令牌。
- `test-agent-system-management` 实时复核 actor 的 `SUPER_ADMIN` 角色，编排 5–240 分钟授权、单登录会话令牌轮换、目标选择和失败关闭；target 只限定查询范围，不替换 actor 身份。
- `test-agent-workspace-management` 通过 `UserWorkspaceQueryService` 返回目标用户个人工作区及其会话引用工作区的逻辑身份；`test-agent-opencode-runtime` 使用 `BackendJavaRouteResolver` 按权威 Workspace 服务器选择目标 Java，不使用 actor affinity、本机降级或 Java→Java 文件代理。
- `test-agent-persistence` 以 MyBatis XML/Flyway 保存授权与一年期审计，以 Redis Lua 保存当前登录会话的短期令牌摘要；数据库、Redis、日志均不保存明文 grant token、平台 Token、消息正文或文件正文。
- `test-agent-api` 暴露独立 `/system-management/support-access/**` HTTP 与文件 route/ticket/RPC，普通 Session/Workspace/文件入口仍按 actor 自身所有权校验。排查文件 RPC 只允许目录、搜索和预览读取，并过滤 `.opencode`。
- `packages/shared-types` 定义 grant/带来源排查单号建议/target/audit DTO，`packages/backend-api` 管理排查单号请求、独立 `X-Support-Access-Grant` 内存令牌和 support socket，`packages/file-explorer` 用 `canWrite/canAttach/canDownload` 分离能力，`apps/agent-web` 仅为实时 `SUPER_ADMIN` 在工作台全局三击 Shift 后显示受控入口。当前没有权威工单数据源时由 `test-agent-system-management` 每轮生成新的 `sai_` 排查单号，禁止从历史授权循环回填；该链路不新增 RunEvent/SSE。

## 后端模块职责

| 模块 | 职责 |
|---|---|
| `test-agent-common` | 公共异常、统一响应 `ApiResponse`/`ApiErrorResponse`、TraceId、分页、校验、时间工具。 |
| `test-agent-domain` | Workspace、Session、含 `QUESTION/PERMISSION` 与独立计数的 SessionRuntimeState、AgentSessionBinding、Run、RunStorageMode、RunRuntimeStore/manifest/snapshot/replay/runtime tail、RunEvent、ExecutionNode、RoutingDecision、应用源码 snapshot/replica/operation/cleanup、内部模型调用精确结果与五类看板结果、通用服务器广播 envelope/端口、opencode 用户进程管理拓扑、通用参数、显式 JVM 内存参数 SPI/状态和工作空间创建进度等纯领域模型与状态机，不依赖 Spring Web/Persistence/generated SDK。 |
| `test-agent-observability` | traceId、结构化日志、Micrometer 指标、观测性工具。 |
| `test-agent-opencode-sdk-generated` | 从 opencode OpenAPI spec 生成的 Java SDK，禁止手改。 |
| `test-agent-opencode-client` | 封装 generated SDK，提供 `OpencodeClientFacade`，是业务访问 opencode 的唯一门面。 |
| `test-agent-local-client-protocol` | 后台与本地客户端共享的 `local-opencode-client.v1` JSON 帧、256 KiB 分片、版本与大小校验，不依赖服务端领域。 |
| `test-agent-workspace-filesystem` | 服务端和本地客户端复用的真实根锚定、相对路径、符号链接防逃逸、预览/搜索、原子移动及分片上传下载内核。 |
| `test-agent-local-client` | Java 21 用户级客户端，负责 WSS 反向连接、稳定实例身份、loopback OpenCode 监管、文件 RPC 和模型中继。 |
| `test-agent-notification` | 通用用户站内通知模型编排、分享通知生命周期、配置 dispose 按 rollout/用户单行状态演进、受控动作、未读统计、事务提交后本机与跨 Java 变化、30 秒数据库校准和 90 天历史清理；不承载 Controller、SQL 或页面。 |
| `test-agent-agent-runtime` | 定义 `AgentRuntime`、`AgentRuntimeRegistry`、统一日志/指标包装、`OpencodeAgentRuntime` 适配器和未注册的 `OtherAgentRuntime` 抽象占位。 |
| `test-agent-workspace-management` | Workspace、服务器归属、文件查看/新增/修改/上传/复制/移动/删除、基于工作区 JSONC 与本机 READY 引用副本的只读组合文件视图、超级管理员服务器目录选择、git/diff、对话 Tool 到当前个人 workspace 的安全映射与 Git 编排、版本选择前按当前用户身份做 Git 只读访问预检、设置页初始版本工作区创建、应用版本工作区、每服务器版本副本、个人工作区、个人拉取成功后的单用户运行态重载登记、按角色和有效成员关系预览工作空间/版本/实际分支并按单分支或全应用刷新物理 feature 组、feature 固定提交向相关个人 worktree 的原生 Git merge（非重叠本地改动保留、覆盖风险待同步、真实冲突三方处理）、Agent & Skill Hub 远端精确提交快照/显式发布/固定依赖/两阶段引用与取消/更新三方合并、应用 Agent/Skill 发布 rollout、应用引用资产库的 generation/租约/本机有界即时调度/定向退避/补偿副本、应用源码固定提交的多服务器物化/启动与周期数据库补偿/打开/最近选择/索引保护/XXL 清理、受控分支切换、只读实际指针核验与安全目录树、agent 和 skill 管理业务。 |
| `test-agent-opencode-runtime` | Session、Run、RunEvent 编排、夜间任务提交/查询/会话锁/XXL 固定目标批量分发/attempt 租约、Run 锚点补偿与显式内存容量条目、订阅级 root/child scope、Redis active 索引、RunEvent SSE 按 Redis manifest 优先解析生产 Java、每用户有效公共配置软链接与公共个人保存热加载、公共全机/应用定向 Agent 配置发布排空及通知状态推进、个人拉取应用 Agent 的当前用户持久化排空、含 question/permission attention 与事件触发的用户级会话运行态摘要/状态流、stale active Run 收敛与十五天未使用进程关闭业务任务、当前用户 opencode 进程强状态/弱健康/初始化/确认式重启契约和可选引用目录启动环境、Run 和 runtime 代理防绕过校验、用户进程/固定节点目标解析、带实时应用成员校验且禁止应用源码副本本机降级的 workspace 文件 WebSocket 后端路由、manager WebSocket 网关与后端实例生命周期、按 `backendProcessId` 精确选择在线 Java、超级管理员运行管理 Redis 快照聚合和 48 小时指标历史查询、归档内部 Session + 临时 fork + 按预算 compact + build agent 系统提示只读约束的宠物旁路 RunEvent 流式问答及 10 分钟孤儿清理、通过 `AgentRuntimeRegistry` 调用 agent、Diff/revert、terminal ticket/PTY 业务。 |
| `test-agent-system-management` | 用户、角色、权限等平台内部管理业务，包括注册、统一认证首次建号默认授予普通用户、登录认证和 Token 管理，以及用户管理组合查询、创建测试用户、手工用户名修正、单人角色调整和按显式用户/筛选快照的批量角色调整；新增外部 API Key 生成、RSA 密文生命周期、不可变 JVM 注册表、常量时间认证和跨 Java 刷新。 |
| `test-agent-configuration-management` | 应用定义只读消费、应用成员、代码库英文名与应用关联、已初始化引用资产库及已有应用源码历史的英文名/类型冻结、应用工作空间、个人 SSH key 和 Git 远端只读目录查询配置业务；通用参数数据库直读视图（`RepositoryCommonParameterValues`）、变量引用解析器、参数更新跨实例广播，以及只管理显式 SPI 条目的本机内存参数注册表/诊断响应。 |
| `test-agent-scheduler` | 保留 `ScheduledTaskHandler`/context/result、Redis 全局锁与旧运行记录清理；不再启动 runner 或创建/执行 `USER_PLAN`，全部周期任务由 XXL adapter 调用业务 handler。 |
| `test-agent-integration` | 非 opencode 外部系统联动业务边界；当前承载 IT-Tools/OmniTools 版本化离线目录、193 项目录校验、热门 Top 10 和用户/工具 30 秒点击计数服务，以及按统一认证号读取既有用户 SSH Key 并输出 TAEK1 加密信封。 |
| `test-agent-model-gateway` | 中立的企业模型目录投影、能力探测、OpenAI-compatible 请求准备/流式转发、上游错误脱敏和每日聚合调用；同时提供 OpenCode 内部代理复用的 URL/可信 Header/响应头安全支持，不承载 Controller 或 SQL。 |
| `test-agent-memory` | 通用长期记忆业务边界；承载个人/团队治理、官方风格 Mem0 REST 端口、证据安全引用、学习 Outbox、2 秒 fail-open 检索和 Skill 提案，不保存聊天正文、不直连记忆 PostgreSQL。 |
| `test-agent-xxl-job-admin-upstream` | 未做业务修改的 XXL-JOB Admin 3.4.2 源码与资源普通 JAR；只允许整体上游升级。 |
| `test-agent-xxl-job-integration` | 进程内独立 Servlet Admin、独立 MySQL/Flyway/MyBatis、平台 advertised host 地址派生、由本机 Admin readiness 门控且不阻塞主服务的 executor、统一 handler adapter、一次性 SSO/JIT 用户、平台 session marker 校验和隔离 health。 |
| `test-agent-api` | Controller、WebSocket 入口适配、请求/响应 DTO、统一异常、鉴权、限流、含 `X-Test-Agent-Linux-Server-Id` 的 CORS 边界、RunEvent SSE 按生产 Java 流式转发入口、用户通知分页/已读/fetch SSE、夜间时段/任务 HTTP 入口、工具盒子目录/点击 HTTP 入口、带 `permissionCount/PERMISSION` 的用户级会话运行态 HTTP/fetch SSE 入口、平台文件 WebSocket route/ticket/RPC 入口（含 workspace 原始文件、引用组合视图、Agent 配置文件及 Hub 制品/引用操作）、应用源码仓库/物化/打开/最近选择/持久化操作快照 HTTP 入口及独立一次性 ticket 进度 WebSocket、Agent & Skill Hub 浏览/发布/更新 HTTP 入口、应用引用资产库 7 个内部入口、工作空间创建进度轮询入口、manager 控制面入口、超级管理员运行管理 overview/指标历史、XXL 一次性 SSO 票据和显式 JVM 内存参数跨 Java 查询/刷新入口、trace Web 入口。 |
| `test-agent-persistence` | 数据库、MyBatis XML mapper、Flyway、Repository 和 Redis 必需适配，包括 Run manifest/input/durable 与 runtime 双 Stream/Hash + order ZSET 物化 snapshot/scope/active 索引、应用源码 slot/snapshot/replica/operation/step/cleanup/recent、会话上下文、workspace 服务器归属、按顶层请求 ID 收敛 `QUESTION/PERMISSION` 的用户级会话运行态只读查询、通用参数表、工作空间创建进度表、应用版本副本表、引用资产总体/服务器副本表、Agent & Skill Hub 内容寻址制品/修订/依赖/引用/更新操作表、工具点击永久明细/累计/用户窗口状态、opencode 用户进程管理表、scheduler/夜间任务/会话锁/时段容量表与 Repository 映射；新增外部 API 凭据/scope 表与整表加载、分页、CRUD mapper。 |
| `test-agent-event` | 按 RunStorageMode 分流的 RunEvent 追加、SSE、Redis 首帧物化 reset 与 `runtimeVersion` 有序尾流、legacy 数据库回放、全局事件触发流，以及 Redis/Noop 通用服务器广播适配。 |
| `test-agent-test-support` | 测试 fixture、mock server、集成测试支撑。 |
| `test-agent-app` | 唯一启动入口和可部署服务包，强制 WebFlux 主上下文并装配 XXL Admin 子上下文/executor；只放启动、装配、profile、migration、health 和日志。 |

平台体验工作区按既有模块边界实现：`test-agent-api` 提供无请求体 open 与纯本地 git-commit Controller，并复用用户 TestAgent Java 路由；`test-agent-workspace-management` 负责启动期本机目录/Git 初始化、确定性身份、当前绑定、统一实时权限以及本地 stage/unstage/discard/冲突/commit；`test-agent-opencode-runtime` 在 Session/Run/上下文入口复用该权限；`test-agent-persistence` 仅以 Flyway 和 MyBatis XML 保存通用参数与每服务器当前绑定；`test-agent-app` 的 Runner 在业务可用前触发幂等初始化。前端由 `packages/backend-api` 调用平台 API，`apps/agent-web` 维护 `EXPERIENCE` 选择语义、本地 Git 交互和永久隐藏 push 的门禁。该链路不创建虚拟应用/版本/成员，不直连 OpenCode server，不新增文件 HTTP 代理或 RunEvent 类型。

`apps/agent-web` 的体验选择同时拥有独立 continuation generation 与工作区 intent：用户点击时即废弃旧 managed/source 异步链，成功打开后才按用户保存说明知晓标记；应用目录刷新、进程 READY 和窗口 focus 只能读取该 intent，不能越过 `EXPERIENCE` 边界自动恢复应用。该前端状态收敛不改变后端 API、事件、数据库或服务器路由职责。

公共 Agent 卡死 rollout 的纠错编排仍沿用现有边界：`test-agent-workspace-management.AgentConfigApplicationService` 解析远端修正 commit 并广播新任务，`test-agent-opencode-runtime.PublicAgentConfigRolloutService` 负责原子替换协调、精确目标强停和排空，`test-agent-persistence` 的 MyBatis XML/Flyway 保存替换审计链与 `force_stop` 派生标记，`test-agent-api.AgentConfigController` 仅暴露 `SUPER_ADMIN` DTO/鉴权入口。停止进程必须继续复用 `OpencodeProcessStopService`，不在 workspace、API 或 persistence 层直接控制 manager。

新增后端文件前先按上表归属；没有合适工程时按业务边界新建 Maven module。

### LobeHub 企业集成定位

- `test-agent-domain`：只定义 LobeHub ticket/grant Redis 端口、模型目录/探测端口和每日聚合端口，不知道 HMAC、
  HTTP、Redis key 或 MyBatis 行模型。
- `test-agent-integration`：负责平台用户到一次性票据、部门规范化/摘要、虚拟邮箱、实例角色、HMAC 和模型委托的
  业务编排；不实现 fork 内的用户或 Workspace 表。
- `test-agent-configuration-management`：负责超级管理员维护供应商公开模型目录及触发固定样本能力探测。
- `test-agent-model-gateway`：负责按已探测能力解析公开模型、改写上游模型 ID、流式代理、错误清洗和用量增量；
  `OpenAiUpstreamSupport` 供既有 OpenCode 内部模型代理复用，不把 OpenCode Responses 转换搬入本模块。
- `test-agent-persistence`：Redis 原子票据/nonce/委托，以及模型目录、探测和每日聚合的 MyBatis XML 实现与
  Flyway；LobeHub 自身 ParadeDB 和 Workspace 数据不属于平台 persistence。
- `test-agent-api`：只暴露当前用户签票、服务 HMAC 兑换/撤销、管理员模型目录/探测和固定模型网关端点。
- `apps/agent-web`、`packages/backend-api`、`packages/shared-types`：分别承担入口/固定 launch、签票 client 与
  短期 ticket DTO；票据不进入 Pinia、router state 或持久化。
- 独立 LobeHub fork：负责 consume、Session、用户/部门 Workspace JIT、私有资源、企业模型适配器、离线开关与
  Windows/Linux 设备策略，源码不放入当前仓库；本地默认 checkout 为同级 `../lobehub-platform`，精确提交由
  `deploy/internal/lobehub/version.env` 锁定。

## 前端包职责

普通用户首次引导由 `apps/agent-web` 复用工作台现有控件锚点，覆盖应用下拉、workspace/version 切换、小地球引入需求子条目、首条消息建立对话、设置和手册；具体操作说明由 `apps/user-manual` 的快速开始、设置与权限、工作区和对话章节维护，设置章节按普通用户与应用管理员权限区分入口。

引用资产指针展示仍由 `packages/backend-api` 透传既有 7 个 API 的可空服务器路径和状态，由 `apps/agent-web` 用 2 秒轮询驱动三阶段核验进度弹层；不新增事件流、数据库或 manager 边界。

应用源码树的固定提交快照由 `test-agent-workspace-management` 在一次 Git 提交解析内生成，`test-agent-api` 在既有 tree URL 上兼容投影节点数组或 `{targetCommit,nodes}`；`packages/backend-api` 保留旧方法并新增 snapshot 方法，`packages/shared-types` 只承载两种稳定返回类型。

应用源码工作台由 `apps/agent-web` 维护显式 `MANAGED/APP_SOURCE` 语义、紧凑打开列表、全关联版本库四步物化、recent 恢复和逐服务器进度 observation；只保存服务端返回的 app/repository/generation/workspace 等逻辑身份，不构造物理根目录。source selection/recovery 以 token/app/repository/generation/workspace kind 收敛，tree 额外绑定 branch 与 snapshot commit；切应用、撤权、返回托管工作区或卸载会统一失效入口、弹窗、树、进度连接和重连 timer。源码普通文件写入继续依赖 `packages/backend-api` 的 Workspace 文件 WebSocket route/ticket/RPC，Git、应用 Agent/Hub 发布、宠物应用配置重载和版本选择由统一能力在 UI 与 handler 双层禁用。进度断线由页面按有界退避串行执行数据库 snapshot、新 ticket 与新 WebSocket，主动关闭不取消后台任务。

托管 Workspace 的可写身份由 `apps/agent-web` 根据既有个人 worktree 列表与运行态 Workspace ID 精确匹配；版本、应用、服务器目录和历史 Session 切换都在激活文件树前同步写入该身份，未匹配时保持只读。该恢复复用 `packages/backend-api` 既有个人工作区查询，不新增 API、事件或物理路径推断。

工作区文件下载仍沿用平台文件 WebSocket 边界：`test-agent-workspace-management` 安全读取约 512 KiB 原始字节分段，`test-agent-api` 暴露普通工作区/组合 locator 两类 RPC，`packages/backend-api` 映射共享分段类型，`apps/agent-web` 在浏览器校验目录完整性、按来源消解冲突并生成 ZIP；不新增跨服务器 HTTP 文件代理。

个人工作区与用户 `opencode` binding 跨服务器错配的补偿边界固定为：`test-agent-workspace-management` 负责源端 Git 工作态快照、目标恢复和每分钟 XXL handler，`test-agent-persistence` 用 MyBatis XML/Flyway 保存租约状态并在一个事务内切换 Workspace/个人路径，`test-agent-api` 复用公共 Java 路由签发目标 JVM 的一次性专用 WebSocket ticket，`test-agent-xxl-job-integration` 只注册全局广播任务。文件字节不经过 Java→Java HTTP，OpenCode 源码和 generated SDK 均不参与该流程。

managed/source 选择共用完整 intent authority，旧 terminal 的 repository summary 刷新使用独立 list authority；child commit 漂移会失效整棵 tree authority。每次进度自动重连按 connection epoch 隔离，当前 socket 失败会先作废 epoch，使退避与 replacement snapshot 等待期的旧帧无效；只有有效 operation frame 清零指数退避，`AbortSignal` 可在 CONNECTING 期间立即停止观察。source 中 Run Diff 的普通文件仍可写，PUBLIC/WORKSPACE Agent 保存由组件和父 handler 双重拒绝；已打开的 Hub、配置重载、新增版本和 Git pull 弹窗在能力失效时立即收敛。

活动栏底部 `Boxes` 入口的前端 Hub 统一表示 Agent、Skill、MCP、Tool：Agent/Skill 继续调用平台 Hub API 管理远端资产，MCP/Tool 只复用 `apps/agent-web` 已加载的 OpenCode 运行态目录，不新增服务端资产类型。顶部资源摘要另保留 Plugin 计数；顶部摘要与 Hub 详情面板都支持拖拽调宽和页面内全屏。

活动栏 `BrainCircuit` 入口和 `/memories` 路由由 `apps/agent-web` 组合通用记忆中心：`MemoryCenter.vue` 负责个人/团队/Skill 提案治理与含 Session 标题/ID 的证据 rail，`MemoryAdminPanel.vue` 负责 Mem0 多节点、企业/CPU profile、投影积压、模型设置和白名单，`FigmaChatPanel.vue` 只显示 `run-usage/query` 恢复的真实注入数量。`packages/backend-api` 是页面访问 `/api/internal/platform/memory/v1` 的唯一入口；前端不直连 memory-service、不复制原始聊天、不扩展 RunEvent。

`apps/agent-web/router.ts` 与 `AgentWorkbench.vue` 共同维护活动栏 URI：`/workbench`、`/toolbox`、`/memories`、`/system`、`/hub` 和 `/settings` 分别对应工作台、工具箱、记忆中心、超级管理员控制台、能力库和设置弹窗，`/` 只兼容跳转到 `/workbench`。`toolbox-navigation.ts` 复用同一沉浸式布局状态机，路由名是刷新、登录回跳和浏览器历史恢复的权威来源；组件内后台状态不能覆盖当前路由页面。

Skill Hub 的事项分类以逻辑资产持久化：应用推送 Skill 复用 `agent_skill_hub_assets`，公共 Git Skill 使用 `agent_skill_hub_builtin_classifications`，首次入库默认 `OTHER`，后续修订或 commit 不覆盖分类；`test-agent-workspace-management` 校验 `WORKER/TEST/CODE/OTHER` 与受控二级事项组合，`test-agent-persistence` 通过 `AgentSkillHubMapper.xml` 筛选并审计分类者，`test-agent-api` 仅向 `SUPER_ADMIN` 开放分类 mutation。公共 Agent/Skill 由 `AgentSkillHubApplicationService` 定时用共享仓库现有 Git 身份刷新当前分支远端引用并按精确 commit 对账，修订元数据写入公共快照表、正文复用内容寻址 artifact 表，查询链路只读数据库。前端 `AgentSkillHub.vue` 复用同一目录/详情链路提供两级筛选和详情内管理，不新增独立分类服务或客户端直连。

批量生成子条目测试案例不新增工作区目录协议：`apps/agent-web` 的弹层直接消费输入 `#` 已有的 `workspaceRequirementCandidates`，仍由 `AgentWorkbench` 通过四阶段 `searchFiles()` 聚合。前端局部上下文构建和最多四路编排不写当前输入附件；`packages/backend-api` 只新增批量 Session 方法并为夜间任务透传可选 `batchContext`。后端由 `test-agent-api` 暴露单项 Session HTTP DTO，`test-agent-opencode-runtime` 负责用户级幂等创建和定时事务归因，`test-agent-domain` 定义归因端口，`test-agent-persistence` 以专用 MyBatis XML 和 Flyway 保存 Session 字段、唯一索引与事务锁。每项仍使用既有 RunEvent SSE，不新增事件或统一运营报表。

工作台通知中心由 `packages/shared-types` 定义通知分页和变化 DTO，`packages/backend-api` 只负责列表/通用已读 HTTP，`packages/event-stream-client` 负责带 Bearer 的通知 fetch SSE 与退避重连，`apps/agent-web` 组合铃铛、筛选、分页、可访问性和受控动作映射。列表复用 `unread/readAt/actionAvailable` 显示未读闭合信封、已读打开信封和未读取即失效的停用图标；分享管理弹框仅在页脚保留低强调备用链接并允许受视口约束的 CSS 双向拉伸。页面只把 `SESSION_SHARE + shareId` 映射到同源 `/s/{shareId}` 新标签页，`RESTART_OWN_PROCESS` 只调用当前用户进程重启，`NONE` 仍可未读和标记已读；未知类型/动作失败关闭，禁止消费任意 URL。分享访问成功后的已读事实仍由后端分享业务统一确认。

| 包 | 职责 |
|---|---|
| `apps/agent-web` | 自研 Vue 3 + Vite 主应用，负责页面组合、Vue Query Provider、Pinia、工作空间选择、活动栏沉浸式 Agent & Skill Hub 浏览/发布/引用/更新角标和冲突确认、所有登录用户可见的沉浸式工具盒子目录/热门/吸顶搜索来源与动态计数分类标签/新标签页与静默点击上报、支持缩放/页面内全屏/真实 URL 新标签页且带超级管理员服务器终端视图的服务器工作空间选择（标签页状态通过同源会话存储交接）、应用管理员引用配置双栏/2 秒状态轮询/JSONC 最小补丁与空闲 dispose 热加载、工作区与引用目录组合文件树（合并引用蓝色、冲突红色、只读 tab 和局部告警）、带上下文/路径请求代次和 dirty 修订保护的普通文件及公共级/应用级 Agent 文件加载编排、Agent 合成 tab 路由与真实绝对复制路径隔离、`opencode.jsonc`/Agent/Skill 应用配置 Git 作用域、Agent 保存后的 Git Changes 修订刷新与变更面板可见期间的 5 秒核验、用户 opencode 进程状态提示/初始化/头像确认式重启、Run 启动、夜间任务时段选择/会话列表浮层内待执行列表/当前会话锁定/30 秒刷新、Run 与通知 SSE 订阅编排、会话分享和配置 dispose 四态通知的受控动作、基于用户级摘要的后台运行会话历史计数与 question/permission 铃铛提醒、根 permission 快照 scope 替换、child task 精确铃铛、每 Session 最新 2000 条的前端原始报文内存查看器、七种图片宠物的本地轮换/随机/固定选择和一次性旁路问答、对话页中的公共级/应用级个人运行态重载入口、设置模态（含版本库英文名、版本库类型、工作空间创建进度、通用参数 JVM 内存值按需抽屉和用户管理页签，用户管理支持角色/组织/部门组合筛选、当前页或全部检索结果批量选择并通过一次请求设置角色、创建测试用户和超管直接调角色）、按角色收敛的系统管理容器（`APP_ADMIN` 仅配置管理中的成员应用 Git 刷新；`SUPER_ADMIN` 使用完整 XXL、运行管理、公共配置和应用 Git 全量刷新）和全局错误提示；系统管理新增仅超级管理员可见的 API Key 面板，支持分页、新建、scope、编辑、启停、掩码、按需查看、复制、轮换和删除，明文只保留在弹窗组件内存。 |
| `apps/user-manual` | VitePress 内置用户手册，负责稳定 Markdown 操作说明、本地全文搜索和 `/help/` 静态构建；不访问后端 API、不保存用户数据，构建结果由 `agent-web` 同源嵌入。 |
| `packages/backend-api` | 访问平台后端服务的唯一前端 client，负责统一响应、错误、traceId、绑定请求动态 `X-Test-Agent-Linux-Server-Id` 首跳提示、敏感字段递归脱敏的原始 HTTP 交换 observer、工具盒子目录/点击 API、超级管理员服务器目录选择、带 keyed single-flight/连接所有权清理/只读单次传输重试的平台文件 WebSocket route/ticket/RPC（workspace 原始文件、引用组合视图、Agent 配置文件与 Hub 制品/引用操作）、Agent & Skill Hub 元数据/发布/更新角标 API、应用引用资产库 7 个 API 及目标/实际指针状态、工作区 Git diff/stage/unstage/冲突 API、管理员应用 Git 授权范围查询及单分支/全应用刷新共享控制面、用户 opencode 进程状态/初始化/确认式重启、通知分页和通用已读、兼容 `permissionCount` 缺失与 `patterns[]` 的用户级会话运行态/权限请求、夜间时段与任务 CRUD、运行管理 overview 与指标历史、XXL 一次性 SSO 票据、配置管理及 JVM 内存值四接口、版本库类型字典、工作空间创建进度轮询、应用版本工作区 API 映射、active run 恢复查询、兼容同步 `askSideQuestion`、流式 `startSideQuestionRun` 和默认 `opencode` 的 agent URL 前缀。 |
| `packages/backend-api` | 访问平台后端服务的唯一前端 client，负责统一响应、错误、traceId、绑定请求动态 `X-Test-Agent-Linux-Server-Id` 首跳提示、敏感字段递归脱敏的原始 HTTP 交换 observer、工具盒子目录/点击 API、超级管理员服务器目录选择、带 keyed single-flight/连接所有权清理/只读单次传输重试的平台文件 WebSocket route/ticket/RPC（workspace 原始文件、引用组合视图、Agent 配置文件与 Hub 制品/引用操作）、应用源码仓库/物化/打开/最近选择/操作快照 API 与每次新签 ticket、调用方管理重连的独立进度 WebSocket、Agent & Skill Hub 元数据/发布/更新角标 API、应用引用资产库 7 个 API 及目标/实际指针状态、工作区 Git diff/stage/unstage/冲突 API、管理员应用 Git 授权范围/刷新、用户 opencode 进程状态/初始化/确认式重启、通知分页和通用已读、兼容 `permissionCount` 缺失与 `patterns[]` 的用户级会话运行态/权限请求、夜间时段与任务 CRUD、运行管理 overview 与指标历史、XXL 一次性 SSO 票据、配置管理及 JVM 内存值四接口、版本库类型字典、工作空间创建进度轮询、应用版本工作区 API 映射、active run 恢复查询、兼容同步 `askSideQuestion`、流式 `startSideQuestionRun` 和默认 `opencode` 的 agent URL 前缀。 |
| `packages/event-stream-client` | RunEvent SSE、用户级运行态和通知中心 fetch SSE client，负责按默认 `opencode` agent URL 连接 RunEvent、在空 `baseUrl` 的企业同源构建中保留 `/api/...` 相对地址、携带 Bearer Token 和可选动态服务器首跳提示连接 fetch SSE、自动重连、识别 `run.snapshot.reset`、兼容解析 `permissionCount/PERMISSION`、通知 snapshot/updated 解析、原始 `MessageEvent.data` 回调、事件去重和取消订阅。 |
| `packages/workbench-shell` | dockview-vue 工作台布局、顶部栏、面板、带加载三态/稳定快照身份/用户内容修订代次及真实绝对路径元数据的文件 tab Pinia 状态，以及 Git 变更面板应用工作区/应用级 Agent mock 数据。 |
| `packages/file-explorer` | 文件树、普通文件复制/剪切/粘贴与拖放、浏览器文件上传选择、超级管理员服务器工作空间选择事件、已加载文件名过滤、变更列表和打开文件入口；实际文件操作由 app 层调用 backend-api。 |
| `packages/editor` | Monaco 编辑器（原生 `monaco-editor`，源码区默认按可视宽度自动换行）、语言识别、内容编辑、只读展示、path/model URI 一致时才执行的外部正文同步，以及 Mermaid Flowchart、Sequence、State Diagram 的懒加载可视化编辑。 |
| `packages/diff-viewer` | Monaco Diff、变更文件列表、Run/Session/VCS 来源切换、split/unified 视图、Run 级接受/拒绝按钮和当前文件反馈。 |
| `packages/agent-chat` | 自建最小 chat 运行时、opencode-like 主时间线、用户消息及夜间定时来源标签、message part timeline（text/reasoning/tool/file/retry/unknown fallback）、受控的工具详情强制展开与 reasoning 显隐、带高度上限和纵向滚动的工作状态 dock、工具视图、Diff 摘要、runtime selector/status、slash command、`@` context、对齐 OpenCode 中文说明和 `patterns[]` 的 permission/question/Todo dock、child task 权限铃铛、Markdown 懒加载渲染（markdown-it + DOMPurify + highlight.js）、支持 `run.snapshot.reset` 的纯 RunEvent reducer，以及供实时事件和历史 `partsJson` 共用的 message part 归一化入口。旧 `AgentCard`/`TimelineCard`/`MessageParts` 路径已作废，仅保留兼容。 |
| `packages/terminal` | 受控 PTY 前端包，负责 ticket WebSocket 连接、输入、resize、关闭和输出渲染，不创建 ticket、不直连 opencode server。 |
| `packages/test-runner` | 底部 Run 状态、取消、重试和事件日志面板。 |
| `packages/ui-kit` | 平台通用 UI 组件、基础样式组合和反馈组件。 |
| `packages/shared-types` | 跨包共享 TypeScript 类型和事件/DTO 模型；包含应用源码严格四态、固定选择、操作安全快照和独立进度 WebSocket envelope，以及工具盒子目录、工具项和点击响应，Workspace 的 `physicalRootPath`、`PermissionRequest.patterns` 与 `SessionRuntimeStateSummary.permissionCount` 保持可选，attention 接受 `PERMISSION`，Session/SessionMessage/Run 来源、夜间时段/任务、代码库英文名、版本库类型、工作空间创建进度、平台文件 WebSocket route/ticket 等新增契约字段必须保持可选或按请求/响应兼容策略处理；新增外部 API scope、凭据列表/分页、新建/编辑和一次性明文响应类型。 |
| `../frontend-opencode` | 独立 Vue/TypeScript/Vite opencode IDE App 复刻工程；不加入 `frontend/pnpm-workspace.yaml`，通过 alias 复用 `backend-api`、`event-stream-client`、`shared-types` 源码。 |

外部 API 凭据链路固定为：`test-agent-domain.externalapi` 定义聚合/端口，`test-agent-system-management.externalapi` 管理 Key 与 JVM 快照，`test-agent-persistence` 只用 MyBatis XML/Flyway 保存 RSA 密文，`test-agent-integration.externalapi` 查询用户并生成 TAEK1，`test-agent-api` 负责独立认证过滤器和两类 Controller，`test-agent-app` 只负责 Flyway 后严格启动加载。前端由 `packages/shared-types` 固定 DTO、`packages/backend-api` 调用管理 API 并脱敏，`apps/agent-web` 仅在组件内存短暂展示明文；该链路不进入 RunEvent/SSE。

Workspace 的存储路径与物理路径边界由 `test-agent-domain/ManagedWorkspacePathResolver` 统一定义：数据库逻辑前缀保留跨服务器可迁移性，`test-agent-api` 和 `test-agent-workspace-management` 的对外 DTO 必须投影成同值的 `rootPath/physicalRootPath` 绝对路径。`apps/agent-web/components/physical-path.ts` 只允许复制路径与小地球消费该物理绝对路径，逻辑前缀、普通相对路径和内部 tab route 均失败关闭。

`apps/agent-web` 的视觉边界由应用层维护：`FigmaShell.vue` 组合外围壳层，并让顶栏与 8px 栏间间隔共用浅雾蓝画布色、左/中/右三栏各自形成纯白悬浮面板；左侧工作区/Agent 目录加载前后与中间未打开文件时的预览区均保持纯白，当前文件标签只用 2px 工行红上沿标记激活态。顶栏按“36px 首行 + 8px 面板间隔”的 44px 视觉带统一上下居中：Logo 左对齐，直接使用用户确认的初版耳机/拱形品牌图形 PNG，保留原图轮廓和比例，图形使用低饱和暗红实色 #7f1e2b，中文品牌字标使用黑色，英文副标题使用深红以呼应图形；应用、工作空间、版本三个白底细框按钮放在 Logo 末端与右侧工具组起点之间的网格列正中，使左右留白相等；书本手册、通知铃铛、透明底细框运行态摘要和单字头像依次固定在右侧。手册入口默认透明无框，打开弹框后保持与活动栏一致的柔红底、深红图标和工行红定位标记；通知未读角标和打开态复用工行红/柔红令牌。顶部工作空间/版本选择只复用 `AgentWorkbench` 既有数据和 `handleLoadVersions` / `handleSelectVersion` 回调，左下角 `WorkbenchFooter` 入口继续保留，两处不得各自新增切换链路；用户在顶部选定工作空间时，版本列表只有一项则直接默认该项，多项则复用后端倒序结果的首项（最新版本）。非品牌首行文字默认保持纯黑，单字用户名为 12px。`styles/globals.css` 提供隔离的 `--ta-shell-*` token；`styles/element-overrides.css` 让非模态顶部反馈主体透传指针事件，仅保留关闭按钮交互，避免遮挡工作区。`FigmaChatPanel.vue` 和 `packages/agent-chat` 不消费 shell token，避免外围品牌色影响对话内容。

`apps/agent-web` 的 Git Changes 负责应用 Agent 与公共 Agent 当前作用域的逐文件和批量暂存；批量入口复用 `packages/backend-api` 既有 Agent stage 方法，不新增 API 或跨作用域状态。

`apps/agent-web` 的版本库新增和编辑入口共用类型字典，顺序为测试工作库、自动化代码库、应用代码库、应用资产库；编辑通过 `packages/backend-api` 和 `packages/shared-types` 的可选 `repositoryType` 请求字段访问配置管理 PATCH API，旧 `standard` 只保留为后端协议兼容字段。工作空间候选只包含测试工作库与自动化代码库：前者继续由标准分支和目录规则约束，后者复用非标准库的任意分支/已有目录/显式日期版本路径。

## 前端访问关系

允许方向：

```text
apps/agent-web
  -> packages/workbench-shell / agent-chat / file-explorer / editor
  -> packages/diff-viewer / terminal / test-runner
  -> packages/backend-api / event-stream-client
  -> packages/ui-kit / shared-types

feature packages -> packages/ui-kit / shared-types
packages/backend-api -> packages/shared-types
packages/event-stream-client -> packages/shared-types
```

`apps/agent-web` 负责把聊天面板本地失败、停止和完成标记绑定到当前 Session 生命周期：已有 Session 被替换或清空时清理旧标记，历史会话只按自身消息与 Run 状态恢复；空草稿首次生成真实 Session ID 不切断同一轮 Run。

禁止方向：

- `backend-api`、`event-stream-client` 不得依赖页面、工作台或具体业务组件。
- `shared-types` 不得依赖任何业务包。
- `ui-kit` 不得依赖业务 API、事件流或页面状态。
- `editor`、`diff-viewer` 不得启动 Run 或直连 opencode server。
- 前端不得直连 opencode server；所有 HTTP 请求经 `backend-api`，所有实时事件经 `event-stream-client`。

## 前后端调用边界

1. `packages/backend-api` 是前端访问后端的唯一入口，负责统一 base URL、鉴权头、traceId、请求超时、统一解析成功/错误响应、将后端统一错误格式转换为前端错误对象，并为 `@tanstack/vue-query` 提供稳定 query key 和 mutation 方法；动态 `routeLinuxServerId` 只给用户 OpenCode、Session、Run、夜间任务和本地工作区/Agent 配置请求设置首跳提示，空值与共享控制面不发送；可选 `rawExchangeObserver` 仅向上层调试面板暴露已对 ticket、token、cookie、password、secret 与 session digest 递归脱敏的前后端原始交换摘要；agent 相关能力默认拼接 `/api/internal/agent/opencode/...`，包括用户 opencode 进程状态、初始化和 runtime 代理；工作区文件操作先经 `/api/workspaces/{workspaceId}/file-ws-route` 路由，再连接目标后端文件 WebSocket；Agent 配置文件列表、读取、写入先经 `/api/internal/platform/workspace-management/agent-config/file-ws-route` 路由，再连接目标后端文件 WebSocket；两类文件连接都按路由键 single-flight，旧连接回调只能清理自身，只有明确传输失败的读取允许重连重试一次；应用源码 HTTP 能力拼接 `/api/internal/platform/workspace-management/...`，物化选择发送 `{path,type}`，独立进度 WebSocket 每次连接先签新 ticket 且由调用方决定是否重连，关闭不触发取消；运行管理 overview 和指标历史能力拼接 `/api/internal/platform/opencode-runtime/management/...`；用户级会话运行态摘要拼接 `/api/internal/platform/opencode-runtime/sessions/runtime-state`；宠物旁路问答优先通过 `startSideQuestionRun` 启动 `/sessions/{sessionId}/side-question/runs` 并用既有 RunEvent SSE 消费，旧 `askSideQuestion` 同步路径保留兼容；定时任务页只调用 `/api/internal/platform/xxl-job/sso-tickets` 取得短期票据，再以同源隐藏表单 POST 到 `/xxl-job-admin/platform-sso/login`，旧 `/api/internal/platform/scheduler-management/**` 不再作为前端能力入口并返回 410；配置管理能力拼接 `/api/internal/platform/configuration-management/...`，包括版本库类型字典和工作空间创建进度轮询；应用版本工作区能力拼接 `/api/internal/platform/workspace-management/...`；`getActiveRun(sessionId)` 用于刷新后恢复非终态 RunEvent 订阅；可通过 `agentId` 切换 agent；不得直连 opencode server、不得保存 UI 状态、不得吞掉后端错误。
2. `packages/event-stream-client` 是前端消费实时事件的唯一入口，负责建立/关闭 agent-scoped RunEvent SSE 连接、断线续传（首次续传 `?lastEventId=`，后端保留 `Last-Event-ID` header 兼容）、在显式空 `baseUrl` 时保留 `/api/...` 同源相对地址、给已认证 fetch SSE 携带 Bearer Token 和可选动态服务器首跳提示、建立/关闭用户级 runtime-state fetch SSE、解析前原始 `MessageEvent.data` 回调、重复事件幂等保护、识别并上送 transient `run.snapshot.reset` 与旁路流事件；client 不从 reset payload 推导 durable 游标，清空和 snapshot 重放由 agent-chat/app reducer 负责。该包不得直接修改 Vue 组件状态、不得访问 opencode server。
3. 后端 HTTP DTO 映射到 `shared-types` 或 `backend-api` 内部类型；RunEvent 事件类型映射到 `shared-types`；页面展示模型必须由 API DTO 或 RunEvent 明确转换而来。
4. 新增字段必须默认可选，前端能处理旧响应缺字段；废弃字段必须保留过渡期；新事件类型前端必须有安全展示或忽略策略。
5. 后端统一错误响应转换为前端错误对象，至少包含 `traceId`、`code`、`message`、`retryable`、`details`；可重试错误提供重试入口，权限错误引导重新登录，限流错误展示等待语义，系统错误展示 traceId。

## 参考/实验目录

`frontend/interaction-visual-demo` 和 `opencode-source/opencode-1.18.4/` 仅作为 opencode Web 行为参考或交互资料；OpenCode 源码快照严格只读，禁止提交源码、测试、配置、构建脚本、资源或临时补丁。需要平台适配时必须修改本项目自身的后端、前端、worker 启动器或受控配置层。顶层 `frontend-opencode` 是独立 Vue/Vite 复刻工程，验收命令在该目录执行，不替代 `frontend/` 主 workspace 的检查；`requirements/` 下的历史文档不作为编码依据。

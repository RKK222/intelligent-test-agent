# 模块与包速查

本文件是“按功能找模块/包”的速查表，合并原前端架构、前后端契约和总体方案的模块职责。依赖边界与禁止关系见 `docs/architecture/dependency-rules.md`；HTTP/SSE 契约见 `docs/api/`。

## 总体架构

```text
Browser
  -> frontend/apps/agent-web
      -> packages/backend-api
      -> packages/event-stream-client
  -> frontend-opencode
      -> packages/backend-api (source alias)
      -> packages/event-stream-client (source alias)
  -> test-agent-app
      -> test-agent-api
          -> workspace-management / opencode-runtime / system-management / configuration-management / scheduler / integration
              -> agent-runtime
      -> persistence / event / observability
      -> test-agent-scheduler
      -> test-agent-agent-runtime
          -> test-agent-opencode-client
              -> test-agent-opencode-sdk-generated
                  -> opencode server pool
```

关键边界：

- 浏览器只认识平台后端 API 和平台事件流。
- `test-agent-api` 统一承载 API、鉴权、限流、traceId、任务入口、事件出口和错误处理。
- `test-agent-app` 只承载启动、装配、profile、migration、health 和日志等运行入口，不承载业务逻辑。
- `test-agent-agent-runtime` 是多 agent 选择、统一日志/指标包装和具体 agent 适配器边界。
- `test-agent-opencode-client` 是业务代码访问 opencode server 的唯一门面。
- `test-agent-opencode-sdk-generated` 只保存生成代码，不承载业务逻辑。

## 后端模块职责

| 模块 | 职责 |
|---|---|
| `test-agent-common` | 公共异常、统一响应 `ApiResponse`/`ApiErrorResponse`、TraceId、分页、校验、时间工具。 |
| `test-agent-domain` | Workspace、Session、SessionRuntimeState、AgentSessionBinding、Run、RunStorageMode、RunRuntimeStore/manifest/snapshot/replay/runtime tail、RunEvent、ExecutionNode、RoutingDecision、通用服务器广播 envelope/端口、opencode 用户进程管理拓扑、通用参数、显式 JVM 内存参数 SPI/状态和工作空间创建进度等纯领域模型与状态机，不依赖 Spring Web/Persistence/generated SDK。 |
| `test-agent-observability` | traceId、结构化日志、Micrometer 指标、观测性工具。 |
| `test-agent-opencode-sdk-generated` | 从 opencode OpenAPI spec 生成的 Java SDK，禁止手改。 |
| `test-agent-opencode-client` | 封装 generated SDK，提供 `OpencodeClientFacade`，是业务访问 opencode 的唯一门面。 |
| `test-agent-agent-runtime` | 定义 `AgentRuntime`、`AgentRuntimeRegistry`、统一日志/指标包装、`OpencodeAgentRuntime` 适配器和未注册的 `OtherAgentRuntime` 抽象占位。 |
| `test-agent-workspace-management` | Workspace、服务器归属、文件查看/新增/修改/上传/复制/移动/删除、基于工作区 JSONC 与本机 READY 引用副本的只读组合文件视图、超级管理员服务器目录选择、git/diff、设置页初始版本工作区创建、应用版本工作区、每服务器版本副本、个人工作区、feature 固定提交向相关个人 worktree 的原生 Git merge/dirty 待同步/三方冲突与完成、应用 Agent/Skill 发布 rollout、应用引用资产库的 generation/租约/本机有界即时调度/定向退避/补偿副本、受控分支切换、只读实际指针核验与安全目录树、agent 和 skill 管理业务。 |
| `test-agent-opencode-runtime` | Session、Run、RunEvent 编排、夜间任务提交/查询/会话锁/USER_PLAN 投递/窗口补偿与首个显式内存参数容量条目、订阅级 root/child scope、Redis active 索引、RunEvent SSE 按 Redis manifest 优先解析生产 Java、每用户有效公共配置软链接与公共个人保存热加载、公共全机/应用定向 Agent 配置发布排空、用户级会话运行态摘要/状态流、stale active Run 收敛业务任务、当前用户 opencode 进程强状态/弱健康/初始化契约和可选引用目录启动环境、Run 和 runtime 代理防绕过校验、用户进程/固定节点目标解析、带实时应用成员校验的 workspace 文件 WebSocket 后端路由、manager WebSocket 网关与后端实例生命周期、按 `backendProcessId` 精确选择在线 Java、超级管理员运行管理 Redis 快照聚合和 48 小时指标历史查询、归档内部 Session + 临时 fork + 按预算 compact + build agent 系统提示只读约束的宠物旁路 RunEvent 流式问答及 10 分钟孤儿清理、通过 `AgentRuntimeRegistry` 调用 agent、Diff/revert、terminal ticket/PTY 业务。 |
| `test-agent-system-management` | 用户、角色、权限等平台内部管理业务，包括注册、登录认证和 Token 管理，以及用户管理查询、创建测试用户和单角色调整。 |
| `test-agent-configuration-management` | 应用定义只读消费、应用成员、代码库英文名与应用关联、已初始化引用资产库英文名/类型冻结、应用工作空间、个人 SSH key 和 Git 远端只读目录查询配置业务；通用参数数据库直读视图（`RepositoryCommonParameterValues`）、变量引用解析器、参数更新跨实例广播，以及只管理显式 SPI 条目的本机内存参数注册表/诊断响应。 |
| `test-agent-scheduler` | 通用分布式定时任务框架，负责任务注册、Cron 计算、服务器亲和 USER_PLAN、有界并发、Redis 锁、后台扫描、统一运行记录、运行记录保留清理、Cron 调整、手动触发和协作式停止管理服务；其它具体业务任务放回所属业务模块。 |
| `test-agent-integration` | 非 opencode 外部系统联动业务边界（当前为空骨架）。 |
| `test-agent-api` | Controller、WebSocket 入口适配、请求/响应 DTO、统一异常、鉴权、限流、RunEvent SSE 按生产 Java 流式转发入口、夜间时段/任务 HTTP 入口、用户级会话运行态 HTTP/fetch SSE 入口、平台文件 WebSocket route/ticket/RPC 入口（含 workspace 原始文件、引用组合视图与 Agent 配置文件）、应用引用资产库 7 个内部入口、工作空间创建进度轮询入口、manager 控制面入口、超级管理员运行管理 overview/指标历史、定时任务管理和显式 JVM 内存参数跨 Java 查询/刷新入口、trace Web 入口。 |
| `test-agent-persistence` | 数据库、MyBatis XML mapper、Flyway、Repository 和 Redis 必需适配，包括 Run manifest/input/durable 与 runtime 双 Stream/Hash + order ZSET 物化 snapshot/scope/active 索引、会话上下文、workspace 服务器归属、用户级会话运行态只读查询、通用参数表、工作空间创建进度表、应用版本副本表、引用资产总体/服务器副本表、opencode 用户进程管理表、scheduler/夜间任务/会话锁/时段容量表与 Repository 映射。 |
| `test-agent-event` | 按 RunStorageMode 分流的 RunEvent 追加、SSE、Redis 首帧物化 reset 与 `runtimeVersion` 有序尾流、legacy 数据库回放、全局事件触发流，以及 Redis/Noop 通用服务器广播适配。 |
| `test-agent-test-support` | 测试 fixture、mock server、集成测试支撑。 |
| `test-agent-app` | 唯一启动入口和可部署服务包，只放启动、装配、profile、migration、health 和日志。 |

新增后端文件前先按上表归属；没有合适工程时按业务边界新建 Maven module。

## 前端包职责

普通用户首次引导由 `apps/agent-web` 复用工作台现有控件锚点，覆盖应用下拉、workspace/version 切换、小地球引入需求子条目、首条消息建立对话、设置和手册；具体操作说明由 `apps/user-manual` 的快速开始、设置与权限、工作区和对话章节维护，设置章节按普通用户与应用管理员权限区分入口。

引用资产指针展示仍由 `packages/backend-api` 透传既有 7 个 API 的可空服务器路径和状态，由 `apps/agent-web` 用 2 秒轮询驱动三阶段核验进度弹层；不新增事件流、数据库或 manager 边界。

| 包 | 职责 |
|---|---|
| `apps/agent-web` | 自研 Vue 3 + Vite 主应用，负责页面组合、Vue Query Provider、Pinia、工作空间选择、带超级管理员服务器终端视图的服务器工作空间选择、应用管理员引用配置双栏/2 秒状态轮询/JSONC 最小补丁与空闲 dispose 热加载、工作区与引用目录组合文件树（合并引用蓝色、冲突红色、只读 tab 和局部告警）、带上下文/路径请求代次和 dirty 修订保护的普通文件及公共级/应用级 Agent 文件加载编排、Agent 合成 tab 路由与真实绝对复制路径隔离、`opencode.jsonc`/Agent/Skill 应用配置 Git 作用域、Agent 保存后的 Git Changes 修订刷新与变更面板可见期间的 5 秒核验、用户 opencode 进程状态提示/初始化入口、Run 启动、夜间任务时段选择/会话列表浮层内待执行列表/当前会话锁定/30 秒刷新、SSE 订阅编排、后台运行会话历史计数/铃铛提醒（历史按钮数字只统计第一页 30 条中的未完成会话）、每 Session 最新 2000 条的前端原始报文内存查看器、五种 SVG 宠物的本地轮换/随机/固定选择和一次性旁路问答、设置模态（含版本库英文名、版本库类型、工作空间创建进度、通用参数 JVM 内存值按需抽屉和用户管理页签，用户管理支持查询、创建测试用户和超管直接调角色）、超级管理员系统管理容器（定时任务管理 + 运行管理最新指标与 ECharts 趋势）和全局错误提示。 |
| `apps/user-manual` | VitePress 内置用户手册，负责稳定 Markdown 操作说明、本地全文搜索和 `/help/` 静态构建；不访问后端 API、不保存用户数据，构建结果由 `agent-web` 同源嵌入。 |
| `packages/backend-api` | 访问平台后端服务的唯一前端 client，负责统一响应、错误、traceId、可选安全原始 HTTP 交换 observer、超级管理员服务器目录选择、带 keyed single-flight/连接所有权清理/只读单次传输重试的平台文件 WebSocket route/ticket/RPC（workspace 原始文件、引用组合视图与 Agent 配置文件）、应用引用资产库 7 个 API及目标/实际指针状态、工作区 Git diff/stage/unstage/冲突 API、用户 opencode 进程状态/初始化、用户级会话运行态摘要、夜间时段与任务 CRUD、运行管理 overview 与指标历史、定时任务管理、配置管理及 JVM 内存值四接口、版本库类型字典、工作空间创建进度轮询、应用版本工作区 API 映射、active run 恢复查询、兼容同步 `askSideQuestion`、流式 `startSideQuestionRun` 和默认 `opencode` 的 agent URL 前缀。 |
| `packages/event-stream-client` | RunEvent SSE 和用户级运行态 fetch SSE client，负责按默认 `opencode` agent URL 连接 RunEvent、携带 Bearer Token 连接 runtime-state、自动重连、识别 `run.snapshot.reset`、解析前原始 `MessageEvent.data` 回调、事件解析、去重和取消订阅。 |
| `packages/workbench-shell` | dockview-vue 工作台布局、顶部栏、面板、带加载三态/稳定快照身份/用户内容修订代次及真实绝对路径元数据的文件 tab Pinia 状态，以及 Git 变更面板应用工作区/应用级 Agent mock 数据。 |
| `packages/file-explorer` | 文件树、普通文件复制/剪切/粘贴与拖放、浏览器文件上传选择、超级管理员服务器工作空间选择事件、已加载文件名过滤、变更列表和打开文件入口；实际文件操作由 app 层调用 backend-api。 |
| `packages/editor` | Monaco 编辑器（原生 `monaco-editor`，源码区默认按可视宽度自动换行）、语言识别、内容编辑、只读展示、path/model URI 一致时才执行的外部正文同步，以及 Mermaid Flowchart、Sequence、State Diagram 的懒加载可视化编辑。 |
| `packages/diff-viewer` | Monaco Diff、变更文件列表、Run/Session/VCS 来源切换、split/unified 视图、Run 级接受/拒绝按钮和当前文件反馈。 |
| `packages/agent-chat` | 自建最小 chat 运行时、opencode-like 主时间线、用户消息及夜间定时来源标签、message part timeline（text/reasoning/tool/file/retry/unknown fallback）、工具视图、Diff 摘要、runtime selector/status、slash command、`@` context、permission/question/Todo dock、Markdown 懒加载渲染（markdown-it + DOMPurify + highlight.js）、支持 `run.snapshot.reset` 的纯 RunEvent reducer，以及供实时事件和历史 `partsJson` 共用的 message part 归一化入口。旧 `AgentCard`/`TimelineCard`/`MessageParts` 路径已作废，仅保留兼容。 |
| `packages/terminal` | 受控 PTY 前端包，负责 ticket WebSocket 连接、输入、resize、关闭和输出渲染，不创建 ticket、不直连 opencode server。 |
| `packages/test-runner` | 底部 Run 状态、取消、重试和事件日志面板。 |
| `packages/ui-kit` | 平台通用 UI 组件、基础样式组合和反馈组件。 |
| `packages/shared-types` | 跨包共享 TypeScript 类型和事件/DTO 模型，Session/SessionMessage/Run 来源、夜间时段/任务、用户级会话运行态、代码库英文名、版本库类型、工作空间创建进度、平台文件 WebSocket route/ticket 等新增契约字段必须保持可选或按请求/响应兼容策略处理。 |
| `../frontend-opencode` | 独立 Vue/TypeScript/Vite opencode IDE App 复刻工程；不加入 `frontend/pnpm-workspace.yaml`，通过 alias 复用 `backend-api`、`event-stream-client`、`shared-types` 源码。 |

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

禁止方向：

- `backend-api`、`event-stream-client` 不得依赖页面、工作台或具体业务组件。
- `shared-types` 不得依赖任何业务包。
- `ui-kit` 不得依赖业务 API、事件流或页面状态。
- `editor`、`diff-viewer` 不得启动 Run 或直连 opencode server。
- 前端不得直连 opencode server；所有 HTTP 请求经 `backend-api`，所有实时事件经 `event-stream-client`。

## 前后端调用边界

1. `packages/backend-api` 是前端访问后端的唯一入口，负责统一 base URL、鉴权头、traceId、请求超时、统一解析成功/错误响应、将后端统一错误格式转换为前端错误对象，并为 `@tanstack/vue-query` 提供稳定 query key 和 mutation 方法；可选 `rawExchangeObserver` 仅向上层调试面板暴露不含敏感请求头的前后端原始交换摘要；agent 相关能力默认拼接 `/api/internal/agent/opencode/...`，包括用户 opencode 进程状态、初始化和 runtime 代理；工作区文件操作先经 `/api/workspaces/{workspaceId}/file-ws-route` 路由，再连接目标后端文件 WebSocket；Agent 配置文件列表、读取、写入先经 `/api/internal/platform/workspace-management/agent-config/file-ws-route` 路由，再连接目标后端文件 WebSocket；两类文件连接都按路由键 single-flight，旧连接回调只能清理自身，只有明确传输失败的读取允许重连重试一次；运行管理 overview 和指标历史能力拼接 `/api/internal/platform/opencode-runtime/management/...`；用户级会话运行态摘要拼接 `/api/internal/platform/opencode-runtime/sessions/runtime-state`；宠物旁路问答优先通过 `startSideQuestionRun` 启动 `/sessions/{sessionId}/side-question/runs` 并用既有 RunEvent SSE 消费，旧 `askSideQuestion` 同步路径保留兼容；定时任务管理能力拼接 `/api/internal/platform/scheduler-management/...`；配置管理能力拼接 `/api/internal/platform/configuration-management/...`，包括版本库类型字典和工作空间创建进度轮询；应用版本工作区能力拼接 `/api/internal/platform/workspace-management/...`；`getActiveRun(sessionId)` 用于刷新后恢复非终态 RunEvent 订阅；可通过 `agentId` 切换 agent；不得直连 opencode server、不得保存 UI 状态、不得吞掉后端错误。
2. `packages/event-stream-client` 是前端消费实时事件的唯一入口，负责建立/关闭 agent-scoped RunEvent SSE 连接、断线续传（首次续传 `?lastEventId=`，后端保留 `Last-Event-ID` header 兼容）、建立/关闭用户级 runtime-state fetch SSE、解析前原始 `MessageEvent.data` 回调、重复事件幂等保护、识别并上送 transient `run.snapshot.reset` 与旁路流事件；client 不从 reset payload 推导 durable 游标，清空和 snapshot 重放由 agent-chat/app reducer 负责。该包不得直接修改 Vue 组件状态、不得访问 opencode server。
3. 后端 HTTP DTO 映射到 `shared-types` 或 `backend-api` 内部类型；RunEvent 事件类型映射到 `shared-types`；页面展示模型必须由 API DTO 或 RunEvent 明确转换而来。
4. 新增字段必须默认可选，前端能处理旧响应缺字段；废弃字段必须保留过渡期；新事件类型前端必须有安全展示或忽略策略。
5. 后端统一错误响应转换为前端错误对象，至少包含 `traceId`、`code`、`message`、`retryable`、`details`；可重试错误提供重试入口，权限错误引导重新登录，限流错误展示等待语义，系统错误展示 traceId。

## 参考/实验目录

`frontend/interaction-visual-demo` 和 `opencode-source/opencode-1.17.8/` 仅作为 opencode Web 行为参考或交互资料；顶层 `frontend-opencode` 是独立 Vue/Vite 复刻工程，验收命令在该目录执行，不替代 `frontend/` 主 workspace 的检查；`requirements/` 下的历史文档不作为编码依据。

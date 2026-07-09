# test-agent-opencode-runtime

## 工程定位

与 agent 运行相关的后端业务编排模块，承载 Session、Run、RunEvent 编排、通过 `AgentRuntimeRegistry` 调用 agent、Diff/revert 和受控 PTY terminal 业务；当前唯一真实 agent 实现为 opencode。

## 主要职责

- Session 创建、查询、消息追加、归档和当前用户历史会话分页；用户历史由 `SessionHistoryRepository` 只读端口提供，按会话创建人、Run 触发人、消息发送人归因，保留 `pinned` 字段但排序只使用更新时间倒序。
- 用户级会话运行态摘要和状态流；`SessionRuntimeStateApplicationService` 读取当前用户可见历史会话中的非终态 Run，按最新 `question.asked/replied/rejected` 派生待回答关注状态，并通过 `RunEventLiveBus.streamAll()` 的 run/question 事件触发刷新，同时保留低频轮询兜底。
- Run 启动、取消、远端 agent session 懒创建/复用、事件订阅和终态处理；平台保存 `RUNNING` 并订阅事件后异步提交远端 prompt 或原生 session command，不等待远端长任务完成才返回 Run。root `run.succeeded/run.failed` 终态事件是 Run 终态事实源；`Streaming response failed` 等提交/订阅 transport error 会短暂延迟收敛，若窗口内收到 root 终态则以 root 终态为准，不追加旧失败；若无 root 终态才写 `run.failed`，且 payload 会携带单行、长度受限的安全错误说明。后到 root 终态允许纠正先到 transport error 临时失败并刷新最终快照。事件订阅先经 `RunSessionScopeRouter` 判定 root/child scope，并在 root 成功/失败终态后结束，避免其它会话或同一会话下一轮消息串入旧 Run。
- stale active Run 收敛：`StaleActiveRunReconcileTaskHandler` 作为业务 `ScheduledTaskHandler` 注册到 `test-agent-scheduler`，默认 cron `0 */5 * * * *`、锁 TTL 5 分钟。`StaleActiveRunReconcileService` 每轮最多扫描 200 条 `updated_at` 超过 2 小时且仍为 `PENDING/RUNNING/CANCELLING` 的 Run；收敛前先查 Redis 运行态，`test-agent:run-output-activity:{runId}` 30 分钟内存在代表仍有用户可见输出，`test-agent:run-pending-ask:{runId}` 存在代表当前最新状态仍停在未处理 ask，二者任一存在都跳过。pending ask 只由实时 RunEvent 处理写入 Redis，不通过数据库 RunEvent 反查；当前 ask 类事件包括 `permission.asked` 和 `question.asked`，对应 `permission.replied/question.replied/question.rejected` 或 Run 终态会清理。Redis 读取异常时保守跳过，避免误杀仍在运行或等待用户处理的会话。真正超时的 Run 通过 `saveIfStatus` CAS 标记为 `FAILED`，追加 `run.failed`，message 固定为“运行超时，后台订阅已失效或长时间无输出”，并 best-effort 刷新 session message snapshot；该机制只修复平台 Run 状态，不主动 cancel/abort 远端 opencode 会话。
- AI 回复满意度反馈归属校验和 upsert：只允许登录用户对自己会话或自己触发 Run 的 `ASSISTANT` 消息提交 `POSITIVE/NEGATIVE` 反馈，评论最多 300 字。
- 运营分析 rollup 与查询：主链路只写事实，后台 runner 通过数据库锁默认刷新最近窗口的 hourly/daily rollup 和 Run 耗时直方图；查询服务只读 rollup 并返回 freshness，不统计、不展示、不导出 cost/costUsd。
- 当前用户 opencode 进程状态查询、头像菜单服务状态投影、初始化契约、防绕过 Run 校验、runtime 代理用户进程路由、manager WebSocket 命令网关，以及用户进程到兼容 `ExecutionNode` 的投影。
- `BackendJavaProcessLifecycleService` 首次保存后端实例时以进程启动时间作为 `createdAt`，心跳时间作为 `updatedAt`，避免启动阶段 manager 注册与周期心跳并发导致时间逆序、阻断 manager WebSocket 注册。
- `BackendJavaRouteResolver` 统一解析当前 Java 所属稳定 `linuxServerId`、Redis 中按 `backendProcessId` 保留的 Java 快照、以及 `containerId` 对应 manager 所属服务器；同一服务器多 Java 时优先选择与目标服务器 manager 已连接的 Java，其次选择同服务器最新心跳 Java，最后才使用当前 Java 兜底。所有用户进程、运行管理、Agent 配置和文件 WebSocket 路由都必须复用它做目标选择。新增 opencode-manager 路由或 Java->manager 控制入口时，不得在其它 service 中重新扫描 Redis 快照、复制 `linuxServerId/containerId` 解析、直接控制远端 manager 或绕过目标 Java。
- `OpencodeProcessStatusQueryService` 是唯一公共 opencode server 状态查询封装。强状态查询先确认平台进程记录是否存在，再通过本服务器 manager health 归一为 `NOT_STARTED/RUNNING/STALE`，并按健康结果回写数据库与 Redis heartbeat；弱健康检查 `weakHealth` 只读取 Redis manager snapshot，直接调用 opencode `/global/health`，不读写数据库、不调用 manager gateway、不刷新 heartbeat，供前端 10 秒健康轮询使用。新增用户状态、Run 前置检查、运行管理探测、启动后确认、停止后确认或后台心跳刷新时必须复用它，不得在业务 service 中直接调用 `OpencodeProcessManagerGateway.checkHealth()` 后自行映射状态。
- `OpencodeProcessStartupService` 是唯一公共 opencode server 启动封装，负责目标 Java 调用本服务器 manager `start` 后保存候选进程、在 manager command-timeout 窗口内复用公共状态查询服务等待本地 state/PID 和 opencode HTTP health ready、最终回写 `RUNNING/STOPPED/UNHEALTHY/FAILED`、Redis heartbeat、ACTIVE binding 和兼容 `ExecutionNode`。新增初始化、重启后拉起、端口复用或其它启动入口时必须复用它，不得在业务 service 中直接调用 `OpencodeProcessManagerGateway.startProcess()` 后自行写状态。
- 用户进程初始化支持可选 `operationId` 进度记录。`UserOpencodeProcessAssignmentService` 负责校验请求、确认分配、选择容器和准备参数等分配层步骤，`OpencodeProcessStartupService` 负责进程启动、记录候选进程、检查进程、健康检查和写入绑定等公共启动步骤；失败时通过 `OpencodeProcessStartOperationRepository` 保存 `errorCode/errorMessage/traceId`，查询进度只读数据库，不触发 manager health/start 或 RunEvent。
- `OpencodeProcessStopService` 是唯一公共 opencode server 停止封装，负责目标 Java 调用本服务器 manager `stop`，并在平台已有进程记录时继续执行 manager health，确认本地 state/PID 或 opencode HTTP health 已不健康后才回写 `STOPPED`。新增运行管理、清理、回收或其它停止入口时必须复用它，不得在业务 service 中直接调用 `OpencodeProcessManagerGateway.stopProcess()` 后自行判定成功或写状态；没有平台用户进程记录的无主 manager state 只以 manager `STOPPED` 回包为准，不新增数据库进程记录。
- `WorkspaceFileRoutingService` 根据当前用户 opencode 进程的服务器归属和统一 Java 后端解析器定位同服务器后端 Java 进程，供前端工作区文件 WebSocket 先路由到目标后端；该文件路由归属查询只读取 ACTIVE binding 和可恢复进程记录，不触发 manager health/start 命令，避免文件树加载被 opencode-manager 慢响应阻塞。超级管理员服务器工作空间选择器也复用该在线快照返回活跃后端服务器列表和默认目录。本地服务器身份变化或切换数据库后，历史 workspace 的 `linux_server_id` 若指向已无在线 Java 后端的旧服务器，且当前用户 opencode 已迁移到本后端、workspace 根目录在本机可访问，路由时会把 workspace 回绑到当前 `linuxServerId`；旧服务器仍在线或本机目录不可访问时继续返回 `CONFLICT`。
- `AgentRuntimeTargetResolver` 统一封装用户进程节点、固定节点 fallback、远端 session 创建/复用以及 binding 节点不一致时的自动覆盖。复用已有 binding 前会调用 agent runtime 校验远端 session 是否仍存在；opencode 返回 404 时会记录结构化 WARN 并创建新的远端 session，同时更新通用 `AgentSessionBinding` 和兼容 `sessions.opencode_*` 字段，其它远端错误不吞掉。
- `RuntimeManagementQueryService` 聚合 Linux 服务器、后端 Java 进程、opencode 容器、manager、manager 管理的本地 opencode server 明细、manager-backend 连接、用户进程和绑定状态，供超级管理员运行管理页展示；Java 后端、manager 和连接在线态只读取 Redis 快照，不回退数据库 heartbeat 字段。manager 明细中的 `startCommand` 保持可空以兼容旧快照，并按同服务器、同容器、同端口扫描候选用户进程，再结合 `ACTIVE` 用户绑定标记 `BOUND/UNBOUND` 归属；该归属不依赖页面底部用户进程分页筛选。底部用户进程查询通过用户关键字定位用户名、`userId` 或统一认证号，不依赖 Redis 进程心跳过滤，读取数据库历史进程后只对当前服务器进程通过公共状态查询服务探测；远端服务器进程返回 `REMOTE_SERVER/CHECK_SKIPPED`，避免随机 Java 直接控制其他服务器 manager。容器指标历史按容器 ID 读取；后端 Java 指标历史主接口按稳定 `linuxServerId` 读取，把同一服务器下服务器 CPU/内存/磁盘样本与 JVM 样本合并返回，保留近 48 小时原始 5 秒样本，查询时按 Controller 解析出的时间窗口读取，超出查询上限时按时间桶降采样；旧 `backendProcessId` 指标入口仅兼容旧客户端，能解析到稳定服务器身份时委托同一服务器历史。
- `RuntimeManagementCommandService` 为超级管理员运行管理页提供按 `containerId + port` 重启/停止 opencode server 的命令入口，复用公共启动/停止服务转发到 manager WebSocket `start/stop`，不直接访问 opencode server；跨 Java 后端路由由 `test-agent-api` 的运行管理路由服务先按统一 resolver 定位容器所属服务器，再交给目标 Java 调用本服务。对于平台已有记录的用户进程，`RUNNING/UNHEALTHY/STARTING` 等非 `STOPPED` 状态会先调用 `OpencodeProcessStopService` 确认停止，再按原 `process.sessionPath`、`containerId + port` 调用 `OpencodeProcessStartupService` 重新拉起并做启动后 health 确认；`STOPPED` 记录直接走公共启动服务。没有平台用户进程记录的无主 manager state 才保持原 manager `restart/stop` 语义，不写进程/binding。
- `OpencodeProcessHeartbeatMaintenanceService` 每 3 分钟只扫描当前服务器的 RUNNING opencode server 进程，通过公共状态查询服务确认并刷新 Redis 心跳，每 5 分钟清理 Redis 心跳索引中过期的 Java/opencode 进程 ID；opencode 进程心跳仍保留 5 分钟窗口。
- `BackendJavaProcessLifecycleService.registerHeartbeat` 每 5 秒为当前 Java 实例按 `backendProcessId` 写 Redis 后端快照，TTL 为 10 秒，并采集服务器 CPU、内存、当前工作目录所在磁盘容量和 JVM 指标；在线服务器身份、服务器级样本和 JVM 样本都按稳定 `linuxServerId` 保存，使同一服务器 Java 进程重启后指标趋势连续。`backendProcessId` 继续作为当前 Java 实例元数据、manager-backend 连接和拓扑连线字段，容量摘要包含当前 `advertisedHost`。数据库只在拓扑首次落库或状态变化时写入 `linux_servers` / `backend_java_processes`。它仍会为同 `linux_server_id` 下所有 `connection_status = CONNECTED` 的本地开发 manager 补齐到本实例的连接行（仅在 (manager, backend) 组合不存在连接时插入），让本地开发环境在 V17 迁移预置 manager 但还没有 manager WebSocket 注册时，仍能通过 `findHealthyContainersConnectedToBackend*` 查询到本机容器。
- `ManagerControlApplicationService.register` 会在写 manager-backend 连接前先补齐当前 Java 后端进程拓扑，避免 Web 端口已监听但 `ApplicationRunner` 首次心跳尚未落库时触发 `backend_java_processes` 外键失败；持久拓扑写入仍是 best-effort，`opencode_container_managers` 等历史拓扑表的唯一键冲突不会阻断 WebSocket 注册。manager 启动和在线态以控制面连接及 Redis 快照为准，避免已过期的数据库拓扑行导致 manager 重连失败。
- `OpencodeProcessManagerGateway` 生产装配只有 `SocketOpencodeProcessManagerGateway`，统一通过本服务器 manager WebSocket 控制面执行 `start`/`health`/`restart`/`stop`；本地开发也必须启动 Go manager，不再支持 `gateway-mode=local` 或本地直连 `baseUrl` 绕过。
- manager WebSocket 控制面通过 `ManagerControlMessageCodec` 编码 JSON；发给 Go manager 的时间字段必须保持 RFC3339 字符串，不能使用 Jackson 默认时间戳，否则 Go `time.Time` 解码会断开连接并导致用户进程初始化不可用。
- `UserOpencodeProcessAssignmentService` 不再支持 local-direct 合成进程；`status`、`initialize`、`requireReadyProcess` 都必须走 ACTIVE binding、容器拓扑和公共状态查询/启动链路。`initialize` 只负责选择目标容器、端口和路径，真实启动、候选进程保存、启动后状态查询、binding、heartbeat 和兼容节点投影全部委托 `OpencodeProcessStartupService`；`requireReadyProcess` 不自动启动，只通过 `OpencodeProcessStatusQueryService` 检查已有进程健康。瞬时 `STALE` 仅在最近一次成功健康检查后的 60 秒内沿用 READY，超过宽限期后状态页和 Run 前置校验都会拒绝继续使用旧绿灯。
- `UserOpencodeProcessAssignmentService` 对已有 binding 的用户只允许在原 `linux_server_id` 上重建；当请求落到其他 Java 后端时，由 API 层先按 ACTIVE binding 的 `linuxServerId` 路由到目标服务器 Java，再由目标 Java 控制本服务器 manager，不允许当前 Java fallback 到其他服务器并迁移 binding。是否已分配只读取 `user_opencode_process_bindings` 的 ACTIVE 记录；`allocationStatus` 是状态查询降级专用只读入口，不触发 manager health/start 或容器可用性检查，目标后端不可用时仍返回 `linuxServerId/port` 让前端展示稳定服务器身份，只有能解析到当前在线 Java 地址时才返回 `serviceAddress`。端口选择按数据库唯一约束 `(linux_server_id, port)` 在同服务器全局避让所有历史进程行，包含其它容器和非运行态脏数据。
- `UserOpencodeProcessAssignmentService.fileRoutingAffinity` 是文件 WebSocket 路由专用只读入口，只表达文件操作应落到哪台 Linux 服务器，不代表 opencode server 健康可用；Run、初始化、头像状态仍调用 `status` / `initialize` / `requireReadyProcess` 保持强健康检查语义。
- `UserOpencodeProcessAssignmentService` 创建用户 opencode 进程时，session/config 路径读取 `common_parameters.OPENCODE_SESSION_DIR` 和 `common_parameters.OPENCODE_PUBLIC_CONFIG_DIR`；缺失或空白时抛平台错误，不回退环境变量或代码默认路径。opencode 原生 session 数据目录固定为 `{OPENCODE_SESSION_DIR}/users/{unifiedAuthId}`，Java 通过用户仓储解析统一认证号，并拒绝空白、`/`、`\` 和 `..` 等无法作为安全路径片段的统一认证号；旧 `{OPENCODE_SESSION_DIR}/{port}` 目录不自动合并。初始化时 Java 仍按当前后端已连接的健康容器视图选择进程数最少且有空闲端口的目标容器，然后经公共启动服务向该容器对应的 manager 下发携带 `sessionPath` 的 `start`；manager 使用已通过 `configUpdate` 同步的配置路径。`OPENCODE_PUBLIC_CONFIG_DIR` 是否存在且非空只由目标 manager 在所在服务器检查，Java 本机不做 `Files.*` 校验。manager 返回 `errorCode=OPENCODE_UNAVAILABLE` 时，message 会包含目标服务器和 manager 实际检查的配置目录，socket gateway 映射为同码平台错误并原样透出。manager `STARTED` 仅表示启动命令完成，公共启动服务还必须立即调用 health；health healthy 才能返回 READY。
  `baseUrl/serviceAddress` 使用当前服务器 advertised host 和端口生成，不使用 `linuxServerId` 拼接地址。
- RunEvent 持久化策略、实时发布和 agent projected messages 恢复；Run 启动后记录 root session scope，`RunSessionScopeRouter` 根据 task/tool metadata、`session.created/session.updated parentID` 和 `session.children` bootstrap 发现 child；task metadata 指向 child session 时只发布 child discovery/scope updated 供前端建立导航索引，原始 root `message.part.updated` task part 仍按 root scope 输出，避免主 Agent 时间线的子 Agent 入口被过滤。原生 opencode 先发 root pending task part、再发 child `session.created(parentID)` 时，router 会在同一 parent 下用 FIFO pending task 队列补齐 `taskMessageId/taskPartId/taskCallId`，并从 child `info.agent/info.title` 提取展示 metadata；同时过滤 `heartbeat/server.heartbeat/tui/pty/workspace/worktree/installation/plugin/catalog` 等无 session 归属的全局 unknown 噪声。`RunSessionScopeRuntimeCache` 用 Redis 30 分钟 TTL 做 pending child event buffer 与 raw event dedup，Redis 不可用时降级为 DB-only。SSE/HTTP snapshot 恢复优先按 Run scope 拉取 root + child projected messages，Session 历史 snapshot 按 root session 查询跨 Run 已发现的 child，scope 缺失时回退 root-only。
- Run 终态/取消后的 `session_messages` 快照持久化会按 agent projected messages cursor 分页拉取，包含 assistant 可见 text、完整 message parts 和 token/cost；reasoning/tool output 不拼入回答正文，无 text 的工具步骤以空正文加结构化 parts 保存。上游 projected message 缺少稳定 message id 时，会用 session、可见正文、parts 和创建时间生成内部合成 `remoteMessageId`，只用于幂等 upsert，避免历史刷新反复落重复 assistant 快照。
- 从完成态 `write`/`edit`/`apply_patch` tool part 派生运行中 `diff.proposed`，供前端实时追踪文件变化；不调用 opencode `/vcs/diff?mode=working`，实际 Git patch 和精确行数由工作区 Git Diff 接口读取。
- Run Diff 查询、接受和拒绝。
- agent runtime 能力映射，包括 catalog/fs/vcs/lsp/mcp、config、provider auth/OAuth、worktree、session share、permission/question 和 MCP auth；permission/question 代理使用 opencode v2 `/api/session/{remoteSessionId}/permission|question` 路由，平台外部 API 和请求体保持兼容。question reply/reject 可消费请求体中的 `remoteSessionId`，用于把 task 子会话 ask 发回对应远端 session；平台 Session 仍用于定位用户进程和 workspace。
- Model/Provider 目录编排：前端对话框始终通过 runtime 代理 opencode 原生 `/api/model`、`/api/provider`，不再从 `ai_model_configs` 或 `ModelCatalogApplicationService` 返回托管目录；Run 启动前不再 `PATCH /global/config` 同步 provider。
- 内部模型代理：按 `X-ICBC-Model-Provider` 查 JVM 内存中的内部供应商地址，向上游注入数据库保存的全局 `ICBC_OPENAI_AUTH_TOKEN` 和 `ucid`，并把流式 `<think>...</think>` 转换为 `reasoning_content`。
- PTY terminal ticket、限流、active session registry、进程适配和审计。

## Model 目录配置

`test-agent.model-catalog.source` 和 `ai_model_configs` 相关类保留历史兼容，但不再参与前端模型目录、供应商目录、Run 模型校验或默认模型回退。内部供应商地址维护在 `internal_model_providers`，全局 token 维护在 `internal_model_proxy_settings`；Java 启动和刷新事件会把启用供应商加载到 `InternalModelProviderRegistry`。

## 测试覆盖

- `RunApplicationServiceTest` 覆盖 Run 创建、远端 prompt 非阻塞提交及异步失败错误说明、通用 binding 保存/复用、远端 session 懒创建/复用、用户进程节点 upsert、用户进程 binding 不一致自动重建、internal 模式拒绝匿名 Run 并把当前用户传给 provider 同步、sticky node、prompt parts、终态事件、终态消息快照/token 分页持久化、reasoning/tool output 与可见正文隔离、瞬态消息事件、tool part 实时 Diff 派生、取消编排、用户可见输出刷新 Redis 活跃标记、permission/question ask Redis 待处理状态维护，以及 `run.succeeded` 与 `Streaming response failed` 竞态：成功先到时不追加冲突失败，transport error 先到但随后 root 成功时最终仍为成功，没有 root 终态时延迟收敛失败且保留安全错误说明。
- `StaleActiveRunReconcileServiceTest` 覆盖超过 2 小时且无近期输出/未处理 ask 时标记 `FAILED` 并追加 `run.failed`、Redis 近期输出存在时跳过、Redis pending ask 存在时跳过、Run 已刷新或终态时跳过、CAS 失败不追加事件、Redis 读取异常保守跳过。
- `StaleActiveRunReconcileTaskHandlerTest` 覆盖业务任务 key、5 分钟 cron、5 分钟锁 TTL、启动 catch-up 不扫描和手动触发仍扫描。
- `BackendJavaRouteResolverTest` 覆盖同服务器多 Java 快照保留、manager 连接优先于最新心跳、当前服务器本地兜底、远端目标判断、`containerId` 按最新 manager 快照解析所属服务器，以及目标 Java 不可用时统一 `OPENCODE_UNAVAILABLE`。
- `OpencodeProcessStatusQueryServiceTest` 覆盖公共状态查询服务的进程记录缺失、health healthy、not-running 映射 STOPPED、普通不健康和 manager 异常返回 STALE 且不覆盖数据库稳定状态、heartbeat 刷新，以及弱健康只读 Redis 快照、不触碰 Repository/gateway/heartbeat 的行为。
- `OpencodeProcessStartupServiceTest` 覆盖公共启动服务的 start、候选进程保存、启动后公共状态查询、短暂 HTTP health 不可达时等待恢复、manager 控制错误立即失败、持续健康失败超时、失败候选状态收敛、RUNNING/binding/heartbeat/ExecutionNode 回写和旧进程/绑定时间复用。
- `UserOpencodeProcessAssignmentServiceTest` 覆盖当前用户 opencode 进程状态、初始化、已有 READY 进程复用、进度 operation 步骤推进和失败归因。
- `OpencodeProcessStopServiceTest` 覆盖公共停止服务的 stop、停止后公共状态查询确认、STOPPED 回写、health 仍健康或健康检查异常时不返回成功，以及无平台进程记录端口只复用 manager stop 回包。
- `UserOpencodeProcessAssignmentServiceTest` 覆盖未绑定状态、READY 复用、头像菜单未分配/运行中/未运行服务状态、同服务器重建、旧 binding 所属服务器无容器时不再跨服务器 fallback、端口选择、同服务器历史脏行端口避让、manager 不可用、通用参数 session/config 路径读取、按统一认证号生成用户稳定 session 目录、危险统一认证号路径片段拒绝、Java 本机公共配置目录不存在时仍按负载策略向目标 manager 下发 `start`、启动后 health healthy 才返回 READY、binding 路由服务器归属、binding-only 分配状态降级和绑定/节点投影。
- `WorkspaceFileRoutingServiceTest` 覆盖同服务器 workspace 通过 Redis 后端快照路由到目标后端、文件路由不调用阻塞式用户进程健康检查、workspace 与 agent 不同服务器时拒绝、本地旧 `linux_server_id` 安全回绑，以及旧服务器仍在线时不回绑。
- `RuntimeManagementQueryServiceTest` 覆盖运行管理 Redis 快照聚合、manager 下属 opencode server 明细透传、`ACTIVE` 绑定归为有主、无活跃绑定归为无主、归属聚合不受底部用户进程分页/用户名筛选影响、活跃进程过滤、用户名筛选、按用户关键字查询无心跳 STOPPED 进程、健康结果回写 RUNNING 快照、远端用户进程跳过本机 manager 探测、统一认证号定位用户、绑定状态合并、空数据、分钟级时间窗口、容器/后端指标历史降采样，以及同一 `linuxServerId` 下 Java 重启后的服务器与 JVM 指标连续查询。
- `RuntimeManagementCommandServiceTest` 覆盖运行管理按容器和端口委托公共启动/停止服务执行重启/停止，`STOPPED` 用户进程复用公共启动服务按原端口拉起，已有平台记录的非停止进程先 stop 再按持久化 `sessionPath` start，启动后短暂 health 不可达会等待恢复，持续 health 失败不返回成功；平台已有记录的停止命令必须 health 不健康后才回写 `STOPPED`，health 仍健康时不返回成功；跨 Java 后端路由覆盖在 `test-agent-api` 的运行管理路由测试中。
- `ManagerControlMessageCodecTest`、`ManagerControlApplicationServiceTest`、`ManagerConnectionRegistryTest`、`SocketOpencodeProcessManagerGatewayTest`、`BackendJavaProcessLifecycleServiceTest`、`OpencodeManagerConfigSyncServiceTest` 覆盖 manager 控制面消息、Redis manager 心跳、兼容后端列表响应编码、连接路由、启动期后端进程父表补齐、start/health/restart/stop 命令等待、后端实例心跳与创建时间固定、本地 manager-backend 连接自举，以及 `configRequest` 下发完整运行配置、最大进程数热刷新和路径参数不热广播。
- `RunDiffApplicationServiceTest` 覆盖 Diff 事件优先读取、agent runtime Diff fallback、接受/拒绝动作和缺失 messageID 冲突。
- `RunEventPersistencePolicyTest` 覆盖消息投影只走实时通道、关键状态事件持久化、tool payload 清洗、scopeContext 保留和 rawPayload 移除。
- `RunSessionScopeRouterTest` 覆盖 root/child idle/error 终态派生、task metadata/session parentID/session.children child discovery、task metadata discovery 不改写 root task part scope、原生 pending task part 到 child session.created 的 FIFO 绑定、未知 child pending drain、嵌套 session 防串流、raw event dedup 和全局 unknown 噪声过滤。
- `RunSessionScopeRuntimeCacheTest` 覆盖 Redis pending/dedup key、30 分钟 TTL、pending JSON drain 和 Redis 不可用降级。
- `RunMessageRecoveryServiceTest` 覆盖 agent session messages 中 assistant 恢复为 transient SSE snapshot、Run scope 下 root + child snapshot、Session root 下全量历史 snapshot、user part 不重复回放，以及未绑定/远端失败时降级为空。
- `SessionApplicationServiceTest` 覆盖 Session 创建前 Workspace 校验、归档隐藏、标题/置顶更新、当前用户历史会话查询委托、消息追加默认 role 和消息列表 DB fallback。
- `SessionRuntimeStateApplicationServiceTest` 覆盖用户级运行态 snapshot、首帧输出、run/question 全局事件触发刷新，以及 message-only 事件不触发摘要变更。
- `AiMessageFeedbackApplicationServiceTest` 覆盖反馈创建/更新、assistant role 校验、消息归属校验和评论长度边界。
- `AnalyticsQueryServiceTest` 覆盖 overview 指标口径、空分母、参数边界和 CSV 不含 cost 字段。
- `OpencodeRuntimeApplicationServiceTest` 覆盖 agent/provider/MCP runtime path、config/provider OAuth/worktree/share/MCP auth、workspace directory 透传、permission/question v2 session-scoped path、question child remote session override、permission reply body 兼容和过期请求 `CONFLICT + STALE_RUNTIME_REQUEST` 映射。
- `InternalModelThinkStreamConverterTest` 覆盖企业内部模型流式 `<think>` 标签跨 chunk 转换为 `reasoning_content`；模型目录接口和 Run 选择测试以 opencode 原生目录透传为准。
- `OpencodeRuntimeApplicationServiceTest` 覆盖 agent/provider/MCP runtime path、用户进程节点路由、固定节点 fallback、session binding 节点迁移自动重建、远端 session 404 缺失时自动重建绑定、远端校验非 404 错误透出、config/provider OAuth/worktree/share/MCP auth、workspace directory 透传、permission/question v2 session-scoped path、question child remote session override、permission reply body 兼容和过期请求 `CONFLICT + STALE_RUNTIME_REQUEST` 映射。
- `Terminal*Test` 覆盖 ticket 签发/消费/过期、active session 互斥、输入/输出限流、WebSocket envelope 编解码和本地进程适配。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-event`。
- `test-agent-agent-runtime`。
- `test-agent-scheduler`，仅用于注册本模块业务定时任务，不把业务任务放入 scheduler 模块。
- Reactor、Jackson、Spring Context。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-persistence` 实现类。
- `test-agent-opencode-sdk-generated`。

## 后续 AI 编码指引

新增与会话、运行、事件、Diff、permission/question、runtime catalog、terminal 相关业务编排时改这里；新增 agent 适配器应放在 `test-agent-agent-runtime`。Controller 和 URL 映射必须放在 `test-agent-api`。
高频文本 delta、message projection 和大段 tool/bash 输出不应写入 `run_events`；消息内容刷新恢复优先从 agent 标准 session messages 分页拉取并 upsert 到 `session_messages`，兼容消息列表接口的远端刷新必须在 bounded-elastic 线程执行，agent 不可用时回退数据库快照；`refresh=false` 只读数据库，不触发远端刷新。Run scope 存在时只恢复当前 Run 的 root + child 子树；Session 级历史树按 root session 汇总跨 Run 已发现的 child，是工作台历史恢复主路径。旧 Run 缺少 scope 记录时，可从 root/parent task part metadata 临时补偿 child 查询范围。Run 状态、Diff、permission/question/todo 等平台关键事件继续依赖 durable RunEvent，HTTP 历史接口会合并 durable 状态事件。child idle/error 只能产生 session 事件，root idle/error 才能派生 `run.succeeded/run.failed`；child 事件早于 discovery 到达时只进入 Redis pending，不按 root 入库。
运营分析新增指标时优先扩展 `AnalyticsModels`、`AnalyticsRepository`、rollup runner 和查询服务；API 查询不得绕过 rollup 直接扫原始事实表，导出字段不得包含 prompt/assistant 原文、密钥或费用字段。
生产 `OpencodeProcessManagerGateway` 通过 manager WebSocket 控制面下发 `start`/`health`/`restart`/`stop` 命令；无连接、超时或异常必须转换为平台 opencode 错误码。测试仍可使用 fake gateway 固定初始化、健康检查或运行管理命令结果。
所有 opencode server 启动入口必须调用 `OpencodeProcessStartupService`，由公共服务统一完成启动、候选进程快照、启动后 health 和最终状态回写；不要在新增业务编排中直接调用 `gateway.startProcess()` 并自行写进程、binding、heartbeat 或 `ExecutionNode`。
所有 opencode server 停止入口必须调用 `OpencodeProcessStopService`，由公共服务统一完成 manager stop、停止后 health 失败确认和最终状态回写；不要在新增业务编排中直接调用 `gateway.stopProcess()` 并自行写 `STOPPED`。
所有 opencode server 状态查询入口必须调用 `OpencodeProcessStatusQueryService`。强状态查询由公共服务统一完成进程存在性判断、manager health、状态回写和 Redis heartbeat；弱健康查询只能走 `weakHealth`，保持只读 Redis 快照和直接 opencode `/global/health`。不要在新增业务编排中直接调用 `gateway.checkHealth()` 并自行写 `RUNNING/STOPPED/UNHEALTHY/FAILED`。
`OpencodeManagerConfigSyncService` 把通用参数表中的 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`）、`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR` 经控制面 WebSocket 下发给已连接 manager：Java 收到 `register` 后只返回 `registered`，manager 收到后发送一次 `configRequest` 拉取完整配置；连接断开后 manager 无限重连，重连成功后重新拉取。前端只允许修改最大进程数和公共 Git 地址 `OPENCODE_PUBLIC_AGENT_GIT_URL`（均为 `editable=true`），参数刷新事件只广播 max-only `configUpdate` 给当前 Java 持有的本服务器 manager；路径类参数属于部署/初始化参数，不通过前端热刷新。公共 Git 地址虽可在前端修改，但不触发 manager 热刷新，其更新经 `common-parameter.refresh-requested` 广播保证跨实例 DB 一致后，由 AgentConfig 在下次公共配置操作时按当前部署模式直读 DB 生效。参数缺失、空白或最大进程数非正整数时不下发可启动配置，manager 保持未 ready 并拒绝启动用户进程；`opencode_containers.max_processes` 仍由 manager heartbeat 回报的生效值同步。
runtime 代理入口有认证用户时必须通过 `AgentRuntimeTargetResolver` 使用用户专属 opencode 进程；无用户主体的 static-token 或本地兼容调用才允许使用固定 `execution_nodes` fallback。

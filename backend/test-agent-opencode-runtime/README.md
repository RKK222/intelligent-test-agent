# test-agent-opencode-runtime

## 工程定位

与 agent 运行相关的后端业务编排模块，承载 Session、Run、RunEvent 编排、通过 `AgentRuntimeRegistry` 调用 agent、Diff/revert 和受控 PTY terminal 业务；当前唯一真实 agent 实现为 opencode。

## 主要职责

- Session 创建、查询、消息追加和归档。
- Run 启动、取消、远端 agent session 懒创建/复用、事件订阅和终态处理。
- AI 回复满意度反馈归属校验和 upsert：只允许登录用户对自己会话或自己触发 Run 的 `ASSISTANT` 消息提交 `POSITIVE/NEGATIVE` 反馈，评论最多 300 字。
- 运营分析 rollup 与查询：主链路只写事实，后台 runner 通过数据库锁默认刷新最近窗口的 hourly/daily rollup 和 Run 耗时直方图；查询服务只读 rollup 并返回 freshness，不统计、不展示、不导出 cost/costUsd。
- 当前用户 opencode 进程状态查询、头像菜单服务状态投影、初始化契约、防绕过 Run 校验、runtime 代理用户进程路由、manager WebSocket 命令网关，以及用户进程到兼容 `ExecutionNode` 的投影。
- `BackendJavaRouteResolver` 统一解析当前 Java 所属 `linuxServerId`、Redis 中每台服务器最新 `BackendJavaProcess`、以及 `containerId` 对应 manager 所属服务器；所有用户进程、运行管理、Agent 配置和文件 WebSocket 路由都必须复用它做目标选择。新增 opencode-manager 路由或 Java->manager 控制入口时，不得在其它 service 中重新扫描 Redis 快照、复制 `linuxServerId/containerId` 解析、直接控制远端 manager 或绕过目标 Java。
- `OpencodeProcessStatusQueryService` 是唯一公共 opencode server 强状态查询封装，负责先确认平台进程记录是否存在，再通过本服务器 manager health 归一为 `NOT_STARTED/RUNNING/HEALTH_CHECK_FAILED`，并统一回写 `RUNNING/STOPPED/UNHEALTHY/FAILED`、刷新健康时间、健康消息和 Redis heartbeat。新增用户状态、Run 前置检查、运行管理探测、启动后确认、停止后确认或后台心跳刷新时必须复用它，不得在业务 service 中直接调用 `OpencodeProcessManagerGateway.checkHealth()` 后自行映射状态。
- `OpencodeProcessStartupService` 是唯一公共 opencode server 启动封装，负责目标 Java 调用本服务器 manager `start` 后保存候选进程、执行 manager health、确认本地 state/PID 和 opencode HTTP health、最终回写 `RUNNING/STOPPED/UNHEALTHY/FAILED`、Redis heartbeat、ACTIVE binding 和兼容 `ExecutionNode`。新增初始化、重启后拉起、端口复用或其它启动入口时必须复用它，不得在业务 service 中直接调用 `OpencodeProcessManagerGateway.startProcess()` 后自行写状态。
- `OpencodeProcessStopService` 是唯一公共 opencode server 停止封装，负责目标 Java 调用本服务器 manager `stop`，并在平台已有进程记录时继续执行 manager health，确认本地 state/PID 或 opencode HTTP health 已不健康后才回写 `STOPPED`。新增运行管理、清理、回收或其它停止入口时必须复用它，不得在业务 service 中直接调用 `OpencodeProcessManagerGateway.stopProcess()` 后自行判定成功或写状态；没有平台用户进程记录的无主 manager state 只以 manager `STOPPED` 回包为准，不新增数据库进程记录。
- `WorkspaceFileRoutingService` 根据当前用户 opencode 进程的服务器归属和统一 Java 后端解析器定位同服务器后端 Java 进程，供前端工作区文件 WebSocket 先路由到目标后端；该文件路由归属查询只读取 ACTIVE binding 和可恢复进程记录，不触发 manager health/start 命令，避免文件树加载被 opencode-manager 慢响应阻塞。超级管理员服务器工作空间选择器也复用该在线快照返回活跃后端服务器列表和默认目录。本地换 IP 或切换数据库后，历史 workspace 的 `linux_server_id` 若指向已无在线 Java 后端的旧服务器，且当前用户 opencode 已迁移到本后端、workspace 根目录在本机可访问，路由时会把 workspace 回绑到当前 `linuxServerId`；旧服务器仍在线或本机目录不可访问时继续返回 `CONFLICT`。
- `AgentRuntimeTargetResolver` 统一封装用户进程节点、固定节点 fallback、远端 session 创建/复用以及 binding 节点不一致时的自动覆盖。
- `RuntimeManagementQueryService` 聚合 Linux 服务器、后端 Java 进程、opencode 容器、manager、manager 管理的本地 opencode server 明细、manager-backend 连接、用户进程和绑定状态，供超级管理员运行管理页展示；Java 后端、manager 和连接在线态只读取 Redis 快照，不回退数据库 heartbeat 字段。manager 明细中的 `startCommand` 保持可空以兼容旧快照，并按同服务器、同容器、同端口扫描候选用户进程，再结合 `ACTIVE` 用户绑定标记 `BOUND/UNBOUND` 归属；该归属不依赖页面底部用户进程分页筛选。底部用户进程查询通过用户关键字定位用户名、`userId` 或统一认证号，不依赖 Redis 进程心跳过滤，读取数据库历史进程后只对当前服务器进程通过公共状态查询服务探测；远端服务器进程返回 `REMOTE_SERVER/CHECK_SKIPPED`，避免随机 Java 直接控制其他服务器 manager。容器指标历史按容器 ID 读取；后端 Java 指标历史主接口按 `linuxServerId` 读取，把同一 IP 下服务器 CPU/内存/磁盘样本与 JVM 样本合并返回，保留近 48 小时原始 5 秒样本，查询时按 Controller 解析出的时间窗口读取，超出查询上限时按时间桶降采样；旧 `backendProcessId` 指标入口仅兼容旧客户端，能解析到 IP 时委托同一服务器历史。
- `RuntimeManagementCommandService` 为超级管理员运行管理页提供按 `containerId + port` 重启/停止 opencode server 的命令入口，复用公共启动/停止服务转发到 manager WebSocket `restart/start/stop`，不直接访问 opencode server；跨 Java 后端路由由 `test-agent-api` 的运行管理路由服务先按统一 resolver 定位容器所属服务器，再交给目标 Java 调用本服务。对于平台已有记录的用户进程，`STOPPED` 或 manager 返回 `port ... is not managed` 时会按原 `containerId + port` 调用 `OpencodeProcessStartupService` 重新拉起并做启动后 health 确认；manager 已管理端口的 `restart` 成功回包也必须继续通过公共启动服务确认健康后才向 API 返回成功；停止时必须调用 `OpencodeProcessStopService`，manager `STOPPED` 后继续 health，确认不健康才回写 `STOPPED` 并向 API 返回成功。没有平台用户进程记录的无主 manager state 仍保持原 manager `restart/stop` 语义，不写进程/binding。
- `OpencodeProcessHeartbeatMaintenanceService` 每 3 分钟只扫描当前服务器的 RUNNING opencode server 进程，通过公共状态查询服务确认并刷新 Redis 心跳，每 5 分钟清理 Redis 心跳索引中过期的 Java/opencode 进程 ID；opencode 进程心跳仍保留 5 分钟窗口。
- `BackendJavaProcessLifecycleService.registerHeartbeat` 每 5 秒为当前 Java 实例写 Redis 后端快照，TTL 为 10 秒，并采集服务器 CPU、内存、当前工作目录所在磁盘容量和 JVM 指标；Java latest snapshot、在线心跳、服务器级样本和 JVM 样本都按 `linuxServerId` 保存，使同一服务器 Java 进程重启后页面只保留一行且 JVM 趋势连续。`backendProcessId` 继续作为当前 Java 实例元数据、manager-backend 连接和拓扑连线字段。数据库只在拓扑首次落库或状态变化时写入 `linux_servers` / `backend_java_processes`。它仍会为同 `linux_server_id` 下所有 `connection_status = CONNECTED` 的本地开发 manager 补齐到本实例的连接行（仅在 (manager, backend) 组合不存在连接时插入），让本地开发环境在 V17 迁移预置 manager 但还没有 manager WebSocket 注册时，仍能通过 `findHealthyContainersConnectedToBackend*` 查询到本机容器。
- `ManagerControlApplicationService.register` 会在写 manager-backend 连接前先补齐当前 Java 后端进程拓扑，避免 Web 端口已监听但 `ApplicationRunner` 首次心跳尚未落库时触发 `backend_java_processes` 外键失败；持久拓扑写入仍是 best-effort，`opencode_container_managers` 等历史拓扑表的唯一键冲突不会阻断 WebSocket 注册。manager 启动和在线态以控制面连接及 Redis 快照为准，避免已过期的数据库拓扑行导致 manager 重连失败。
- `OpencodeProcessManagerGateway` 生产装配只有 `SocketOpencodeProcessManagerGateway`，统一通过本服务器 manager WebSocket 控制面执行 `start`/`health`/`restart`/`stop`；本地开发也必须启动 Go manager，不再支持 `gateway-mode=local` 或本地直连 `baseUrl` 绕过。
- manager WebSocket 控制面通过 `ManagerControlMessageCodec` 编码 JSON；发给 Go manager 的时间字段必须保持 RFC3339 字符串，不能使用 Jackson 默认时间戳，否则 Go `time.Time` 解码会断开连接并导致用户进程初始化不可用。
- `UserOpencodeProcessAssignmentService` 不再支持 local-direct 合成进程；`status`、`initialize`、`requireReadyProcess` 都必须走 ACTIVE binding、容器拓扑和公共状态查询/启动链路。`initialize` 只负责选择目标容器、端口和路径，真实启动、候选进程保存、启动后状态查询、binding、heartbeat 和兼容节点投影全部委托 `OpencodeProcessStartupService`；`requireReadyProcess` 不自动启动，只通过 `OpencodeProcessStatusQueryService` 检查已有进程健康。
- `UserOpencodeProcessAssignmentService` 对已有 binding 的用户只允许在原 `linux_server_id` 上重建；当请求落到其他 Java 后端时，由 API 层先按 ACTIVE binding 的 `linuxServerId` 路由到目标服务器 Java，再由目标 Java 控制本服务器 manager，不允许当前 Java fallback 到其他服务器并迁移 binding。是否已分配只读取 `user_opencode_process_bindings` 的 ACTIVE 记录；`allocationStatus` 是状态查询降级专用只读入口，不触发 manager health/start 或容器可用性检查，目标后端不可用时仍返回 `NOT_RUNNING + serviceAddress` 让前端展示已分配地址。端口选择按数据库唯一约束 `(linux_server_id, port)` 在同服务器全局避让所有历史进程行，包含其它容器和非运行态脏数据。
- `UserOpencodeProcessAssignmentService.fileRoutingAffinity` 是文件 WebSocket 路由专用只读入口，只表达文件操作应落到哪台 Linux 服务器，不代表 opencode server 健康可用；Run、初始化、头像状态仍调用 `status` / `initialize` / `requireReadyProcess` 保持强健康检查语义。
- `UserOpencodeProcessAssignmentService` 创建用户 opencode 进程时，session/config 路径读取 `common_parameters.OPENCODE_SESSION_DIR` 和 `common_parameters.OPENCODE_PUBLIC_CONFIG_DIR`；缺失或空白时抛平台错误，不回退环境变量或代码默认路径。初始化时 Java 仍按当前后端已连接的健康容器视图选择进程数最少且有空闲端口的目标容器，然后经公共启动服务向该容器对应的 manager 下发 `start`；manager 使用已通过 `configUpdate` 同步的配置路径。`OPENCODE_PUBLIC_CONFIG_DIR` 是否存在且非空只由目标 manager 在所在服务器检查，Java 本机不做 `Files.*` 校验。manager 返回 `errorCode=OPENCODE_UNAVAILABLE` 时，message 会包含目标服务器和 manager 实际检查的配置目录，socket gateway 映射为同码平台错误并原样透出。manager `STARTED` 仅表示启动命令完成，公共启动服务还必须立即调用 health；health healthy 才能返回 READY。
- RunEvent 持久化策略、实时发布和 agent projected messages 恢复。
- Run 终态/取消后的 `session_messages` 快照持久化，包含 assistant 可见 text、完整 message parts 和 token/cost；reasoning/tool output 不拼入回答正文，无 text 的工具步骤以空正文加结构化 parts 保存。
- 从完成态 `write`/`edit`/`apply_patch` tool part 派生运行中 `diff.proposed`，供前端实时追踪文件变化和行数统计。
- Run Diff 查询、接受和拒绝。
- agent runtime 能力映射，包括 catalog/fs/vcs/lsp/mcp、config、provider auth/OAuth、worktree、session share、permission/question 和 MCP auth；opencode 原路径作为当前标准适配形态。
- Model 目录编排：`opencode` 来源保持旧代理；`external` 来源直连 OpenAI-compatible `/models` 并把外部 provider 配置同步给 opencode；`internal` 来源读取 `ai_model_configs` 表并按 openclaw 企业 patch 的 `icbc-openai` 兼容配置同步给 opencode。历史 `bailian` source 会按 `external` 兼容处理。
- PTY terminal ticket、限流、active session registry、进程适配和审计。

## Model 目录配置

`test-agent.model-catalog.source` 控制模型来源：

| source | 行为 |
|---|---|
| `opencode` | 保持旧行为，`/api/model`、`/api/provider` 直接代理 opencode。 |
| `external` | 外网测试模式，后端请求 `external.base-url + /models` 获取模型列表；获取失败时回退到配置内置外网模型。 |
| `internal` | 企业内模式，启动时把 openclaw 企业 patch 中的模型清单 seed 到 `ai_model_configs`，接口从数据库读取启用模型。默认模型为 `DeepSeek-V4-Flash-W8A8`。 |

在 `external` 和 `internal` 模式下，Run 启动和模型/Provider 目录读取前会尽力 `PATCH /global/config` 到当前 opencode 执行节点，写入 OpenAI-compatible provider、默认模型和请求头配置。provider API Key 优先读取 `test-agent.model-catalog.<external|internal>.api-key`，未配置时回退到 `api-key-env` 指定的环境变量，便于 IDEA 直接启动和脚本启动同时兼容。同步失败只记录告警，Run 仍走原有错误处理路径。

## 测试覆盖

- `RunApplicationServiceTest` 覆盖 Run 创建、通用 binding 保存/复用、远端 session 懒创建/复用、用户进程节点 upsert、用户进程 binding 不一致自动重建、sticky node、prompt parts、终态事件、终态消息快照/token 持久化、reasoning/tool output 与可见正文隔离、瞬态消息事件、tool part 实时 Diff 派生和取消编排。
- `BackendJavaRouteResolverTest` 覆盖同服务器多 Java 快照取最新、当前服务器本地兜底、远端目标判断、`containerId` 按最新 manager 快照解析所属服务器，以及目标 Java 不可用时统一 `OPENCODE_UNAVAILABLE`。
- `OpencodeProcessStatusQueryServiceTest` 覆盖公共状态查询服务的进程记录缺失、health healthy、not-running 映射 STOPPED、普通不健康映射 UNHEALTHY、health 命令异常映射 FAILED 和 heartbeat 刷新。
- `OpencodeProcessStartupServiceTest` 覆盖公共启动服务的 start、候选进程保存、启动后公共状态查询、RUNNING/binding/heartbeat/ExecutionNode 回写、复用旧进程/绑定时间和健康失败状态映射。
- `OpencodeProcessStopServiceTest` 覆盖公共停止服务的 stop、停止后公共状态查询确认、STOPPED 回写、health 仍健康或健康检查异常时不返回成功，以及无平台进程记录端口只复用 manager stop 回包。
- `UserOpencodeProcessAssignmentServiceTest` 覆盖未绑定状态、READY 复用、头像菜单未分配/运行中/未运行服务状态、同服务器重建、旧 binding 所属服务器无容器时不再跨服务器 fallback、端口选择、同服务器历史脏行端口避让、manager 不可用、通用参数 session/config 路径读取、Java 本机公共配置目录不存在时仍按负载策略向目标 manager 下发 `start`、启动后 health healthy 才返回 READY、binding 路由服务器归属、binding-only 分配状态降级和绑定/节点投影。
- `WorkspaceFileRoutingServiceTest` 覆盖同服务器 workspace 通过 Redis 后端快照路由到目标后端、文件路由不调用阻塞式用户进程健康检查、workspace 与 agent 不同服务器时拒绝、本地旧 `linux_server_id` 安全回绑，以及旧服务器仍在线时不回绑。
- `RuntimeManagementQueryServiceTest` 覆盖运行管理 Redis 快照聚合、manager 下属 opencode server 明细透传、`ACTIVE` 绑定归为有主、无活跃绑定归为无主、归属聚合不受底部用户进程分页/用户名筛选影响、活跃进程过滤、用户名筛选、按用户关键字查询无心跳 STOPPED 进程、健康结果回写 RUNNING 快照、远端用户进程跳过本机 manager 探测、统一认证号定位用户、绑定状态合并、空数据、分钟级时间窗口、容器/后端指标历史降采样，以及同一 `linuxServerId` 下 Java 重启后的服务器与 JVM 指标连续查询。
- `RuntimeManagementCommandServiceTest` 覆盖运行管理按容器和端口委托公共启动/停止服务执行重启/停止，`STOPPED` 用户进程和 manager `port ... is not managed` 场景复用公共启动服务按原端口拉起并做 health 确认，启动后 health 失败不返回成功；平台已有记录的停止命令必须 health 不健康后才回写 `STOPPED`，health 仍健康时不返回成功；跨 Java 后端路由覆盖在 `test-agent-api` 的运行管理路由测试中。
- `ManagerControlMessageCodecTest`、`ManagerControlApplicationServiceTest`、`ManagerConnectionRegistryTest`、`SocketOpencodeProcessManagerGatewayTest`、`BackendJavaProcessLifecycleServiceTest`、`OpencodeManagerConfigSyncServiceTest` 覆盖 manager 控制面消息、Redis manager 心跳、兼容后端列表响应编码、连接路由、启动期后端进程父表补齐、start/health/restart/stop 命令等待、后端实例心跳、本地 manager-backend 连接自举，以及 `configRequest` 下发完整运行配置、最大进程数热刷新和路径参数不热广播。
- `RunDiffApplicationServiceTest` 覆盖 Diff 事件优先读取、agent runtime Diff fallback、接受/拒绝动作和缺失 messageID 冲突。
- `RunEventPersistencePolicyTest` 覆盖消息投影只走实时通道、关键状态事件持久化、tool payload 清洗和 rawPayload 移除。
- `RunMessageRecoveryServiceTest` 覆盖 agent session messages 中 assistant 恢复为 transient SSE snapshot、user part 不重复回放，以及未绑定/远端失败时降级为空。
- `SessionApplicationServiceTest` 覆盖 Session 创建前 Workspace 校验、归档隐藏、标题/置顶更新、消息追加默认 role 和消息列表 DB fallback。
- `AiMessageFeedbackApplicationServiceTest` 覆盖反馈创建/更新、assistant role 校验、消息归属校验和评论长度边界。
- `AnalyticsQueryServiceTest` 覆盖 overview 指标口径、空分母、参数边界和 CSV 不含 cost 字段。
- `OpencodeRuntimeApplicationServiceTest` 覆盖 agent/provider/MCP runtime path、config/provider OAuth/worktree/share/MCP auth、workspace directory 透传和 permission reply body 兼容。
- `ModelCatalogApplicationServiceTest` 覆盖企业内模型 seed、`DeepSeek-V4-Flash-W8A8` 默认模型和 opencode provider 配置同步请求。
- `OpencodeRuntimeApplicationServiceTest` 覆盖 agent/provider/MCP runtime path、用户进程节点路由、固定节点 fallback、session binding 自动重建、config/provider OAuth/worktree/share/MCP auth、workspace directory 透传和 permission reply body 兼容。
- `Terminal*Test` 覆盖 ticket 签发/消费/过期、active session 互斥、输入/输出限流、WebSocket envelope 编解码和本地进程适配。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-event`。
- `test-agent-agent-runtime`。
- Reactor、Jackson、Spring Context。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-persistence` 实现类。
- `test-agent-opencode-sdk-generated`。

## 后续 AI 编码指引

新增与会话、运行、事件、Diff、permission/question、runtime catalog、terminal 相关业务编排时改这里；新增 agent 适配器应放在 `test-agent-agent-runtime`。Controller 和 URL 映射必须放在 `test-agent-api`。
高频文本 delta、message projection 和大段 tool/bash 输出不应写入 `run_events`；消息内容刷新恢复优先从 agent 标准 session messages 拉取并 upsert 到 `session_messages`，历史查询的远端刷新必须在 bounded-elastic 线程执行，agent 不可用时回退数据库快照。Run 状态、Diff、permission/question 等平台关键事件继续依赖 durable RunEvent。
运营分析新增指标时优先扩展 `AnalyticsModels`、`AnalyticsRepository`、rollup runner 和查询服务；API 查询不得绕过 rollup 直接扫原始事实表，导出字段不得包含 prompt/assistant 原文、密钥或费用字段。
生产 `OpencodeProcessManagerGateway` 通过 manager WebSocket 控制面下发 `start`/`health`/`restart`/`stop` 命令；无连接、超时或异常必须转换为平台 opencode 错误码。测试仍可使用 fake gateway 固定初始化、健康检查或运行管理命令结果。
所有 opencode server 启动入口必须调用 `OpencodeProcessStartupService`，由公共服务统一完成启动、候选进程快照、启动后 health 和最终状态回写；不要在新增业务编排中直接调用 `gateway.startProcess()` 并自行写进程、binding、heartbeat 或 `ExecutionNode`。
所有 opencode server 停止入口必须调用 `OpencodeProcessStopService`，由公共服务统一完成 manager stop、停止后 health 失败确认和最终状态回写；不要在新增业务编排中直接调用 `gateway.stopProcess()` 并自行写 `STOPPED`。
所有 opencode server 强状态查询入口必须调用 `OpencodeProcessStatusQueryService`，由公共服务统一完成进程存在性判断、manager health、状态回写和 Redis heartbeat；不要在新增业务编排中直接调用 `gateway.checkHealth()` 并自行写 `RUNNING/STOPPED/UNHEALTHY/FAILED`。
`OpencodeManagerConfigSyncService` 把通用参数表中的 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`）、`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR` 经控制面 WebSocket 下发给已连接 manager：Java 收到 `register` 后只返回 `registered`，manager 收到后发送一次 `configRequest` 拉取完整配置；连接断开后 manager 无限重连，重连成功后重新拉取。前端只允许修改最大进程数，参数刷新事件只广播 max-only `configUpdate` 给当前 Java 持有的本服务器 manager；路径类参数属于部署/初始化参数，不通过前端热刷新。参数缺失、空白或最大进程数非正整数时不下发可启动配置，manager 保持未 ready 并拒绝启动用户进程；`opencode_containers.max_processes` 仍由 manager heartbeat 回报的生效值同步。
runtime 代理入口有认证用户时必须通过 `AgentRuntimeTargetResolver` 使用用户专属 opencode 进程；无用户主体的 static-token 或本地兼容调用才允许使用固定 `execution_nodes` fallback。

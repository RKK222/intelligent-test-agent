# test-agent-opencode-runtime

## 工程定位

与 agent 运行相关的后端业务编排模块，承载 Session、Run、RunEvent 编排、通过 `AgentRuntimeRegistry` 调用 agent、Diff/revert 和受控 PTY terminal 业务；当前唯一真实 agent 实现为 opencode。

## 主要职责

- Session 创建、查询、消息追加和归档。
- Run 启动、取消、远端 agent session 懒创建/复用、事件订阅和终态处理。
- AI 回复满意度反馈归属校验和 upsert：只允许登录用户对自己会话或自己触发 Run 的 `ASSISTANT` 消息提交 `POSITIVE/NEGATIVE` 反馈，评论最多 300 字。
- 运营分析 rollup 与查询：主链路只写事实，后台 runner 通过数据库锁默认刷新最近窗口的 hourly/daily rollup 和 Run 耗时直方图；查询服务只读 rollup 并返回 freshness，不统计、不展示、不导出 cost/costUsd。
- 当前用户 opencode 进程状态查询、头像菜单服务状态投影、初始化契约、防绕过 Run 校验、runtime 代理用户进程路由、manager WebSocket 命令网关，以及用户进程到兼容 `ExecutionNode` 的投影。
- `WorkspaceFileRoutingService` 根据当前用户 opencode 进程的服务器归属和 Redis Java 后端快照定位同服务器后端 Java 进程，供前端工作区文件 WebSocket 先路由到目标后端；该文件路由归属查询只读取 ACTIVE binding 和可恢复进程记录，不触发 manager health/start 命令，避免文件树加载被 opencode-manager 慢响应阻塞。超级管理员服务器工作空间选择器也复用该在线快照返回活跃后端服务器列表和默认目录。本地换 IP 或切换数据库后，历史 workspace 的 `linux_server_id` 若指向已无在线 Java 后端的旧服务器，且当前用户 opencode 已迁移到本后端、workspace 根目录在本机可访问，路由时会把 workspace 回绑到当前 `linuxServerId`；旧服务器仍在线或本机目录不可访问时继续返回 `CONFLICT`。
- `AgentRuntimeTargetResolver` 统一封装用户进程节点、固定节点 fallback、远端 session 创建/复用以及 binding 节点不一致时的自动覆盖。
- `RuntimeManagementQueryService` 聚合 Linux 服务器、后端 Java 进程、opencode 容器、manager、manager 管理的本地 opencode server 明细、manager-backend 连接、用户进程和绑定状态，供超级管理员运行管理页展示；Java 后端、manager 和连接在线态只读取 Redis 快照，不回退数据库 heartbeat 字段。manager 明细中的 `startCommand` 保持可空以兼容旧快照，并按同服务器、同容器、同端口扫描候选用户进程，再结合 `ACTIVE` 用户绑定标记 `BOUND/UNBOUND` 归属；该归属不依赖页面底部用户进程分页筛选。底部用户进程查询通过用户关键字定位用户名、`userId` 或统一认证号，不依赖 Redis 进程心跳过滤，读取数据库历史进程后主动调用 manager health 区分 `HEALTHY`、`NOT_RUNNING`、`UNHEALTHY`、`CHECK_FAILED`，并在可重启时返回 `restartable=true`。容器指标历史按容器 ID 读取；后端 Java 指标历史会把按 `linuxServerId` 保存的服务器 CPU/内存/磁盘样本与按 `backendProcessId` 保存的当前 JVM 样本合并返回，保留近 48 小时原始 5 秒样本，查询时按 Controller 解析出的时间窗口读取，超出查询上限时按时间桶降采样。
- `RuntimeManagementCommandService` 为超级管理员运行管理页提供按 `containerId + port` 重启/停止 opencode server 的命令入口，复用 `OpencodeProcessManagerGateway` 转发到 manager WebSocket `restart`/`stop`，不直接访问 opencode server、不写数据库；local 网关明确返回不可用。
- `OpencodeProcessHeartbeatMaintenanceService` 每 3 分钟通过 manager health 命令确认 RUNNING opencode server 进程并刷新 Redis 心跳，每 5 分钟清理 Redis 心跳索引中过期的 Java/opencode 进程 ID；opencode 进程心跳仍保留 5 分钟窗口。
- `BackendJavaProcessLifecycleService.registerHeartbeat` 每 5 秒为当前 Java 实例写 Redis 后端快照，TTL 为 10 秒，并采集服务器 CPU、内存、当前工作目录所在磁盘容量和 JVM 指标；服务器级样本追加到按 `linuxServerId` 保存的 48 小时 Redis 历史，JVM 样本追加到按 `backendProcessId` 保存的 48 小时 Redis 历史。数据库只在拓扑首次落库或状态变化时写入 `linux_servers` / `backend_java_processes`。它仍会为同 `linux_server_id` 下所有 `connection_status = CONNECTED` 的本地开发 manager 补齐到本实例的连接行（仅在 (manager, backend) 组合不存在连接时插入），让本地开发环境在 V17 迁移预置 manager 但还没有 manager WebSocket 注册时，仍能通过 `findHealthyContainersConnectedToBackend*` 查询到本机容器。
- `OpencodeProcessManagerGateway` 提供两套实现，通过 `test-agent.opencode.manager-control.gateway-mode` 切换：`socket`（默认，生产用 `SocketOpencodeProcessManagerGateway` 走 manager WebSocket）与 `local`（`LocalOpencodeProcessManagerGateway` 直连 `baseUrl` 跑 HTTP GET 检查、`startProcess` 走占位返回）。两个实现都打 `@ConditionalOnProperty` 互斥激活；`application-local.yml` 默认 `local`，其它 profile 留空走 `socket`。
- manager WebSocket 控制面通过 `ManagerControlMessageCodec` 编码 JSON；发给 Go manager 的时间字段必须保持 RFC3339 字符串，不能使用 Jackson 默认时间戳，否则 Go `time.Time` 解码会断开连接并导致用户进程初始化不可用。
- `UserOpencodeProcessAssignmentService` 支持 `test-agent.opencode.local-direct` 短路开关：开启后 `status` / `initialize` / `requireReadyProcess` 三个入口完全跳过 database topology / user binding / manager health 校验链路，直接合成一个指向 `test-agent.opencode.local-direct-base-url`（默认 `http://127.0.0.1:4096`）的 READY 进程对象。Run 启动拿到合成节点后仍会先 upsert 兼容 `ExecutionNode`，再保存路由审计和 agent session binding，避免本地直连节点触发外键失败。`application-local.yml` / `application-guo.yml` 默认 `true`，生产必须保持 `false`。配置类由 `OpencodeManagerControlConfig.localDirectSettings` 注入到 runtime 的 `LocalDirectSettings`。
- `UserOpencodeProcessAssignmentService` 对已有 binding 的用户仍优先在原 `linux_server_id` 上重建；当原服务器没有当前后端已连接的健康容器时，会 fallback 到当前后端任意健康容器，并在初始化成功后把 binding 迁移到新 `linuxServerId`，避免本地换 IP、换测试库或 manager 迁移后旧用户被旧 binding 锁死。端口选择按数据库唯一约束 `(linux_server_id, port)` 在同服务器全局避让所有历史进程行，包含其它容器和非运行态脏数据。
- `UserOpencodeProcessAssignmentService.fileRoutingAffinity` 是文件 WebSocket 路由专用只读入口，只表达文件操作应落到哪台 Linux 服务器，不代表 opencode server 健康可用；Run、初始化、头像状态仍调用 `status` / `initialize` / `requireReadyProcess` 保持强健康检查语义。
- `UserOpencodeProcessAssignmentService` 创建或合成用户 opencode 进程时，session/config 路径读取 `common_parameters.OPENCODE_SESSION_DIR` 和 `common_parameters.OPENCODE_PUBLIC_CONFIG_DIR`；缺失或空白时抛平台错误，不回退环境变量或代码默认路径。初始化时 Java 仍按当前后端已连接的健康容器视图选择进程数最少且有空闲端口的目标容器，然后向该容器对应的 manager 下发 `start`；manager 使用已通过 `configUpdate` 同步的配置路径。`OPENCODE_PUBLIC_CONFIG_DIR` 是否存在且非空只由目标 manager 在所在服务器检查，Java 本机不做 `Files.*` 校验。manager 返回 `errorCode=OPENCODE_UNAVAILABLE` 时，socket gateway 映射为同码平台错误，并提示超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化。
- RunEvent 持久化策略、实时发布和 agent projected messages 恢复。
- Run 终态/取消后的 `session_messages` 快照持久化，包含 assistant 可见 text、完整 message parts 和 token/cost；reasoning/tool output 不拼入回答正文，无 text 的工具步骤以空正文加结构化 parts 保存。
- 从完成态 `write`/`edit`/`apply_patch` tool part 派生运行中 `diff.proposed`，供前端实时追踪文件变化和行数统计。
- Run Diff 查询、接受和拒绝。
- agent runtime 能力映射，包括 catalog/fs/vcs/lsp/mcp、config、provider auth/OAuth、worktree、session share、permission/question 和 MCP auth；opencode 原路径作为当前标准适配形态。
- Model 目录编排：`opencode` 来源保持旧代理；`bailian` 来源直连百炼 `/models` 并把外网 provider 配置同步给 opencode；`internal` 来源读取 `ai_model_configs` 表并按 openclaw 企业 patch 的 `icbc-openai` 兼容配置同步给 opencode。
- PTY terminal ticket、限流、active session registry、进程适配和审计。

## Model 目录配置

`test-agent.model-catalog.source` 控制模型来源：

| source | 行为 |
|---|---|
| `opencode` | 保持旧行为，`/api/model`、`/api/provider` 直接代理 opencode。 |
| `bailian` | 外网测试模式，后端请求 `external.base-url + /models` 获取模型列表；获取失败时回退到配置内置外网模型。 |
| `internal` | 企业内模式，启动时把 openclaw 企业 patch 中的模型清单 seed 到 `ai_model_configs`，接口从数据库读取启用模型。默认模型为 `DeepSeek-V4-Flash-W8A8`。 |

在 `bailian` 和 `internal` 模式下，Run 启动和模型/Provider 目录读取前会尽力 `PATCH /global/config` 到当前 opencode 执行节点，写入 OpenAI-compatible provider、默认模型和请求头配置。provider API Key 优先读取 `test-agent.model-catalog.<external|internal>.api-key`，未配置时回退到 `api-key-env` 指定的环境变量，便于 IDEA 直接启动和脚本启动同时兼容。同步失败只记录告警，Run 仍走原有错误处理路径。

## 测试覆盖

- `RunApplicationServiceTest` 覆盖 Run 创建、通用 binding 保存/复用、远端 session 懒创建/复用、用户进程节点 upsert、用户进程 binding 不一致自动重建、sticky node、prompt parts、终态事件、终态消息快照/token 持久化、reasoning/tool output 与可见正文隔离、瞬态消息事件、tool part 实时 Diff 派生和取消编排。
- `UserOpencodeProcessAssignmentServiceTest` 覆盖未绑定状态、READY 复用、头像菜单未分配/运行中/未运行服务状态、同服务器重建、换 IP 后 fallback 到全局健康容器、端口选择、同服务器历史脏行端口避让、manager 不可用、通用参数 session/config 路径读取、Java 本机公共配置目录不存在时仍按负载策略向目标 manager 下发 `start`、绑定/节点投影，以及本地短路模式（`local-direct=true`）下 `status` / `initialize` / `requireReadyProcess` 完全跳过 database 与 gateway 直返合成 READY 的行为。
- `WorkspaceFileRoutingServiceTest` 覆盖同服务器 workspace 通过 Redis 后端快照路由到目标后端、文件路由不调用阻塞式用户进程健康检查、workspace 与 agent 不同服务器时拒绝、本地旧 `linux_server_id` 安全回绑，以及旧服务器仍在线时不回绑。
- `RuntimeManagementQueryServiceTest` 覆盖运行管理 Redis 快照聚合、manager 下属 opencode server 明细透传、`ACTIVE` 绑定归为有主、无活跃绑定归为无主、归属聚合不受底部用户进程分页/用户名筛选影响、活跃进程过滤、用户名筛选、按用户关键字查询无心跳 STOPPED 进程、健康结果回写 RUNNING 快照、统一认证号定位用户、绑定状态合并、空数据、分钟级时间窗口、容器/后端指标历史降采样，以及同一 `linuxServerId` 下 Java 重启后的服务器指标连续查询。
- `RuntimeManagementCommandServiceTest` 覆盖运行管理按容器和端口委托网关执行重启/停止。
- `ManagerControlMessageCodecTest`、`ManagerControlApplicationServiceTest`、`ManagerConnectionRegistryTest`、`SocketOpencodeProcessManagerGatewayTest`、`BackendJavaProcessLifecycleServiceTest`、`LocalOpencodeProcessManagerGatewayTest`、`OpencodeManagerConfigSyncServiceTest` 覆盖 manager 控制面消息、Redis manager 心跳、后端列表响应及其 `lastHeartbeatAt` RFC3339 字符串编码、连接路由、start/health/restart/stop 命令等待、后端实例心跳、本地 manager-backend 连接自举、local 网关的 HTTP 健康检查与 start 占位，以及 `configRequest` / `configUpdate` 下发完整运行配置、广播和参数过滤。
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
`OpencodeManagerConfigSyncService` 把通用参数表中的 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`）、`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR` 经控制面 WebSocket 下发给已连接 manager：manager 注册成功后立即补推一帧完整 `configUpdate`，manager 收到 `registered` 后也会发送 `configRequest` 拉取同一配置；前端修改上述任一参数触发 `CommonParameterUpdatedEvent`，由 `ManagerConnectionRegistry.broadcast` 推给当前实例持有的所有连接（全互联拓扑下可触达全部 manager）。参数缺失、空白或最大进程数非正整数时不下发可启动配置，manager 保持未 ready 并拒绝启动用户进程；`opencode_containers.max_processes` 仍由 manager heartbeat 回报的生效值同步。
runtime 代理入口有认证用户时必须通过 `AgentRuntimeTargetResolver` 使用用户专属 opencode 进程；无用户主体的 static-token 或本地兼容调用才允许使用固定 `execution_nodes` fallback。

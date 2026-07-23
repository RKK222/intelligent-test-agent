# 安全规范

本规范适用于后端和前端所有安全相关修改，合并原安全规范、日志脱敏规则和 PTY WebSocket 安全例外。

## 鉴权与授权

1. 对外 API 默认需要鉴权，公开接口必须在 API 文档中明确说明。
2. 权限判断应在入口层或应用服务层完成，不散落在 Repository。
3. 前端只做展示和交互控制，不能依赖前端判断作为最终权限控制。
4. 全局角色 `SUPER_ADMIN` 继承 `APP_ADMIN` 的应用配置管理权限，但认证响应仍返回用户实际拥有的角色，不伪造派生角色。
5. 用户物理删除和 TCDS 存量信息同步只能由后端确认的 `SUPER_ADMIN` 执行。删除不得包含当前登录用户；任一目标仍被会话、工作区、运行进程、调度或其它受保护业务记录引用时必须整批拒绝，不能为清账号级联删除业务历史。删除提交前后均应撤销目标用户 Redis 登录 Token，并用 user mutation gate 覆盖关系型事务窗口；TCDS 同步只原位更新姓名和部门，不替换 `userId`、角色或应用成员关系。

认证方式（按优先级）：

1. **用户 Token 鉴权**（`JwtAuthWebFilter`）：所有 `/api/` 请求自动检查 Bearer Token。Token 通过 UUID 生成，存储在 Redis，1 天过期。登录接口 `/api/auth/login` 无需 Token。
2. **静态 API Token 兜底**（`ApiTokenWebFilter`）：未配置用户 Token 时，检查 `TEST_AGENT_API_TOKEN` 环境变量，向后兼容。

Token 校验流程：
- `JwtAuthWebFilter`（Order +10）优先检查用户 Token，有效时设置 `AuthPrincipal` 到请求属性。
- `ApiTokenWebFilter`（Order +20）作为静态 API Token 兜底，未配置时放行。
- opencode runtime 代理可以读取可选 `AuthPrincipal`：存在用户主体时业务层使用用户专属 opencode 进程；用户已有 ACTIVE binding 且属于其他服务器时，API 层只允许把用户进程状态、初始化、Run 启动和 opencode runtime 代理请求转发到 binding 所属服务器 Java，并必须透传原始用户 Authorization 和 traceId，由目标 Java 继续鉴权。只有 static token 或本地放行而没有用户主体时，才允许走固定 `execution_nodes` 兼容 fallback。静态 API token 不得被伪装成用户身份。
- RunEvent SSE 跨 Java 路由必须在鉴权过滤器之后执行，按 Run 原始归属定位生产 Java，并透传原始 `Authorization`、`X-Trace-Id`、`Last-Event-ID` 和 query；目标 Java 收到 `X-Test-Agent-Backend-Routed=true` 后跳过二次路由，但仍执行同一 Controller 和业务校验。
- Run cancel 是跨 Java 写操作，不得仅凭 `X-Test-Agent-Backend-Routed` 跳过生产节点解析，因为该 HTTP 头可由浏览器伪造；每一跳都必须通过 `RunEventSseRouteService.forwardTargetStrict` 重新确认 Run 原始生产服务器和当前被选中的 Java，到达本机 owner 后才允许进入 Controller。
- XXL SSO 票据签发必须使用真实用户 Token 主体并强制 `SUPER_ADMIN`；静态 API token、本地放行或仅前端菜单可见性都不能建立 XXL 用户会话。
- 夜间系统分发只对精确路径 `/api/internal/platform/opencode-runtime/night-execution/internal-dispatch` 豁免普通静态 API token；Controller 必须使用 `MessageDigest.isEqual` 常量时间校验标准 `XXL-JOB-ACCESS-TOKEN`。前缀、子路径和其它夜间 API 不得继承该豁免。

本地占位策略：

- Redis 是系统必需依赖，用户 Token、会话运行上下文、运行心跳、调度锁和运行指标均不提供内存或数据库降级。
- 未配置 `TEST_AGENT_API_TOKEN` 时，`/api/**` 默认放行，便于本地联调。
- 配置 `TEST_AGENT_API_TOKEN` 后，`/api/**` 必须携带 `Authorization: Bearer <token>`。
- 鉴权失败返回统一错误格式，错误码 `UNAUTHENTICATED`，不得回显 token。
- Actuator health 不使用占位 token，生产暴露范围后续单独收敛。

## 会话运行上下文安全

1. `contextToken` 是 256 位安全随机生成的 opaque token，只用于引用后端已解析的可信会话运行上下文，不能替代用户 Bearer Token、权限校验或 Session 归属校验。
2. 上下文必须绑定认证用户、Session、Workspace、agent、用户进程、执行节点、Linux 服务器、可复用远端 session 和后端解析的可信工作区根路径；Run 请求中的用户、agent 和 Session 任一不匹配都按上下文失效处理，客户端不能用请求字段覆盖可信路径或运行归属。
3. 浏览器只可把原始 `contextToken` 保存在当前页面内存，禁止写入 localStorage、sessionStorage、IndexedDB、持久化 Pinia 状态、URL、埋点或错误上报。页面刷新、退出登录或认证用户变化后必须丢弃。
4. Redis token key 只保存原始 token 的 SHA-256 摘要，不得把原始 token 写入 Redis key、value、集合成员或 PostgreSQL。所有上下文 key 使用同一 hash tag，并维护用户+Session、用户、Session、Workspace、进程五类 ZSET 反向索引、资源/全局 generation，以及 Session revoke、user mutation、Workspace mutation gate；索引 score 使用 token 绝对过期时间，保存、续期和失效脚本必须先清理过期成员。`saveIfCurrent`、`resolveForRouting` 和 `touch` 必须校验关联 generation 与全部 gate，禁止失效、归档或 mutation 窗口中的旧快照迟到写入或续期。
5. 权限或可信 Workspace 变更必须用 mutation gate 覆盖整个关系型写入窗口：先建 gate 并失效，数据库成功后原子“再次失效 + 释放自己的 gate token”，数据库失败只释放自己的 token；Redis 完成失败时保留 gate fail-closed。Session revoke 与两类 mutation gate TTL 均为 24 小时，避免异常永久锁死；资源/global generation 不设 TTL，确保 gate 过期后旧 token 仍不能复活。权限变化无法低成本反查 app→Session 时按用户粗粒度失效，不得新增数据库扫描。
6. 签发托管 Workspace 上下文必须在任何历史 Workspace 回绑副作用前实时校验：应用仍启用、当前用户仍为有效成员，个人 Workspace 还必须属于当前用户；`SUPER_ADMIN` 不旁路应用成员规则。找不到托管版本或个人映射的历史 Workspace 沿用 Session owner、Workspace ACTIVE、可信路径及服务器归属校验。自回绑发生后必须放弃当前签发并只使用全新租约完整重读一次，任何其它 CAS 失败不得猜测原因后重试。
7. 每次 context fast path Run 都必须用缓存的完整进程快照调用公共 `OpencodeProcessStatusQueryService.querySnapshot` 动态探测，不能为了零数据库读取而跳过健康检查，也不能重新按 processId 查询 Repository。稳定 `RUNNING` 只能刷新 Redis heartbeat，数据库状态、PID 和服务地址均未变化时不得写库；`STALE` 只拒绝本次 Run 并保留 token，只有公共状态服务明确返回 `NOT_STARTED` 才按进程反向索引失效上下文。
8. 后端 API/Service 日志、请求响应摘要和异常详情必须把 `contextToken` 脱敏；响应该 token 的签发接口也不得由通用日志切面记录明文响应体。前端 HTTP 与 RunEvent SSE 原始输出统一在写入页面缓存前递归脱敏所有层级、大小写不敏感的 `contextToken` 字段，再执行长度截断；不得让 SSE `MessageEvent.data` 绕过同一边界。
9. Redis 不可用时签发或读取上下文返回 `RUNTIME_STATE_UNAVAILABLE`，禁止回退 PostgreSQL、JVM 内存或接受客户端传入的工作区路径/进程快照。
10. start-run 路由层缓存请求体的硬上限为 32 MiB，超限必须在 assignment 或 Controller 查询前返回统一 `VALIDATION_ERROR`；请求 JSON 已出现 `contextToken` 但值为空、非字符串或无效时必须 fail-closed 返回 `CONVERSATION_CONTEXT_EXPIRED`，不得回退无 token 兼容路径。

## Run 运行数据面安全

1. `REDIS_SUMMARY` 的 Redis 详情可能包含完整 prompt、消息/part、工具输入输出、附件内容和运行事件，因此 Redis 必须视为敏感业务数据存储：只允许受控内网访问，生产必须启用 TLS、独立最小权限 ACL 用户和静态/磁盘层加密能力；不得使用无密码公网 Redis、共享 `default` 管理用户或把 Redis 端口暴露给浏览器。
2. 单 Run 的 manifest、input、durable/runtime 双 Stream、snapshot Hash + order ZSET、动态 key registry、scope、dedup 和 pending key 必须使用同一个 `{runId}` hash tag，active 用户/Session/服务器索引只能保存 Run ID 与过期时间。读取 active 索引后必须回读 manifest 校验认证用户、Session、服务器和非终态状态，禁止仅凭可猜测的索引成员跨用户返回运行态。
3. Run durable seq、全事件 runtimeVersion 分配，双 Stream 追加，Hash/ZSET snapshot 投影，manifest 容量计数和动态 key TTL 刷新必须由同 slot Lua 原子执行；durable Stream ID 固定为 `${seq}-0`，runtime Stream ID 固定为 `${runtimeVersion}-0`。脚本、JSON、连接或 manifest 异常统一返回安全的 `RUNTIME_STATE_UNAVAILABLE` / `RUN_DETAILS_EXPIRED`，错误详情和日志不得包含 Redis value、prompt、消息、工具内容、附件或内部连接凭据。
4. 生产 Redis 必须使用 `noeviction` 和 AOF `everysec`，并对容量、AOF、复制、命令延迟、拒绝连接及 `evicted_keys` 告警。单 Run durable/runtime 事件或 snapshot 投影项超过 20,000，或 input + scope + 双 Stream + snapshot 详情超过 32 MiB 时，只允许应用 Lua 显式删除旧 Stream、规范化过大 payload、优先移除低价值投影、保留当前关键物化状态并生成 `run.snapshot.reset`；禁止依赖 LRU/LFU/随机淘汰、Stream 静默裁剪或跨租户 key 清理。
5. `run.snapshot.reset` 只允许携带当前 Run 的物化状态和安全元数据，不设置 SSE `id`，不作为鉴权或续传凭据。Run 详情、取消、Diff、RunEvent SSE 和 Run 级 session-tree 在任何读取或副作用前都必须校验认证用户归属：`REDIS_SUMMARY` manifest 存在时只比较其中的 `userId`，不得为鉴权回查 PostgreSQL；legacy 或 manifest 已过期时才读取 Run 与 Session，并要求所有已记录的 `triggeredByUserId/createdByUserId` 都属于当前用户，归属缺失或不一致一律返回 `FORBIDDEN`。跨 Java 转发后的目标 Controller 必须再次执行同一校验。Redis 新模式 SSE 首帧和容量换代后的 reset 都必须经过该用户/Run 归属校验；前端只清空当前订阅 Run 的 reducer，再按顺序应用 snapshot；未知/空 snapshot 必须安全兼容，不能借 reset 读取其它 Run 或覆盖当前认证/Workspace 上下文。
6. Redis 运行态不可用时新模式必须 fail-closed，禁止把完整输入输出、reasoning、工具内容或原始事件降级写入 PostgreSQL/JVM 内存，也禁止切换活动 Run 的 storageMode。legacy/旧 Run 仅按其创建时模式使用既有数据库恢复，不得通过请求参数伪造模式。
7. `REDIS_SUMMARY` 只允许携带已校验 `contextToken + clientRequestId` 的新请求按 userId 稳定灰度进入；开关默认关闭、rollout 为 0。活动 Run 不得切换模式，回滚只把后续新 Run 比例调为 0。
8. 新模式 PostgreSQL 只允许保存无原文 Run 锚点和终态 USER/ASSISTANT 双摘要。摘要生成必须确定性删除 `<context>`、reasoning、工具输入输出、附件正文、data URL、控制字符、私钥、Bearer/JWT/常见云密钥和 secret 赋值；USER/ASSISTANT 分别限制 512/2000 Unicode 字符，失败只写固定 `FALLBACK`，不得把原文当降级内容。
9. `safe_error_message` 必须经过同一敏感模式清洗并限制长度；任何数据库异常、终态重试或 Redis 故障不得把 prompt、回答、parts、原始事件、Redis value 或第三方响应正文写入 PostgreSQL/日志。稳定 `assistantSummaryMessageId` 只作为平台消息业务 ID，不是鉴权凭据。
10. Run 恢复必须先经过公共后端路由选择并取得 15 秒 owner lease；续租、释放和终态投影必须校验同一 fencing token。dispatch 探测只能使用 Redis 中的可信节点快照查询 OpenCode，会话查询失败或未穷尽统一视为 UNKNOWN，禁止盲目重发 prompt；恢复日志不得记录第三方响应、异常 message 或堆栈中的原始内容。

## 限流

1. 登录、创建 session、发送 message、代理 opencode 请求等高风险接口必须考虑限流。
2. 限流返回统一错误格式和 `429` 状态码，限流 key 不得直接暴露敏感 token。
3. 内存限流只作为本地和测试占位，通过 `test-agent.rate-limit.enabled` 开关启用；生产必须替换为网关或 Redis 等分布式限流，不能依赖单实例内存计数。

## 密钥与配置

1. 除基础 `application.yml` 中经用户明确批准的 XXL 本地开发默认 access token 外，禁止硬编码密钥、token、账号、生产地址；该默认值不是生产凭据，生产必须通过环境变量或配置中心覆盖。其它密钥只能来自环境变量、配置中心或本地安全配置；示例配置必须使用占位值。
2. 日志、错误响应、前端状态不得输出密钥。
3. 后端生产容器只运行 Java 进程；数据库、Redis 和 opencode server 地址必须从外部配置注入，不能写入镜像或仓库。
4. 前端不得把密钥写入源码、localStorage 或可公开构建产物。
5. 个人 Git SSH 私钥必须在浏览器端使用每条记录独立的 AES-256-GCM 临时密钥加密，临时 AES 密钥再用平台 RSA-OAEP/SHA-256 公钥加密后落库。生产 Java 固定加载交付 JAR 内置 `classpath:rsa-private.key`；共享同一数据库的全部 Java 必须部署同一 JAR，禁止使用重启即变化的临时 RSA key。由于交付 JAR/ZIP 包含平台私钥，必须按密钥交付物限制访问、复制和留存；替换内置密钥前必须完成既有 SSH key 迁移或要求用户重新保存。
6. SSH key API 只能返回 `sshKeyId/name/fingerprint/createdAt` 元信息，禁止回显私钥明文或密文。指纹基于规范化私钥内容的 SHA-256 生成。
7. Git SSH 远端命令只允许使用当前登录用户保存的唯一 SSH key。临时私钥文件必须设置最小可行权限并在命令结束后清理；Git 命令环境必须禁用交互式凭据提示。
8. 应用版本工作区、个人工作区和引用资产库的 Git clone/worktree/diff/push/pull/副本同步仍只允许使用当前登录用户保存的唯一 SSH key；不得回退到机器账号、部署用户默认 SSH key 或其他用户 key。托管根目录只允许来自对应通用参数；缺失或空白时必须失败，不能回退到 yml、环境变量或代码默认路径。磁盘目录已存在时只能在校验可信 Git、目标 origin 和干净状态后接管，不得覆盖或删除未知目录。引用资产同分支同步只允许快进；显式分支切换也必须阻断已分叉的目标本地分支，不能用 `checkout -B` 或清理命令强制覆盖。跨服务器副本在任何 fetch/checkout/reset 前必须确认工作树无未提交变更；主动指针核验必须使用不取得 Git optional lock 的只读命令，禁止刷新 index、fetch 或修改工作树。
9. 设置页创建应用工作空间的 `workspace_create_operations.error_message` 只能保存平台安全错误说明或通用失败文案，不得写入 SSH 私钥、token、Authorization、Cookie、完整命令行、完整用户输入或敏感路径片段。
10. opencode-manager 控制面必须使用独立 manager token，配置键为 `test-agent.opencode.manager-control.token` / `TEST_AGENT_OPENCODE_MANAGER_TOKEN`；不得复用用户 JWT、普通 `TEST_AGENT_API_TOKEN` 或 opencode server 密钥。生产环境该 token 必须由环境变量或配置中心注入，示例只能使用占位值。
11. 超级管理员运行管理 API 必须使用用户 JWT，并由后端强制校验 `SUPER_ADMIN`；前端菜单可见性只作为体验优化，不能作为权限边界。manager 心跳中的 `unifiedAuthId` 只允许由现有运行管理 overview 透传和展示，普通用户进程状态、普通错误响应、RunEvent/SSE、监控指标及业务日志不得新增该字段。运行管理归属必须按数据库 binding/process 与 manager 快照关联，禁止从 `startCommand` 解析身份；无平台记录的进程不得自动认领、停止或改绑。
12. XXL SSO 票据 API 必须使用用户 JWT 并由后端强制校验 `SUPER_ADMIN`；票据使用至少 256 位安全随机值、最长 60 秒、Redis `GETDEL` 一次消费，且不得保存原始平台 Token。iframe 只能通过隐藏表单 POST 传票据，禁止 URL/query/hash、浏览器存储、访问日志和错误响应携带票据。JIT 用户以稳定平台用户 ID 唯一，所有 XXL 账号均为管理员展示账号但不得使用本地密码登录；原生登录、改密和账号写入口必须禁用。XXL 会话每次请求校验平台 SHA-256 session marker，平台登出、刷新或过期必须同步失效。周期任务 `GLOBAL_MUTEX` 必须使用现有 Redis 锁和续租，不得回退本机或数据库锁。
13. 普通夜间任务 API 必须使用用户 JWT，owner 只能取认证主体；按 `taskId/sessionId` 查询或变更时必须隔离其他用户。完整 prompt/parts 只允许在 `night_execution_tasks.run_input_json` 的待执行期短期保存，不得写入 XXL 参数/result、跨服务器请求、HTTP 响应、RunEvent、运营分析或日志；普通 Run 锚点受理、取消或最终调度失败时立即清空，数据库 30 天后删除终态行。对外只返回有界 `contentPreview` 和安全错误。目标 Java 必须从共享数据库重读完整任务并重新校验 Session/Workspace 权限，固定目标只能使用任务提交时服务端保存的 `target_linux_server_id`；不得接受客户端覆盖、根据后续 binding 自动迁移、直调 manager gateway或建立夜间专属队列。内部批量请求只允许 `linuxServerId + 1..50 taskId`，使用公共 resolver 选出的精确 backendProcessId 和公共 forwarder、traceId、标准 XXL token、统一防循环 header；同服务器多 JVM 不得按 linuxServerId 本机短路。Run 锚点恢复必须校验来源类型、taskId、owner、Session 和 Workspace，客户端提供的幂等 ID 不能替代归属校验。token、prompt、附件、用户信息和底层异常不得进入日志。夜间容量只能由 `SUPER_ADMIN` 通过既有通用参数管理入口修改，服务端必须在审计和广播前校验正整数；跨服务器刷新 payload 不携带参数值，刷新失败日志不得记录数据库原值或底层敏感错误。
14. JVM 内存通用参数的查询和手工刷新接口必须强制校验 `SUPER_ADMIN`，因为响应会同时暴露数据库加载源值与进程实际生效值；前端入口可见性不能替代后端权限。跨 Java 请求必须按 `backendProcessId` 精确路由并使用统一防循环头。手工刷新不得写参数修改历史或重复发布广播，日志只允许记录脱敏 traceId、进程身份、参数键和结果状态，不得记录源值、内存值、底层异常消息或堆栈。
15. 企业离线完整包中的 MySQL root/应用密码和 XXL access token 必须在打包阶段使用安全随机值生成，只能写入权限为 `0600` 的 `.147` MySQL 节点配置和对应后台节点敏感配置；部署脚本只能按文本解析 dotenv，禁止 `source`、回显或写入普通日志。外层 ZIP 因同时包含这些配置和 JAR 内置 RSA 私钥，必须整体按密钥交付物通过受控 U 盘和企业中转机传递。
15. 内部模型 Token 由外部系统提供，平台不得生成或猜测。仅 `SUPER_ADMIN` 可新增、改名、轮换和删除；API 响应只能返回 `tokenId/name/referencedProviderCount/createdAt/updatedAt`，不得返回明文或密文。`internal_model_tokens.token_value` 继续遵循本系统已确认的明文存储约定，数据库权限、备份和导出必须按密钥数据保护；被 Provider 引用时必须拒绝删除。前端密钥草稿只保存在组件内存，请求完成后立即清空，不得进入浏览器持久化、原始报文或错误提示。刷新广播只携带 traceId 等安全元数据，不携带 Token；Java 仅在一次联表重载时读取明文，并按 Provider ID 保存于不可变内存快照。启用不同 Provider Token 前必须确保全部 Java 节点已经升级。

## 日志脱敏

必须脱敏或禁止记录：

- Authorization、Cookie、API key、用户 Token、内部模型 `token/authToken/tokenValue`、`contextToken`、XXL SSO ticket 和 platform session digest。
- 用户输入中的敏感内容。
- 文件路径中的隐私片段。
- 过大的请求体和响应体。

日志配置必须对可变 message、thread 和 traceId 做 CRLF 编码，避免换行注入伪造日志记录。opencode 节点 health、Redis health、scheduler/XXL 运行日志和 opencode-manager 控制面日志必须避免输出 ticket、token、完整 Authorization header、Cookie、session digest、MySQL 密码、用户输入、完整 prompt 或原始 executor 敏感 payload。前端 `rawExchangeObserver` 和原始输出缓存对 ticket/token/authToken/tokenValue/cookie/password/secret/sessionDigest 做递归、大小写不敏感脱敏后才允许展示。

受管用户 opencode server 的日志文件名包含统一认证号的路径安全编码、UTC 启动时间和端口。`%HH` 编码或“有界前缀 + 完整 SHA-256”只用于避免路径穿越、冲突和文件名超限，不构成匿名化；日志目录必须限制为运维所需的最小访问权限。manager 生命周期日志、错误响应和普通业务日志不得记录原始统一认证号或完整用户日志文件名，身份与 session 路径不一致时只返回通用校验错误。对外工单、日志下载或排障报告必须遮蔽文件名中的身份部分，可保留启动时间、端口、traceId 等低敏关联字段；日志正文仍按上述 token、prompt 和用户输入规则脱敏。

## Web 安全

1. CORS 必须明确允许来源，不使用无限制生产配置。本地默认允许主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E 端口（`localhost`/`127.0.0.1` 的 `3000`、`4173`、`4177`、`4187`、`5173`、`5174`）；局域网 IP 调试必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 或根目录启动脚本追加实际前端 origin；生产环境必须通过配置显式声明允许来源。
2. 安全响应头必须在 `test-agent-api` 的入口配置中统一定义，并由 `test-agent-app` 装配生效。
3. Druid Web 控制台默认关闭；如后续启用，必须通过环境变量配置账号、密码和访问 allowlist，并同步 API、运维和安全文档。
4. 旧 `/api/...`、新 `/api/internal/platform/...` 和 `/api/internal/agent/opencode/...` 共享同一鉴权、限流、CORS、traceId 与错误格式。Workspace 文件 API 和文件 WebSocket RPC 必须把所有请求路径归一化到注册的 workspace root 内，路径穿越或越权访问返回 `FORBIDDEN`。
5. 普通前端不提供本机目录选择或传物理目录创建 Workspace 的入口；应用和个人工作区目录由后端根据通用参数与业务 id 派生。超级管理员服务器工作空间选择器只能通过目标后端签发的文件 WebSocket ticket 浏览目录，创建服务器工作空间时仍由后端校验目标服务器与当前 agent 服务器一致。
6. opencode-manager WebSocket 控制面只允许容器内 manager 使用独立 token 访问，不接受浏览器用户 token；manager 不得通过 HTTP 与 Java 后端交互，也不再连接其他服务器 Java。manager 只连接 `.serverhost + OPENCODE_MANAGER_BACKEND_PORT` 推导出的本服务器 Java，断开后按重连间隔无限重连并重新拉取配置。返回给 Java 间用户进程路由使用的 `listenUrl` 必须是可信内网内其它后端可访问的直连地址，不应暴露到公网或不可信网络。
7. 用户专属 opencode server 默认监听 `0.0.0.0:{port}` 且不设置 Basic Auth，生产必须用容器网络、主机防火墙或内网网关限制端口池访问面；浏览器和外部系统不得直接访问这些端口。
8. `tools/verify-opencode-process-deployment.sh` 只用于只读 smoke check；传入的 manager token 和 `SUPER_ADMIN` 用户 token 不会由脚本打印。生产执行时应使用临时 shell、禁用命令历史或通过安全变量注入，避免 token 留在 history 中。
9. 应用引用资产库状态中的 `repositoryPath` 只能由服务端使用当前平台 `OPENCODE_REFERENCES_DIR` 和已校验版本库英文名派生，并且只通过既有 `APP_ADMIN` 接口返回；参数缺失或历史名称非法时返回空。客户端输入不得控制该路径，日志、trace 和错误消息不得记录该物理路径。
10. `X-Test-Agent-Linux-Server-Id` 只能作为 Nginx 首跳性能提示，不能作为鉴权、binding、Session 归属或运行上下文事实源。Nginx 必须通过静态 `linuxServerId -> Java endpoint` 白名单映射，禁止把头值直接拼成地址；缺失或未知值回退默认 upstream。代理给 Java 前必须删除该头，并清除外部传入的 `X-Test-Agent-Backend-Routed`，后者只允许公共 Java→Java 转发器产生。前端只在页面内存保存 binding ID，仅对用户 OpenCode、会话、Run、SSE 和本地工作区请求发送；登录和共享控制面不得被该提示固定到用户节点。CORS 可允许该头，但目标 Java 仍必须重新执行完整鉴权和归属校验。
11. XXL Admin 只允许经同源 `/xxl-job-admin/` iframe 访问；响应必须包含 `Content-Security-Policy: frame-ancestors 'self'`、`X-Frame-Options: SAMEORIGIN`，会话 Cookie 必须为 `HttpOnly; Secure; SameSite=Lax` 并限制 Path。Nginx/Vite 必须保持同源和路径前缀，不能通过放宽 frame/Cookie 策略解决代理错误。
12. XXL MySQL 密码和生产 access token 必须从外部配置注入，所有 Java/Admin/executor 使用同一生产 access token；基础配置中的默认 token 仅供本地开发，生产漏配会继承该值并形成已接受但必须由部署检查阻断的风险。executor 端口只对可信 Admin 网络开放。Admin/MySQL health 不进入平台 readiness，但必须独立告警。

## 平台文件 WebSocket 安全例外

工作区文件与 Agent 配置文件操作属于受控 WebSocket 例外。前端不得直连 opencode server 或任意文件服务，必须先通过平台后端解析目标服务器，再使用目标后端的一次性 ticket 建立 WebSocket。实现和后续扩展必须满足：

1. `file-ws-route` 必须基于当前登录用户的 opencode 进程解析目标后端，并强校验 `workspace.linuxServerId == opencodeProcess.linuxServerId == targetBackend.linuxServerId`；历史 `workspace.linuxServerId` 为空时只能在 root path 校验成功后回填。托管工作区在 route、workspace ticket 签发和每一条 `workspace.*` RPC 都必须实时校验当前用户仍是有效应用成员，`SUPER_ADMIN` 不旁路成员关系，不能依赖 ticket 签发时缓存的成员状态；非托管 Workspace 默认拒绝文件访问，仅服务器工作空间兼容链路可依据当前登录角色向 `SUPER_ADMIN` 放行，并把该角色写入 ticket 供每条 RPC 复核。
2. Agent 配置文件必须通过 `agent-config/file-ws-route` 按 `scope/workspaceId/worktreeId/linuxServerId` 解析目标后端；公共 worktree 使用落库 `linuxServerId`，公共直接模式必须由前端传入已初始化公共配置服务器 ID。
3. ticket 只能通过用户登录态创建，短期过期、一次性消费，并绑定 workspace、目标服务器、当前 agent 服务器、模式、Agent 配置 scope/worktree、traceId 和是否 `SUPER_ADMIN`；不得把长期 Bearer token 放入 WebSocket URL。
4. WebSocket upgrade 必须校验 Origin 白名单、ticket 有效性和 ticket 模式；ticket 消费后无论连接成功与否都不能重复使用。
5. 所有 `workspace.*` 操作必须绑定 ticket workspace，路径必须归一化在 workspace root 内；`rename` 只允许同一父目录内的普通文件或目录改名，目录树删除不跟随符号链接并拒绝根目录和任意层级 `.git`。`workspace.view.list/read/read.chunk` 的 locator 只能表达逻辑来源，后端必须从当前工作区最新 JSONC 重建允许挂载，重新校验当前应用关联、`APPLICATION_ASSET_REPOSITORY`、总体及本机副本 READY、当前平台 `OPENCODE_REFERENCES_DIR`、SDD 根目录白名单和路径安全，禁止接收物理路径或 repositoryId。引用内容只能读取，单引用错误以不含物理路径的局部 warning 返回。渐进读取每段都必须重新校验 ticket、成员关系、逻辑 locator 和文件快照，不能把首次解析出的物理路径保存在客户端。
6. `agent-config.list/read/read.chunk/write/upload.*/rename/delete` 必须绑定 ticket scope、workspaceId 和 worktreeId；读取允许登录用户，公共配置写入、上传、重命名和删除校验 `SUPER_ADMIN`，应用配置对应变更校验 `APP_ADMIN`（`SUPER_ADMIN` 继承）。分片上传的 begin/chunk/complete/abort 必须在同一 WebSocket 连接内复用 workspace-management 的分片顺序、声明大小、不覆盖和越界校验，每个分片继续复核 ticket 绑定和权限；应用上传还必须限制在 `opencode.jsonc`、`agents/**`、`skills/**` 白名单。上传总大小不设应用层上限，但单分片必须有界，完成前只能写隐藏临时文件，取消、失败和连接关闭必须清理；改名只允许同目录文件，删除文件或目录树必须复用 root 归一化、根目录/`.git` 保护与不跟随符号链接语义。
7. 应用 `.opencode/**` 与普通文件共用版本个人 worktree 时，个人 worktree 的 `commit/publish` HTTP 入口也必须对规范化文件白名单执行 `APP_ADMIN` 校验；不能只依赖前端 Tab 或 Agent 文件 WebSocket 权限。`spec/**` 禁止发布的服务层规则继续对所有角色生效。
8. `directory.list` 只允许 `directory-picker` ticket；跨服务器目录浏览仅 `SUPER_ADMIN` 可创建 ticket，普通用户只能浏览当前 agent 同服务器目录。
9. `workspace.create` 必须要求 `SUPER_ADMIN`，并且选择服务器与当前 agent 服务器一致；不一致时前端禁用输入，后端仍必须返回 `CONFLICT` 或 `FORBIDDEN`。
10. 日志和错误响应不得输出 ticket、Authorization、Cookie、完整用户输入、完整文件内容或敏感路径片段；审计只记录 traceId、workspaceId、worktreeId、服务器 ID、操作类型、路径摘要和错误码等必要字段。

## 服务器广播安全

后端内部服务器广播用于跨后端实例同步业务状态，不是浏览器 API 或 SSE。实现和后续扩展必须满足：

1. 广播 payload 只允许包含业务 ID、事件原因、服务器 ID、版本号、分支名、目标 commit hash 和 traceId 等必要字段；禁止携带 SSH 私钥、token、Authorization、Cookie、文件内容、完整用户输入或大段错误堆栈。
2. Redis pub/sub 仅作为同一可信后端集群内的实时增强通道；生产必须使用受控内网 Redis，并通过外部配置开启 `test-agent.server-broadcast.enabled=true`，不得在代码或示例中硬编码 Redis 密码或生产地址。
3. 消费端必须跳过本服务器来源事件，并在业务层做幂等校验；广播失败不能影响本机已完成的 Git/数据库主流程，漏消息由数据库目标 commit 与本机补偿扫描恢复。
4. 日志只记录 `eventId`、`type`、`traceId`、`versionId`、`linuxServerId` 和错误码等低敏字段，不能输出私钥、token、完整路径中的敏感片段或原始第三方错误详情。

## PTY WebSocket 安全例外

交互式 PTY 终端属于受控 WebSocket 例外。当前已落地后端 ticket、Origin、cwd workspace root 归一化、单次使用、每 session 单 active PTY、ticket 创建限流、input/resize 限速、审计、idle/hard timeout、输出截断和前端 terminal panel。实现和后续扩展必须满足：

1. 先通过 HTTP API 创建一次性 ticket，再使用 ticket 建立 WebSocket；不得直接以长期 Bearer token 暴露在 WebSocket URL 中。
2. workspace ticket 必须绑定 session、workspace、execution node；服务器 ticket 必须绑定 linuxServerId 和发起用户。两类 ticket 均绑定 traceId、过期时间且只能使用一次。
3. cwd 必须归一化在 workspace root 内，shell 必须走后端白名单；在白名单配置完成前，前端不得覆盖 shell。
4. WebSocket upgrade 必须校验 Origin、ticket、session/workspace 归属和限流。
5. input/output 审计日志默认只记录长度、事件类型、截断、退出码和必要状态，不记录完整终端内容。
6. input、resize、output buffer、idle timeout 和 hard timeout 必须有明确上限。
7. 断开连接、session abort、后端关闭或超时时必须清理 PTY 进程。
8. 服务器终端的应用级默认值必须关闭；获批的企业交付模板可在同时配置 WSS 定向网关时显式启用。终端仅允许 `SUPER_ADMIN`，每次连接都展示目标服务器二次确认，并由后端严格校验 `SERVER@{linuxServerId}` 目标绑定值；PTY 必须直接继承启动目标 Java 的操作系统用户和权限，禁止 `sudo`、切换用户、SSH 密码、私钥或其它额外提权。
9. 正式环境的服务器终端只能返回 `wss://` 网关地址，网关必须按 `linuxServerId` 定向到签票 JVM；仅本地 `test` profile 可显式允许直连 `ws://`。shell 使用不含 Java 进程密钥的最小环境，审计不得记录命令和输出正文。默认配色只能通过当前 Java 用户创建的随机临时 rcfile 注入，不得写入用户主目录、系统 shell 配置或全局 Git 配置；兼容加载用户已有 `.bashrc` 时不得改变其文件内容。

ticket 创建与 WebSocket 协议细节见 `docs/api/http-api.md`。

## 安全变更文档

鉴权、限流、CORS、密钥、日志脱敏变更必须同步 `docs/standards/security.md`、`docs/api/http-api.md` 和相关 README。

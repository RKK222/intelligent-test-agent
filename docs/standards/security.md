# 安全规范

本规范适用于后端和前端所有安全相关修改，合并原安全规范、日志脱敏规则和 PTY WebSocket 安全例外。

## 鉴权与授权

1. 对外 API 默认需要鉴权，公开接口必须在 API 文档中明确说明。
2. 权限判断应在入口层或应用服务层完成，不散落在 Repository。
3. 前端只做展示和交互控制，不能依赖前端判断作为最终权限控制。
4. 全局角色 `SUPER_ADMIN` 继承 `APP_ADMIN` 的应用配置管理权限，但认证响应仍返回用户实际拥有的角色，不伪造派生角色。
5. 用户物理删除、手工用户名修正和 TCDS 存量信息同步只能由后端确认的 `SUPER_ADMIN` 执行。删除不得包含当前登录用户；任一目标仍被会话、工作区、运行进程、调度或其它受保护业务记录引用时必须整批拒绝，不能为清账号级联删除业务历史。删除提交前后均应撤销目标用户 Redis 登录 Token，并用 user mutation gate 覆盖关系型事务窗口；手工修正只允许更新唯一用户名，统一认证号保持只读，且不得修改 `userId`、角色、部门或业务关系。用户名不属于权限边界，因此手工修正不撤销 Token，旧认证主体中的显示名到下次登录才刷新；TCDS 同步仍可覆盖手工用户名。TCDS 同步只原位更新姓名和部门，不替换 `userId`、角色或应用成员关系。
6. 统一认证首次建号必须在同一短事务内写入用户与最小权限 `USER` 角色，默认角色字典缺失时失败关闭，禁止先生成无角色用户再依赖定时补偿。存量空角色账号不允许在登录时静默提权，只能由 `SUPER_ADMIN` 通过用户管理筛选并手工确认目标角色；角色替换沿用 user mutation gate，并在事务前后撤销目标用户全部平台 Token，使旧认证主体立即失效。批量角色入口的操作者必须取后端认证主体，禁止客户端指定；按筛选全选时必须在数据库侧排除当前操作者，显式目标包含当前操作者时整批拒绝。关系型角色替换必须全有或全无，单次上限 5000；Token 撤销按整批执行，不能因性能优化省略事务前后两次失效。
7. 本地客户端版本管理 API 每次实时校验 `SUPER_ADMIN`；普通用户更新入口只能操作当前主体拥有的在线实例，并在执行前重新校验通知接收人、实例所有权、generation 与有效策略。前端不可把通知 `actionTargetId` 解释为 URL，也不可自行推断目标版本或下载地址。

### 本地客户端凭据、认证与制品

- 平台以安全随机数生成 `tack_v1_` Client key，创建/重置时只显示一次：只有
  `CredentialView.revealAvailable=true` 才允许调用 copy；首次成功后立即消费展示资格，同一版本再次 copy 返回
  `409 CONFLICT` 且不得再解密。存量凭据升级后视为已展示，必须轮换才可再次查看。客户端不得生成 Key。首次 enroll
  或 Key 失效时，用户在交互终端输入统一认证号和隐藏 Key；Key 不得进入命令行、环境变量、URL、日志、持久化浏览器
  存储、可恢复缓存或错误。管理页只允许在一次性内存/DOM 中展示，弹窗关闭或页面离开后立即清理。
- 服务端按 Key 定位用户后精确校验统一认证号、凭据和用户状态；任何失败统一返回“本地客户端认证失败”，不泄露
  哪一项不匹配。当前认证限流是按可信代理解析后的来源 IP、每分钟 5 次的单 Java 节点内存窗口；多 Java 节点会
  放大配额，生产全局限流须另由 Redis 或可信网关实现，不能把当前实现宣传为全局配额。
- 用户凭据在 release 目录外，配置目录必须为 `0700`、凭据文件必须为 `0600`。普通重启、更新、回退和自动回切
  复用相同凭据和 `clientInstanceId`，不再次提示输入。
- HTTP 仅是制品传输层：客户端下载后必须先验签 manifest，再校验可信 Host/受限相对路径、大小及 SHA-256。私钥
  仅保留在外网 Mac 构建环境；用户包内安装脚本、客户端和平台只携带同一信任公钥。release 目录不可原地覆盖，catalog 必须最后
  发布，避免客户端发现未完整或未签名的 release。

认证方式（按优先级）：

1. **用户 Token 鉴权**（`JwtAuthWebFilter`）：所有 `/api/` 请求自动检查 Bearer Token。平台 Token 是无格式承诺的随机 opaque string，存储在 Redis v2 命名空间并在 1 天后过期。只有精确 `/api/auth/login` 与 `/api/auth/login-by-unified-auth` 无需 Token。
2. **静态 API Token 兜底**（`ApiTokenWebFilter`）：未配置用户 Token 时，检查 `TEST_AGENT_API_TOKEN` 环境变量，向后兼容。

Token 校验流程：
- `JwtAuthWebFilter`（Order +10）优先检查用户 Token，有效时设置 `AuthPrincipal` 到请求属性。
- `ApiTokenWebFilter`（Order +20）作为静态 API Token 兜底，未配置时放行。
- AAM 兑换入口必须先通过 `AamLoginTokenVerifier` 调用固定 `/aam/checkLogin` 验真，再查询/创建用户并签发独立平台 Token；AAM Token 禁止写入 Redis、日志或响应。401/403/业务拒绝收敛为 `UNAUTHENTICATED`，网络、协议、响应超限或非法响应收敛为 `EXTERNAL_API_UNAVAILABLE`。
- AAM 基础地址只接受无 user-info、路径、query、fragment 的 HTTP/HTTPS origin；连接/请求默认上限为 3 秒/5 秒，响应上限 64 KiB，不自动重试。日志与异常不得包含 AAM Token、用户号、URL 或正文。
- 浏览器 AAM 回调只接受唯一 `userId + token`，必须先通过 `history.replaceState` 清除 `userId/token/SSIAuth/SSISign`，再清旧平台认证并兑换。只允许在 `sessionStorage` 保存返回的平台 Token；AAM 拒绝重新登录，其它异常进入无自动跳转的安全错误页。
- 企业 Nginx 的 SPA history fallback 必须关闭访问日志并返回 `Referrer-Policy: no-referrer`，避免短暂回调 query 进入日志或 Referer。
- opencode runtime 代理可以读取可选 `AuthPrincipal`：存在用户主体时业务层使用用户专属 opencode 进程；用户已有 ACTIVE binding 且属于其他服务器时，API 层只允许把用户进程状态、初始化、个人重启、Run 启动和 opencode runtime 代理请求转发到 binding 所属服务器 Java，并必须透传原始用户 Authorization 和 traceId，由目标 Java 继续鉴权。只有 static token 或本地放行而没有用户主体时，才允许走固定 `execution_nodes` 兼容 fallback。静态 API token 不得被伪装成用户身份。
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

## 通用长期记忆安全

1. 浏览器只能访问 `/api/internal/platform/memory/v1/**`。Java 到 memory-service 使用至少 32 字节的 `X-Memory-Service-Key`，该 key 只认证调用服务，不能替代登录用户、白名单、owner、Application 成员或角色校验；前端、URL、日志、错误正文和镜像层不得得到该 key。
2. Mem0 到 Java 模型网关只允许固定 `/chat/completions` 与 `/embeddings` HMAC 入口。签名必须覆盖方法、固定路径、body SHA-256、client/user/run/session/operation、timestamp、nonce、capability 和 embedding input type；默认时钟偏差 30 秒，nonce 在 Redis 中原子消费并保留 2 分钟。缺失、过期、重放、body/身份不一致统一失败关闭；记忆开关启用但 HMAC secret 不合规时 Java 必须启动失败，不能延迟到首个回调才暴露错误。
3. HMAC 只允许管理设置中的固定 CHAT 模型，以及已配置企业 Embedding 或固定 CPU profile；调用者不能提交供应商、上游 URL或 Token。Java 到 CPU 服务复用内部模型供应商 API key，不把 key 转交 Mem0。模型网关错误不得回显供应商 URL、凭据、prompt、answer 或原始响应。
4. 原始 USER/ASSISTANT 只允许从现有 Session 事实源瞬时进入一次 `Mem0.add(messages,infer=true)` 请求；memory-service 不保存 message history，readiness 必须报告 `rawMessageCount=0`。metadata 任意层级禁止 `messages/transcript/rawConversation/prompt/answer/assistantMessage/userMessage`；记忆库、outbox、容器文件和日志不得建立原始对话副本。
5. 平台证据只保存有界摘要和 `sessionId/sessionTitle/runId`。授权记忆查看者可见标题和 ID，但完整 transcript 仍由既有 `/s/{sessionId}` owner 校验控制；团队成员、`APP_ADMIN` 或 `SUPER_ADMIN` 都不能仅凭记忆权限旁路 Session owner。`transcriptAvailable` 必须由后端实时派生，前端隐藏链接不是授权边界。记忆正文保存原文且不做语义封禁，但注入 Run system context 时必须按不可信数据处理并转义提示词容器边界，正文不能闭合 `<long_term_memory>` 或伪造结构节点。
6. 个人原生记忆默认只属于当前用户与 Application；手工提升全局必须由 owner 发起。团队记忆只能由当前成员手工提交并由 `APP_ADMIN` 审核，自动学习不得写团队 scope。成员退出后团队查询、审核和 Run 注入立即失效。
7. 不同 embedding model/dimension/fingerprint 必须使用不同 collection。逻辑版本、幂等、投影和 outbox 全部在独立共享 PostgreSQL；Mem0 副本只读根文件系统、无本地数据 mount。数据库账号、service key、HMAC secret、CPU API key 分别最小授权，不能复用平台用户 Token。
8. Run 前记忆检索总预算 2 秒，所有 profile 不可用时 fail-open 为空记忆，不得为可用性放宽鉴权、使用其它用户数据、本地缓存正文或直连模型。fail-open 日志只记录 traceId、Run ID、耗时、profile 身份和安全错误码。
9. `/health` 只作 liveness；业务和发布门禁必须使用带 key 的 `/ready`，同时验证共享库、profile、投影积压和 `rawMessageCount=0`。管理健康数据不返回数据库连接串、内网模型地址或密钥。
10. 企业配置文件必须是 `0600` 非符号链接普通文件；构建与发布脚本不得 `source` 敏感 dotenv、回显 secret 或使用 `latest` 镜像。BGE 权重只在外网构建阶段下载并通过模型身份清单锁定，企业运行时禁止访问 Hugging Face。

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
5. `run.snapshot.reset` 只允许携带当前 Run 的物化状态和安全元数据，不设置 SSE `id`，不作为鉴权或续传凭据。普通 Run 详情、取消、Diff、RunEvent SSE 和 Run 级 session-tree 在任何读取或副作用前都必须校验认证用户归属：`REDIS_SUMMARY` manifest 存在时只比较其中的执行所属人，不得为鉴权回查 PostgreSQL；legacy 或 manifest 已过期时才读取 Run 与 Session，并要求所有已记录的 `triggeredByUserId/createdByUserId` 都属于当前用户。分享请求则必须额外解析独立代操作上下文，并同时匹配 share/version/actor/execution owner/session/workspace 和操作权限；不能把实际消息发送人归因当成普通归属。跨 Java 转发后的目标 Controller 必须再次执行同一校验。Redis 新模式 SSE 首帧和容量换代后的 reset 都必须经过该用户/Run 归属或分享范围校验；前端只清空当前订阅 Run 的 reducer，再按顺序应用 snapshot；未知/空 snapshot 必须安全兼容，不能借 reset 读取其它 Run 或覆盖当前认证/Workspace 上下文。
6. Redis 运行态不可用时新模式必须 fail-closed，禁止把完整输入输出、reasoning、工具内容或原始事件降级写入 PostgreSQL/JVM 内存，也禁止切换活动 Run 的 storageMode。legacy/旧 Run 仅按其创建时模式使用既有数据库恢复，不得通过请求参数伪造模式。
7. `REDIS_SUMMARY` 只允许携带已校验 `contextToken + clientRequestId` 的新请求按 userId 稳定灰度进入；开关默认关闭、rollout 为 0。活动 Run 不得切换模式，回滚只把后续新 Run 比例调为 0。
8. 新模式 PostgreSQL 只允许保存无原文 Run 锚点和终态 USER/ASSISTANT 双摘要。摘要生成必须确定性删除 `<context>`、reasoning、工具输入输出、附件正文、data URL、控制字符、私钥、Bearer/JWT/常见云密钥和 secret 赋值；USER/ASSISTANT 分别限制 512/2000 Unicode 字符，失败只写固定 `FALLBACK`，不得把原文当降级内容。
9. `safe_error_message` 必须经过同一敏感模式清洗并限制长度；任何数据库异常、终态重试或 Redis 故障不得把 prompt、回答、parts、原始事件、Redis value 或第三方响应正文写入 PostgreSQL/日志。稳定 `assistantSummaryMessageId` 只作为平台消息业务 ID，不是鉴权凭据。
10. Run 恢复必须先经过公共后端路由选择并取得 15 秒 owner lease；续租、释放和终态投影必须校验同一 fencing token。dispatch 探测只能使用 Redis 中的可信节点快照查询 OpenCode，会话查询失败或未穷尽统一视为 UNKNOWN，禁止盲目重发 prompt；恢复日志不得记录第三方响应、异常 message 或堆栈中的原始内容。

## 运营分析数据安全

1. ClickHouse 只允许保存运营计数、业务 ID、用户/组织归属快照、Token 数、Agent/Skill/Tool 名和调用状态。禁止保存 prompt、用户/assistant 正文、reasoning、附件内容、工具输入输出、反馈评论、密钥、Token 单价或费用。
2. PostgreSQL `analytics_event_outbox` 是与业务写同事务的临时投递记录，ClickHouse 确认接收后必须删除；Redis 运营 stream 必须与 Run key 同槽、设置有限 TTL，并只保留白名单字段。两者均不得成为第二份会话正文存储。
3. 运营 API 只允许 `SUPER_ADMIN`，服务端仍必须鉴权；前端菜单隐藏不是权限边界。ClickHouse 故障时返回统一 `ANALYTICS_UNAVAILABLE`，不得为可用性回退扫描 PostgreSQL 原始消息或 RunEvent。
4. ClickHouse 使用独立最小权限账号，端口只向平台 Java 节点开放；密码从受控环境配置注入，不得出现在仓库、日志、错误、URL、前端构建物或普通运维命令历史。离线包包含随机密码时按 `0600` 密钥交付物管理。
5. 用户与组织归属按事件发生时快照保存；历史回填无法恢复事件时归属时，使用当前主数据并显式标记 `CURRENT_ORG_BACKFILL`。页面和导出不得把这种近似归因伪装成历史精确快照。

## OpenCode Trace 正文安全

1. prompt、reasoning、用户/assistant 正文和 Tool/Skill 输入输出只允许进入服务器不可变 Trace 归档；PostgreSQL、ClickHouse、Redis、RunEvent、应用日志和审计正文均不得保存。ClickHouse 只保留目录与 span 元数据。
2. 插件在进入服务端或本地 spool 前必须递归脱敏 Authorization、Cookie、私钥、JWT、云密钥及明显的 secret/env 赋值；二进制只记录类型、大小、SHA-256 和既有内容引用，不复制正文。脱敏失败按不完整处理，不能把原文降级写日志。
3. 服务端插件令牌必须短期且绑定用户、进程、服务器、generation 和 expiry，不得复用 manager/platform token。本地插件只持有随机 loopback relay token，与模型 relay grant 隔离，不能直接持有平台凭据或操作 WSS。
4. Trace 归档路径只能由服务器从严格校验的 opaque ID 生成；拒绝客户端路径、分隔符和目录穿越。首次接收冻结 owner 节点，跨 Java 只复用公共 resolver/forwarder，不扫描路由快照、不本机降级。
5. Trace 列表、详情、正文和下载必须由后端强制 `SUPER_ADMIN`。每次正文查看、单条下载和失败尝试记录 actor、目标用户、Trace、动作、结果、时间和请求 traceId；审计不记录正文、摘要正文、物理路径或凭据。不提供批量正文导出。
6. 服务端插件只使用绑定用户、进程、服务器、generation 和过期时间的专用 HMAC 令牌；TTL 可配置，默认 7 天且只允许 1 分钟至 30 天。令牌过期、进程重启或数据库 generation 改变都必须拒绝，不能降级接受普通平台 Token。
7. 本地未确认 spool 使用用户私有目录和原子文件，服务器 ACK 前不得删除、按时间清理或显示上传成功。空间/队列耗尽时停止采集并标记 `INCOMPLETE`，优先保证对话；禁止丢弃未上传数据后伪造完整状态。

## 会话协作分享安全

1. 分享链接必须使用至少 256 位安全随机 `shareId`，但 shareId 不是登录凭据。每个请求都必须先校验真实 Bearer Token 对应的有效 `AuthPrincipal`，再校验所属人/成员、分享状态、有效期、版本和精确 Session/Workspace；禁止匿名访问、仅凭 URL 访问或把 shareId 写入 Cookie/本地持久化认证状态。
2. 必须使用独立 `DelegatedOperationContext`，绝不替换、包装成所属人或覆盖真实 `AuthPrincipal`。上下文至少绑定真实 actor/统一认证号/用户名、执行所属人、share/version/session/workspace、`canChat`、有效期和 ownerAccess；所有 OpenCode、进程、Workspace、Git/SSH 操作只显式使用执行所属人，所有平台归因和审计只使用真实 actor。
3. 分享授权严格限定一个 Session 及其创建时绑定的 Workspace，且上限为所属人当前真实权限。Workspace 改绑、会话归档、所属人停用、分享取消/过期、成员移除都必须 fail-closed；被分享人不需要成为工作区成员，但不能切换应用/Workspace、持久 fork、管理分享/会话、置顶、设置、Agent 配置、源码/Hub、系统管理或服务器终端。
4. 只读成员只允许会话/消息/Run/SSE、文件树/正文、状态和 Diff 读取。`canChat=true` 才允许发送、文件写入、当前工作区 Git、工作区终端、command/shell、compact/revert、permission/question 回复、反馈和定时任务。服务端必须在每个入口判定，不能依赖前端隐藏按钮。
5. 停止 Run 只允许所属人或该 Run 的实际消息发送人，发送人降为只读后仍可停止。撤回并重新发送只允许最后 USER 消息的实际发送人；分享发送人必须仍有 `canChat=true`，包括会话所属人在内的其它用户不得改写他人的消息。请求可携带修改后的 `editedPrompt`，但只能写入既有有限 TTL 的 Redis 精确重放输入，不得进入控制表、RunEvent、审计或日志；原轮附件及其它结构化 part 必须由服务端从可信远端用户轮次恢复，不能相信前端重建。定时任务创建/改期要求 `canChat`，所属人和实际创建人可管理，降为只读的创建人只允许取消。
6. 单 Session 发送必须同时取得 Redis 原子 active-session 占用和 PostgreSQL `runs.active_session_id` 唯一准入；任一失败都必须在发布 RunEvent、写可见消息或调用 OpenCode 前退出并返回 `SESSION_BUSY`。终态清空占用，存储异常 fail-closed，不允许降级 JVM 锁或 busy follow-up queue。
7. 分享范围敏感读取和写入必须记录 actor、执行所属人、share/session/workspace/resource、结果、traceId；路径只能保存 SHA-256 摘要。审计禁止正文、明文路径、Token、终端输入、命令输出和第三方响应，默认保留 365 天。分享管理成功/失败和访问拒绝同样需要审计。
8. `X-Test-Agent-Session-Share` 只允许跨 Java 公共 forwarder/SSE forwarder按原值透传，目标 Java 必须重新鉴权且仍使用所属人的进程路由；禁止自行扫描 Redis、使用本机降级、信任防循环头放行或新增 Java→Java 文件代理。CORS 只把该头加入受控允许列表，反向代理和日志必须脱敏。
9. 分享 runtime SSE、RunEvent SSE、文件 WebSocket 与 PTY 必须周期和/或逐操作刷新授权；版本变化、降权、移除、取消、到期后关闭旧连接并记录拒绝。授权失效不自动取消已经启动的 Run，也不能让旧连接继续产生副作用。
10. 分享创建的定时任务必须固化授权快照，后续分享失效仍使用所属人的 Workspace/OpenCode 执行并保留原创建人归因。这是明确的延迟代操作授权，UI 必须在取消分享时提示待执行任务；任务快照不能被复用为其它 Session/Workspace 的访问凭据。

## 用户站内通知安全

1. 通知列表、已读和 SSE 必须使用当前用户 Bearer Token，接收人只取服务端 `AuthPrincipal.userId`。按通知 ID 已读还必须同时匹配接收人；不存在和不属于当前用户统一返回 `NOT_FOUND`，不得泄露其它用户通知是否存在。
2. 通知动作只允许枚举化 `action_type + action_target_id`。`SESSION_SHARE` 的目标只能是 `shareId`，浏览器自行映射到同源 `/s/{shareId}`；`NONE` 不执行动作；`RESTART_OWN_PROCESS` 只允许与 dispose 失败类型组合并调用当前认证用户的进程重启 API，目标只保存 rolloutId 且不得参与进程或路由选择。数据库、HTTP 和 SSE 均禁止保存或下发任意外部 URL、javascript/data scheme 或客户端自报跳转地址；未知类型或动作必须失败关闭。
3. `shareId` 仍不是认证凭据。通知点击不能提前取得分享权限或标记已读；只有 `/s/{shareId}` 完成真实登录用户、分享、成员、版本、有效期、Session/Workspace 和所属人校验后，才按接收人及 shareId 幂等已读。从通知、“分享给我”或旧链接进入必须走同一服务端入口。
4. 标题和摘要只允许会话标题、发送人展示名、只读/可对话权限或平台固定的 dispose 状态文案；禁止消息正文、文件名/路径、Prompt、Token、终端输入/输出、第三方响应和异常堆栈。通知同步失败日志只记录低敏业务 ID 与 traceId，不回显标题、摘要或链接；已读同步失败不得阻断已经成功的分享访问或进程重启。
5. 未读和可点击状态必须实时合并通知、分享、成员、会话、所属人和到期事实，不能只信任历史通知行。撤销、移除、归档、到期或所属人停用后立即视为不可用且不计未读，即使失效通知回写或广播暂时失败也不能恢复授权。
6. 通知变化只能在数据库事务提交后发布。用户级 SSE 只发送变化类型、可选通知 ID、未读数和生成时间；正文必须重新走当前用户分页 API。每 25 秒 heartbeat、30 秒数据库校准和断线重连都必须重新使用当前认证，SSE `id` 不是续传或授权凭据。
7. 通知保存 90 天后由定时任务删除。迁移回填只允许仍有效的分享；只有发生在成员当前 `shared_at` 之后的成功 `READ_ACCESS_GRANTED` 审计可回填已读，过期、撤销、移除、归档或所属人停用数据不得回填。

## 限流

1. 登录、创建 session、发送 message、代理 opencode 请求等高风险接口必须考虑限流。
2. 限流返回统一错误格式和 `429` 状态码，限流 key 不得直接暴露敏感 token。
3. 内存限流只作为本地和测试占位，通过 `test-agent.rate-limit.enabled` 开关启用；生产必须替换为网关或 Redis 等分布式限流，不能依赖单实例内存计数。
4. 问题排查入口不接收部署侧共享暗号，因此不存在“暗号尝试次数”这一条独立限流规则；授权签发、目标选择和只读查询仍必须经过平台统一限流。生产网关可按登录用户与接口组设置分布式频率和并发上限，但不得把登录 Token 或排查授权 Token 明文作为限流键或日志字段。

## 密钥与配置

1. 除基础 `application.yml` 中经用户明确批准的 XXL 本地开发默认 access token 和下述仅局域网可达的 TCDS 地址外，禁止硬编码密钥、token、账号、生产地址；XXL 默认值不是生产凭据，生产必须通过环境变量或配置中心覆盖。其它密钥只能来自环境变量、配置中心或本地安全配置；除该 TCDS 局域网地址外，示例配置必须使用占位值。
2. 日志、错误响应、前端状态不得输出密钥。
- 经用户确认，TCDS 是仅在企业局域网可达的例外端点：默认 yml 固定为 `http://tcds-prod.sdc.icbc:9080`，并允许 `TEST_AGENT_TCDS_BASE_URL` 以 HTTP/HTTPS 绝对地址覆盖。业务接口和 TCDS 同源文档请求统一携带非敏感 `toolId: 66f36bfa5c1c6105572b0118880261d6`；文档重定向到跨域对象存储后不得透传该 header。文档 URL 只能取后端重新查询的 TCDS 响应，并限制协议、超时、重定向和下载容量。token、签名参数、URL 与响应正文不得进入正式日志或浏览器响应。
- 工作区普通响应不得返回物理根路径。绝对文件路径只允许通过文件 WebSocket 在用户点击后按单个相对文件即时解析，并拒绝越界、符号链接、分享、支持访问、体验空间和源码快照。文件 WebSocket 必须在 upgrade 前非消费式校验 ticket，实际 upgrade 再原子消费；无效、过期和复用对外统一为 401。
3. 后端生产容器只运行 Java 进程；数据库、Redis 和 opencode server 地址必须从外部配置注入，不能写入镜像或仓库。
4. 前端不得把密钥写入源码、localStorage 或可公开构建产物。
5. 个人 Git SSH 私钥必须在浏览器端使用每条记录独立的 AES-256-GCM 临时密钥加密，临时 AES 密钥再用平台 RSA-OAEP/SHA-256 公钥加密后落库。生产 Java 固定加载交付 JAR 内置 `classpath:rsa-private.key`；共享同一数据库的全部 Java 必须部署同一 JAR，禁止使用重启即变化的临时 RSA key。由于交付 JAR/ZIP 包含平台私钥，必须按密钥交付物限制访问、复制和留存；替换内置密钥前必须完成既有 SSH key 迁移或要求用户重新保存。
6. SSH key API 只能返回 `sshKeyId/name/fingerprint/createdAt` 元信息，禁止回显私钥明文或密文。指纹基于规范化私钥内容的 SHA-256 生成。
7. Git SSH 远端命令只允许使用当前登录用户保存的唯一 SSH key。临时私钥文件必须设置最小可行权限并在命令结束后清理；Git 命令环境必须禁用交互式凭据提示。
8. 应用版本工作区、个人工作区和引用资产库的 Git clone/worktree/diff/push/pull/副本同步仍只允许使用当前登录用户保存的唯一 SSH key；不得回退到机器账号、部署用户默认 SSH key 或其他用户 key。托管根目录只允许来自对应通用参数；缺失或空白时必须失败，不能回退到 yml、环境变量或代码默认路径。磁盘目录已存在时只能在校验可信 Git、目标 origin 和干净状态后接管，不得覆盖或删除未知目录。引用资产同分支同步只允许快进；显式分支切换也必须阻断已分叉的目标本地分支，不能用 `checkout -B` 或清理命令强制覆盖。跨服务器副本在任何 fetch/checkout/reset 前必须确认工作树无未提交变更；主动指针核验必须使用不取得 Git optional lock 的只读命令，禁止刷新 index、fetch 或修改工作树。
   引用资产库终止必须要求调用方提交实际观察到的 generation，并在同一事务中使该代次未完成副本失去租约；禁止仅关闭前端轮询、无 fencing 地复用旧 worker，或用迟到页面终止更新的代次。跨 Java 中断只传递代码库 ID、generation 和 traceId，不得在广播、错误或日志中携带 Git 原始 stderr、凭据或物理目录。
   版本选择前的只读 Git 权限预检允许在单个 Java 内缓存成功结果最多 10 分钟，但每次请求仍必须实时校验应用成员关系并重新读取当前仓库和 SSH key 元数据。缓存键必须绑定用户、版本库、有效 URL 摘要及 SSH key ID/指纹，不得保存或记录私钥明文；URL 或 key 身份变化必须立即重检，失败结果不得缓存，容量必须有明确上限。远端仓库成员权限可在 SSH key 不变时被直接撤销，因此不能使用永久缓存；该短缓存也不能替代真正 clone/fetch/push 等 Git 操作自身的远端鉴权。
9. 设置页创建应用工作空间的 `workspace_create_operations.error_message` 只能保存平台安全错误说明或通用失败文案，不得写入 SSH 私钥、token、Authorization、Cookie、完整命令行、完整用户输入或敏感路径片段。
   企业 SCM 右控姓名不一致报文属于个人信息证据：期望姓名、实际姓名、邮箱和原始 stderr 只能在当前 Git 命令内存中用于严格匹配，不得进入错误 details、正式日志、XXL 参数/result 或前端响应。持久化只保存按 `user_id` 关联的 Git 姓名、证据来源、可空提交哈希和时间；定时任务结果只允许输出仓库数、失败仓库数、检查用户数和更新用户数。
10. opencode-manager 控制面必须使用独立 manager token，配置键为 `test-agent.opencode.manager-control.token` / `TEST_AGENT_OPENCODE_MANAGER_TOKEN`；不得复用用户 JWT、普通 `TEST_AGENT_API_TOKEN` 或 opencode server 密钥。生产环境该 token 必须由环境变量或配置中心注入，示例只能使用占位值。
11. 超级管理员运行管理 API 必须使用用户 JWT，并由后端强制校验 `SUPER_ADMIN`；前端菜单可见性只作为体验优化，不能作为权限边界。manager 心跳中的 `unifiedAuthId` 只允许由现有运行管理 overview 透传和展示，普通用户进程状态、普通错误响应、RunEvent/SSE、监控指标及业务日志不得新增该字段。运行管理归属必须按数据库 binding/process 与 manager 快照关联，禁止从 `startCommand` 解析身份；无平台记录的进程不得自动认领、停止或改绑。
12. XXL SSO 票据 API 必须使用用户 JWT 并由后端强制校验 `SUPER_ADMIN`；票据使用至少 256 位安全随机值、最长 60 秒、Redis Lua 原子读删一次消费，且不得保存原始平台 Token。iframe 只能通过隐藏表单 POST 传票据，禁止 URL/query/hash、浏览器存储、访问日志和错误响应携带票据。JIT 用户以稳定平台用户 ID 唯一，所有 XXL 账号均为管理员展示账号但不得使用本地密码登录；原生登录、改密和账号写入口必须禁用。XXL 会话每次请求校验平台 SHA-256 session marker，平台登出、刷新或过期必须同步失效。Cookie 必须保持 `HttpOnly`、`SameSite=Lax` 和受限 Path，`Secure` 默认开启；仅当受控企业内网明确无法提供 HTTPS 时，才允许通过受审部署配置显式关闭 `Secure`，并在 HTTPS 可用后恢复。周期任务 `GLOBAL_MUTEX` 必须使用现有 Redis 锁和续租，不得回退本机或数据库锁。
13. 普通定时任务 API 必须使用用户 JWT；普通入口 owner 取认证主体，分享入口 owner 取会话所属人并另存真实 creator 与授权快照。按 `taskId/sessionId` 查询或变更时必须隔离无关用户，所属人和 creator 权限按“会话协作分享安全”执行。`ADMIN_CUSTOM` 创建和改期必须由后端根据真实认证主体强制校验 `SUPER_ADMIN`，分享上下文、前端入口可见性、请求中的模式值或历史创建人身份均不能替代；权限被移除后只允许取消，不允许继续改期。模式权限和自定义时间边界必须先于幂等锁、Session、会话锁、任务和容量写入校验，伪造请求不得留下副作用。完整 prompt/parts 只允许在 `night_execution_tasks.run_input_json` 的待执行期短期保存，不得写入 XXL 参数/result、跨服务器请求、HTTP 响应、RunEvent、运营分析或日志；普通 Run 锚点受理、取消或最终调度失败时立即清空，数据库 30 天后删除终态行。对外只返回有界 `contentPreview`、调度模式和安全 actor 归因。目标 Java 必须从共享数据库重读完整任务并重新校验固定 Session/Workspace 范围，固定目标只能使用任务提交时服务端保存的 `target_linux_server_id`；不得接受客户端覆盖、根据后续 binding 自动迁移、直调 manager gateway或建立夜间专属队列。内部批量请求只允许 `linuxServerId + 1..50 taskId`，使用公共 resolver 选出的精确 backendProcessId 和公共 forwarder、traceId、标准 XXL token、统一防循环 header；同服务器多 JVM 不得按 linuxServerId 本机短路。Run 锚点恢复必须校验来源类型、taskId、owner、Session 和 Workspace，客户端提供的幂等 ID 不能替代归属校验。token、prompt、附件、用户信息和底层异常不得进入日志。夜间容量只能由 `SUPER_ADMIN` 通过既有通用参数管理入口修改，服务端必须在审计和广播前校验正整数；`ADMIN_CUSTOM` 不得预留、释放或读取夜间容量。跨服务器刷新 payload 不携带参数值，刷新失败日志不得记录数据库原值或底层敏感错误。
14. JVM 内存通用参数的查询和手工刷新接口必须强制校验 `SUPER_ADMIN`，因为响应会同时暴露数据库加载源值与进程实际生效值；前端入口可见性不能替代后端权限。跨 Java 请求必须按 `backendProcessId` 精确路由并使用统一防循环头。手工刷新不得写参数修改历史或重复发布广播，日志只允许记录脱敏 traceId、进程身份、参数键和结果状态，不得记录源值、内存值、底层异常消息或堆栈。
15. 企业离线完整包中的 MySQL root/应用密码和 XXL access token 必须在打包阶段使用安全随机值生成，只能写入权限为 `0600` 的 `.147` MySQL 节点配置和对应后台节点敏感配置；部署脚本只能按文本解析 dotenv，禁止 `source`、回显或写入普通日志。外层 ZIP 因同时包含这些配置和 JAR 内置 RSA 私钥，必须整体按密钥交付物通过受控 U 盘和企业中转机传递。
15. 内部模型 Token 由外部系统提供，平台不得生成或猜测。仅 `SUPER_ADMIN` 可新增、改名、轮换和删除；API 响应只能返回 `tokenId/name/referencedProviderCount/createdAt/updatedAt`，不得返回明文或密文。`internal_model_tokens.token_value` 继续遵循本系统已确认的明文存储约定，数据库权限、备份和导出必须按密钥数据保护；被 Provider 引用时必须拒绝删除。前端密钥草稿只保存在组件内存，请求完成后立即清空，不得进入浏览器持久化、原始报文或错误提示。刷新广播只携带 traceId 等安全元数据，不携带 Token；Java 仅在一次联表重载时读取明文，并按 Provider ID 保存于不可变内存快照。启用不同 Provider Token 前必须确保全部 Java 节点已经升级。企业 AI 上游的 `Authorization: Bearer <供应商关联 Token>` 只能完成鉴权，不能让 `ucid` 生效；平台必须用 `Auth-Token: <供应商关联 Token>` 调用上游，并覆盖客户端提供的两种供应商鉴权头。OpenCode → Java 内部代理的 Bearer Key 是独立边界，不得转发到上游或与供应商 Token 混用。

## 外部 API Key 与 SSH Key 安全边界

TCDS 案例维护是固定目标的服务端集成，不属于可配置外部 API Key：

1. 浏览器只能通过 `backend-api` 调用 `/api/internal/platform/integration/tcds/task-types` 和 `/api/internal/platform/integration/tcds/test-cases`，不得直连 TCDS HTTP 地址，避免跨域、mixed content 和客户端暴露固定工具标识。
2. 任务类型与案例维护地址必须相对统一 `TEST_AGENT_TCDS_BASE_URL` 解析，所有 TCDS 同源请求的 `toolId` 必须由 `test-agent-integration` 共享请求构造器注入；设计方法和其它固定业务字段由服务端组装，不接受 URL、请求头、固定字段或 `userId` 客户端覆盖，禁止复用旧 `/api/proxy/call` 形成 SSRF/任意代理。
3. 任务类型查询只向浏览器返回经过数量、长度、控制字符、分隔符和重复值校验的 `subItemTypes.name/value`，不得透传完整上游响应，也不得在失败时降级为可能过期的前端快照、代码内固定输出或简称映射；所有 Spring profile（包括 `local`）都必须真实调用任务类型接口。
4. `userId` 只取当前 `AuthPrincipal.unifiedAuthId`；案例提交前必须再次实时查询 TCDS，逐项校验并原样使用当前返回的完整 `name`，多值只接受英文逗号分隔。`createGraphCase` 上游请求日志必须把 `userId` 完全删除且不得记录固定 `toolId`；案例 `name/step/data/expect/dataDependencies` 只允许记录长度和 SHA-256 短摘要，安全枚举和固定字段可保留。响应日志只允许记录 HTTP 状态、有界业务 `code/msg` 及 `data` 类型、数量和摘要，禁止记录 `data` 原值、未知响应正文或非法 JSON 原文。请求预览最多 20 条案例，单条安全报文日志最大 8 KiB；所有日志携带 traceId。
5. 后端 HTTP client 禁止跟随重定向，并设置连接/请求超时与响应体上限；案例维护上游非零业务码只允许返回受限安全消息，任务类型查询和网络、协议、解析错误统一收敛为平台错误，不暴露地址、响应正文或异常堆栈。

1. `/api/external/v1/**` 必须由独立外部认证过滤器强制认证；只有精确 `/api/external/v1` 根及其 `/` 子路径可以绕过旧用户 JWT 和静态 `TEST_AGENT_API_TOKEN` 过滤器，相邻路径不得继承。用户 Bearer Token、Cookie、静态 Token 或前端菜单都不能替代 `X-Test-Agent-Tool-Code` 与 `X-Test-Agent-Api-Key`。
2. API Key 只能由平台使用 32 字节安全随机数生成，格式固定为 `taak_v1_` 加无填充 Base64URL。数据库只保存 RSA-OAEP/SHA-256 密文、SHA-256 指纹和掩码提示；认证在 JVM 不可变快照中按 `toolCode` O(1) 查询，并用 `MessageDigest.isEqual` 常量时间比较。未知、停用和错误 Key 必须统一为 `UNAUTHENTICATED`，禁止泄露工具存在性或启用状态。
3. 新建、查看和轮换只允许实时 `SUPER_ADMIN`，响应必须 `no-store/no-cache`；列表不得返回明文或数据库密文。前端明文只允许存在于当前弹窗组件内存，请求结束后清除 mutation 数据，关闭或卸载立即清空，禁止写 localStorage/sessionStorage、TanStack Query cache、URL、原始交换观察器或错误提示。
4. 外部 SSH 私钥成功响应必须按 `docs/api/external-api.md` 的 TAEK1 协议使用 API Key 派生的 AES-256-GCM 密钥加密，并把工具编码、统一认证号和 traceId 全部纳入 AAD。私钥明文只允许存在于后端方法局部变量和调用方受控处理过程；HTTP 正文、错误、日志、广播、数据库新表和监控均不得出现明文。用户不存在、停用或未配置 Key 使用同一 404，旧加密格式使用安全 409。
5. 管理事务提交后只能广播空 payload 的 `external-api-credential.refresh-requested`；各 Java 必须自行整表读取、完整解密校验并原子替换快照。启动加载失败必须阻止实例就绪，运行期刷新失败保留上一份有效快照；60 秒补偿刷新只用于收敛漏广播。
6. Header 按已确认契约以明文传输，不做应用层二次加密；该例外只允许受信内网服务端调用。截获 API Key 的攻击者同时能冒用请求和解密响应，因此生产必须依赖网络隔离和链路保护，不开放浏览器 CORS Header，网关按工具/来源限流并屏蔽公网路由。应用层不新增单机限流。
7. 共享同一数据库的全部 Java 必须使用同一交付 JAR 内的 `classpath:rsa-private.key`，并共享 Redis、开启服务器广播。滚动升级必须先完成数据库 migration 和全部 Java 升级，确认所有注册表已加载后才开放外部路由；仍有旧 Java 时不得启用外部调用。

## 日志脱敏

必须脱敏或禁止记录：

- 本地客户端 `tack_v1_` client key、其数据库密文、模型 grant、本地随机模型 token、`client.key` 内容和
  WebSocket REGISTER payload。设置页只显示掩码；copy 明文只能存在于后端方法局部变量和前端剪贴板
  写入局部变量，禁止进入 DOM、Query cache、local/session storage、原始报文观察器或错误消息。
- 本地绝对根路径不得进入普通审计、运行事件、指标或错误响应；审计只保存 SHA-256 root/path digest。
  客户端 reported IP、observed address 和端口是状态信息，不得用作可信路由或授权依据。
- client key 轮换/撤销必须同时 fencing 该用户所有连接和模型 grant。连接、文件 ticket、模型 grant、
  HTTP/SSE 请求都必须绑定 `clientInstanceId + backendProcessId + connectionGeneration`，不允许跨代复用。客户端托盘
  主动发送 `WORKSPACE_REGISTER` 时，userId 和 clientInstanceId 只能取自已认证连接状态，载荷只接受工作区名称与
  待验证绝对路径；服务端必须继续通过 FILE_REQUEST 安全内核校验和注册根目录，不能信任客户端声明或允许伪造身份。
- 生产控制面默认只允许 HTTPS/WSS。当前企业 `mimo.sdc.cs.icbc:9996` 是已批准的可信内网 HTTP/WS 例外，
  客户端包与 Java 必须同时显式开启 `allowInsecureControl`，且不得把该例外扩大到公网或未知网段。OpenCode 只能绑定 loopback，平台模型 key 永不下发；OpenCode 仅持有
  随机本地 token，后台 grant 必须短 TTL 且可立即撤销。客户端 key 文件必须是当前用户所有的 `0600`，
  禁止命令行参数和环境变量传 key。
- 后台默认只允许直接 HTTPS/WSS URI，或信任代理源 IP 清单内的连接携带 `X-Forwarded-Proto: https|wss`；禁止
  无条件信任客户端可伪造的 forwarded header。企业部署必须显式维护 Nginx 源 IP；当前固定 HTTP 现场允许在
  两台 Java 显式设置 `TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true`，入口升级 TLS 后必须恢复 `false`。
- 内网 HTTP 下载不使用 client key。stable 清单必须签名，版本化运行制品的 SHA-256 必须进入签名清单；打包私钥
  不得进入仓库、企业 ZIP、Nginx 目录或客户端。麒麟只交付普通用户级 `tar.gz`：包内静态 ARM64 启动器会校验随包
  安装脚本和图标摘要，安装脚本再验证 JAR、JDK、OpenCode 和公共能力制品的平台 RSA 签名；包不写 `/usr` 或
  `/var/lib/dpkg`，不得调用 sudo/dpkg，也不生成 DEB。用户包自身不具备麒麟系统包签名身份，经明文 HTTP 获取时，
  主动中间人仍可能整体替换启动器和资源，因此必须依赖网络 ACL、可信 HTTPS 或带外固定 SHA-256；包内摘要不能被
  表述为操作系统代码签名。
- 本地客户端相关功能默认隐藏，只允许 `SUPER_ADMIN` 通过受认证管理 API 按已存在且可登录的 userId 加入
  灰度名单。普通用户通过兼容路径 `download-access/me` 仅接收 `allowed` 布尔值；缺字段、请求失败、存储
  异常和值为 false 均隐藏下载、实例状态、本地工作区和个人客户端设置。实例 capability 仅作向后兼容，
  不能覆盖独立灰度结论，避免跨 Java 滚动升级时旧节点导致入口闪现后消失。
  灰度名单不替代 Nginx ACL、制品签名或 client key 认证，前端显示与否不得作为任何后端授权依据。
- 本地客户端托盘不得展示 prompt、绝对路径、请求体、client key 或模型 grant。日志下载只能由用户本机
  主动触发，只允许读取 state 日志目录中受控命名的客户端日志，限制文件数和单文件字节数，并排除配置、
  OpenCode 日志及工作区内容；导出失败不得退化为打包整个 state 或 config 目录。
- 本地浏览器只能由当前用户的本地客户端监管：使用独立持久 profile、可见窗口、随机 loopback CDP 端口和随机本地 relay token，
  禁止连接用户日常浏览器 profile、绑定非 loopback 地址或把 CDP/token 发送到平台。Tool 按 HTTP(S) origin 授权，未知跨 origin
  主文档跳转在发出请求前阻断；提交、上传、下载不得使用永久授权。上传只允许当前工作区内不超过 100 MiB 的非符号链接普通
  文件；下载和截图只写入当前工作区的显式产物目录。模型输出不得包含 Cookie、认证 token、密码/input value、完整 HTML 或
  截图字节；浏览器 stdout/stderr 不写入可导出日志。
- 受保护 Agent/Skill 正文、系统提示词和编排只允许在服务器不可变制品与单 Run 模型上下文中出现，不得进入
  Agent 目录响应、本地客户端配置目录、WSS 注册/心跳、RunEvent、审计正文或浏览器缓存。只要向用户电脑
  下载完整正文，就不能声称用户不可读取；签名只能检出篡改，不能提供保密性。
- 受保护文件 MCP grant 必须至少 32 字节随机，Redis/数据库只允许保存必要映射而不得保存 grant 明文；当前
  实现仅在签发 Java 有界内存保存 SHA-256 指纹。每次工具调用重新校验 Run 未终态、userId、Workspace、
  clientInstanceId、backendProcessId、generation 和 root digest，任一不一致立即删除授权并返回统一未认证。
- 服务器 OpenCode 的受保护目录必须与用户本地绝对路径分离，原生 bash/read/write/edit/glob/grep/task 等能力
  默认关闭。本地文件只能通过现有 WSS `FILE_REQUEST` 和安全内核的相对路径操作；MCP 不得新增任意 HTTP
  文件代理、终端或任意操作名。写入、移动、重命名和删除继续受 OpenCode permission 结果约束。
- 受保护 MCP Controller 的通用请求日志只记录 JSON-RPC method/id/params 是否存在，响应只记录 result/error
  是否存在。Authorization、文件路径、写入正文、读取结果和 Skill 资源不得序列化到 API 日志；错误只返回
  稳定平台消息，不能回显底层路径或文件内容。

- Authorization、Cookie、API key、`X-Test-Agent-Api-Key`、用户 Token、内部模型 `token/authToken/tokenValue`、`contextToken`、`grantToken`、`ciphertext/encryptedApiKey/privateKey`、`X-Test-Agent-Session-Share`/shareId、`X-Support-Access-Grant`、XXL SSO ticket 和 platform session digest；一次性凭据作为 URL path 参数时只记录固定路由形状。
- 用户输入中的敏感内容。
- 文件路径中的隐私片段。
- 过大的请求体和响应体。

长密文、私钥信封和其它大字段的日志脱敏必须使用线性、有界且不会递归回溯的实现；禁止让日志摘要处理因正则栈溢出或超量回溯中断业务请求。脱敏必须先于日志长度截断，确保敏感值不会因截断边界而残留。

日志配置必须对可变 message、thread 和 traceId 做 CRLF 编码，避免换行注入伪造日志记录。opencode 节点 health、Redis health、scheduler/XXL 运行日志和 opencode-manager 控制面日志必须避免输出 ticket、token、完整 Authorization header、Cookie、session digest、MySQL 密码、用户输入、完整 prompt 或原始 executor 敏感 payload。前端 `rawExchangeObserver` 和原始输出缓存对 ticket/token/authToken/tokenValue/grantToken/supportAccessGrant/cookie/password/secret/sessionDigest 做递归、大小写不敏感脱敏后才允许展示。

受管用户 opencode server 的日志文件名包含统一认证号的路径安全编码、UTC 启动时间和端口。`%HH` 编码或“有界前缀 + 完整 SHA-256”只用于避免路径穿越、冲突和文件名超限，不构成匿名化；日志目录必须限制为运维所需的最小访问权限。manager 生命周期日志、错误响应和普通业务日志不得记录原始统一认证号或完整用户日志文件名，身份与 session 路径不一致时只返回通用校验错误。对外工单、日志下载或排障报告必须遮蔽文件名中的身份部分，可保留启动时间、端口、traceId 等低敏关联字段；日志正文仍按上述 token、prompt 和用户输入规则脱敏。

企业进程日志诊断归档必须只读、限时、限文件数、限每来源行数和限归档总大小；先删除已识别的凭据、URL query、
私钥块、prompt/message/tool payload、用户 home/workspace 路径片段，再写入归档。受管 OpenCode 日志不得保留
含统一认证号的原文件名，只能使用摘要索引；不得采集 dotenv、Docker inspect 环境、数据库/Redis 数据、manager
`processes/*.json` state 或原始完整用户进程日志。即使完成上述处理，归档仍按敏感业务材料以 mode `0600` 通过受控
企业渠道传递，并遵循事件留存和删除要求；关键词摘要只能作为排查线索，不能自动定性为缺陷。

## Web 安全

1. CORS 必须明确允许来源，不使用无限制生产配置。本地默认允许主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E 端口（`localhost`/`127.0.0.1` 的 `3000`、`4173`、`4177`、`4187`、`5173`、`5174`）；局域网 IP 调试必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 或根目录启动脚本追加实际前端 origin；生产环境必须通过配置显式声明允许来源。
2. 安全响应头必须在 `test-agent-api` 的入口配置中统一定义，并由 `test-agent-app` 装配生效。
3. Druid Web 控制台默认关闭；如后续启用，必须通过环境变量配置账号、密码和访问 allowlist，并同步 API、运维和安全文档。
4. 旧 `/api/...`、新 `/api/internal/platform/...` 和 `/api/internal/agent/opencode/...` 共享同一鉴权、限流、CORS、traceId 与错误格式。Workspace 文件 API 和文件 WebSocket RPC 必须把所有请求路径归一化到注册的 workspace root 内，路径穿越或越权访问返回 `FORBIDDEN`。
5. 普通前端不提供本机目录选择或传物理目录创建 Workspace 的入口；应用和个人工作区目录由后端根据通用参数与业务 id 派生。个人工作区新建和 default 显式确保/修复必须先校验当前用户已有 READY TestAgent 进程，并只在 ACTIVE binding 路由到的服务器执行；未初始化或健康失败时必须 fail-closed，禁止按入口 Java 本机归属落盘。超级管理员服务器工作空间选择器只能通过目标后端签发的文件 WebSocket ticket 浏览目录，创建服务器工作空间时仍由后端校验目标服务器与当前 agent 服务器一致。
6. opencode-manager WebSocket 控制面只允许容器内 manager 使用独立 token 访问，不接受浏览器用户 token；manager 不得通过 HTTP 与 Java 后端交互，也不再连接其他服务器 Java。manager 只连接 `.serverhost + OPENCODE_MANAGER_BACKEND_PORT` 推导出的本服务器 Java，断开后按重连间隔无限重连并重新拉取配置。返回给 Java 间用户进程路由使用的 `listenUrl` 必须是可信内网内其它后端可访问的直连地址，不应暴露到公网或不可信网络。
7. 用户专属 opencode server 默认监听 `0.0.0.0:{port}` 且不设置 Basic Auth，生产必须用容器网络、主机防火墙或内网网关限制端口池访问面；浏览器和外部系统不得直接访问这些端口。
8. `tools/verify-opencode-process-deployment.sh` 只用于只读 smoke check；传入的 manager token 和 `SUPER_ADMIN` 用户 token 不会由脚本打印。生产执行时应使用临时 shell、禁用命令历史或通过安全变量注入，避免 token 留在 history 中。
9. 应用引用资产库状态中的 `repositoryPath` 只能由服务端使用当前平台 `OPENCODE_REFERENCES_DIR` 和已校验版本库英文名派生，并且只通过既有 `APP_ADMIN` 接口返回；参数缺失或历史名称非法时返回空。客户端输入不得控制该路径，日志、trace 和错误消息不得记录该物理路径。
10. `X-Test-Agent-Linux-Server-Id` 只能作为 Nginx 首跳性能提示，不能作为鉴权、binding、Session 归属或运行上下文事实源。Nginx 必须通过静态 `linuxServerId -> Java endpoint` 白名单映射，禁止把头值直接拼成地址；缺失或未知值回退默认 upstream。代理给 Java 前必须删除该头，并清除外部传入的 `X-Test-Agent-Backend-Routed`，后者只允许公共 Java→Java 转发器产生。前端只在页面内存保存 binding ID，仅对用户 OpenCode、会话、Run、SSE 和本地工作区请求发送；登录和共享控制面不得被该提示固定到用户节点。CORS 可允许该头以及排查专用 `X-Support-Access-Grant`，但目标 Java 仍必须重新执行完整鉴权和归属校验。
11. XXL Admin 只允许经同源 `/xxl-job-admin/` iframe 访问；响应必须包含 `Content-Security-Policy: frame-ancestors 'self'`、`X-Frame-Options: SAMEORIGIN`，会话 Cookie 必须为 `HttpOnly; Secure; SameSite=Lax` 并限制 Path。Nginx/Vite 必须保持同源和路径前缀，不能通过放宽 frame/Cookie 策略解决代理错误。
12. XXL MySQL 密码和生产 access token 必须从外部配置注入，所有 Java/Admin/executor 使用同一生产 access token；基础配置中的默认 token 仅供本地开发，生产漏配会继承该值并形成已接受但必须由部署检查阻断的风险。executor 端口只对可信 Admin 网络开放。Admin/MySQL health 不进入平台 readiness，但必须独立告警。

## 超级管理员问题排查只读访问

该能力只用于受控内网故障排查，不实现身份冒用或共享后门。工作台全局手势“2 秒内连续按 3 次 Shift”只为实时 `SUPER_ADMIN` 展示入口，不是认证因素；手势使用捕获阶段监听以覆盖会阻止冒泡的父页面控件，但 iframe 内的键盘事件不会跨文档冒泡到父页面，跨源 iframe 也无法由平台补充监听。系统不读取部署侧“激活暗号”，也没有可查询或可配置的暗号值。

1. actor 始终是当前登录用户，必须在签发和每次 HTTP/文件 RPC 时重新校验平台登录会话、用户可登录状态和数据库实时 `SUPER_ADMIN` 角色；target 只决定查询范围，不能替换 `AuthPrincipal`、签发目标用户 Token 或继承目标用户写权限。
2. 开启访问必须携带后端提供的排查单号、排查原因、5–240 分钟时长并确认只读约束。当前没有权威工单数据源时后端每轮生成新的 `sai_` 单号，禁止从历史授权循环回填；同一平台登录会话同时最多一个有效授权，新签发原子淘汰旧授权；显式关闭、过期、登出、停用或移除角色后立即失败关闭。
3. `grantToken` 必须使用至少 32 字节安全随机值，只在签发响应返回一次并仅保存在当前页面组件内存。后续请求使用 `X-Support-Access-Grant`，不得写入 URL、localStorage、sessionStorage、Pinia、日志或错误正文；Redis 和 PostgreSQL 只保存 SHA-256 摘要或授权元数据，不保存明文。授权签发的权威时间必须先归一化到 PostgreSQL `timestamp` 的微秒精度，再同时写入 Redis payload 和关系库；校验仍要求两侧授权 ID、会话摘要和到期时间严格一致，禁止用容差窗口掩盖存储精度差异。
4. 可读范围只包括目标用户的 ACTIVE 个人工作区，以及由该用户创建会话、触发 Run 或发送消息所归因到的 ACTIVE 工作区/会话；只有排查会话列表和正文入口显式传 `includeArchived=true` 时可读取同一归因范围内的 ARCHIVED 软删除会话，内部 `SIDE_QUESTION` 始终排除。普通 Workspace、Session 和文件入口始终按当前 actor 自身归属校验，`SUPER_ADMIN` 不得用普通接口旁路排查授权与审计。
5. 对话正文复用既有历史恢复、首页 Session tree reducer/时间线和保留策略，并显式返回 `FULL/SUMMARY/LEGACY`、回放可用性和详情保留时间；空 Redis/OpenCode 快照不得阻断关系库兜底。工作区权威 Java 后端非 ONLINE 时必须跳过不可达 OpenCode，只读持久化来源；`LEGACY` 只能有界读取既有 `session_messages` 原文且必须标记不可完整回放，不得建立第二份消息镜像、延长正文保留或提供批量导出。用户视角仍是只读投影，不得挂载发送、permission/question 回复或其它真实用户写入口。排查页可以展示并复制会话业务 ID 与事件 Trace ID 以关联日志，但不得把 grantToken、Bearer、文件正文或路径明文混入该上下文栏。
6. 文件访问只允许 `workspace.list/search/read/read.chunk/read.binary.chunk`，每条 RPC 都重新校验授权、实时角色、目标用户工作区归属和权威服务器。工作区列表可用公共路由快照标记 `ONLINE/OFFLINE/UNBOUND/UNKNOWN`，非 ONLINE 状态必须禁用页面文件入口；无论页面状态如何，后端均不得重绑 `linuxServerId`、降级本机或绕过权威路由。`.opencode` 命名空间、写入、上传、删除、重命名、复制/移动、Git、终端、Agent 配置、加入对话和下载/批量导出全部拒绝；浏览器隐藏控件不能替代后端白名单。
7. 目标切换、授权签发/撤销、每次会话/工作区/文件读取及失败结果必须在响应正文返回前落库审计；审计失败时正文不得返回。审计保存 actor/target 快照、排查单号、原因、动作、资源业务 ID、traceId、结果、IP 和 User-Agent/文件路径摘要，不保存消息或文件正文、路径明文和任何 Token。
8. 审计向所有实时 `SUPER_ADMIN` 开放，保留 365 天后由定时任务清理。删除用户只将关联外键置空，actor/target 用户名和排查单号快照继续用于追溯；目标用户不接收自动通知。

## 平台文件 WebSocket 安全例外

工作区文件与 Agent 配置文件操作属于受控 WebSocket 例外。前端不得直连 opencode server 或任意文件服务，必须先通过平台后端解析目标服务器，再使用目标后端的一次性 ticket 建立 WebSocket。实现和后续扩展必须满足：

1. `file-ws-route` 必须基于当前登录用户的 opencode 进程解析目标后端，并强校验 `workspace.linuxServerId == opencodeProcess.linuxServerId == targetBackend.linuxServerId`；历史 `workspace.linuxServerId` 为空时只能在 root path 校验成功后回填。应用源码 Runtime Workspace 例外地以数据库当前 active generation 的 READY replica 作为精确目标服务器，历史 Workspace 信息不得触发本机回绑或本机降级；目标 Java 继续由公共 `BackendJavaRouteResolver` 选择，入口转发只用 `BackendHttpForwarder`，文件内容不经过 Java→Java HTTP 代理。托管工作区在 route、workspace ticket 签发和每一条 `workspace.*` RPC 都必须实时校验当前用户仍是有效应用成员，`SUPER_ADMIN` 不旁路成员关系，不能依赖 ticket 签发时缓存的成员状态；签票授权必须在同一次权威读取/判断中返回 `STANDARD/APP_SOURCE` 分类并写入不可伪造的短期 ticket，APP_SOURCE 票后续每条 RPC 都禁止 unmanaged 回退且必须再次识别为 APP_SOURCE，replica 映射消失时不得降级为超级管理员服务器工作区。每条 RPC 还必须重新读取用户 `opencode` 文件路由 affinity，并校验它与 ticket 目标/agent 服务器、Workspace/托管副本服务器和当前 JVM 全部一致，连接后的 binding 迁移必须使旧 socket 立即失败关闭，不能继续调用文件服务。非托管 Workspace 的普通路由、ticket 与 RPC 对所有角色默认拒绝；超级管理员跨用户排查只能使用上一节的专用只读通道。
2. Agent 配置文件必须通过 `agent-config/file-ws-route` 按 `scope/workspaceId/worktreeId/linuxServerId` 解析目标后端；公共 worktree 使用落库 `linuxServerId`，公共直接模式必须由前端传入已初始化公共配置服务器 ID。
3. ticket 只能通过用户登录态创建，短期过期、一次性消费，并绑定 workspace、目标服务器、当前 agent 服务器、签票时工作区是否为 AppSource 的权威分类、模式、Agent 配置 scope/worktree、traceId 和是否 `SUPER_ADMIN`；不得把长期 Bearer token 放入 WebSocket URL。
4. WebSocket upgrade 必须校验 Origin 白名单、ticket 有效性和 ticket 模式；ticket 消费后无论连接成功与否都不能重复使用。仅当全局 CORS 配置恰好为单个 `*` 时，平台文件 WebSocket 才允许任意格式合法的 canonical Origin；缺失、畸形 Origin 以及混合 wildcard 与显式来源都不得放宽，生产仍必须配置显式来源。
5. 所有 `workspace.*` 操作必须绑定 ticket workspace，路径必须归一化在 workspace root 内；`rename` 只允许同一父目录内的普通文件或目录改名，目录树删除不跟随符号链接并拒绝根目录和任意层级 `.git`。应用源码 Runtime Workspace 还必须在每条 RPC 实时复核 enabled 应用、当前成员、`APPLICATION_CODE_REPOSITORY` 关联、slot active generation、snapshot 未过期和本机 READY replica；`SUPER_ADMIN` 不旁路这些条件。固定索引 `.testagent-appsource-index.json` 不得出现在列表/搜索，也不得通过读取、分片、写入、上传、复制、移动、重命名、状态或删除访问。`workspace.view.list/read/read.chunk` 的 locator 只能表达逻辑来源，后端必须从当前工作区最新 JSONC 重建允许挂载：应用资产重新校验应用关联、`APPLICATION_ASSET_REPOSITORY`、总体/本机副本、当前平台 `OPENCODE_REFERENCES_DIR` 与 SDD 根白名单；自动化 locator 只携带应用 ID、版本库 ID、配置 generation、逻辑相对路径和可选服务端历史标签租约，后端重新校验 `merge=false`、服务端可复算的 `OPENCODE_REFERENCES_DIR` 逻辑代次路径、当前成员、应用与 `AUTOMATION_CODE_REPOSITORY` 关联、当前 generation 或与用户/主工作区/应用/版本库/generation 完整绑定且未过期的标签租约、本机 READY 副本及固定目标提交；数据库只保存租约 SHA-256，非自动化 locator 禁止携带该字段。两类引用都禁止接收物理路径或仅凭 repositoryId 授权，只能读取，单引用错误以不含物理路径的局部 warning 返回。渐进读取每段都必须重新校验 ticket、成员关系、逻辑 locator、标签租约和文件快照，不能把首次解析出的物理路径保存在客户端。
6. `agent-config.list/read/read.chunk/write/upload.*/rename/delete` 必须绑定 ticket scope、workspaceId 和 worktreeId；读取允许登录用户，公共配置写入、上传、重命名和删除校验 `SUPER_ADMIN`，应用配置对应变更校验 `APP_ADMIN`（`SUPER_ADMIN` 继承）。分片上传的 begin/chunk/complete/abort 必须在同一 WebSocket 连接内复用 workspace-management 的分片顺序、声明大小、不覆盖和越界校验，每个分片继续复核 ticket 绑定和权限；应用上传、改名、复制、移动和删除必须统一限制在当前配置根对应的 `.opencode/**` 命名空间，不得枚举 agent/skill/tool 等子目录形成权限缺口。上传总大小不设应用层上限，但单分片必须有界，完成前只能写隐藏临时文件，取消、失败和连接关闭必须清理；改名只允许同目录文件，删除文件或目录树必须复用 root 归一化、根目录/`.git` 保护与不跟随符号链接语义。
7. 应用 `.opencode/**` 与普通文件共用版本个人 worktree 时，个人 worktree的 `commit/publish` HTTP 入口也必须对规范化后的整个配置命名空间执行 `APP_ADMIN` 校验；不能只依赖前端 Tab 或 Agent 文件 WebSocket 权限。`spec/**` 禁止发布的服务层规则继续对所有角色生效。
8. `directory.list` 只允许 `directory-picker` ticket；跨服务器目录浏览仅 `SUPER_ADMIN` 可创建 ticket，普通用户只能浏览当前 agent 同服务器目录。
9. `workspace.create` 必须要求 `SUPER_ADMIN`，并且选择服务器与当前 agent 服务器一致；不一致时前端禁用输入，后端仍必须返回 `CONFLICT` 或 `FORBIDDEN`。
10. 日志和错误响应不得输出 ticket、Authorization、Cookie、完整用户输入、完整文件内容或敏感路径片段；审计只记录 traceId、workspaceId、worktreeId、服务器 ID、操作类型、路径摘要和错误码等必要字段。
11. Git 网络/权限/超时错误面向用户只展示稳定错误码、后端脱敏提示、可恢复状态和 traceId；前端不得拼接原始命令、stderr、远端 URL 或凭据。Git 命令超时必须终止 Git 及其 SSH 等后代进程，不能在请求失败后留下后台连接；浏览器待推送恢复只保存逻辑 ID、相对文件白名单和提交说明，不保存 patch、文件正文或物理路径。
12. 分享工作区文件 ticket 必须额外绑定 share/version、真实 actor、执行所属人、固定 session/workspace、`canChat` 和分享到期时间；路由使用执行所属人的进程服务器。每条 RPC 和连接级定时监视都必须刷新分享授权，读写按当前 `canChat` 分流，失效时中止未完成上传、记录路径摘要审计并关闭连接。分享 ticket 禁止执行 `agent-config.*`、`directory.*`、`workspace.create` 或 Hub 操作，也禁止通过普通用户 affinity 获得其它 Workspace。

## 个人工作区搬迁 WebSocket 安全例外

个人工作区搬迁是平台后端之间的受控文件通道，不接受浏览器用户凭据或任意文件路径：

1. 源 Java 必须由持久化搬迁记录和本机 `WorkspaceServerIdentity` 确认源服务器，目标 Java 必须复用 `BackendJavaRouteResolver` 选择精确在线后端；`BackendHttpForwarder` 只允许把搬迁 ID、源/目标服务器、归档 SHA-256/大小和 traceId 送到精确 ticket 路径，不得承载文件字节或自建转发器。
2. ticket HTTP 入口只对精确路径豁免普通 API token，并使用标准非空 `XXL-JOB-ACCESS-TOKEN` 常量时间校验。ticket 只保存在签发 JVM 内存 60 秒、消费即删除，绑定搬迁事实和源服务器；HTTP 响应的 `webSocketPath` 和实际 WebSocket URL 均不得内嵌 ticket，源 Java 只能通过专用 `X-Test-Agent-Relocation-Ticket` 握手 Header 携带，避免通用响应日志和网关访问日志泄露凭据。WebSocket upgrade 还必须匹配固定内部 Origin 和源服务器头；全局 CORS 只能为精确搬迁 WebSocket 路径单独允许该内部 Origin，不得把它加入普通浏览器白名单或放宽为 `*`，相邻/子路径不得继承豁免。
3. WebSocket 只接受有界二进制归档帧和唯一 `complete` 控制帧；服务端按声明大小、2 GiB 总上限和 SHA-256 校验，ZIP 解包还必须限制 manifest、entry 类型/数量、总展开大小和相对路径。目标目录、分支和应用副本只能从数据库及受控路径参数派生，协议不得接受客户端物理路径。
4. 目标恢复校验完成前不得更新 Workspace 归属；数据库切换失败、活动 Run、源文件并发变化、摘要不一致或不支持的文件类型均不得清理源 worktree。错误响应、数据库 `safe_error_message` 和日志只保留固定安全说明/错误码/搬迁 ID/服务器 ID，不得记录用户文件、物理路径、SSH 私钥、ticket 或原始 Git stderr。普通用户错误不返回 workspaceId；运维关联只能在授权的数据库和日志侧完成。

## 应用源码进度 WebSocket 安全例外

应用源码进度 WebSocket 是只读观察通道，不属于平台文件 RPC 或 RunEvent。实现和后续扩展必须满足：

1. ticket 只能通过登录态创建，签发前先读取持久化 operation 并确认 repository 仍是应用源码类型；TEAM 要求用户属于任一当前启用且仍关联该 repository 的应用，PERSONAL 只允许 owner 或同时满足上述成员条件的 `APP_ADMIN/SUPER_ADMIN`，管理员不旁路成员关系。
2. ticket 短期过期、一次性消费，并绑定 operationId、userId、签发时 `APP_ADMIN` 事实、签发 backendProcessId 和 traceId。WebSocket upgrade 必须校验 ticket path operationId、当前 JVM 和 Origin 白名单；仅当全局 CORS 配置恰好为单个 `*` 时允许任意格式合法的 canonical Origin，ticket 仍必须绑定实际 canonical Origin，混合 wildcard 与显式来源不得放宽。生产仍必须配置显式来源。失败统一返回通用拒绝，不能泄露 operation 是否存在。
3. 建连首帧和后续轮询只读数据库权威 operation/step/replica 状态；每次轮询重新鉴权，不能只信任 ticket 中缓存的成员或 owner。断开连接只停止观察，不能取消或修改后台操作。
4. payload 只返回逻辑 ID、固定 commit、状态、时间和安全步骤摘要；不得包含物理源码根、repositoryPath、SSH 私钥、原始 Git stderr、文件内容、完整异常堆栈或敏感路径。序列化及内部读取异常只返回安全错误码和固定消息。
5. 多 Java 部署必须把 upgrade 固定回签发 JVM；其它服务器 worker 通过持久化步骤汇聚进度，不新增跨 Java 内存事件、Redis 原始错误广播或 Java→Java 文件代理。

## 服务器广播安全

后端内部服务器广播用于跨后端实例同步业务状态，不是浏览器 API 或 SSE。实现和后续扩展必须满足：

1. 广播 payload 只允许包含业务 ID、事件原因、服务器 ID、版本号、分支名、目标 commit hash 和 traceId 等必要字段；禁止携带 SSH 私钥、token、Authorization、Cookie、文件内容、完整用户输入或大段错误堆栈。
2. Redis pub/sub 仅作为同一可信后端集群内的实时增强通道；生产必须使用受控内网 Redis，并通过外部配置开启 `test-agent.server-broadcast.enabled=true`，不得在代码或示例中硬编码 Redis 密码或生产地址。
3. 消费端必须跳过本服务器来源事件，并在业务层做幂等校验；广播失败不能影响本机已完成的 Git/数据库主流程，漏消息由数据库目标 commit 与本机补偿扫描恢复。
4. 公共 Agent 全局刷新必须在任何 reset/clean/merge 前取得数据库活动 rollout 锁。共享运行副本存在本地内容时，恢复确认必须由 `SUPER_ADMIN` 在聚合真实服务器状态后显式提交并持久化；未确认不得修改工作树。确认范围只能覆盖共享运行副本，个人 worktree 禁止 stash/reset/clean，只允许 Git 原生 merge 和用户自行解决冲突。状态 API 可返回服务器 ID、计数、稳定原因和脱敏 `lastError`；仅 `SUPER_ADMIN` 可额外读取每台服务器最多 200 个未排空目标的内部 `userId`、用户名和停止所需进程坐标，用于定位阻塞并复用既有运行管理停止命令。不得返回统一认证号、Session 内容、SSH key、内部凭据或文件正文。
5. 公共 rollout 纠错替换只允许 `SUPER_ADMIN` 调用，并要求显式提交当前 `DRAINING` rolloutId、远端修正分支和非空审计原因；旧任务终态、新任务活动态与全部旧租约失效必须在同一数据库事务完成。强停标记只能由仓储按旧目标与新快照的用户、服务器、容器、端口、PID 和 manager 启动时间精确派生，业务入口不得接受前端自报 `forceStop`。执行时必须复用 `OpencodeProcessStopService` 的 tracked owned-stop 与停止后 health 确认；身份变化、旧 manager 不支持或结果不确定一律重试，禁止回退到仅按端口停止。响应和日志只返回 rollout 关系、计数、原因和安全错误，不返回 UCID、凭据、会话正文或内部配置内容。
6. 日志只记录 `eventId`、`type`、`traceId`、`versionId`、`linuxServerId` 和错误码等低敏字段，不能输出私钥、token、完整路径中的敏感片段或原始第三方错误详情。
7. 应用源码 `app-source.replica-requested` 只允许 repositoryId、generation 和目标服务器 ID，`app-source.cleanup-requested` 使用空 payload；SSH 私钥只在目标 worker 的 Git 命令期从操作人加密配置解析，clone、冻结提交 fetch、checkout 和提交校验复用同一临时凭据，禁止写入 snapshot、operation、step、广播、索引、错误响应、物化源码或日志。广播只负责唤醒，数据库租约、本机有界 dispatcher 及其启动/周期数据库补偿扫描才是执行与幂等事实源。应用源码物化、索引修复、打开和清理必须以可信配置根为边界逐段执行 `NOFOLLOW_LINKS` 校验，并在目录创建后、文件锁内或破坏性操作前复核，禁止祖先或目标符号链接把读写/删除重定向到托管根之外。
8. `user-notification.changed` 只允许接收人内部 userId、变化类型和可选通知 ID；禁止 shareId、会话标题、通知摘要、用户名、URL 或权限正文。消费端按接收人只唤醒本机对应 SSE，正文和未读数从数据库重新读取；发布或消费失败不能回滚已提交分享/通知事务，漏消息由初始快照和 30 秒数据库校准恢复。

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
10. 分享成员只能在 `canChat=true` 时创建固定会话/工作区的 workspace terminal ticket；ticket 必须绑定 share/version、真实 actor、执行所属人和到期时间，PTY 使用所属人的进程、工作区和操作系统身份。upgrade 后至少每秒及每次 input 重新校验授权，降权、移除、取消或到期立即关闭 PTY；分享权限永远不能创建服务器终端。

ticket 创建与 WebSocket 协议细节见 `docs/api/http-api.md`。

## 企业旧 Docker 的中间件 seccomp 兼容例外

1. 当前 Linux 4.19 / Docker 18.09.7 节点运行 ClickHouse 26.3、Bookworm Python/pgvector 或 Alpine 3.20 镜像时，旧默认 seccomp 可能把新系统调用返回为 `EPERM`，导致时区解析、线程创建或镜像入口失败。经现场负责人明确批准，交付脚本使用 `--privileged` 启动 ClickHouse、独立记忆 pgvector、Alembic、Mem0、CPU BGE 和记忆 VIP 容器。
2. 该例外不得写入 Docker daemon 全局默认，不得扩大到共置平台 PostgreSQL、克隆机遗留容器或其它业务容器。非 root、只读根、最小 mount、端口 ACL 和密钥文件权限继续保留，但不得宣称它们抵消了 privileged 带来的设备、capability、seccomp/AppArmor 隔离放宽；这些容器必须按高权限工作负载限制宿主访问和运维人员范围。ClickHouse 继续由官方入口降权为 UID/GID `101:101`，其 `0600` 用户配置和持久目录必须归同一 UID/GID 所有；禁止通过 `CLICKHOUSE_RUN_AS_ROOT=1` 绕过文件所有权，否则既扩大权限，又会触发 ClickHouse 的进程用户/数据所有者一致性保护。
3. 外网 Mac 的现代 Docker 启动验证只能证明制品功能，不能替代每台旧 Docker 企业宿主验证。宿主 Docker、runc 和 libseccomp 完成受控升级后，必须逐镜像移除 `--privileged` 实启并通过 readiness，才能取消例外；不能只按版本号推断兼容。

## 官方 Codex MCP 安全边界

1. 公共配置通过中性名称 `code_analysis` 注册本地 MCP。使用者必须仍是当前应用有效成员；普通成员、应用管理员和已加入应用的 `SUPER_ADMIN` 权限相同，超级管理员不得旁路成员校验。
2. 启动器直接执行官方 `codex mcp-server`，工具保持为 `codex` 与 `codex-reply`。官方 `cwd`、模型、配置、sandbox、审批、基础指令和开发者指令参数不做过滤；不得再把 MCP 描述为“只能读取当前 workspace”。应用和源码 workspace 的访问资格仍由平台既有入口鉴权，但 MCP 文件可见范围最终取决于容器挂载、操作系统权限与调用参数。
3. 管理员级 `/etc/codex/requirements.toml` 只允许 `approval_policy=never`，避免夜间任务产生 permission 等待。白盒 Agent 默认显式传 `sandbox=read-only`；这是官方沙箱模式，不提供平台自定义的 workspace 外拒读、临时目录拒绝或工具级断网保证。
4. 官方 MCP 自行管理会话、thread ID、取消、stderr 和进程生命周期。平台不再创建每进程临时 `CODEX_HOME`，而是在当前 OpenCode 用户 HOME 下维护持久化 `.testagent-codex-mcp/config.toml` 以提供企业模型默认值；文件不保存 API key。平台不维护 thread ID 白名单，也不重写提示词、工具参数、返回值或错误正文；调用与运行日志必须按现有 OpenCode、worker 和代理脱敏规则管控。
5. 模型代理密钥、代理地址和用户 UCID 只复用 Java/manager 向该用户 OpenCode 进程注入的环境变量，不写入公共或应用 JSONC。公共 MCP 默认模型配置固定为 Java 路由键 `deepseek-prod`、模型 `DeepSeek-V4-Flash-W8A8` 和上下文窗口 `262144`；不得把 OpenCode provider key `enterprise-deepseek` 填入 Java 路由字段，也不得混用其他模型的上下文长度。
6. Responses 适配只接收纯文本与 function calling 子集，不透传图片、文件、Web 工具、reasoning 或加密思维链。代理日志只记录 traceId、耗时、状态与稳定错误信息，不记录代码、提示词、Token 或密钥；官方 MCP 原生日志不经过平台门面二次过滤。
7. 企业 Linux 节点必须在启用前执行 `deploy/internal/check-codex-whitebox-host.sh`，真实验证当前内核/Docker 上的官方工具契约、指定 cwd、读取、原生 read-only 拒写、Git 状态不变和续写。当前现场基线为 Linux 4.19、Docker 18.09.7、x86_64、privileged worker；构建机验证不能替代逐节点能力验收。
8. worker 内 Python、pip、venv 和通用脚本工具属于镜像受控运行时，不得通过挂载宿主 `/usr/bin` 或继承宿主 PATH 补齐。生产镜像不保留业务 Python 包或编译工具链；获批的第三方依赖必须固定版本和逐 wheel 哈希，按目标 Python ABI 与 `linux/amd64` 单独打包、断网功能验证、只读挂载和独立升级，企业运行容器通过 `PIP_NO_INDEX=1` 禁止访问公网执行 `pip install`。

详细启用、构建和回滚流程见 `docs/deployment/codex-whitebox-mcp.md`。

## Agent & Skill Hub 安全边界

- Hub 是企业内显式共享域：所有已登录用户可以查看所有应用成功 push 的 Agent/Skill 完整快照，因此 Agent/Skill 文件不得包含密钥、Token 或本应按应用隔离的秘密；UI 不把“未发布”误表示为私密。
- 发布要求 `APP_ADMIN`（`SUPER_ADMIN` 继承）且必须仍是来源应用有效成员。引用、取消引用、更新和冲突解决要求同一角色、当前个人 worktree owner 以及目标应用成员三项同时成立；HTTP 的 `targetWorkspaceId` 也必须解析为当前用户拥有的个人运行工作区后才能投影状态。
- 已生效引用的目标应用/工作空间名称属于企业共享 Hub 元数据，可随资产详情向已登录用户展示；尚未 push 的 `PENDING_PUSH` 只向目标应用有效成员展示，已取消的 `PENDING_REMOVE` 不再作为消费者展示，避免公开本地未提交或已撤销意图。
- 浏览正文只接受独立 `agent-skill-hub/HUB` 一次性只读 ticket；引用写入复用绑定目标 `workspaceId` 的 `agent-config/WORKSPACE` ticket，不新增后端到后端文件 HTTP 代理。
- 快照从 Git 精确 commit 读取，不读取 push 后可能变化的工作树。技术 ID、相对路径、文件数、未压缩大小、压缩编码和 SHA-256 都必须校验，拒绝绝对路径、路径穿越、符号链接目标和越过 `.opencode` 的写入。
- 三方合并有冲突时不得提前改工作树；完成前再次校验 current 摘要，数据库持久化失败时恢复文件系统备份，避免把被用户继续修改的内容静默覆盖。取消引用同样先备份后移除，数据库失败必须恢复；关系立即退出用户侧有效视图，但只有远端 push 能证明目标路径已消失时才正式删除引用元数据，确认前重新引用必须复用原记录而非绕过唯一关系约束。
- Hub 业务日志只记录资产/修订/工作空间标识和错误摘要，不记录制品正文或冲突内容。
- Skill 事项分类修改只允许 `SUPER_ADMIN` 通过专用 HTTP 入口执行，`APP_ADMIN` 继承关系不适用于该入口；服务端只接受固定枚举组合，记录最近分类管理员和时间。来源仓库内容、Skill frontmatter、请求关键字和应用管理员身份都不能自动提升或覆盖分类。
- 外部 SkillHub `/list`、`/upload`、`/upload/progress` 和 `/download/{id}` 请求固定携带 `X-Skill-Access-Key`，密钥只允许从 `TEST_AGENT_SKILLHUB_ACCESS_KEY` 注入，不得进入源码、YAML 默认值、数据库、响应或日志；企业交付只能通过敏感节点包或目标机 `backend.env` 继承，普通发布 ZIP 和 Git 模板只保留占位符。启用集成但基础地址/密钥缺失时启动失败关闭；地址已配置但运行期暂时不可达时不得阻断 Java 启动，只允许后台同步记录脱敏错误并重试。下载固定使用受控枚举 `PLATFORM(3)`，客户端不能覆盖 `channel`；新版文档必填 `userId` 只能取当前 `AuthPrincipal.unifiedAuthId` 或工作区文件 ticket 已冻结的同一字段，不接受浏览器参数，不得进入平台日志或错误响应。
- SkillHub 上传入口只允许 `SUPER_ADMIN`，平台必须且只能接收六个业务字段，再由服务端补入当前统一认证号作为上游必填 `userId`；ZIP 在 API 和业务层都限制 20 MiB，三张图片分别限制 5 MiB，文件名禁止路径和换行。上传包会原样交给上游，所以在路径穿越、重复项、文件数和技术 ID 校验之外，仍严格要求大写根 `SKILL.md`；仅按需下载物化允许将唯一外层目录和清单文件名大小写安全归一化。请求文件、图片、统一认证号、taskId、第三方 `message` 和响应正文不得写入业务日志。应用 Git push 不自动上传，避免绕过图片和显式管理员授权。
- 外部 `/list` 只写有界元数据，未知附加字段忽略；`name` 必须原样满足平台安全技术 ID 规则：大小写字母、数字、点、下划线和短横线，且首字符为字母或数字，禁止斜杠、空白、点段或别名转换。ZIP 仅在显式预览、引用或更新时下载，压缩包和解压总量分别不超过 20 MiB、文件不超过 256 个；拒绝绝对路径、空/点段、路径穿越、重复路径、无根 `SKILL.md`、非 UTF-8 `SKILL.md` 和 `name` 与稳定目录 ID 不一致。同一外部 ID+版本出现不同摘要必须拒绝覆盖并告警。

## 对话工作区 Git Tool 安全边界

- 公共 `workspace-git` Tool 禁止直接执行原生 Git 绕过平台；所有副作用必须调用 agent-scoped 专用入口并复用 workspace-management 的 owner、路径角色、`spec/**` 禁发布、应用同步和冲突规则。
- 对话中的个人拉取仅能合并当前会话绑定 owner 在当前应用的个人 worktree；应用 workspace 和应用 Agent 统一使用 Git 原生合并保护，禁止自动 stash/reset，也不得把该凭据扩大为共享版本、其他用户或公共 Agent 的更新权限。
- “应用 Git 刷新”、单分支组刷新及其工作空间/版本/分支范围查询是独立共享控制面能力，HTTP 入口必须强校验 `APP_ADMIN`（`SUPER_ADMIN` 继承）。超级管理员保留启用/停用应用全量能力且不要求成员关系；应用管理员只能读取和刷新启用且自己仍为有效成员的应用，列表与执行必须复用同一授权程序，伪造 appId 统一 `FORBIDDEN`。范围查询只能读取应用与托管工作区元数据，不访问 Git 远端或返回物理仓库路径；单分支选择必须以 `repositoryId + version + branch` 三字段精确命中当前应用，禁止只按可重名分支字符串执行。Git 远端访问只使用当前操作者保存的唯一 SSH Key；物理 feature 只允许快进，脏工作树或分叉必须按仓库组失败。向相关个人 worktree 收敛时继续使用原生 merge，禁止 stash、reset 或强制覆盖个人 staged、unstaged、untracked 内容；部分失败必须在响应中显式计数和列明，不能伪装为全部成功。
- Tool 凭据由 `OpencodeProcessStartupService` 按用户签发，只允许专用 Git 端点使用，不能被通用用户 Token 过滤器接受，也不能访问其它平台 API；签名密钥不得注入 OpenCode 进程。凭据包含过期时间，验证时必须实时检查用户启用状态和角色。
- 当前 workspace 必须由远端 session 经平台 agent binding 反查，禁止接受 Tool 传入 workspace ID、个人 workspace ID、物理路径或目标服务器。owner 不一致、非个人 workspace 或绑定缺失必须失败关闭。
- `discard`、`publish`、冲突解决和取消合并必须先显示 OpenCode permission 确认；Tool 返回给模型的错误详情只保留原因、相对文件和并发提交等安全字段，不返回凭据、Git 命令或物理路径。

## 平台体验工作区安全边界

- 体验入口对任意已登录用户永久开放，不再把应用成员关系、角色或首次进入作为资格条件。客户端仍不得提交应用、路径、服务器或 Workspace ID 来改变路由；目标目录只能来自当前 TestAgent 进程所属服务器的权威通用参数和当前 binding。应用成员变化不撤销体验访问，也不能把体验 ID 降级为超级管理员任意目录或普通非托管 Workspace。
- 每次打开、Workspace 详情、Session 创建与用户写入、Git 状态、文件 route/ticket、每条文件 RPC、Session runtime 目标、无 token 兼容 Run、contextToken 滑动续期和会话上下文签发都必须确认当前服务器最新绑定、Workspace ACTIVE、稳定服务器身份和当前目录真实路径一致。参数换目录、服务器变化或绑定失效后，旧连接和历史 Session 不能改写会话、读取文件、启动新 Run 或签发/续期上下文；历史会话列表和数据库快照可按原归因保留，但不能退化为写入或运行授权。Workspace PTY 与体验 Run SSE 必须独立于客户端输入/新事件按固定短间隔复核，撤权后主动关闭长连接；Diff、Diff 决策和 session-tree 等实时 Run 子路由必须严格转发到 Run 生产 Java，不能在随机入口服务器本地执行。
- 体验目录是同服务器多用户共享写域，Git index 和提交历史同样共享，不能承诺用户隔离、并发写保护、快照恢复、自动清理、跨服务器同步或远端备份。邀请和工作台必须明确提示并发覆盖与敏感数据风险；禁止在目录中存放密码、Token、SSH key、客户数据或其它秘密。平台文件接口继续拒绝根目录以及任意层级 `.git`、`.opencode`；目录列表不展示符号链接，直接请求逐段拒绝末端或中间链接，搜索不跟随链接。由于保留终端和 Agent 普通写工具，且共享用户以同一 Java/worker 操作系统身份访问，这些路径校验不是抵御同机恶意并发替换的 OS 沙箱；需要对不互信用户提供隔离时，必须另行采用独立操作系统身份、容器/挂载策略或关闭终端与通用写工具，不能把本体验区用于该场景。
- 体验 Git 是“本机可提交、不可发布”的产品能力：允许受控 diff、stage、unstage、discard、三方冲突处理、完成 merge 和按指定路径 commit；指定路径提交不得夹带共享 index 中其他用户已暂存的文件。体验 Workspace 不获得 personal workspace ID，不得进入个人 pull/publish 或 feature/公共发布程序；前端不展示 push，后端不暴露体验 push/发布入口，启动初始化也不创建 remote。受控 diff 继续使用精确 pathspec、`--no-optional-locks`、禁 fsmonitor/untracked cache、external diff/textconv，并过滤任意层级且大小写不敏感的 `.git`/`.opencode` 与符号链接；patch 必须同时限制单文件与整次响应预算。OpenCode experimental worktree 及通用 `/vcs/status|diff` 仍固定拒绝。所有体验 ID（含历史绑定）的 Agent 配置 HTTP/路由服务必须失败关闭，`agent-config/WORKSPACE` 文件 ticket 必须先实时校验当前绑定再返回 `FORBIDDEN`。普通文件 RPC 也必须拒绝受控命名空间并过滤列表/搜索；体验区只使用普通 `workspace.*` 文件 RPC，不开放应用引用 `workspace.view.*` 组合视图，旧 `/opencode-runtime/fs/list|find|read` 也固定拒绝。保留的终端和 Agent shell/write/edit 可能直接执行原生命令，因此“不提供 push”不是对同机恶意 shell 的 OS 级强隔离。
- 目录不存在/不可写、启动 Git 初始化失败、进程/服务器不可用和 Git 状态错误只返回稳定错误码、通用文案与 traceId。成功 `WorkspaceResponse` 只返回 `workspace:{workspaceId}` 逻辑根；OpenCode runtime 的 catalog/session 投影、Run 实时/恢复事件、旁路问答 delta/答案、自动标题、消息持久化快照和错误详情必须递归替换体验物理根。响应 details、异常 cause 和日志不得包含配置原值、真实物理路径、remote 或底层命令输出；Git 初始化、工作树探测和状态命令必须在专用脱敏作用域内执行。

## UI 测试执行 Tool 安全边界

- `ui_test_execute` 每次先通过 `TEST_AGENT_PLATFORM_BASE_URL` 直连同节点 Java 的精确只读接口，读取超级管理员维护的 `UITEST_BASE_URL`，再直连独立 UI 平台；地址不进入 Tool 参数、Agent prompt 或案例内容，Java 不代理 UI 请求。
- UI 配置查询和独立 UI 平台请求均不携带应用层凭据。配置查询只允许受信任 OpenCode worker 内网直连，公共 Nginx 必须对精确路径 `/api/internal/agent/opencode/ui-test-tool/config` 返回 `404`；Java 只返回 `configured/baseUrl`，API Token 过滤器只豁免该精确路径，相邻路径仍鉴权。独立 UI 平台端口也不得暴露到不可信网络；需要跨安全域时必须重新设计平台鉴权，不能在公共 Tool 中硬编码共享密钥。
- `UITEST_BASE_URL` 只允许无 user-info、query 和 fragment 的 HTTP/HTTPS 地址；管理端写入和 Tool 消费两端都校验，错误与日志不得回显非法原值。Tool 不读取 UI 平台地址进程环境，也不把现有 Workspace Git Tool Token 扩展到 UI 链路。
- Tool 请求只允许必填的被测系统环境文本、一行四列案例和调用上下文派生的幂等键；环境只能来自用户输入或父 Agent 从用户明确指定路径读取后的原文，缺失时失败关闭。只传已读取内容，不向 UI 子 agent 或独立 UI 平台传物理路径；调用方不得指定 UI 平台地址、浏览器参数或批量任务；一轮 Tool 执行最多发送一个创建请求。
- “测试步骤”是唯一操作流程，“测试数据”和“预期结果”只作为输入与验证上下文。外部响应堆栈不得透传，状态查询不回显原始案例内容。
- 执行报告使用 executionId 绑定路径，禁止接受任意 report path；报告接口与执行接口使用相同的受信任网络边界。

## LobeHub 登录交接与模型委托安全边界

- LobeHub 不读取平台页面的 `sessionStorage`。平台前端只能用现有 Bearer Token 申请一次性票据，再以隐藏表单
  POST 到配置生成的固定聊天地址；票据禁止进入 URL、router、Web Storage、剪贴板、错误详情和日志。
- 票据和模型委托均为至少 32 字节随机 opaque 值，Redis 只保存 SHA-256 摘要。票据默认 60 秒、一次性原子
  消费；模型委托绑定用户、`lobehub` client 和 `model-gateway` scope，最长 30 天且重新兑换时原子轮换。
- 安全时限只能收紧，不能通过部署配置放宽：ticket 必须为 `(0,60s]`、grant 为 `(0,30d]`、HMAC 时钟偏差为
  `(0,60s]`，nonce 摘要必须至少保留 120 秒。无效配置在 Spring 绑定阶段失败，不能静默回退到不安全值。
- 兑换与撤销必须对 timestamp、nonce、HTTP method、精确 path 和原始 body digest 的五行 canonical string 做
  HMAC-SHA256。共享密钥至少 32 字节；平台限制默认 ±60 秒时钟偏差并原子占用 nonce，比较签名时使用常量时间。
  不得先解析并重新序列化 JSON 后再验签。
- API 日志只把票据交换原始 bytes 记为长度占位；字段脱敏必须覆盖 `ticket`、`modelGrant`、Authorization、
  Cookie、password、secret 和 token。上游模型错误不得回显 URL、响应正文、密钥、prompt、回答、UCID 或堆栈。
- 模型委托只允许 LobeHub 服务端用外部密钥加密持久化，浏览器、Desktop、CLI 和设备 broker 不得获得。
  网关每次调用重新检查平台用户状态，并覆盖客户端 Authorization、供应商选择、UCID 和 trace header。
- 企业 Desktop/CLI 只能使用 fork 的浏览器确认协议，禁止 OIDC discovery/JWKS、Device Code Flow、JWT、API Key、
  refresh token 和自定义 Server URL。request、poll secret、授权码和客户端 Session 都必须使用独立 32 字节随机值，
  Redis 只保存摘要；浏览器 pending request 必须绑定 HttpOnly/SameSite=Lax cookie 和 CSRF，页面与客户端同时显示
  同一验证码并要求显式确认。授权码必须在 PKCE 校验前原子消费，客户端 Session 最长 24 小时且每次请求复查用户状态。
  企业离线 `/oidc/*` 拒绝必须先于 Session 查询和未登录跳转，确保匿名旧客户端也得到 `403`，不得跳回平台登录。
- Desktop opaque Session 只进入操作系统 safeStorage，CLI 只进入 mode `0600` 的自身凭据文件；请求只使用标准
  `Authorization: Bearer`。无效 Bearer 不得回退浏览器 Cookie，显式退出必须撤销服务端 Session 和模型委托，并在
  远端失败时仍清除本机凭据。
- LobeHub Redis 使用独立 ACL 用户，key 与 pub/sub channel 均限制为 `lobehub:app:*`，并拒绝管理命令和平台
  前缀；`REDIS_PREFIX` 配置为不带尾冒号的 `lobehub:app`，由上游 Redis wrapper 追加唯一分隔冒号。客户端认证
  仅允许仓库内固定 Lua 状态机所需的 `EVAL`，ACL key pattern 不得因此放宽。Agent Runtime 的直接 ioredis
  连接也必须设置同一 `keyPrefix`，枚举只允许游标 `SCAN`，不得使用 `KEYS`。现场 ACL 从 `-@all` 开始按固定
  命令白名单授权，并验证 Lua 跨前缀、前缀外 channel、`CONFIG/ACL/MODULE/FLUSH*/KEYS/SCRIPT FLUSH` 均被拒绝。
  平台 SSO key 固定在
  `test-agent:lobehub-sso:*`。RustFS bucket 必须私有，初始化任务必须显式撤销匿名访问。
- LobeHub app 默认只绑定 `127.0.0.1:3210`。跨机反向代理时只允许绑定获批的具体内网 IPv4，拒绝 wildcard，
  并用主机防火墙把 3210 来源限制为代理主机；RustFS、ParadeDB 和 Redis 不得随之暴露。
- `/data/testagent/config/lobehub.env` 必须是 root 控制的非符号链接 mode `0600` 文件，拒绝重复键和非法 dotenv。
  启动脚本必须按容器生成临时最小 env：数据库和对象存储容器不得获得 HMAC、Session、模型委托或彼此密钥；
  临时文件退出即删，secret 不放入 Docker 命令行。
- LobeHub 现场预检必须交叉校验连接目标：数据库 URL 与库用户、Redis URL 与 ACL 用户/DB 0、S3/MC 与私有
  bucket、内部 app URL 与容器名保持一致，平台 launch/兑换/撤销/模型网关必须同源；只验证单个 URL 格式不算
  通过。URL credential 必须编码，不能让未编码分隔符改变 authority、path 或 query。
- LobeHub 冷备份必须在 app、RustFS 和 ParadeDB 全部停止后创建，归档及校验和使用 mode `0600` 并置于
  `/data/testagent` 外的受控加密介质。恢复必须要求显式确认、先校验摘要和归档白名单路径，并保留被替换数据供
  回滚；工具不得自动停服务或用整目录删除代替精确目标替换。
- `LOBEHUB_ENABLED=true` 不是绕过配置校验的开关：固定聊天 origin、虚拟邮箱域和唯一 owner 必须同时脱离
  migration 占位值，平台才允许落票据。现场 `validate-config` 还必须核对 digest 镜像、secret 长度、离线开关、
  Cookie/Session 契约和执行能力门禁，任一不满足都禁止 migration 或启动 app。
- 本地在线模式自动申请 Marketplace M2M 会话时，`clientSecret` 只能通过 POST body 发送；禁止使用 query
  procedure，避免凭据进入 URL、Next.js 访问日志、反向代理历史或浏览器历史。
- 完全离线部署必须在 UI 和服务端同时关闭公网搜索、SaaS Connector、BYOK、自定义 Base URL、遥测、在线更新、
  Marketplace、CDN 与运行期下载。离线可选 Agent 只能来自锁定 fork 中经过来源、许可证、官方/验证状态、
  自包含依赖和本地头像 SHA-256 校验的 Community 快照；安装不得产生 Marketplace 写请求。`v2.2.11-platform.7`
  对 Windows/Linux 都强制
  `LOBEHUB_DEVICE_EXECUTION_MODE=disabled`，不存在通过旧变量放开的路径；后续 Linux 执行版本仍须在目标主机
  通过真实边界验收并 fail closed，且提供 root 所有、mode `0600`、绑定当前发行版/内核的通过证据。
- 企业客户端必须从锁定 fork commit 的源码工具包在原生 Windows x64 / Linux x86_64 构建，仿真或交叉构建
  不能作为正式结果。Windows 证据必须由 `signtool` 和 `Get-AuthenticodeSignature=Valid` 产生，并绑定签名身份、
  客户端 SHA、版本、commit、架构和执行禁用状态；Linux 构建人与审批人必须分离，原生构建证据必须绑定
  构建身份、客户端、版本、commit、OS/内核和执行禁用状态，最终审批证据必须绑定客户端、构建证据和独立验收
  记录 SHA。验收记录关联审批人、变更单及登录、无公网依赖、下载阻断、数据隔离和执行禁用结果。构件
  汇集、Mac 打包和现场安装必须复用同一校验器，任一占位值、摘要或身份不一致都失败关闭。
- LobeHub server-only 介质只能由无网络定稿工具补入已签名/已审批客户端；工具必须先校验原目录的完整
  `SHA256SUMS`、版本和镜像身份，不修改原目录，并在共享客户端门禁全部通过后才写入两个成功状态和新清单。
  `--force` 只能替换经校验的精确输出，输出不得等于或包含任何客户端、证据、版本锁、部署脚本、平台仓库或
  server-only 输入。输出父目录必须预先存在；相邻锁、复制前后摘要与 inode 复核必须拒绝输入变化和并发目标
  替换，且不得删除并发方创建的目录。
  LobeHub fork 转运只能从干净、锁定的 `main` 与 annotated 内部 tag 生成自包含 Git Bundle，只发布这两个 ref，
  并执行独立 clone、内外两层 SHA-256 校验；生成器必须扫描 fork 增量全部可达 blob/commit/tag 和当前 tag，
  命中高置信私钥/token 格式时失败关闭。转运包不携带 Git 配置、企业 Git URL 或 credential helper 数据；历史
  扫描不能替代企业 Git 持续 secret scanning。企业管理员推送后必须只读复核远端 branch/tag commit。
- 企业离线版必须在代理与工作流 router 两层拒绝 `/api/workflows/*`，不得配置 QStash。定时任务只允许单一 app
  实例使用至少 32 字节的独立 `ENTERPRISE_INTERNAL_SCHEDULER_SECRET` 调用 loopback 内部入口，并强制
  `AGENT_RUNTIME_MODE=local`；该密钥不得进入浏览器、日志或进程命令行，queue 模式必须失败关闭。
- 当前企业现场纯 HTTP 会使表单票据、Session Cookie 和服务端委托暴露于同网段窃听与劫持风险。网络隔离、
  短票据、HMAC、nonce、短 Session 和 scope 只能缓解，不能替代 TLS；该剩余风险必须进入上线审批。

稳定交接契约、客户端证据和部署门禁见 `docs/architecture/lobehub-integration.md`、
`docs/deployment/lobehub-client-build.md` 与 `docs/deployment/lobehub-offline.md`。

## 安全变更文档

鉴权、限流、CORS、密钥、日志脱敏变更必须同步 `docs/standards/security.md`、`docs/api/http-api.md` 和相关 README。

## 撤销重发安全边界

- 浏览器手动入口必须重新校验登录 actor 是源消息实际发送人；源 Run 为共享人工重发替代 Run 时，必须以持久化 requester 审计纠正历史上可能被执行所属人覆盖的发送人归因。分享发送人还必须有 `canChat=true`，会话所属人只有在其本人就是实际发送人时才能操作。同时校验可信 `contextToken`、根会话、最后远端 user message、终态 Run 和会话活动锁；
  `expectedRunId/expectedRemoteMessageId/clientRequestId` 都只是并发前置条件，不是授权事实源。
- Java→Java 内部分发只对精确路径豁免用户 token，并使用既有 XXL access token 常量时间校验；跨服务器固定复用公共路由解析与
  HTTP 转发器，不允许浏览器指定目标后端或通过本机降级绕过目标服务器。
- PostgreSQL `run_resends`、RunEvent、访问日志、trace、错误详情和 session log 禁止记录 prompt、附件正文、回答、reasoning、
  工具输入输出、供应商错误正文、Authorization 或上下文 token。只允许安全错误摘要、次数、身份和 traceId。
- 精确重放输入只能进入有限 TTL Redis，缺失时必须在 revert 前失败；未知投递状态保持锁并继续探测，禁止重复发送；只有稳定替代
  message ID 明确不存在时才允许 unrevert，unrevert 回包未知仍不得解锁。
- 源 Run 明细清理不得删除 feedback、usage、Run 或重发审计关系；API/SSE 只返回 additive 元数据，旧客户端安全忽略。

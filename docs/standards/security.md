# 安全规范

本规范适用于后端和前端所有安全相关修改，合并原安全规范、日志脱敏规则和 PTY WebSocket 安全例外。

## 鉴权与授权

1. 对外 API 默认需要鉴权，公开接口必须在 API 文档中明确说明。
2. 权限判断应在入口层或应用服务层完成，不散落在 Repository。
3. 前端只做展示和交互控制，不能依赖前端判断作为最终权限控制。
4. 全局角色 `SUPER_ADMIN` 继承 `APP_ADMIN` 的应用配置管理权限，但认证响应仍返回用户实际拥有的角色，不伪造派生角色。

认证方式（按优先级）：

1. **用户 Token 鉴权**（`JwtAuthWebFilter`）：所有 `/api/` 请求自动检查 Bearer Token。Token 通过 UUID 生成，存储在 Redis，1 天过期。登录接口 `/api/auth/login` 无需 Token。
2. **静态 API Token 兜底**（`ApiTokenWebFilter`）：未配置用户 Token 时，检查 `TEST_AGENT_API_TOKEN` 环境变量，向后兼容。

Token 校验流程：
- `JwtAuthWebFilter`（Order +10）优先检查用户 Token，有效时设置 `AuthPrincipal` 到请求属性。
- `ApiTokenWebFilter`（Order +20）作为静态 API Token 兜底，未配置时放行。
- opencode runtime 代理可以读取可选 `AuthPrincipal`：存在用户主体时业务层使用用户专属 opencode 进程；只有 static token 或本地放行而没有用户主体时，才允许走固定 `execution_nodes` 兼容 fallback。静态 API token 不得被伪装成用户身份。

本地占位策略：

- Redis 是系统必需依赖，用户 Token、运行心跳、调度锁和运行指标均不提供内存降级。
- 未配置 `TEST_AGENT_API_TOKEN` 时，`/api/**` 默认放行，便于本地联调。
- 配置 `TEST_AGENT_API_TOKEN` 后，`/api/**` 必须携带 `Authorization: Bearer <token>`。
- 鉴权失败返回统一错误格式，错误码 `UNAUTHENTICATED`，不得回显 token。
- Actuator health 不使用占位 token，生产暴露范围后续单独收敛。

## 限流

1. 登录、创建 session、发送 message、代理 opencode 请求等高风险接口必须考虑限流。
2. 限流返回统一错误格式和 `429` 状态码，限流 key 不得直接暴露敏感 token。
3. 内存限流只作为本地和测试占位，通过 `test-agent.rate-limit.enabled` 开关启用；生产必须替换为网关或 Redis 等分布式限流，不能依赖单实例内存计数。

## 密钥与配置

1. 禁止硬编码密钥、token、账号、生产地址；密钥只能来自环境变量、配置中心或本地安全配置；示例配置必须使用占位值。
2. 日志、错误响应、前端状态不得输出密钥。
3. 后端生产容器只运行 Java 进程；数据库、Redis 和 opencode server 地址必须从外部配置注入，不能写入镜像或仓库。
4. 前端不得把密钥写入源码、localStorage 或可公开构建产物。
5. 个人 Git SSH 私钥必须使用 AES-GCM 加密后落库，加密密钥来自 `TEST_AGENT_SSH_KEY_ENCRYPTION_KEY` 或 `test-agent.security.ssh-key-encryption-key`，要求为 Base64 编码的 16/24/32 字节 AES key；不得提供硬编码默认值。
6. SSH key API 只能返回 `sshKeyId/name/fingerprint/createdAt` 元信息，禁止回显私钥明文或密文。指纹基于规范化私钥内容的 SHA-256 生成。
7. Git SSH 远端命令只允许使用当前登录用户保存的唯一 SSH key。临时私钥文件必须设置最小可行权限并在命令结束后清理；Git 命令环境必须禁用交互式凭据提示。
8. 应用版本工作区和个人工作区的 Git clone/worktree/diff/push/pull/副本同步仍只允许使用当前登录用户保存的唯一 SSH key；不得回退到机器账号、部署用户默认 SSH key 或其他用户 key。托管根目录优先来自 `common_parameters.OPENCODE_APP_WORKSPACE_ROOT` / `OPENCODE_PERSONAL_WORKTREE_ROOT`，缺失时回退 `test-agent.managed-workspace.root` / `TEST_AGENT_MANAGED_WORKSPACE_ROOT`；磁盘目录已存在时只能在校验目标 origin URL 和分支匹配后接管，不得覆盖或删除未知目录。跨服务器副本同步在 `fetch/reset --hard` 前必须确认工作树无未提交变更，否则标记副本 `FAILED` 并拒绝静默覆盖。
9. 设置页创建应用工作空间的 `workspace_create_operations.error_message` 只能保存平台安全错误说明或通用失败文案，不得写入 SSH 私钥、token、Authorization、Cookie、完整命令行、完整用户输入或敏感路径片段。
10. opencode-manager 控制面必须使用独立 manager token，配置键为 `test-agent.opencode.manager-control.token` / `TEST_AGENT_OPENCODE_MANAGER_TOKEN`；不得复用用户 JWT、普通 `TEST_AGENT_API_TOKEN` 或 opencode server 密钥。生产环境该 token 必须由环境变量或配置中心注入，示例只能使用占位值。
11. 超级管理员运行管理 API 必须使用用户 JWT，并由后端强制校验 `SUPER_ADMIN`；前端菜单可见性只作为体验优化，不能作为权限边界。
12. 定时任务管理 API 必须使用用户 JWT，并由后端强制校验 `SUPER_ADMIN`；前端系统管理菜单可见性只作为体验优化。管理员手动触发运行记录必须写入 `requestedByUserId` 和 traceId，停止正在执行的运行记录必须写入 `stopRequestedAt`、`stopRequestedByUserId` 和 `stopReason`。scheduler 启用时必须使用 Redis 分布式锁，不得回退到本机锁或数据库锁，以免分布式多节点重复执行。

## 日志脱敏

必须脱敏或禁止记录：

- Authorization、Cookie、API key、token。
- 用户输入中的敏感内容。
- 文件路径中的隐私片段。
- 过大的请求体和响应体。

日志配置必须对可变 message、thread 和 traceId 做 CRLF 编码，避免换行注入伪造日志记录。opencode 节点 health、Redis health、scheduler 运行记录日志和 opencode-manager 控制面日志必须避免输出 token、完整 Authorization header、Cookie、用户输入或完整 prompt。

## Web 安全

1. CORS 必须明确允许来源，不使用无限制生产配置。本地默认允许主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E 端口（`localhost`/`127.0.0.1` 的 `3000`、`4173`、`4177`、`4187`、`5173`、`5174`）；局域网 IP 调试必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 或根目录启动脚本追加实际前端 origin；生产环境必须通过配置显式声明允许来源。
2. 安全响应头必须在 `test-agent-api` 的入口配置中统一定义，并由 `test-agent-app` 装配生效。
3. Druid Web 控制台默认关闭；如后续启用，必须通过环境变量配置账号、密码和访问 allowlist，并同步 API、运维和安全文档。
4. 旧 `/api/...`、新 `/api/internal/platform/...` 和 `/api/internal/agent/opencode/...` 共享同一鉴权、限流、CORS、traceId 与错误格式。Workspace 文件 API 和文件 WebSocket RPC 必须把所有请求路径归一化到注册的 workspace root 内，路径穿越或越权访问返回 `FORBIDDEN`。
5. Workspace 目录选择器只能浏览 `test-agent.workspace-picker.allowed-roots` / `TEST_AGENT_WORKSPACE_PICKER_ROOTS` 声明的本机根目录，默认 `${user.home}/workspace`；越出允许根目录返回 `FORBIDDEN`，缺失或非目录返回 `VALIDATION_ERROR`，前端不得直接调用浏览器、本地插件或 opencode server 枚举任意磁盘路径。超级管理员服务器工作空间选择器只能通过目标后端签发的文件 WebSocket ticket 浏览目录。
6. opencode-manager WebSocket 控制面只允许容器内 manager 使用独立 token 访问，不接受浏览器用户 token；manager 不得通过 HTTP 与 Java 后端交互，后端列表发现只通过 WebSocket `backendListRequest/backendListResponse` 完成。返回给 manager 的 `listenUrl`/`webSocketUrl` 必须是 manager 可访问的后端实例直连地址，不应暴露到公网或不可信网络。
7. 用户专属 opencode server 默认监听 `0.0.0.0:{port}` 且不设置 Basic Auth，生产必须用容器网络、主机防火墙或内网网关限制端口池访问面；浏览器和外部系统不得直接访问这些端口。
8. `tools/verify-opencode-process-deployment.sh` 只用于只读 smoke check；传入的 manager token 和 `SUPER_ADMIN` 用户 token 不会由脚本打印。生产执行时应使用临时 shell、禁用命令历史或通过安全变量注入，避免 token 留在 history 中。

## 平台文件 WebSocket 安全例外

工作区文件与 Agent 配置文件操作属于受控 WebSocket 例外。前端不得直连 opencode server 或任意文件服务，必须先通过平台后端解析目标服务器，再使用目标后端的一次性 ticket 建立 WebSocket。实现和后续扩展必须满足：

1. `file-ws-route` 必须基于当前登录用户的 opencode 进程解析目标后端，并强校验 `workspace.linuxServerId == opencodeProcess.linuxServerId == targetBackend.linuxServerId`；历史 `workspace.linuxServerId` 为空时只能在 root path 校验成功后回填。
2. Agent 配置文件必须通过 `agent-config/file-ws-route` 按 `scope/workspaceId/worktreeId/linuxServerId` 解析目标后端；公共 worktree 使用落库 `linuxServerId`，公共直接模式必须由前端传入已初始化公共配置服务器 ID。
3. ticket 只能通过用户登录态创建，短期过期、一次性消费，并绑定 workspace、目标服务器、当前 agent 服务器、模式、Agent 配置 scope/worktree、traceId 和是否 `SUPER_ADMIN`；不得把长期 Bearer token 放入 WebSocket URL。
4. WebSocket upgrade 必须校验 Origin 白名单、ticket 有效性和 ticket 模式；ticket 消费后无论连接成功与否都不能重复使用。
5. `workspace.list/read/write/status/delete` 必须绑定 ticket workspace，路径必须归一化在 workspace root 内；删除默认只允许普通文件，目录删除返回统一错误，不允许递归删除。
6. `agent-config.list/read/write` 必须绑定 ticket scope、workspaceId 和 worktreeId；读取允许登录用户，写入必须校验 `SUPER_ADMIN`，路径仍由 workspace-management 文件服务归一化。
7. `directory.list` 只允许 `directory-picker` ticket；跨服务器目录浏览仅 `SUPER_ADMIN` 可创建 ticket，普通用户只能浏览当前 agent 同服务器目录。
8. `workspace.create` 必须要求 `SUPER_ADMIN`，并且选择服务器与当前 agent 服务器一致；不一致时前端禁用输入，后端仍必须返回 `CONFLICT` 或 `FORBIDDEN`。
9. 日志和错误响应不得输出 ticket、Authorization、Cookie、完整用户输入、完整文件内容或敏感路径片段；审计只记录 traceId、workspaceId、worktreeId、服务器 ID、操作类型、路径摘要和错误码等必要字段。

## 服务器广播安全

后端内部服务器广播用于跨后端实例同步业务状态，不是浏览器 API 或 SSE。实现和后续扩展必须满足：

1. 广播 payload 只允许包含业务 ID、事件原因、服务器 ID、版本号、分支名、目标 commit hash 和 traceId 等必要字段；禁止携带 SSH 私钥、token、Authorization、Cookie、文件内容、完整用户输入或大段错误堆栈。
2. Redis pub/sub 仅作为同一可信后端集群内的实时增强通道；生产必须使用受控内网 Redis，并通过外部配置开启 `test-agent.server-broadcast.enabled=true`，不得在代码或示例中硬编码 Redis 密码或生产地址。
3. 消费端必须跳过本服务器来源事件，并在业务层做幂等校验；广播失败不能影响本机已完成的 Git/数据库主流程，漏消息由数据库目标 commit 与本机补偿扫描恢复。
4. 日志只记录 `eventId`、`type`、`traceId`、`versionId`、`linuxServerId` 和错误码等低敏字段，不能输出私钥、token、完整路径中的敏感片段或原始第三方错误详情。

## PTY WebSocket 安全例外

交互式 PTY 终端属于受控 WebSocket 例外。当前已落地后端 ticket、Origin、cwd workspace root 归一化、单次使用、每 session 单 active PTY、ticket 创建限流、input/resize 限速、审计、idle/hard timeout、输出截断和前端 terminal panel。实现和后续扩展必须满足：

1. 先通过 HTTP API 创建一次性 ticket，再使用 ticket 建立 WebSocket；不得直接以长期 Bearer token 暴露在 WebSocket URL 中。
2. ticket 必须绑定 session、workspace、execution node、traceId 和过期时间，且只能使用一次。
3. cwd 必须归一化在 workspace root 内，shell 必须走后端白名单；在白名单配置完成前，前端不得覆盖 shell。
4. WebSocket upgrade 必须校验 Origin、ticket、session/workspace 归属和限流。
5. input/output 审计日志默认只记录长度、事件类型、截断、退出码和必要状态，不记录完整终端内容。
6. input、resize、output buffer、idle timeout 和 hard timeout 必须有明确上限。
7. 断开连接、session abort、后端关闭或超时时必须清理 PTY 进程。

ticket 创建与 WebSocket 协议细节见 `docs/api/http-api.md`。

## 安全变更文档

鉴权、限流、CORS、密钥、日志脱敏变更必须同步 `docs/standards/security.md`、`docs/api/http-api.md` 和相关 README。

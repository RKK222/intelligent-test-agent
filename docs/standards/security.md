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

本地占位策略：

- 未配置 Redis 时，`TokenStore` 回退到 `InMemoryTokenStore`，Token 信息仅存储在本地内存，重启后丢失。
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
8. 应用版本工作区和个人工作区的 Git clone/worktree/diff/push 仍只允许使用当前登录用户保存的唯一 SSH key；不得回退到机器账号、部署用户默认 SSH key 或其他用户 key。托管根目录来自 `test-agent.managed-workspace.root` / `TEST_AGENT_MANAGED_WORKSPACE_ROOT`，磁盘目录已存在时只能在校验目标 origin URL 和分支匹配后接管，不得覆盖或删除未知目录。

## 日志脱敏

必须脱敏或禁止记录：

- Authorization、Cookie、API key、token。
- 用户输入中的敏感内容。
- 文件路径中的隐私片段。
- 过大的请求体和响应体。

日志配置必须对可变 message、thread 和 traceId 做 CRLF 编码，避免换行注入伪造日志记录。opencode 节点 health、Redis optional health 必须避免输出 token、完整 Authorization header 或用户输入。

## Web 安全

1. CORS 必须明确允许来源，不使用无限制生产配置。本地默认允许主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E 端口（`localhost`/`127.0.0.1` 的 `3000`、`4173`、`4177`、`4187`、`5173`、`5174`）；生产环境必须通过配置显式声明允许来源。
2. 安全响应头必须在 `test-agent-api` 的入口配置中统一定义，并由 `test-agent-app` 装配生效。
3. Druid Web 控制台默认关闭；如后续启用，必须通过环境变量配置账号、密码和访问 allowlist，并同步 API、运维和安全文档。
4. 旧 `/api/...`、新 `/api/internal/platform/...` 和 `/api/internal/agent/opencode/...` 共享同一鉴权、限流、CORS、traceId 与错误格式。Workspace 文件 API 必须把所有请求路径归一化到注册的 workspace root 内，路径穿越或越权访问返回 `FORBIDDEN`。
5. Workspace 目录选择器只能浏览 `test-agent.workspace-picker.allowed-roots` / `TEST_AGENT_WORKSPACE_PICKER_ROOTS` 声明的本机根目录，默认 `${user.home}/workspace`；越出允许根目录返回 `FORBIDDEN`，缺失或非目录返回 `VALIDATION_ERROR`，前端不得直接调用浏览器、本地插件或 opencode server 枚举任意磁盘路径。

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

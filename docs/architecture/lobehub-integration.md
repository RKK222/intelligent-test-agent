# LobeHub 企业集成契约

本文是平台与独立 LobeHub fork 之间的稳定契约。首个内部版本固定为
`v2.2.11-platform.3`，上游基线为 [LobeHub v2.2.11](https://github.com/lobehub/lobehub/releases/tag/v2.2.11)、
commit `5b4cef6`。当前仓库承载平台认证、模型网关、前端入口、数据库 migration 和离线交付准入；
LobeHub fork 源码不放入本仓库。本地开发默认从同级独立仓库 `../lobehub-platform` 读取，当前锁定 fork commit
为 `ccd0400fbe934ba929de637a315d25e969977c76`；版本事实源为
`deploy/internal/lobehub/version.env`。企业 Git 管理员仍须把该独立仓库推送到受控内部远端，本机 checkout
不能替代内部源码托管。

当前 fork 已实现平台票据消费/HMAC 兑换、24 小时 Session、用户和部门 Workspace JIT、委托加密、企业模型
适配、后台模型身份继承、离线请求门禁、BYOK/本地身份源关闭、默认私有对象和全部设备执行失败关闭。平台仓库
已提供真实镜像构建器、本地显式启动模式和现场 Docker/systemd 脚本。`v2.2.11-platform.3` 不开放 Linux 本地
执行；目标发行版/内核的真实沙箱和逃逸验收属于后续可执行版本的上线前置项，不得通过环境变量提前开启。

## 边界与状态

| 能力 | 平台仓库责任 | LobeHub fork 责任 |
|---|---|---|
| 登录入口 | 当前用户申请一次性票据；隐藏表单 POST | 消费表单、HMAC 兑换、建立自身 HttpOnly Session |
| 用户 | 校验平台用户存在且可登录，返回稳定 `userId` | 以平台 `userId` JIT 创建或更新用户，关闭本地注册/密码/自定义身份源 |
| 部门 Workspace | 规范化部门并返回摘要键 | 唯一映射、并发单创建、成员关系和结构化审计 |
| 模型 | 管理公开模型目录、能力探测、密钥注入、流式代理和每日聚合 | 只显示“企业模型”适配器，服务端加密保存用户委托 |
| 对象与资源 | 不承载 LobeHub 文件 | 私有 RustFS bucket、鉴权下载、默认私有资源和显式共享 |
| 终端能力 | 不复用平台/OpenCode 工作区 | 当前版本 Windows/Linux 全部执行失败关闭；后续 Linux 沙箱另行设计验收 |
| 离线 | 校验外部制品、版本、摘要、签名证据和许可证 | 禁止联网功能、遥测、运行期下载并输出 SBOM/白名单制品 |

完整介质未取得企业 Authenticode 签名的 Windows x64 客户端、批准的 Linux x86_64 客户端并通过本文和
`docs/deployment/lobehub-offline.md` 的现场验收前，`LOBEHUB_ENABLED` 必须保持 `false`。已有服务端阶段介质
只用于镜像、migration 和部署流程验证，不能冒充完整交付包。

## 浏览器登录交接

1. 工作台“通用问答”点击处理器同步执行 `window.open("about:blank", 唯一窗口名)`，并切断 `opener`。
2. 前端使用 `sessionStorage` 中既有平台 Bearer Token 调用
   `POST /api/internal/platform/lobehub-sso/tickets`。
3. 平台检查功能开关、平台会话、用户状态和非空部门，生成 32 字节 SecureRandom 票据。Redis 只保存
   SHA-256 摘要，TTL 默认 60 秒且不超过平台会话剩余时间。开关启用但 owner、虚拟邮箱域或固定基址仍为
   默认占位值时，在保存票据前失败关闭。
4. 前端校验 `consumeUrl` 是无 userinfo/query/fragment 的 HTTP(S) 地址，且路径精确为
   `/api/auth/platform/consume`，随后用隐藏表单 POST `ticket`。票据不得进入 URL、router、Web Storage 或日志。
5. fork 的 consume 入口立即把票据交给服务端兑换，不在浏览器中持久化；兑换成功后清除表单值并建立
   host-only、HttpOnly、SameSite=Lax 的 LobeHub Session Cookie。普通浏览器交接固定继续到
   `/api/auth/platform/client/authorize`；没有待确认客户端请求时，该路由再回到 LobeHub 首页。

直接访问聊天域名且没有有效 LobeHub Session 时，fork 只能跳转平台固定
`/lobehub/launch`。平台未登录时先进入现有统一认证，成功后恢复该固定路由；不得接受客户端提供的
`returnUrl`。已有 LobeHub Session 时直接进入，平台与 LobeHub 的登录、过期和退出保持相互独立。

## Desktop/CLI 浏览器确认

企业客户端不使用 OIDC discovery、JWKS、Device Code Flow、refresh token 或 `Oidc-Auth`。客户端固定向
LobeHub `/api/auth/platform/client/*` 发起浏览器确认：

1. `start` 接收客户端类型和 PKCE S256 challenge，返回五分钟有效的 32 字节随机 request ID、独立 poll
   secret、短验证码及固定聊天域名确认地址；Redis 只保存 request/poll secret 摘要。
2. 浏览器确认地址用 host-only、HttpOnly、SameSite=Lax 的五分钟 pending cookie 绑定 request 和 CSRF。
   没有 LobeHub Session 时只跳平台固定 `/lobehub/launch`；票据消费完成后回到同一确认页。CLI 在终端、
   Desktop 在应用内显示与浏览器相同的短验证码，用户必须显式允许或拒绝。
3. 客户端用 request ID 与 poll secret 轮询；批准后只交付一次、最长 60 秒的授权码。授权码兑换时先原子消费，
   再校验客户端类型及本机 PKCE verifier，失败不能重试旧码。
4. 成功兑换得到最长 24 小时、不可刷新的 opaque LobeHub client Session。Redis 只保存会话摘要；Desktop
   只写操作系统安全凭据存储，CLI 只写 mode `0600` 的自身凭据文件。客户端只用标准
   `Authorization: Bearer`，浏览器、设备和日志都不能得到模型委托。
5. fork 的 Next middleware、tRPC 和 OpenAPI 入口每次校验 Redis 会话并复查用户状态；无效 Bearer 不得回退
   浏览器 Cookie。显式客户端退出删除该会话并调用平台撤销用户模型委托，远端不可达时仍清除本机凭据。

企业离线策略在服务端统一拒绝全部 `/oidc/*`；该门禁在 Session 查询和未登录跳转之前执行，所以未认证请求
也必须直接得到 `403 ENTERPRISE_OFFLINE_FEATURE_DISABLED`，不能被改写成平台登录 `307`。旧 OIDC/JWT 客户端不能作为兼容回退。客户端确认 URL、平台
launch URL 和最终聊天域名均来自部署配置或编译期固定值，不接受任意 return URL。

## 服务 HMAC

兑换和撤销只接受 LobeHub 服务身份。请求头为：

- `X-LobeHub-Timestamp`：UTC Unix 秒。
- `X-LobeHub-Nonce`：16–128 位 `[A-Za-z0-9._~-]`。
- `X-LobeHub-Signature`：无填充 Base64URL HMAC-SHA256。

规范化原文严格为以下五行，末尾没有额外换行：

```text
{timestamp}
{nonce}
{UPPERCASE_METHOD}
{exact_path}
{lowercase_sha256_of_raw_body}
```

五行之间只使用单个 LF（`\n`）连接。

平台先检查至少 32 字节的共享密钥、默认 ±60 秒时钟偏差、签名常量时间比较，再把 nonce 摘要以默认
120 秒 TTL 写入 Redis。只有签名通过后才占用 nonce；同一 nonce 只能成功一次。fork 必须对“收到的原始
JSON bytes”签名，不能先 parse 后重新序列化。

## 兑换结果和用户映射

兑换返回以下可信字段：

- `userId`：LobeHub 外部用户主键，永久使用平台 `userId`。
- `unifiedAuthId`、`username`。
- `email`：`{unifiedAuthId}@{LOBEHUB_SSO_EMAIL_DOMAIN}`。
- `department`：NFKC、去首尾空白、连续 Unicode 空白折叠为一个 ASCII 空格后的名称。
- `departmentKey`：上述名称按英文小写归一化后的 UTF-8 SHA-256。
- `instanceRole`：配置的唯一初始 owner 为 `owner`，其他 `SUPER_ADMIN` 为 `admin`，其余为 `member`。
- `roles`：当前平台角色快照。
- `modelGrant`、`grantExpiresAt`：只供 fork 服务端保存的 opaque 委托。

fork 不得用邮箱作为用户主键，也不得依据组织、研发部或其它字段拆分同名部门。

## 部门 Workspace JIT（fork 责任）

fork 的独立 PostgreSQL 必须保存 `departmentKey -> workspaceId` 唯一映射。首次兑换在同一数据库事务中：

1. 以 `userId` upsert 外部用户资料，不覆盖 LobeHub 内部内容所有权。
2. 对 `departmentKey` 取得事务级锁或执行等价的唯一约束 upsert。
3. 不存在映射时创建一个名称为规范化 `department` 的 Workspace，并写入唯一映射；并发竞争者读取赢家。
4. 幂等加入当前 Workspace，写入结构化成员审计。
5. 更新用户当前部门指针，但不删除任何旧部门 membership。

不同组织下名称规范化后相同的部门必须进入同一个 Workspace。空部门在平台签票阶段即被拒绝，fork 也必须
二次拒绝，不能创建“未分配”公共空间。用户调动后加入新部门，同时保留旧部门 Workspace 的原有完整权限；
只有管理员显式撤销才移除。成员新增、调动保留、管理员撤销、导出、归档和删除至少记录操作者、目标用户、
部门摘要、Workspace、动作、时间和 trace/audit ID，不记录票据或模型委托。

对话、知识库、Agent 和文件创建时默认私有；只有用户显式共享后，同 Workspace 成员才可访问。服务端权限
判断必须与 UI 隐藏同时存在。

## 模型委托和企业适配器

平台在每次成功兑换时轮换 32 字节 opaque 模型委托：

- 绑定单一平台用户、`lobehub` client 和 `model-gateway` scope，最长 30 天。
- Redis 只保存 SHA-256 摘要；旧委托在轮换时原子删除。
- 每次模型调用重新检查委托、scope、client、过期时间和平台用户状态。
- LobeHub 退出、设备撤销或管理员操作调用撤销接口；平台退出本身不撤销委托。

fork 必须用独立外部密钥加密委托后再写数据库。浏览器、Desktop、CLI、插件、日志和错误响应都不得得到
该值。定时话题、标题、索引等后台任务使用创建者的委托；过期或用户失效时明确失败，不能回退 owner、系统
账号或其他用户。

### 离线后台调度

企业离线镜像不部署 QStash，也不接受它的回调。fork 在 Next 代理和独立 Hono router 两层拒绝所有
`/api/workflows/*` 请求，并把遗漏的 QStash client 出站地址固定到不可达 loopback。生产 launcher 每 60 秒
向 `127.0.0.1` 的 `/api/agent/enterprise/schedule-dispatch` 发起一次非重叠 POST，使用至少 32 字节、仅服务端
可见的 `ENTERPRISE_INTERNAL_SCHEDULER_SECRET` 进行 Bearer 认证；请求 30 秒超时，响应正文不进入日志。

该内部 handler 仅在 `LOBEHUB_ENTERPRISE_OFFLINE=1` 时存在，并强制 `AGENT_RUNTIME_MODE=local`；queue 模式
直接返回 503，不能回退 QStash。调度扫描从任务记录读取 `createdByUserId`，到期任务以内嵌
`runScheduleTick(taskId, userId)` 执行，因此后续模型调用仍解析创建者委托。当前部署契约只允许一个 LobeHub
app 实例；未来横向扩容前必须为 sweep 增加跨实例 leader/lock，不能直接复制 app 容器。

fork 只暴露一个“企业模型”适配器，Base URL 固定为平台
`/api/internal/platform/model-gateway/v1`。禁止 BYOK、自定义 Base URL、供应商 Header 和用户供应商设置。
Linux 客户端如需调用模型，必须通过 loopback broker 到 LobeHub 服务端，再由服务端携带委托调用平台。

## 离线与客户端强制策略（fork 责任）

- 关闭公网搜索、SaaS Connector、在线 Marketplace、遥测、更新检查、CDN 资源和运行期插件/模型下载；
  UI 被隐藏的路径也必须在 server action、API、深链和 Labs 中拒绝。
- 禁止 QStash 及公网 workflow 回调；定时任务只能通过上述带独立 Bearer 的 loopback 调度器以内嵌模式执行。
- Skills、工具和静态资源只能来自审批白名单制品；记录来源、版本、SHA-256、许可证和审批结果。
- RustFS bucket 保持私有，附件只通过 Workspace 鉴权的短期签名地址或受控下载代理访问。
- Windows x64 安装包必须使用企业 Authenticode 证书签名；每个 Windows 账号使用独立应用数据和凭据目录。
  terminal、shell、代码 Agent、stdio MCP、设备执行和 Agent 浏览器控制永久禁用。
- Linux 首期客户端目标为 x86_64，但 `v2.2.11-platform.3` 与 Windows 一样强制禁用全部本地/设备执行。
  后续版本只有在专用单用户受管工作站的目标发行版/内核通过真实沙箱逃逸验证后才能另行设计开启；所有 shell、
  CLI Agent 和后台进程入口必须共享一套显式 sandbox policy，默认拒绝外网、Unix socket、Docker socket、
  SSH Agent、完整 HOME 和平台目录，只开放独立 LobeHub 工作区、临时目录及审批内网地址；沙箱初始化失败
  必须 fail closed。当前版本不存在可绕过该限制的启用变量。
- LobeHub 工作区不挂载或复用平台/OpenCode 工作区，不新增平台文件 WebSocket 集成。

## fork 合并和制品门禁

独立 fork 至少需要自动化覆盖：票据过期/重放、固定回跳、客户端 request/poll/code/session 摘要存储与
一次性消费、PKCE 错配、验证码显示、OIDC 路径拒绝、JIT 并发单 Workspace、同名部门合并、调动后
双权限、默认私有资源、委托服务端加密、后台任务身份继承、离线断网、Windows 所有执行入口拒绝、Linux
沙箱逃逸边界和私有对象访问。构建产物必须输出源码包、SPDX SBOM、许可证、审批白名单、三份摘要镜像、
Windows/Linux 客户端及 Authenticode 证据；准入格式见部署文档。镜像必须带
`org.opencontainers.image.revision`，且与锁定 fork commit 完全一致。任何一项缺失时不得把
`LOBEHUB_ENABLED` 切换为 `true`。

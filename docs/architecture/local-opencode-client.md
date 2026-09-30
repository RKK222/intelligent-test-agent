# 本地 OpenCode 客户端架构

## 目标与范围

本地客户端让用户把自己机器上的绝对目录注册为平台 Workspace，同时继续由浏览器只访问后台 Java。
一个客户端实例监管一个 OpenCode 1.18.4 进程，可承载多个已注册工作区；同一用户的服务端 OpenCode
与一个本地客户端可以同时在线，同一用户不同本地实例不能同时保持实时连接。支持聊天（含聊天附件）、OpenCode Agent 自身工具、文件管理和夜间任务，不开放
浏览器终端、Git 发布、Agent 配置管理或协作分享。聊天附件复用同一条工作区分片上传 RPC 落到本地目录，Run 只投递工作区相对路径，
由客户端 OpenCode 自行读取，不向本地隧道内联文件正文。

```mermaid
flowchart LR
    W["Web"] <-->|"HTTP / SSE / 文件 WebSocket"| B["入口 Java"]
    B <-->|"BackendJavaRouteResolver + Forwarder"| O["连接持有 Java"]
    O <-->|"local-opencode-client.v1 / WSS"| C["本地 Java 客户端"]
    C -->|"127.0.0.1"| OC["OpenCode 1.18.4"]
    C --> FS["已注册本地目录"]
    N["内网 Nginx HTTP"] -->|"共享 catalog 与分平台签名制品"| C
```

## 模块边界

- `test-agent-local-client-protocol` 只定义 `local-opencode-client.v1` 帧、载荷、大小和时间常量，不依赖
  服务端领域或持久化。
- `test-agent-workspace-filesystem` 是服务端和客户端共用的安全文件内核。它承载相对路径约束、真实根
  锚定、符号链接防逃逸、无覆盖原子移动、预览、搜索及分片上传下载。
- `test-agent-local-client` 是 Java 21 可执行 JAR，负责用户级配置、稳定实例 ID、反向连接、进程监管、
  文件 RPC、loopback 模型中继和用户级系统托盘。托盘复用现有连接对象的重连入口、活动请求表及心跳状态，
  不创建旁路连接或额外 OpenCode health 轮询。
- `test-agent-system-management` 管理每用户唯一 client key 和客户端实例；`test-agent-opencode-runtime`
  管理连接路由、生命周期、Workspace/Session/Run 固定目标及隧道传输；`test-agent-api` 只承载入口、
  鉴权、DTO 和跨 Java 公共转发；`test-agent-persistence` 仅通过 MyBatis XML/Flyway 保存关系数据。
- `GeneratedOpencodeSdkGateway` 通过可注入的 `OpencodeWebClientTransport` 选择传输。服务端目标仍使用
  HTTP；本地目标使用隧道提供的 `WebClient ExchangeFunction`。generated SDK 和 OpenCode 源码不修改。

## 客户端功能可见性灰度

本地客户端相关功能默认对所有用户隐藏。`SUPER_ADMIN` 在“系统管理 → 用户管理”中按平台 userId 维护
`local_client_rollout_users`；只允许加入存在且可登录的用户，移出时保留操作人和时间。普通用户的
`download-access/me` 由收到浏览器请求的当前 Java 直接读取共享灰度表，只返回 `allowed` 布尔值；前端仅在
值严格为 true 时展示个人设置下载入口、本地实例状态、本地工作区和个人客户端设置。`opencode-endpoints/me` 继续
保留 `localClientDownload` additive capability，但它可能按进程归属转发到滚动升级中的旧 Java，因此不再
作为网页权威灰度来源。独立接口缺字段、查询失败或名单为空都失败关闭为隐藏，实例健康轮询也不会覆盖
灰度结论。头像菜单不再承载客户端安装包下载；个人设置始终提供“下载最新客户端包”作为首次安装、覆盖升级和
人工恢复的唯一网页入口。

该名单只控制 UI 可见性，不扩大认证权限：Nginx HTTP 制品仍按内网 ACL 提供，客户端连接仍必须使用有效
`tack_v1_` key 认证。默认传输为 HTTPS/WSS；当前企业 `mimo.sdc.cs.icbc:9996` 是前后台同时显式批准的可信内网
HTTP/WS 例外。移出名单不会撤销 key 或断开已安装客户端；需要停用客户端时仍使用
凭据撤销入口。灰度关闭时若浏览器正处于本地工作区，只关闭浏览器文件 RPC 并优先恢复进入本地工作区前的
托管工作区，其次恢复当前应用 recent，均不可用时保持未选择空态；客户端控制 WSS 和本地 OpenCode 不变。
管理员显式关闭服务端 OpenCode 只隐藏 `SERVER_PROCESS` 及服务端工作区，不得影响本地投影或当前本地选择。

## 连接协议与 fencing

客户端只主动连接 `/api/internal/platform/local-opencode-client/connections/ws`。首帧 `REGISTER` 带
client key、稳定 `lci_...` 实例 ID、平台、架构和版本；认证成功返回 `REGISTERED` 及递增的
`connectionGeneration`。认证后每帧必须带 `type/requestId/traceId/connectionGeneration`。

协议支持注册、5 秒心跳、生命周期命令、OpenCode HTTP/SSE、文件请求、256 KiB Base64 二进制分片、
模型 grant 更新、取消和稳定错误帧。发送方等待 WebSocket `sendText` 完成后才发送下一分片，形成有界
背压；请求由 requestId 关联并支持中断。Redis 在线记录 TTL 为 15 秒，只信任
`backendProcessId + connectionGeneration`。注册先锁定同一用户的数据库行，发布新 route 后撤销该用户其它实例的
Redis route、模型 grant 和物理连接；并发注册也按锁串行，最后完成认证的 generation 是唯一在线连接。相同实例的
新认证连接继续 fencing 旧 generation；旧连接的心跳、响应、文件票据和模型授权全部失效。客户端上报地址、端口只用于展示，绝不用于路由。

安装器的配置目录和状态目录由稳定启动器显式传给每次 Java 进程，避免桌面会话中的 XDG 变量变化使
`state.json` 漂移到另一目录并生成新实例 ID。重复执行 `setup` 会重新读取并校验签名 catalog，原子切换到最新
release，保留 `credentials.properties`、`state.json` 和 OpenCode 数据目录；若存在 `re-enrollment-required`
则先重新接入并清除标记，再重启已有 user systemd 服务。桌面应用入口执行稳定启动器的 `start`：普通情况仅启动或
重连，凭据失效时才进入交互式 enroll。用户主动退出仍保持 systemd `Restart=on-failure` 语义，不自动违背退出意图。
JDK 和 OpenCode 归档使用固定条目顺序、元数据和 gzip header 生成确定性摘要；新 release 只复用当前 release 中与
新签名清单大小、SHA-256 和 RSA 签名均一致的归档，再复制到新的完整不可变目录，任何一项不一致都重新下载。

Windows 10 1809（build 17763）x64 使用同一 Java 业务 JAR 和协议，但运行路径为 `.exe`，配置/状态分别落在当前用户
`%APPDATA%` / `%LOCALAPPDATA%`，稳定 Go 启动器通过当前用户任务计划运行。共享 catalog 的版本号全局唯一；麒麟安装器
从新到旧选择 `linux/arm64`，Windows 只接受 `windows/x64`。服务端对版本展示、通知、rollout、PREPARED 和补偿发送执行
同样的平台/架构复核。现有数据模型仍是一条全局目标加可选用户覆盖，因此一个全局目标只作用于同平台实例；需要同时推进
另一平台时必须使用对应用户覆盖或分批切换目标，不能把跨平台 release 当作通用版本。

## 认证与模型密钥

每个用户只有一个 `tack_v1_` client key，可用于该用户的多个客户端实例。数据库保存 RSA 密文、
SHA-256 摘要、掩码和版本；认证只比较摘要，普通查询不返回密文或明文。复制接口的明文响应强制
`no-store`，前端只在方法局部变量中写入剪贴板，不进入 DOM、Query cache 或浏览器存储。创建、复制、
轮换和撤销均写审计；轮换或撤销会断开该用户全部连接并撤销模型 grant。关系库继续保留稳定实例、平台工作区绑定
和用户磁盘目录引用，但用户实例列表只返回当前仍有 Redis 短 TTL 连接的记录，离线历史不展示。连接在线但
OpenCode 不健康时仍返回状态，便于执行重启。主动撤销后凭据变为 `REVOKED`，实例列表及本地工作区列表/详情立即
隐藏；重新启用凭据后，只有客户端重新认证并建立实时连接才恢复展示。

平台模型 key 永不下发。客户端启动 loopback 模型中继，并给 OpenCode 注入随机本地 token；后台只签发
绑定用户、客户端实例、generation 和持有 Java 的短 TTL grant。断连、换代、轮换或撤销都会使 grant
立即失效。声明 `MANAGED_MODEL_CONFIG_V1` 时，企业来源以 `OPENCODE_PUBLIC_CONFIG_DIR/opencode.jsonc` 的
V2 `providers` 和最后匹配的 `experimental.policies.provider.use`（同时兼容旧 V1
`model/small_model/enabled_providers/provider`）为配置事实源；OpenCode provider 名称与 Java 路由 ID 通过
V2 `headers.X-Enterprise-Model-Provider` 或 V1 `options.headers.X-Enterprise-Model-Provider` 显式映射，且只有能在 `InternalModelProviderRegistry` 同代快照中解析到
已启用供应商和 Token 的条目才下发。服务端把地址、API key 改写为 loopback 环境变量，只保留安全超时参数和路由头，
不会下发公共配置中的 UCID、服务器地址或密钥。UCID 由模型代理按 grant 所属用户重新解析并覆盖。

## 本地进程与目录安全

客户端从用户私有 `client.properties` 和权限为 `0600` 的 `client.key` 启动，命令行不接受 key。
OpenCode 固定监听 `127.0.0.1`，端口在 4096–4195 的受控范围内选择并持久化。启动成功必须同时满足：
进程仍存活、`ProcessHandle.startInstant` 可取得、loopback `/global/health` 成功。停止前同时核验 PID、
权威启动时间、真实可执行文件和启动参数；PID 复用或身份无法确认时失败关闭。已记录 PID 明确退出后只清理
客户端自身的陈旧身份，不把同端口后来出现的健康进程视为本客户端进程，也不对其执行停止；后续启动跳过该
占用端口并继续选择受控范围内的空闲端口。

企业客户端监管的 OpenCode 进程固定关闭 models.dev 拉取，并强制 npm 离线解析。OpenCode 1.18.4 会合并用户全局、
旧配置和当前受管配置目录，并可能为缺少 `node_modules` 的目录启动后台依赖检查；该检查只能读取本机缓存和随签名
公共能力包交付的完整依赖，不得访问公网 registry。非受管目录依赖缺失时快速失败，不能阻塞插件或 Tool 目录；受管
能力包依赖不完整时仍由目录验收失败关闭并回切上一不可变版本。
本地浏览器 Tool 的 `playwright-core` 同样来自签名能力包；目标用户不需要系统 Node。Tool 在 OpenCode/Bun 进程中以
原生 WebSocket 实现 Playwright 公开的 `ConnectOverCDPTransport`，并把 `/json/version` 返回的 WebSocket 再次约束为
客户端 Relay 已确认的同一 `127.0.0.1` 随机端口，不能借浏览器状态响应连接其它本机服务。

注册工作区时，持有连接的客户端执行 `toRealPath`、目录/读写权限和文件系统身份校验，后台再事务性创建
Workspace 与 `local_client_workspaces` 绑定。后续 RPC 只接收 workspaceId、root digest 和相对路径。
安全内核在每次操作重新锚定真实根，拒绝 `..`、绝对路径、符号链接逃逸、校验后的替换竞态和跨根移动。
删除平台工作区只归档平台记录，不调用本地目录删除。

浏览器继续使用现有 `file-ws-route → ticket → /file/ws`。本地 ticket 额外冻结 runtime kind、实例 ID、
generation 和 root digest；持票 Java 通过现有文件 WebSocket handler 调用反向隧道，不新增 Java 间文件
HTTP 代理。

注册本地工作区的默认入口位于客户端托盘。用户点击“选择并注册工作区”后由客户端桌面打开原生目录选择器，
目录名作为默认工作区名称，并通过已认证反向连接发送 `WORKSPACE_REGISTER`。后台从连接状态取得 owner、实例 ID
和 generation，异步调用既有 `LocalWorkspaceApplicationService`；该服务仍通过 `FILE_REQUEST` 完成真实路径、
权限、符号链接和文件系统身份校验与根注册，再事务性持久化。WSS 入站处理必须先释放当前 `concatMap`，避免等待
根校验时阻塞同一连接的 `FILE_RESPONSE`。`VERSION_CHECK`、更新状态和公共能力通知等数据库型帧进入每连接
独立的 64 项有界串行队列，保持通知顺序但不占用隧道响应入站通道；`FILE_RESPONSE`、`LIFECYCLE_RESULT` 和其它
requestId 关联回包仍由当前入站流立即交给 pending request registry，Redis 心跳也独立调度以免被数据库通知拖到 TTL
过期。网页仅保留 `directory.list` 逐层浏览和 HTTP 注册兜底，单击目录表示
选中、双击才进入下一级。注册按用户行串行化；同一用户、客户端实例和 root digest 命中时复用既有 Workspace ID，
并重新下发 `workspace.registerRoot` 恢复客户端状态，不再创建可能触发唯一约束的临时 Workspace。若重装导致
`clientInstanceId` 变化时，用户从工作台选择历史工作区即可触发恢复：后台把已保存的规范路径交给当前唯一在线客户端
重新校验，只有旧实例离线且客户端返回的
`rootDigest + fileSystemIdentity` 与唯一历史绑定完全一致时才保留 workspaceId 并切换绑定；多个候选或旧实例在线均
失败关闭。自动恢复和手工激活先在事务外用 30 秒有界 `workspace.validateRoot/registerRoot` 完成客户端 RPC，再进入
短事务取得用户注册锁、复核 binding 与 connection generation 并落库；24 小时超时只保留给大文件传输，数据库锁内
不得等待客户端回包。用户从托盘重新选择同一目录仍作为目录位置变化时的人工兜底。接管事务同步迁移该工作区的 Session 冻结目标和尚未投递的夜间任务；旧实例的全部工作区都接管完成后写入
替换关系，但保留旧实例、Run 和终态任务历史外键。用户活动实例列表本身只消费实时连接，因此旧实例离线后即不展示；
旧实例若真实重新认证则清除替换标记并重新进入活动列表，其目录仍需逐个重新校验，不能仅凭客户端名称自动抢占。

客户端收到注册成功帧后自动打开 `/workbench?localWorkspaceId=<workspaceId>`，托盘“打开网页”在本次进程已有最近
注册结果时使用同一深链。URI 不携带本机绝对路径；前端通过带对象级归属校验的 Workspace API 解析逻辑 ID，切换为
`LOCAL_CLIENT` 工作区语义后仍使用既有 `file-ws-route → ticket → /file/ws` 加载文件树，并关闭 Git、版本和物理路径
复制入口。文件树调用客户端已实现的 `workspace.list` 普通目录操作，不调用服务端托管工作区专用的组合引用视图
`workspace.view.list`。ticket 签发涉及的 MyBatis/Redis 同步校验在 `boundedElastic` 执行，不占用 WebFlux
event-loop。工作台顶部直接复用平台 Workspace 列表展示已持久化的本地工作区，可按逻辑 ID 重开其它已注册目录，
并可返回最近服务器应用工作区。前端必须先调用上述激活接口并使用返回的新实例身份，再切换 Workspace 和发起文件
WebSocket；激活失败时不能先渲染不可读工作区。浏览器不另存本机绝对路径。登录页的受控 `redirect` 会保留该同源深链查询参数。

## Session、Run 与夜间任务

`RuntimeKind` 取 `SERVER_PROCESS` 或 `LOCAL_CLIENT`。本地工作区绑定稳定实例 ID，不创建服务端 process 或
binding。Session 创建时冻结目标，Run manifest/context/execution node 再冻结并传播同一目标；本地实例
离线或换代不回退服务端进程，也不在 Run 路由时按在线状态任意切换到其它客户端。唯一例外是用户显式选择历史工作区后，
上节激活接口在当前唯一在线实例完成真实目录身份校验并接管；它在同一数据库事务内同步改写 Workspace、Session 和待执行夜间任务的本地实例目标。路由仓储读取失败同样失败关闭；
workspace/session 在 path、query 或请求体中的入口都先解析本地目标，再决定连接持有 Java。

存量 `agent_session_bindings`、Session 旧映射和 `routing_decisions` 仍以外键引用
`execution_nodes`。为保持这些关系约束，本地目标会保存一条 `OFFLINE` 且 capability 为
`local-client-anchor` 的外键锚点；它的地址无效、容量为零，不会进入全局服务端节点选择。
真实运行目标始终从 Session/Run 冻结字段和 Redis 当前 generation 重建，禁止把该锚点当成
服务端 OpenCode 目标读取。

夜间任务保存 `targetRuntimeKind + targetLocalClientInstanceId`。到点离线时保持当前执行窗口内待处理，窗口
内同一实例重连后执行；窗口结束为 `WINDOW_EXPIRED`。Run 已开始后断连终止为
`LOCAL_CLIENT_DISCONNECTED`，不自动重跑，避免对本地文件产生重复修改。

## 受保护 Agent/Skill 的服务器执行

本地工作区的 Agent 目录会在原生 OpenCode Agent 之后追加当前用户可见的已发布 Hub Agent，选择 ID 使用
`protected:{revisionId}` opaque 句柄。目录响应只有名称、说明、不可变修订 ID 和内容 SHA-256，不返回
`AGENT.md`、`SKILL.md` 或其它制品正文。公共内置 Agent 对应系统级受保护资产；应用 Hub 已发布 Agent 继续
沿用应用成员可见性和管理员发布边界。个人可修改配置仍留在本地 OpenCode，不冒充受保护资产。

```text
平台已发布 Agent/Skill 修订
          │ 服务器解析正文、冻结 revision + SHA-256
          ▼
服务器 OpenCode（隔离空目录）
          │ short-lived MCP grant
          ▼
当前 Java ── WSS FILE_REQUEST ── 本地客户端 ── 用户授权目录
```

受保护 Run 仍先校验网页签发的本地 `contextToken`，但只复用其中的用户、Session、Workspace、客户端实例和
generation 身份，不复用本地执行节点或本地绝对路径。Run 强制路由 `SERVER_PROCESS`，服务器 OpenCode 使用
每用户/Session 的 mode `0700` 隔离目录；其原生 bash/read/write/glob/grep 等文件工具被关闭，只能调用平台
`local_files_*` MCP。MCP 再通过既有本地文件 WSS route/generation/root digest 调用安全内核，不新增 HTTP 文件
代理，也不把 Agent/Skill 正文同步到客户端配置目录。

文件 grant 只驻留签发 Java 的有界内存，Redis/数据库不保存明文 Token；每次调用重新检查 Run 未终态、
Workspace 绑定、连接 generation、root digest 和持有 Java，任一变化立即失败关闭。Skill 附件资源留在 grant
中供服务器模型只读使用。应用 Hub Agent 按发布依赖表冻结 Skill；公共内置 Agent 则只从同一 Git commit 的
数据库快照中选取 `AGENT.md` 按完整技术 ID 明确引用的 Skill，禁止扫描当前工作树或跨提交拼接。资源目录
返回 `{name,path}`，读取时强制校验二者一致，使运行态 Skill 指标只从 `args.name` 取名。
Skill 正文不预载进 system prompt；Agent 指令要求加载时必须真实调用只读 MCP，调用事实与实际加载保持一致。
`run.created.payload.protectedAgent` 只记录 Agent/Skill 的 assetId、revisionId 和
SHA-256，便于审计复现，不记录提示词、文件正文或 grant。

该能力不增加客户端轮询：Agent 目录只随页面请求解析，文件鉴权只在模型真实调用工具时执行。托盘仍复用
原 5 秒心跳快照；受保护文件调用共享现有 WSS 背压和客户端 64 项活动请求上限。

## 兼容性

新增响应字段都追加为可选字段；旧 Session、Run、manifest、execution node 和夜间记录缺少 runtime 字段
时反序列化为 `SERVER_PROCESS`。未携带 workspaceId 的既有 OpenCode API 保持服务端目标语义。本地专属
能力通过 capability 字段显式关闭终端、Git 发布、Agent 配置和分享；聊天附件按
`attachments=true` 开放，但服务端按冻结运行时目标强制降级为工作区相对路径投递：本地工作区在用户机器上，
服务端 JVM 无法把客户端路径（Windows 盘符、UNC 或其它平台形态）解析成客户端可用的 `file://` 地址，
内联正文又会占用本地隧道有限请求体额度，因此客户端 OpenCode 用自带 Read 工具读取附件，
图片和 PDF 仍由该工具按原生附件投递给模型。
`protectedAgentExecution=true` 只表示可选择服务器受保护 Agent，不改变 `agentConfig=false`，前端不能仅凭
在线状态推断。

本地隧道构造的 `WebClient` 显式使用 generated SDK 对应的 Jackson 2 JSON codec，避免 Spring 7 默认
Jackson 3 codec 无法反序列化 SDK `JsonNode`。SSE 的 `STREAM_OPEN` 与后续 body 由独立有界流转发；取得
HTTP 响应对象不会取消后续事件，页面取消订阅时仍会沿既有隧道发送取消帧。

## 桌面托盘与资源约束

macOS、Windows 和提供 Java SystemTray 的麒麟 ARM 桌面显示客户端托盘；不支持托盘或无图形会话时降级为后台服务，
协议和本地文件能力不受影响。托盘复用 Web 端 `radar-bunny.png`，显示连接状态，并提供打开网页、重连、
查看/下载客户端日志、会话进度和退出动作。`webUrl` 与控制面的 `serverUrl` 分离，便于开发环境分别使用
前端和后台端口；两者生产都必须为 HTTPS。托盘点击弹层由 FlatLaf Swing 卡片统一渲染，复用目录选择等客户端窗口的
14px 字体、白色卡片、轻边框和圆角反馈；SystemTray 只承载图标和点击事件，不再挂载平台原生 AWT PopupMenu。

会话进度来自当前连接最多 64 个有界活动请求，只展示操作类型、数量和耗时，不展示 prompt、路径或请求体。
托盘定时刷新只复制内存快照，状态不变时不重绘；OpenCode 状态复用原有 5 秒心跳结果，不新增健康探测。
客户端日志目录在桌面主题和任何 Logger 初始化前创建并写入日志系统属性，由 JVM 自行滚动到 state 目录。Java
`client.log` 为每次 JVM 启动生成 `trace_client_*` session，并在启动、接入、WSS、工作区注册、生命周期、自更新、公共能力、
模型转发和 OpenCode 监管阶段记录受控事件名、requestId/traceId、connection generation、状态、耗时及根异常类型。
麒麟 Shell 和 Windows Go 启动器都在同一目录写 `launcher.log`，以启动 session 串联前置检查、签名校验、release 切换、
运行时自检、接入、自启、激活及回退；达到 5 MiB 后只保留一份 `launcher-1.log`。Windows 另写安全失败摘要
`windows-launcher-error.log`。日志不包含统一认证号、Client key/token、认证头、服务端正文、异常 message、prompt、请求正文
或工作区文件内容。托盘导出限制为最多 20 个 client/launcher 日志、每文件末尾 10 MiB，明确排除密钥、配置、
`opencode.log` 与工作区文件。

Observability 使用独立 loopback relay token，不复用模型 grant。插件 hook 仅把必要引用放入有界队列，后台微任务脱敏和
序列化；Java 以最低优先级单线程写未确认 spool。WSS 发送顺序固定为控制/模型/文件优先，Trace 只有在它们全部空闲至少
3 秒后才允许单分片在途。ACK 丢失按相同摘要幂等重传，旧 connection generation 的上传与 ACK 失败关闭。服务器一旦归档，
Trace 正文读取不依赖客户端在线。

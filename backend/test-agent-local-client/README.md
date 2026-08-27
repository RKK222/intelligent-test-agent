# test-agent-local-client

Mac/麒麟/Windows 普通用户侧 Java 21 客户端。Windows 交付下界为 Windows 10 1809（build 17763）x64。负责 WSS 反向连接、OpenCode 1.18.4 进程监管、本地文件 RPC、工作区原生选择、
整客户端自更新和公共 Agent/Skill/Tool 完整能力包激活。客户端不直接访问平台数据库，也不把本地 OpenCode 请求
回退到服务器运行时。

## 安装与稳定身份

- 麒麟稳定启动器的重复 `setup` 是就地升级入口：每次重新校验签名 catalog，安装并原子切换最新 release，随后 restart
  已有 user systemd service；配置、凭据、`state.json`、OpenCode 数据和工作区根映射位于 release 目录之外并保持不变。
- Windows 安装器和稳定启动器是 Go x64 PE：安装器把签名 release 写入当前用户 `%LOCALAPPDATA%`，配置写入
  `%APPDATA%`，通过当前用户任务计划和开始菜单启动，不要求管理员权限或系统 Java。稳定启动器只接受
  `windows/x64` release，并在切换前复验清单及全部原始制品的大小、SHA-256 和 RSA 签名；PE 本身还必须在正式发布前
  完成企业 Authenticode 签名和真实 Win10 验收。
- 服务端拒绝旧 Client key 时，Java 会写入 `state/re-enrollment-required`。重复 `setup` 或应用菜单中的“Test Agent
  本地客户端”启动动作会先识别该标记，要求用户在本机重新输入统一认证号和当前 Client key，成功后清除标记并重启
  user systemd service；没有标记时普通启动不会重复索取凭据。用户从托盘主动退出后服务保持停止，必须由该桌面入口
  显式启动，避免违背主动退出意图。
- JDK/OpenCode 归档采用确定性打包；稳定安装器与 Java 自更新统一复用安装根下按 `KIND/SHA-256` 内容寻址的签名缓存，
  命中时仍按新 manifest 校验大小、摘要和当前发布公钥签名。旧 release 只用于首次迁移填充，不是长期命中条件；摘要变化、
  缓存缺失或验签失败时只重新下载对应制品。release 目录仍是完整不可变单元，不使用跨目录硬链接。
- 启动器对 enroll、运行、自更新激活和候选 self-check 显式传入同一配置/状态目录，防止 XDG 环境差异让 Java 读取另一份
  `state.json`。`clientInstanceId` 只在该稳定状态文件首次创建时生成，普通重装和升级不得生成新值。

## 桌面界面

- `LocalClientDesktopTheme` 是首次配置、目录选择、会话进度和公共能力确认窗口的统一主题入口。macOS 与麒麟/Linux
  统一使用随 shaded JAR 离线交付的 FlatLaf 3.7.2，不再选择系统 Aqua、JDK Nimbus 或 Metal；界面使用 14px 对话字体、
  白色卡片、轻边框、圆角控件和深蓝灰主操作，不使用红色作为客户端主色。主题初始化失败只降级界面，不能阻断 WSS 连接。
- 托盘点击弹层改由 `LocalClientTrayPopup` 使用同一 FlatLaf 主题渲染，顶部复用小兔子和实时连接状态，动作分组、圆角、
  轻边框与工作区选择窗口保持一致；不再使用无法消费 Swing 主题的原生 AWT `PopupMenu`。会话进度与能力确认继续使用
  无遗留 Java 吉祥物图标的纯内容对话框。
- `LocalClientMain` 在任何桌面主题或 Logger 初始化前创建 state 日志目录并设置日志路径，确保 `client.log` 稳定写入
  用户状态目录，避免首次启动因占位路径过早固化而缺失文件。
- 双击安装触发的短时 `enroll` 失败也由顶层错误报告器写入同一 `client.log`。凭据拒绝、企业入口连接失败、平台稳定
  错误码和协议响应异常分别使用固定类别；日志只附根异常类型，不记录统一认证号、Client key、异常消息、服务端错误正文
  或 details，终端会保留可执行的中文提示和日志绝对路径，不再只显示 `exit code=1`。

## Git 权限巡检

- 新客户端声明 `WORKSPACE_GIT_ACCESS_V1`，通过既有受认证文件 RPC 接收 `workspace.git-access.check`；工作区 ID 与根摘要仍由注册表校验，服务端不能传入任意本地路径。
- 客户端只读执行本地 Git 仓库识别、`remote.origin.url` 读取和 `git ls-remote --heads`，直接复用当前用户自己的 Git/SSH 凭据环境，不 clone、fetch 或修改工作树。
- 回包只包含固定 `ACCESSIBLE/INACCESSIBLE/UNKNOWN`、原因码和中文安全说明。认证/仓库拒绝、网络、SSL/TLS、超时或 Git 工作区缺少 origin 等已执行远端探测失败返回 `INACCESSIBLE`；非 Git 目录返回 `UNKNOWN + NOT_GIT_REPOSITORY`，表示 Git 能力不适用但普通本地工作区仍可使用。旧客户端、断线等未形成有效结论的情况也返回 `UNKNOWN`。路径、远端 URL、命令和 stderr 均不送回平台。

## 公共能力包

- `LocalClientPublicCapabilityStore` 把完整包安全解压到不可变 `public-capabilities/revisions/{digest}`，校验
  manifest、路径、符号链接、文件类型、文件数/大小和逐文件/内容摘要，再通过 `current` 软链接原子切换
  `OPENCODE_CONFIG_DIR`。首次安装从当前 release 的 `public-capabilities.tar.gz` 初始化；macOS 开发 App 通过
  `-Dtestagent.localclient.packagedPublicCapabilityBundle=<绝对路径>` 指向同一完整包，不能用空版本启动验收。
  能力包是与 JAR 同一 release 的独立签名制品，不是 JAR classpath 资源；只替换 JAR 不会覆盖已激活版本。已有客户端
  必须收到平台通知并由用户确认更新，新安装则由完整 release 的 sidecar 能力包初始化。
  新包以 `contentDigest` 标识文件内容，以 `sha256(sourceCommit + "\n" + contentDigest)` 作为版本身份；客户端仍
  接受上线前已经安装、缺少 `contentDigest` 且以文件摘要作为 `bundleDigest` 的首版基线，避免升级后丢失当前版本。
- `LocalClientPublicCapabilityUpdater` 只在用户已确认、服务器发出绑定当前 generation 的命令后，逐个请求
  256 KiB 分片；已写入分片的重复回包幂等忽略，未来序号乱序、截断和摘要不一致均失败。断线重连从完整包起点恢复，
  不做增量覆盖。
- Agent/Skill-only 变化调用 `/global/dispose`；Tool 或依赖变化重启本地 OpenCode。激活后同时检查进程健康和
  `/agent`、`/command`、`/experimental/tool/ids`，失败自动回切并重启恢复。
- 受管 OpenCode 启动时固定注入 `npm_config_offline=true` 和 `OPENCODE_DISABLE_MODELS_FETCH=true`。OpenCode 即使同时扫描
  用户全局目录、旧配置目录和当前受管目录，内部依赖检查也只能使用本机缓存或完整能力包，禁止访问公网 registry/models.dev；
  缺失的非受管依赖会快速失败，不能阻塞 Tool/插件目录加载。签名公共能力包仍必须携带完整依赖闭包，真正缺失的受管依赖继续
  触发能力激活失败和自动回切，不能用离线模式跳过目录验收。
- 客户端在 `PENDING/DOWNLOADING` 中退出时，重启后将旧命令标记失败并等待平台重发；在 `APPLYING` 中退出时，
  重启后先验证已切换版本，验证成功才补报成功，失败则自动回切上一版本并补报回滚状态。
- 托盘只展示“有更新、更新中、已是最新、失败重试”等用户动作状态，不展示 commit 或 Agent/Skill/Tool 数量；
  更新完成或失败时发送系统通知，用户无需进入网页个人设置判断进度。
- Tool 使用当前登录用户权限；本模块不提权、不绕过沙箱、不执行 npm，也不下载缺失依赖。

## 模型配置

- 新客户端声明 `MANAGED_MODEL_CONFIG_V1`，注册成功后接收服务端生成的无密钥模型/provider 配置，并通过
  `OPENCODE_CONFIG_CONTENT` 传给本地 OpenCode；实际模型请求仍只发往客户端 loopback `LocalModelRelay`，再经已认证
  WSS 转发到平台内部模型代理。
- 服务端字段覆盖父进程同名模型字段，但保留主题等无关用户配置；客户端拒绝 plugin、Tool、文件系统或其它根字段，
  也不把动态模型配置写进不可变公共能力目录。
- 客户端升级或重连时如果发现已有 OpenCode 尚未应用新配置，下一次启动命令会先按 PID/启动时间/命令身份停止旧进程，
  再用新配置重启；配置不变时保持幂等，不额外重启。

## 版本策略兼容

- 客户端 release 版本使用固定宽度北京时间时间戳。签名打包入口要求新版本严格高于整包发布传入的已部署版本及当前分发目录中的所有版本；相同版本或更低版本失败关闭。客户端组件未变化时继续复用原 release，不仅为版本递增而重复发布。
- 平台尚未设置全局或个人客户端目标版本时，客户端保持当前版本并继续在线。新版服务端不会下发无目标的
  `VERSION_POLICY`；为兼容已发布的旧服务端，客户端只接受严格的
  `targetVersion=null/direction=SAME/policyRevision=0/force=false` 空策略，不放宽任何真实更新命令的正 revision fencing。
- 控制连接失败日志只输出固定 `failureCode` 或根异常类型，不记录 WebSocket/HTTP 异常正文、凭据或服务端载荷；
  已知空策略协议错误归一为 `VERSION_POLICY_INVALID`，便于现场区分认证成功后的协议断连。

## 验证

```bash
export JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
mvn -pl test-agent-local-client -am test
mvn -pl test-agent-local-client -am -DskipTests package
```

`LocalClientPublicCapabilityStoreTest` 覆盖提交绑定的新包、首版基线兼容、不可变安装、原子切换、回滚与摘要拒绝。企业安装制品还必须通过
`deploy/internal/package-local-opencode-client.sh` 的 commit/manifest、签名和完整 artifact 校验；Windows 候选包还要执行
`deploy/internal/tests/local-opencode-client-windows-package-test.sh`，正式包继续受 Authenticode 和 Win10 真机闸门约束。
`OpencodeProcessSupervisorTest` 覆盖托管进程强制离线依赖解析，`LocalGitAccessCheckerTest` 覆盖成功、认证拒绝和网络未知的保守分类。

## 依赖边界

允许依赖 `test-agent-local-client-protocol`、`test-agent-workspace-filesystem`、Jackson、SLF4J 和 JDK 标准库。
禁止依赖后端 API、持久化实现、OpenCode generated SDK 或修改 `opencode-source/`。

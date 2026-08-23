# test-agent-local-client

Mac/麒麟普通用户侧 Java 21 客户端。负责 WSS 反向连接、OpenCode 1.18.4 进程监管、本地文件 RPC、工作区原生选择、
整客户端自更新和公共 Agent/Skill/Tool 完整能力包激活。客户端不直接访问平台数据库，也不把本地 OpenCode 请求
回退到服务器运行时。

## Git 权限巡检

- 新客户端声明 `WORKSPACE_GIT_ACCESS_V1`，通过既有受认证文件 RPC 接收 `workspace.git-access.check`；工作区 ID 与根摘要仍由注册表校验，服务端不能传入任意本地路径。
- 客户端只读执行本地 Git 仓库识别、`remote.origin.url` 读取和 `git ls-remote --heads`，直接复用当前用户自己的 Git/SSH 凭据环境，不 clone、fetch 或修改工作树。
- 回包只包含固定 `ACCESSIBLE/INACCESSIBLE/UNKNOWN`、原因码和中文安全说明。认证/仓库拒绝、非 Git 目录或缺少 origin 为不可访问；网络和超时为未知，不把路径、远端 URL、命令或 stderr 送回平台。

## 公共能力包

- `LocalClientPublicCapabilityStore` 把完整包安全解压到不可变 `public-capabilities/revisions/{digest}`，校验
  manifest、路径、符号链接、文件类型、文件数/大小和逐文件/内容摘要，再通过 `current` 软链接原子切换
  `OPENCODE_CONFIG_DIR`。首次安装从当前 release 的 `public-capabilities.tar.gz` 初始化；macOS 开发 App 通过
  `-Dtestagent.localclient.packagedPublicCapabilityBundle=<绝对路径>` 指向同一完整包，不能用空版本启动验收。
  新包以 `contentDigest` 标识文件内容，以 `sha256(sourceCommit + "\n" + contentDigest)` 作为版本身份；客户端仍
  接受上线前已经安装、缺少 `contentDigest` 且以文件摘要作为 `bundleDigest` 的首版基线，避免升级后丢失当前版本。
- `LocalClientPublicCapabilityUpdater` 只在用户已确认、服务器发出绑定当前 generation 的命令后，逐个请求
  256 KiB 分片；已写入分片的重复回包幂等忽略，未来序号乱序、截断和摘要不一致均失败。断线重连从完整包起点恢复，
  不做增量覆盖。
- Agent/Skill-only 变化调用 `/global/dispose`；Tool 或依赖变化重启本地 OpenCode。激活后同时检查进程健康和
  `/agent`、`/command`、`/experimental/tool/ids`，失败自动回切并重启恢复。
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

## 验证

```bash
export JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
mvn -pl test-agent-local-client -am test
mvn -pl test-agent-local-client -am -DskipTests package
```

`LocalClientPublicCapabilityStoreTest` 覆盖提交绑定的新包、首版基线兼容、不可变安装、原子切换、回滚与摘要拒绝。企业安装制品还必须通过
`deploy/internal/package-local-opencode-client.sh` 的 commit/manifest、签名和完整 artifact 校验。
`LocalGitAccessCheckerTest` 覆盖成功、认证拒绝和网络未知的保守分类。

## 依赖边界

允许依赖 `test-agent-local-client-protocol`、`test-agent-workspace-filesystem`、Jackson、SLF4J 和 JDK 标准库。
禁止依赖后端 API、持久化实现、OpenCode generated SDK 或修改 `opencode-source/`。

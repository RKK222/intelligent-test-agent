# test-agent-local-client

Java 21 可执行客户端仅交付给麒麟 ARM64/aarch64 + glibc 的登录用户。它主动建立 WSS 反向连接，监管仅监听
`127.0.0.1` 的 OpenCode 1.18.4，并执行本地工作区文件 RPC。DEB 的业务 payload 只有稳定 Shell：用户运行
`test-agent-local-client` 后先检查系统 `java` 与 `javac` 是否均为 21；只有不满足时才下载受签名清单保护的 ARM64 JDK。

带 SystemTray 的麒麟 ARM 桌面会显示复用 Web 端 `radar-bunny` 形象的托盘入口。状态圆点区分
连接中、在线、离线和退出中，菜单提供打开网页、选择并注册工作区、重连、查看日志、下载日志、会话进度和退出客户端。
托盘状态只读取既有 5 秒心跳与请求生命周期缓存，不额外探测 OpenCode；状态和任务数不变时不重绘图标。
桌面环境不支持 SystemTray 时只记录 `local_client_tray_unavailable`，反向连接和文件能力继续运行。

注册本地工作区时，用户直接从托盘点击“选择并注册工作区”：macOS 使用原生 `FileDialog`，其它有图形桌面
的系统使用 `JFileChooser`，目录名作为默认工作区名称。客户端通过当前已认证 WSS 发送
`WORKSPACE_REGISTER`，后台再复用既有根目录校验、注册和事务持久化服务；成功或失败均回到托盘通知，不依赖
浏览器登录态，也不会开放新的本地 HTTP 端口。同一客户端重复选择同一真实目录时复用既有 Workspace 并恢复
客户端根映射，不会重复插入绑定。注册成功后客户端自动打开 `/workbench?localWorkspaceId=<workspaceId>`；该深链
只携带逻辑 ID，不包含本机绝对路径，网页按当前登录用户权限取回工作区并通过文件 WebSocket 展示目录。托盘后续
点击“打开网页”会继续打开本次进程最近注册的工作区；尚未注册时才打开平台首页。网页设置页只保留
`directory.list` 逐层浏览与手工注册兜底。

客户端自行把日志滚动写入用户 state 目录下的 `logs/client.log`，不依赖 `journalctl`；麒麟上的 journal
仍可作为 systemd 服务级补充诊断。日志导出只包含最多 20 个 `client*.log[.gz]`，每个文件最多取末尾
10 MiB，不包含 client key、配置、OpenCode 日志和工作区内容。

首次 enroll（或本地 Key 已失效）从交互终端读取“统一认证号 + Client key”；Key 使用隐藏输入，绝不接受
命令行、环境变量或 URL。平台生成的 `tack_v1_` Key 仅在创建或重置时展示一次，客户端不生成 Key。凭据和
`clientInstanceId` 位于 release 目录外：配置目录为 `0700`、`credentials.properties` 为 `0600`，普通重启、
更新、回退与自动回切均复用它们，且不会再次弹窗。

客户端监管的进程身份始终以 PID、权威启动时间、可执行文件和参数共同判定。若持久化 PID 已退出，即使原端口
后来被其它健康进程占用，也只清理本客户端的过期记录，绝不停止或接管该陌生进程；下一次启动按既有受控端口
探测跳过占用端口并选择空闲端口，避免陈旧状态永久阻断自动恢复。开发机同时运行服务器 OpenCode 时，客户端
必须使用不重叠的端口池，避免两个健康进程共占一个端口后把本地工作区请求路由到服务器目录。客户端启动受管
OpenCode 时固定设置 `OPENCODE_DISABLE_MODELS_FETCH=true`，模型目录只读取随企业包和公共配置下发的事实源，
不在冷启动阶段访问 `models.dev`。

客户端到本机 OpenCode 的 loopback HTTP 请求固定使用 HTTP/1.1。Java 25 默认协商的明文 HTTP/2 upgrade
会让 OpenCode 1.18.4 的部分 POST 请求停在请求体阶段，表现为工作区可浏览但对话无响应；该约束由
`LocalClientHttpTransportTest` 覆盖，不得恢复为 `HttpClient` 默认版本选择。

版本使用北京时间 `yyyyMMddHHmmss` 的 14 位字符串。稳定 Shell 只原子切换整个 release；候选 JAR 先由目标
JDK 自检，下载后先验签 manifest，再核对可信 Host/相对路径、大小和 SHA-256。更新准备完成后仍须等待服务端
按 `commandId + clientInstanceId + generation` 复核策略；成功、失败、取消与自动回切都会回报持久化状态。网络
中断或凭据失效不作为制品失败。终态 result marker 不在单次 WebSocket 写入后删除，断线重连持续重报，直到平台返回
匹配原命令、实例、generation 和状态的持久化 ACK。旧 `0.1.0` 八字段客户端仍能注册，但不会收发自更新帧，必须安装新的 DEB。

正式制品由 `deploy/internal/package-local-opencode-client.sh` 与完整 JDK/OpenCode 压缩包一起生成，客户端本身
通过 Maven Shade 输出 `test-agent-local-client.jar`。不支持 Windows、开机未登录即运行或稳定 Shell 自更新。
原生桌面启动器显式传入安装包内 OpenCode 路径时，该路径优先于用户目录中旧版 `client.properties` 的
`opencodeExecutable`，确保整包升级后始终使用同一 release 随附的 OpenCode 与插件；裸 JAR 和麒麟稳定启动器未传参数时
仍读取配置文件。

OpenCode 进程统一注入服务端相同的 `opencode-observability-plugin.mjs`。插件只向独立随机 token 保护的 loopback relay
批量提交；relay 用最低优先级单线程顺序写入 state 目录的 `observability-spool`，不压缩、不逐事件 fsync，也不持有平台
凭据。服务器校验并 ACK 精确 batch/Trace/序号/SHA-256 后才删除 `.ndjson + .json`；断网、重启和 connection generation
切换均保留未确认分片。

默认分片 256 KiB、单在途、空闲 3 秒后上传、1 MiB/s 上限、16 MiB 内存队列和 1 GiB spool。以下环境变量只能收紧
吞吐/内存预算、关闭上传或延长等待，不能突破硬上限：`TEST_AGENT_OBSERVABILITY_CHUNK_BYTES`、
`TEST_AGENT_OBSERVABILITY_MAX_IN_FLIGHT`（`0|1`）、`TEST_AGENT_OBSERVABILITY_UPLOAD_BYTES_PER_SECOND`、
`TEST_AGENT_OBSERVABILITY_IDLE_MILLIS`、`TEST_AGENT_OBSERVABILITY_ACK_TIMEOUT_MILLIS`、
`TEST_AGENT_OBSERVABILITY_SPOOL_MAX_BYTES`、`TEST_AGENT_OBSERVABILITY_MEMORY_QUEUE_MAX_BYTES`。对话、模型 relay、文件 RPC
或控制操作活跃时不上传；队列/磁盘满只标记 degraded/incomplete，不阻塞聊天，也不自动删除未确认数据。

# test-agent-local-client

Java 21 可执行客户端仅交付给麒麟 ARM64/aarch64 + glibc 的登录用户。它主动建立 WSS 反向连接，监管仅监听
`127.0.0.1` 的 OpenCode 1.18.4，并执行本地工作区文件 RPC。DEB 的业务 payload 只有稳定 Shell：用户运行
`test-agent-local-client` 后先检查系统 `java` 与 `javac` 是否均为 21；只有不满足时才下载受签名清单保护的 ARM64 JDK。

带 SystemTray 的麒麟 ARM 桌面会显示复用 Web 端 `radar-bunny` 形象的托盘入口。状态圆点区分
连接中、在线、离线和退出中，菜单提供打开网页、重连、查看日志、下载日志、会话进度和退出客户端。
托盘状态只读取既有 5 秒心跳与请求生命周期缓存，不额外探测 OpenCode；状态和任务数不变时不重绘图标。
桌面环境不支持 SystemTray 时只记录 `local_client_tray_unavailable`，反向连接和文件能力继续运行。

注册本地工作区时，网页优先通过既有文件 WebSocket 请求客户端打开系统目录选择器：macOS 使用原生
`FileDialog`，其它有图形桌面的系统使用 `JFileChooser`。选择结果仍由客户端执行真实路径、目录权限、
符号链接和文件系统身份校验；用户取消作为正常结果返回，同一客户端同时只允许一个选择器。无图形桌面或
原生选择失败时，网页可显式切换到 `directory.list` 目录浏览兜底，不会开放新的本地 HTTP 端口。

客户端自行把日志滚动写入用户 state 目录下的 `logs/client.log`，不依赖 `journalctl`；麒麟上的 journal
仍可作为 systemd 服务级补充诊断。日志导出只包含最多 20 个 `client*.log[.gz]`，每个文件最多取末尾
10 MiB，不包含 client key、配置、OpenCode 日志和工作区内容。

首次 enroll（或本地 Key 已失效）从交互终端读取“统一认证号 + Client key”；Key 使用隐藏输入，绝不接受
命令行、环境变量或 URL。平台生成的 `tack_v1_` Key 仅在创建或重置时展示一次，客户端不生成 Key。凭据和
`clientInstanceId` 位于 release 目录外：配置目录为 `0700`、`credentials.properties` 为 `0600`，普通重启、
更新、回退与自动回切均复用它们，且不会再次弹窗。

版本使用北京时间 `yyyyMMddHHmmss` 的 14 位字符串。稳定 Shell 只原子切换整个 release；候选 JAR 先由目标
JDK 自检，下载后先验签 manifest，再核对可信 Host/相对路径、大小和 SHA-256。更新准备完成后仍须等待服务端
按 `commandId + clientInstanceId + generation` 复核策略；成功、失败、取消与自动回切都会回报持久化状态。网络
中断或凭据失效不作为制品失败。终态 result marker 不在单次 WebSocket 写入后删除，断线重连持续重报，直到平台返回
匹配原命令、实例、generation 和状态的持久化 ACK。旧 `0.1.0` 八字段客户端仍能注册，但不会收发自更新帧，必须安装新的 DEB。

正式制品由 `deploy/internal/package-local-opencode-client.sh` 与完整 JDK/OpenCode 压缩包一起生成，客户端本身
通过 Maven Shade 输出 `test-agent-local-client.jar`。不支持 Windows、开机未登录即运行或稳定 Shell 自更新。

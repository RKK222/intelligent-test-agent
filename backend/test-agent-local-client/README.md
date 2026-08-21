# test-agent-local-client

Java 21 可执行客户端仅交付给麒麟 ARM64/aarch64 + glibc 的登录用户。它主动建立 WSS 反向连接，监管仅监听
`127.0.0.1` 的 OpenCode 1.18.4，并执行本地工作区文件 RPC。DEB 的业务 payload 只有稳定 Shell：用户运行
`test-agent-local-client` 后先检查系统 `java` 与 `javac` 是否均为 21；只有不满足时才下载受签名清单保护的 ARM64 JDK。

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

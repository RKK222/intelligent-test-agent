# test-agent-local-client

Java 21 可执行客户端。它以登录用户身份运行，读取用户私有配置中的 client key，主动建立 WSS 反向连接，
监管仅监听 `127.0.0.1` 的 OpenCode 1.18.4，并执行本地工作区文件 RPC。client key 不接受命令行参数。

正式制品由 `deploy/internal/package-local-opencode-client.sh` 与平台 JRE/OpenCode 压缩包一起生成，客户端本身
通过 Maven Shade 输出 `test-agent-local-client.jar`。

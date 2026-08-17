# test-agent-local-client

Java 21 可执行客户端。它以登录用户身份运行，读取用户私有配置中的 client key，主动建立 WSS 反向连接，
监管仅监听 `127.0.0.1` 的 OpenCode 1.18.4，并执行本地工作区文件 RPC。client key 不接受命令行参数。

macOS 和带 SystemTray 的麒麟 ARM 桌面会显示复用 Web 端 `radar-bunny` 形象的托盘入口。状态圆点区分
连接中、在线、离线和退出中，菜单提供打开网页、重连、查看日志、下载日志、会话进度和退出客户端。
托盘状态只读取既有 5 秒心跳与请求生命周期缓存，不额外探测 OpenCode；状态和任务数不变时不重绘图标。
桌面环境不支持 SystemTray 时只记录 `local_client_tray_unavailable`，反向连接和文件能力继续运行。

客户端自行把日志滚动写入用户 state 目录下的 `logs/client.log`，不依赖 `journalctl`；麒麟上的 journal
仍可作为 systemd 服务级补充诊断。日志导出只包含最多 20 个 `client*.log[.gz]`，每个文件最多取末尾
10 MiB，不包含 client key、配置、OpenCode 日志和工作区内容。

正式制品由 `deploy/internal/package-local-opencode-client.sh` 与平台 JRE/OpenCode 压缩包一起生成，客户端本身
通过 Maven Shade 输出 `test-agent-local-client.jar`。

实例 capability 保持 `agentConfig=false`，表示平台 Agent/Skill 正文不会同步到本机配置目录；同时返回
`protectedAgentExecution=true`，表示网页可选择服务器受保护 Agent。服务器模型调用本地文件时仍只发送既有
`FILE_REQUEST` 相对路径操作，客户端不接收 Agent/Skill prompt、revision 制品或任意终端命令。该链路复用
当前 WSS、请求背压和活动请求快照，不增加客户端轮询或独立连接。

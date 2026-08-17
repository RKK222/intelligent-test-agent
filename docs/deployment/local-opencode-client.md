# 本地 OpenCode 客户端打包与内网 HTTP 安装

## 制品范围

首版只发布 `darwin-arm64` 和 `linux-arm64-glibc`。每个版本包含跨平台 Java 客户端 JAR、Temurin JRE
21.0.9+10、OpenCode 1.18.4，以及签名的稳定清单。打包脚本固定校验四个上游归档的 SHA-256，再把目录
结构规范化为 `jre/bin/java` 和 `opencode/bin/opencode`。

```text
deploy/internal/dist/local-opencode-client/
├── install.sh
├── stable/
│   ├── manifest.json
│   └── manifest.json.sig
└── releases/0.1.0/
    ├── test-agent-local-client.jar
    ├── temurin-jre21-darwin-arm64.tar.gz
    ├── temurin-jre21-linux-arm64-glibc.tar.gz
    ├── opencode-1.18.4-darwin-arm64.tar.gz
    ├── opencode-1.18.4-linux-arm64-glibc.tar.gz
    └── SHA256SUMS
```

## 外网构建机打包

私钥必须在仓库外生成并以最小权限保存；它不会写入制品。RSA 和 EC PEM 均可由 OpenSSL 签名验证。

```bash
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
  -out /secure/local-client-signing-private.pem

export TEST_AGENT_LOCAL_CLIENT_SIGNING_KEY=/secure/local-client-signing-private.pem
export TEST_AGENT_LOCAL_CLIENT_VERSION=0.1.0
deploy/internal/package-release.sh --local-client-only
```

完整企业包默认同时构建本地客户端分发目录，并把它放入发布 ZIP 的
`dist/local-opencode-client/`。`--zip-only` 要求该目录已存在且清单完整。离线构建可以通过
`TEST_AGENT_LOCAL_CLIENT_*_ARCHIVE` 指向四个已经审批和预下载的归档；脚本仍使用固定 SHA-256 校验。
可在显式 env 文件中覆盖 URL/SHA，但变更必须重新完成来源、许可证和 ARM 实机验证。

## 前端 Nginx 部署

`deploy-internal-release.sh` 和 `deploy-internal-frontend.sh` 会把目录原子切换到：

```text
/data/testagent/dist/local-opencode-client/
```

`gateway.conf.template` 暴露 `/downloads/local-opencode-client/`，关闭目录索引与符号链接，拒绝隐藏路径。
`install.sh` 和 `stable/*` 使用 `Cache-Control: no-store`；`releases/*` 使用一年 immutable 缓存。该下载
位置不校验 client key，访问范围由企业网络 ACL 控制。`TEST_AGENT_LOCAL_CLIENT_DOWNLOAD_PORT` 配置一个
只暴露下载路径、其余请求均返回 404 的独立明文 HTTP server；它不得与 HTTPS 业务网关端口重复。安装器
只接受明确的 `http://` 下载基址。示例现场使用 HTTPS 443 作为业务入口、HTTP 80 作为制品入口。

后台还必须把前端 Nginx 的源 IP 配入 `TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES`。只有来自这些 IP
的 `X-Forwarded-Proto: https|wss` 才能证明控制隧道或模型中继经过 TLS；生产环境不得开启
`TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL`。

部署后至少检查：

```bash
curl -fsS http://NGINX:DOWNLOAD_PORT/downloads/local-opencode-client/install.sh | head
curl -fsS http://NGINX:DOWNLOAD_PORT/downloads/local-opencode-client/stable/manifest.json
curl -fsS http://NGINX:DOWNLOAD_PORT/downloads/local-opencode-client/stable/manifest.json.sig -o /tmp/manifest.json.sig
```

## 用户安装

先点击右上角头像，在 OpenCode 实例列表下方选择“下载本地客户端”，再到“个人设置 → 本地 OpenCode
客户端”创建并复制 client key。该入口下载 `/downloads/local-opencode-client/install.sh`；生产由 Nginx 提供，
dev server 默认从 `deploy/internal/dist/local-opencode-client/` 只读提供，必要时可用
`TEST_AGENT_LOCAL_CLIENT_DIST_DIR` 指向外部已签名分发目录。下载脚本可以先落盘审阅，再执行：

```bash
curl -fsS http://NGINX:DOWNLOAD_PORT/downloads/local-opencode-client/install.sh -o /tmp/install-local-opencode-client.sh
TEST_AGENT_LOCAL_CLIENT_DOWNLOAD_BASE_URL=http://NGINX:DOWNLOAD_PORT/downloads/local-opencode-client \
TEST_AGENT_LOCAL_CLIENT_SERVER_URL=https://PLATFORM \
TEST_AGENT_LOCAL_CLIENT_WEB_URL=https://PLATFORM \
  sh /tmp/install-local-opencode-client.sh
```

脚本从 `/dev/tty` 隐藏读取 key，不接受 key 命令行参数或环境变量。它先验证内嵌公钥对应的
`manifest.json.sig`，再逐一验证 JAR/JRE/OpenCode SHA-256，最后安装到用户目录并原子切换 `current`。
macOS 创建 `~/Library/LaunchAgents/com.enterprise.testagent.local-opencode-client.plist`；麒麟 ARM 创建
`~/.config/systemd/user/test-agent-local-opencode-client.service`。两者均以登录用户运行且不需要 root。
`TEST_AGENT_LOCAL_CLIENT_WEB_URL` 省略时默认等于 `SERVER_URL`；只有本地开发显式启用不安全控制开关时才
允许使用 HTTP。macOS 和提供 Java SystemTray 的麒麟桌面显示宠物托盘；无图形会话或不支持托盘时客户端
继续作为后台服务运行。托盘正常退出后 launchd/systemd 不立即重启，异常退出仍会自动恢复；下次登录会
按已启用的用户服务重新启动。

常用检查：

```bash
# macOS
launchctl print gui/$(id -u)/com.enterprise.testagent.local-opencode-client
tail -f "$HOME/Library/Application Support/TestAgent/local-opencode-client/state/logs/client.log"

# 麒麟 ARM
systemctl --user status test-agent-local-opencode-client
journalctl --user -u test-agent-local-opencode-client -f
tail -f "$HOME/.local/state/testagent/local-opencode-client/logs/client.log"
```

麒麟的 `journalctl` 用于补充查看 systemd 服务事件；托盘“查看日志/下载日志”直接使用客户端自行写入的
`~/.local/state/testagent/local-opencode-client/logs/`，不依赖 journal。下载 ZIP 只收集客户端滚动日志，
最多 20 个文件、每个文件最多末尾 10 MiB，不包含 `client.key`、`client.properties`、OpenCode 日志或
工作区内容。托盘“会话进度”只展示当前操作类型、数量与耗时。

轮换 key 后，同一用户所有设备都必须把新值写入各自 `client.key`（权限保持 `0600`）并重启用户服务。
撤销 key 会立即断开全部设备。

## 受保护 Agent/Skill 运行要求

该链路复用现有服务器 OpenCode 节点、Redis、Java 回调地址和本地客户端 WSS，不新增客户端进程或外部
中间件。至少保证：

- 当前 Java 的监听地址可被同服务器 OpenCode 访问；MCP 回调固定为
  `/api/internal/platform/protected-agent/mcp`，不经过浏览器登录 Token。
- 存在可路由的 `SERVER_PROCESS` OpenCode 节点。本地客户端在线不等于服务器执行节点可用，服务器节点缺失
  时受保护 Run 必须返回不可用，不能回退本地 OpenCode。
- Redis 可保存受保护远端 Session 到服务器隔离目录的七天映射；映射缺失时重建远端 Session，禁止使用本地
  绝对路径兜底。
- `TEST_AGENT_PROTECTED_AGENT_WORK_ROOT` 可选覆盖隔离目录根，默认使用 JVM 临时目录下
  `test-agent-protected-agent`。运行用户必须可创建目录并支持逐级拒绝符号链接；POSIX 目录会收紧为 `0700`。
- 受保护文件 grant 只驻留签发 Java 内存且最长四小时。Java 重启、Run 终态、客户端重连换代或 Workspace
  重绑后，旧 MCP 调用预期失败；浏览器应以普通 Run 失败/重试流程处理，不复用旧 grant。

上线验收应在一个已发布 Hub Agent 中冻结至少一个 Skill，确认网页目录只出现 revision/SHA 摘要；运行时
服务器 OpenCode 能通过 `local_files_read_file` 读取授权目录，并在用户确认权限后写入文件；客户端目录中不
出现 Agent/Skill 正文。后台 API 日志、RunEvent 和客户端日志均不得出现 MCP Authorization、prompt 或文件
内容。

## 发布验证

打包机先执行自动化制品和 Nginx 校验：

```bash
deploy/internal/tests/local-opencode-client-package-test.sh
tools/verify-internal-nginx-config.sh
```

然后必须分别在 Apple Silicon Mac 和真实 ARM64 glibc 麒麟机上从 Nginx 明文 HTTP 地址执行安装，
验证用户服务启动、WSS 认证、OpenCode 1.18.4 loopback 健康、本地工作区注册、聊天修改文件、
分片 CRUD、托盘状态与全部菜单动作以及客户端重启恢复。没有完成真实麒麟 ARM 流程时，发布验收只能记为
“部分验证”。

## 回滚与风险

每个版本位于独立 `releases/{version}`，回滚时把 `current` 原子指回已验证版本并重启用户服务；Nginx
端回滚 stable 清单时必须同时回滚对应签名，版本化制品不可原地替换。

当前部署明确使用无需认证的内网明文 HTTP。签名和 SHA-256 能发现传输损坏，但安装脚本本身也从同一
明文链路取得，主动中间人可以同时替换脚本、内嵌公钥和全部制品。这是保留的部署风险，只能由网络 ACL、
受控发布链路或改用可信 HTTPS/带外固定公钥消除。client key、控制隧道和模型请求仍必须使用 HTTPS/WSS；
只有显式测试开关才允许不安全控制地址，生产禁止开启。

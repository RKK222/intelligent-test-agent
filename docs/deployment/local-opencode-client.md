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

先在“个人设置 → 本地 OpenCode 客户端”创建并复制 client key。下载脚本可以先落盘审阅，再执行：

```bash
curl -fsS http://NGINX:DOWNLOAD_PORT/downloads/local-opencode-client/install.sh -o /tmp/install-local-opencode-client.sh
TEST_AGENT_LOCAL_CLIENT_DOWNLOAD_BASE_URL=http://NGINX:DOWNLOAD_PORT/downloads/local-opencode-client \
TEST_AGENT_LOCAL_CLIENT_SERVER_URL=https://PLATFORM \
  sh /tmp/install-local-opencode-client.sh
```

脚本从 `/dev/tty` 隐藏读取 key，不接受 key 命令行参数或环境变量。它先验证内嵌公钥对应的
`manifest.json.sig`，再逐一验证 JAR/JRE/OpenCode SHA-256，最后安装到用户目录并原子切换 `current`。
macOS 创建 `~/Library/LaunchAgents/com.enterprise.testagent.local-opencode-client.plist`；麒麟 ARM 创建
`~/.config/systemd/user/test-agent-local-opencode-client.service`。两者均以登录用户运行且不需要 root。

常用检查：

```bash
# macOS
launchctl print gui/$(id -u)/com.enterprise.testagent.local-opencode-client
tail -f "$HOME/Library/Application Support/TestAgent/local-opencode-client/state/logs/client.log"

# 麒麟 ARM
systemctl --user status test-agent-local-opencode-client
journalctl --user -u test-agent-local-opencode-client -f
```

轮换 key 后，同一用户所有设备都必须把新值写入各自 `client.key`（权限保持 `0600`）并重启用户服务。
撤销 key 会立即断开全部设备。

## 发布验证

打包机先执行自动化制品和 Nginx 校验：

```bash
deploy/internal/tests/local-opencode-client-package-test.sh
tools/verify-internal-nginx-config.sh
```

然后必须分别在 Apple Silicon Mac 和真实 ARM64 glibc 麒麟机上从 Nginx 明文 HTTP 地址执行安装，
验证用户服务启动、WSS 认证、OpenCode 1.18.4 loopback 健康、本地工作区注册、聊天修改文件、
分片 CRUD 以及客户端重启恢复。没有完成真实麒麟 ARM 流程时，发布验收只能记为“部分验证”。

## 回滚与风险

每个版本位于独立 `releases/{version}`，回滚时把 `current` 原子指回已验证版本并重启用户服务；Nginx
端回滚 stable 清单时必须同时回滚对应签名，版本化制品不可原地替换。

当前部署明确使用无需认证的内网明文 HTTP。签名和 SHA-256 能发现传输损坏，但安装脚本本身也从同一
明文链路取得，主动中间人可以同时替换脚本、内嵌公钥和全部制品。这是保留的部署风险，只能由网络 ACL、
受控发布链路或改用可信 HTTPS/带外固定公钥消除。client key、控制隧道和模型请求仍必须使用 HTTPS/WSS；
只有显式测试开关才允许不安全控制地址，生产禁止开启。

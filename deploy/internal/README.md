# 企业内部署文档入口

当前代码支持单后台和完整多后台部署。两种模式使用同一套 Mac 离线交付物、平台 PostgreSQL、外部共享 XXL MySQL、Redis 运行态、Java→manager 控制协议和内部模型代理。每个 Java 进程同时运行平台 WebFlux、独立 Admin 子端口和 executor；executor 注册不使用 Linux 亲和，夜间任务由 XXL 扫描后按任务固化的服务器通过公共 Java 路由分发。一次性 WebSocket ticket 继续保存在签发 JVM；页面从 `/processes/me` 获得用户 binding 后，会给后续 OpenCode、会话、Run、SSE 和本地工作区请求携带页面内存中的 `linuxServerId`，Nginx 用静态白名单把已知 ID 精确首跳到一机一 Java 的目标节点，缺失或未知 ID 仍走 `least_conn`，后端权威路由继续兜底。workspace PTY、文件和 Agent 配置进度沿用既有固定节点方式；标准生产部署中，服务器 PTY 也复用同一静态路由表固定到签发 Java，不依赖 sticky。

企业交付模板默认设置 `TEST_AGENT_SERVER_TERMINAL_ENABLED=true`，并要求 `TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=wss://<前端入口>`；应用本身在缺少该显式配置时仍保持关闭。上线时确认 systemd Java 的 `User=` 就是期望的运维用户，终端只继承该用户权限，不使用 `sudo` 或额外授权。标准入口的前端 `nginx.env` 必须开启 TLS、配置证书路径，并以 `linuxServerId=host:port` 填写统一的 `TEST_AGENT_NGINX_SERVER_ROUTES`。旧 `TEST_AGENT_NGINX_TERMINAL_ROUTES` 只用于升级兼容，新配置不得与新键并存。当前现场明确选择 HTTP、不能使用 HTTPS，因此单后台和 `.4 + .114` 多后台都按对应文档显式允许 `ws://`，并接受登录数据和终端内容明文传输、浏览器网段必须直达各 Java `:8080` 的风险；该现场例外不改变通用 WSS 安全默认。

请选择对应文档：

- [单后台部署](SINGLE-BACKEND.md)：一个 Java 后端和一个 `opencode-worker`，当前现场示例为 `122.233.30.114`；包含可整文件替换的生产配置。
- [多后台部署](MULTI-BACKEND.md)：两个或更多 Java/worker 节点，包含 `.4 + .114` 各自的完整配置、部署和验收示例。
- [Redis 7.4.9 独立离线升级](REDIS-OFFLINE.md)：将当前本地 Redis 版本和配置单独封包，用于企业 Redis 5.0 的受控备份、升级、验证与回滚；不修改业务代码，也不并入日常平台包。
- [Redis 5 升级 + 双后台平台全量执行手册](FULL-UPGRADE-RUNBOOK.md)：按当前现场路径和 `.20 → .4 → .114 → .2` 顺序整合完整命令、成功条件、页面配置、脏数据边界与回滚。
- [空报文体排查手册](EMPTY-RESPONSE-BODY-TROUBLESHOOTING.md)：部署后按浏览器、Nginx、双 Java、RunEvent SSE、用户 OpenCode 和企业模型代理逐层采证，区分正常空请求与异常空响应。
- [工具盒子离线部署](../../docs/deployment/toolbox.md)：IT-Tools + OmniTools 的 193 项目录、双镜像、双后台共置、Nginx 故障切换和回滚。

底层 Java、manager、Redis 路由设计见 [后端部署说明](../../docs/deployment/backend.md)。

## 共同前提

- Mac 构建机允许联网；企业服务器完全离线。
- 企业内部中转机的固定交付目录是 `~/Desktop/mimoagent/0709`；中转机不使用 `/data/0709`。`.20/.4/.114/.2` 等目标服务器的固定接收目录才是 `/data/0709`。
- `opencode-worker-docker.sh` 固定为 worker 容器设置 `--pids-limit=8192`、`nofile=262144:262144` 和 `nproc=8192:8192`；这些值不从 `docker.env` 覆盖。脚本升级后必须重建容器才会生效。
- Docker 18.09 发布 1000 个 worker 端口前必须在 daemon 中禁用 `userland-proxy`；脚本会在删除旧 worker 前拒绝不安全组合，避免启动中途耗尽 fork 资源。
- worker 构建会自动检查 Python `3.13.14`、pip/venv/常用标准库与脚本工具、Codex 版本、摘要、官方 MCP 契约和失败关闭；启用分析前，每台 Linux 4.19 / Docker 18.09.7 worker 节点还必须执行 `./check-codex-whitebox-host.sh test-agent-opencode-worker:internal`。脚本按十进制解析 `18.09.7`，并用镜像内的 `/bin/true` 和真实 Codex/bubblewrap 验证 namespace、指定 cwd、源码读取、原生 read-only 拒写、Git 不变与续写；不以 Apple Silicon Mac 的 amd64 仿真结果代替现场内核验收。完整说明见 `docs/deployment/codex-whitebox-mcp.md`。
- 企业内不使用 Docker Compose；worker 由 `opencode-worker-docker.sh` 管理，当前 XXL MySQL 直接使用外部实例，不在平台服务器部署 MySQL 容器。
- Redis 仍是独立共享基础设施，不随平台 ZIP 部署；只有明确执行 Redis 专项升级时，才使用固定名 `test-agent-redis-offline.zip`。
- `.20` 通过 Docker `-p 6379:6379` 提供共享 Redis 时必须持久化 `net.ipv4.ip_forward=1`；Redis `deploy/verify` 脚本会提前拒绝值为 `0` 的宿主机。容器本机 `healthy` 后仍必须从 `.4`、`.114` 分别验证 `.20:6379`，跨机超时不得通过反复重启 Java 处理。
- Java 读取 `/data/testagent/config/backend.env`。
- Java 固定读取交付 JAR 内置的 `classpath:rsa-private.key`；`backend.env` 不再接受外置 RSA 路径，多后台必须部署同一 JAR。
- 所有 Java 连接外部 `122.210.106.43:3306/xxl_job`，当前现场统一使用 `root` 账号和同一组纳管密码、XXL access token；JDBC 启用 `createDatabaseIfNotExist=true`，Flyway 负责后续表和基础任务初始化。真实密码只进入 `.4/.114` 敏感节点包，不写入仓库模板、文档或命令行。
- 每个 Java 的 Admin 固定与同 JVM executor 配对，executor 注册地址复用平台 advertised host；同机多 Java 的 Admin/executor 端口必须唯一，所有 Admin 必须能访问所有 executor。前端 Nginx 把 `/xxl-job-admin/` 同源代理到各 Admin 子端口。
- worker 读取 `/data/testagent/config/docker.env`。
- Java 的 `SYS_DATA_ROOT_DIR` 必须与本机 worker 的 `TEST_AGENT_DATA_ROOT` 一致。
- 每个稳定 `TEST_AGENT_LINUX_SERVER_ID` 只运行一个 worker，不配置人工 `containerId/managerId`。
- 企业模型供应商地址和上游 token 由数据库及管理页面维护，不写入 `docker.env`。
- 正式模型链路为 `OpenCode → 本机 Java:8080 → 企业内部模型:9070`，不使用 19070 relay 或 host network。

## 现场 XXL-JOB MySQL 配置变更

当前现场的 XXL-JOB 外部 MySQL 连接配置已固定为：

```text
地址：122.210.106.43
端口：3306
数据库：xxl_job
账号：root
```

本次纳管密码已更新到企业包内 `.4`、`.114` 两个敏感节点的
`config/backend.env`。仓库中的 [backend.env.example](backend.env.example) 只保留占位符，
避免把生产密码提交到 Git。密码包含 `=`、`@`、`*` 等特殊字符，落盘时应直接写入 dotenv
配置行，不要执行 `source backend.env`，也不要把密码放在 shell 命令参数中。

部署前在两台后台分别执行以下脱敏校验，预期第一条输出 `1`，第二条无输出：

```bash
# 122.233.30.4
grep -c '^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=' /data/testagent/config/backend.env
grep -E '^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=.*REPLACE_|^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=$' /data/testagent/config/backend.env

# 122.233.30.114
grep -c '^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=' /data/testagent/config/backend.env
grep -E '^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=.*REPLACE_|^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=$' /data/testagent/config/backend.env
```

## Mac 打包

LobeHub 独立 fork 默认位于平台仓库同级 `../lobehub-platform`，精确提交由 `lobehub/version.env` 锁定。当前
锁定版本为 `v2.2.11-platform.3`；Desktop/CLI 使用 LobeHub 服务端浏览器确认、PKCE 和 24 小时 opaque Session，
不再部署或兼容 OIDC Bridge。独立 fork 仍须由企业 Git 管理员推送到受控内部远端；同级本机 checkout 只用于
构建和验证，不能作为企业源码托管。先用 `build-lobehub-artifacts.sh` 从锁定提交构建真实 `linux/amd64` 镜像、
源码、SBOM、许可证和客户端制品集；
完整构建必须提供企业签名 Windows x64 客户端与签名证据，以及批准的 Linux x86_64 客户端、最终审批证据和
独立验收记录。先由 Mac 运行 `build-lobehub-client-kit.sh` 导出锁定源码及原生构建工具包，再分别交给 Windows
x64 证书构建机和 Linux x86_64 构建/审批人员；具体流程见
[LobeHub 企业客户端原生构建与审批](../../docs/deployment/lobehub-client-build.md)。无客户端时只允许
使用 `--server-only` 做服务端部署演练，后续打包门禁会拒绝该阶段目录。Docker VM 至少分配 8 GiB 内存；
fork 已将 Next.js 静态生成限制为两个 worker，以支持 10 CPU / 8 GiB 的已验证构建基线。

服务端阶段目录生成后应在构建机执行一次真实镜像冒烟；它会实际运行 migration、Redis ACL、私有 RustFS 和
LobeHub app，再写入 PostgreSQL/RustFS 证明数据并完成停机冷备份、恢复和二次验收；它拒绝覆盖已有同名
容器。Apple Silicon 的 Docker 仿真结果不替代现场 Linux 验收：

```bash
TEST_AGENT_LOBEHUB_ARTIFACT_DIR=/absolute/path/to/lobehub-release-artifacts \
  deploy/internal/tests/lobehub-runtime-smoke-test.sh
```

外部 fork 和完整制品通过准入后，可用同一脚本生成全量附带包或只包含 LobeHub 的离线包：

```bash
TEST_AGENT_LOBEHUB_ARTIFACT_DIR=/absolute/path/to/lobehub-release-artifacts \
  deploy/internal/package-release.sh --with-lobehub

TEST_AGENT_LOBEHUB_ARTIFACT_DIR=/absolute/path/to/lobehub-release-artifacts \
  deploy/internal/package-release.sh --lobehub-only
```

脚本会核对上游/内部版本和 fork commit、PostgreSQL 17、三份不可变 tag/image ID 镜像、完整且精确的
`SHA256SUMS`、源码、SBOM、许可证、
资源审批清单、Windows Authenticode 证据、Linux 审批/验收记录及客户端摘要；任一缺失或不一致都会失败关闭。现场安装、systemd/Docker
启停、共享 Redis ACL、HTTP 风险、备份和回滚见 [LobeHub 企业离线部署](../../docs/deployment/lobehub-offline.md)。

现场填写 `/data/testagent/config/lobehub.env` 后必须先运行：

```bash
/data/testagent/deploy/internal/lobehub-docker.sh validate-config
```

该命令不访问 Docker，可先校验镜像 tag 与安装清单、全部 secret、固定 Redis 前缀、离线/认证
开关、`AGENT_RUNTIME_MODE=local`、独立 loopback scheduler 密钥和强制设备执行门禁。企业离线版不会部署
QStash；单一 app 实例由镜像 launcher 每分钟以内嵌方式触发创建者身份的到期任务。通过后再按文档顺序执行 Redis 检查、PostgreSQL 17 启动、migration、私有
RustFS bucket 初始化和 app 启动；外部制品契约版本必须与本仓库 `deploy/internal/lobehub/version.env` 一致。
app 默认只绑定 `127.0.0.1:3210`；跨机 Nginx 必须改为具体内网 IPv4，并把主机防火墙来源限制为代理节点。
运行脚本拒绝重复键、符号链接或非 0600 的 `lobehub.env`，并按 ParadeDB、RustFS、app 生成临时最小 env，
避免把 HMAC、Session 和模型委托密钥横向注入无关容器；它还会交叉核对 DB、Redis、S3、app 和四个
平台 URL 的实际目标，防止格式合法但服务错接。

启动、配置变更或恢复后必须执行完整运行态验收；升级前的冷备份输出必须位于 `/data/testagent` 外：

```bash
/data/testagent/deploy/internal/lobehub-docker.sh verify-deployment
systemctl stop test-agent-lobehub
/data/testagent/deploy/internal/lobehub-backup.sh create --output-dir /data/backup/lobehub/change-<change-id>
```

备份校验、显式恢复、rollback 保留和入口开放顺序以
[LobeHub 企业离线部署](../../docs/deployment/lobehub-offline.md) 为准。

企业包以执行命令时的本地工作树为准：已提交和未提交、但会被 Maven、前端或 Docker 构建实际读取的本地代码都属于本次构建输入。打包前先合并确认需要交付的相关分支并检查状态；这些命令用于记录输入范围，不要求 `git status --short` 为空，也不得为打包擅自清理、stash 或切换到另一份源码：

所有企业 TAR/ZIP 统一通过 `archive-common.sh` 封装：保留部署所需的合法点文件，仅排除 `._*`、`.DS_Store`、`__MACOSX`、`.Spotlight-V100`、`.Trashes`、`.fseventsd` 等 macOS 自动元数据，并禁止 TAR 携带扩展属性、ACL 和 file flags。ZIP 关闭 extra fields，最终交付文件自身也会清除 xattr，避免复制到不支持扩展属性的介质时生成同名 `._*` 旁车文件；回归测试还会用目标侧 Linux GNU tar 检查成员和告警。新增归档流程必须复用该入口，不能直接调用 Mac `tar`/`zip` 生成交付包。

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
git rev-parse HEAD
git status --short
git branch --no-merged main
test -z "$(git diff --name-only --diff-filter=U)"
```

仍在其它活动 worktree 中且未提交的实验改动不会自动进入当前工作树；只有明确完成并合并到当前 `main` 的分支才进入企业包。确认范围后从当前仓库根目录执行：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

`VITE_TEST_AGENT_API_BASE_URL` 是编译期参数。只允许一个入口时可固化完整 origin；域名和 IP 需要同时兼容时必须显式传空值，让前端使用当前页面同源的相对 `/api`。当前双入口包使用：

```bash
VITE_TEST_AGENT_API_BASE_URL="" \
  deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

这样 `http://mimo.sdc.cs.icbc:9996` 和 `http://122.233.30.2:9996` 都请求各自同源 `/api`。入口策略变更后只修改服务器 `docker.env` 不会改变已经编译的静态文件，必须重新构建并替换前端产物。

完整构建和制品验证已经通过、仅补充当前会话日志时，可复用刚生成的制品重新封装内层 ZIP，不重复编译二进制；`--zip-only` 会保持现有 ZIP 的 `included/reuse` 选择，不会把同一批次刚生成但尚未部署的大组件删掉：

```bash
deploy/internal/package-release.sh --zip-only --output-dir deploy/internal/dist
```

`package-release.sh` 默认使用输出目录下的 `.release-component-state.env` 分别判断两个大组件：

- `worker runtime`：Python/通用脚本工具、OpenCode Manager、OpenCode runtime、Codex MCP、Node/MCP SDK、bubblewrap、worker 镜像和 `test-agent-programs.tar.gz` 是一个不可拆分单元。
- `toolbox`：IT-Tools、OmniTools、修改源码和目录文件是一个单元。

Python 第三方库不进入上述 worker 指纹，也不烘焙进 worker 镜像。它使用独立命令、独立 tar 和独立校验文件，升级 pandas/Office/JSON 库时不需要重建或重新加载 worker 镜像：

```bash
deploy/internal/package-release.sh --python-libs-only \
  --output-dir deploy/internal/dist
```

首次构建、状态文件丢失或对应源码/版本/基础镜像指纹变化时，组件标记为 `included`，脚本重新构建并放入 ZIP；指纹未变化时标记为 `reuse`，ZIP 不再携带对应大文件。`--zip-only` 只允许复用带当前指纹戳的已验证制品，源码已变化但没有重新构建时会失败，不能把旧 tar 伪装成新组件。必须持续复用同一个输出目录，或通过 `--component-state-file <稳定路径>` 显式保存基线。迁移到本机制后第一次必须做全量部署：后台会把实际安装成功的组件指纹写入 `/data/testagent/config/release-component-state.env`；后续 `reuse` 包要求清单指纹与目标机指纹相同且组件健康，缺失或不一致都会停止部署。

新装机、灾备全量包或状态不可信时强制携带全部组件：

```bash
VITE_TEST_AGENT_API_BASE_URL="" \
  deploy/internal/package-release.sh --include-all-components \
  --output-dir deploy/internal/dist
```

只查看本次会包含还是复用，不构建任何制品：

```bash
deploy/internal/package-release.sh --component-plan-only \
  --output-dir deploy/internal/dist
```

仅构建工具盒子交付物时可执行：

```bash
deploy/internal/package-release.sh --toolbox-only --output-dir deploy/internal/dist-toolbox
```

显式 `--toolbox-only` 总是重新构建两个固定 `linux/amd64` 工具镜像 tar/SHA、完整修改源码/SHA和 193 项目录/SHA，并更新本地工具制品指纹；完整发布只在工具源码或构建输入变化时把它们放进 ZIP。部署入口在 `included` 时自动加载并替换 `.4/.114` 的工具容器，在 `reuse` 时前后各诊断一次现有容器，健康失败即停止。不能用 `--no-save` 生成企业离线交付。

当前外部 MySQL 不需要离线镜像包。标准发布 ZIP 和三台应用节点配置包齐全后，只生成一个固定平台
U 盘交付包：

```bash
deploy/internal/package-two-backend-complete.sh \
  --release-archive deploy/internal/dist/test-agent-internal-release.zip \
  --nodes-dir /path/to/prepared-node-packages \
  --output-dir /path/to/usb-output

```

每次重新生成内层 `test-agent-internal-release.zip` 后都必须重新执行外层封装，不能继续使用输出目录中的历史固定名外层包。封装完成后在 Mac 校验外层 SHA、ZIP 结构及内嵌内层 ZIP 与本次内层文件完全一致：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent/deploy/internal/dist
shasum -a 256 -c test-agent-two-backend-complete.zip.sha256
unzip -tq test-agent-two-backend-complete.zip
inner_sha="$(shasum -a 256 test-agent-internal-release.zip | awk '{print $1}')"
embedded_sha="$(unzip -p test-agent-two-backend-complete.zip \
  test-agent-two-backend-complete/test-agent-internal-release.zip | shasum -a 256 | awk '{print $1}')"
test "${inner_sha}" = "${embedded_sha}"
```

最后一条必须返回 0；否则外层包不是本次内层发布，禁止交付。

以后只使用以下固定名称，不添加日期、`v2`、`v3` 等后缀；重复打包会无交互覆盖旧文件：

```text
test-agent-two-backend-complete.zip
test-agent-two-backend-complete.zip.sha256
```

平台 ZIP 顶层固定为 `test-agent-two-backend-complete/`，只包含内层平台发布 ZIP、`.4/.114/.2`
三个应用节点包及后台/前端入口，不包含 MySQL 镜像、容器入口或 `.147` 节点包。入口从本机网卡取 IP，连续执行节点包
校验、预校验、正式部署和部署后校验，完整输出保存在交付目录上一层的 `deploy-<本机IP>.log`。
企业内部中转机每次只接收平台 ZIP 和 SHA。
封装脚本可复用旧节点包：它只在临时副本中把旧前端路由键迁移为
`TEST_AGENT_NGINX_SERVER_ROUTES`，并给两个后台补齐当前 HTTP Cookie、大文件预览/分片和 `14096-15095` 端口池等固定非密钥字段。源敏感包、密码和 token 不会被修改或输出；发现重复键、缺少必需路由或新旧路由键并存会直接失败。

交付物：

```text
deploy/internal/dist/test-agent-internal-release.zip
deploy/internal/dist/test-agent-internal-release.zip.sha256
deploy/internal/dist/backend/test-agent-app.jar
deploy/internal/dist/backend/lib/
deploy/internal/dist/backend/xxl-job-upstream/  # 3.4.2 源码、LICENSE、UPSTREAM、VERSION
deploy/internal/dist/test-agent-frontend-dist.tar.gz
deploy/internal/dist/test-agent-programs.tar.gz
deploy/internal/dist/test-agent-opencode-worker_internal-linux-amd64.tar
deploy/internal/dist/test-agent-python-libs-py313-linux-amd64.tar.gz       # 独立交付
deploy/internal/dist/test-agent-python-libs-py313-linux-amd64.tar.gz.sha256
deploy/internal/dist/frontend/
```

公共 Agent/Skill 采用独立固定名完整替换包，不并入平台内层 ZIP：

```text
deploy/internal/dist/test-agent-public-agents-skills.zip
deploy/internal/dist/test-agent-public-agents-skills.zip.sha256
```

该包从当前公共配置 Git 提交归档，包含公共 `opencode.jsonc`、全部 Agent、Skill、Tool 和说明，
不包含 `.git`、`node_modules`、缓存或个人验收样例。通过“系统管理 → 配置管理 → opencode
公共配置管理”的个人 worktree 导入、查看 Diff、提交并发布；不要直接覆盖共享运行目录。
`ui-test-execution-agent.md` 和 `ui_test_execute.ts` 是本仓库保存的 UI 执行公共配置模板；实际
运行文件仍由独立公共配置 Git 管理。模板要求“被测系统环境 + 一行四列案例”，环境缺失时中断；
Tool 每次通过既有 `TEST_AGENT_PLATFORM_BASE_URL` 直连同节点 Java，读取超级管理员在通用参数中
维护的 `UITEST_BASE_URL`，再直连独立平台；Java 不代理 UI 请求，也不包含或打包独立 UI 平台源码。
两段请求均不带新增凭据，公共 Nginx 对配置查询精确路径返回 `404`，只允许 worker 内网直连 Java。
地址修改后下一次 Tool 调用即时生效，不要求重启 Manager 或用户 OpenCode 进程。

平台 ZIP 同时包含 `deploy/internal/` 下的配置模板、部署脚本、Nginx 模板、模型配置示例和本部署文档，并在 `.agents/` 下保留当前仓库全部 `session-log*.md` 会话日志作为交付追溯基线；外层完整包封装前会逐一校验这些日志均已进入内层 ZIP。
仓库保留的 MySQL 容器脚本只作为其它隔离环境备用，不属于当前现场交付。企业服务器只执行校验、解压、`docker load` 和服务启停，不执行
Maven、pnpm、Docker build 或联网下载。

Redis 专项升级与平台发布相互独立。需要把企业 Redis 5.0 升级到当前本地基线时，另行执行：

```bash
deploy/internal/package-redis-offline.sh
```

输出固定为 `test-agent-redis-offline.zip` 和同名 `.sha256`。该包包含随机 Redis 密码，必须按 `0600` 敏感文件传输；完整停写、备份、数据副本、部署、双后台密码更新及回滚步骤只以 [Redis 7.4.9 独立离线升级](REDIS-OFFLINE.md) 为准。
如果只是更新已有 Redis 包中的手册和部署脚本，必须保留已与平台节点包匹配的密码和镜像，使用：

```bash
deploy/internal/package-redis-offline.sh --zip-only --output-dir deploy/internal/dist
```

`--zip-only` 不旋转 Redis 密码，不重新导出镜像；已有固定包缺失、存在占位符或关键文件不全时会失败。

## OpenCode worker 版本与回滚包

当前 worker 固定 OpenCode `1.18.4` 官方 `opencode-linux-x64-baseline.tar.gz`。源码快照不参与程序构建，版本、release commit、asset 和两级 SHA 校验值由 `env.example` 与 Dockerfile 同时固定。标准构建会同时导出镜像 tar 和 `test-agent-programs.tar.gz`，两者必须成对升级。

worker 还固定 Python `3.13.14`：外网 Mac 从 `PYTHON_SOURCE_BASE_URL` 指向的国内镜像下载官方源码，并校验 `23021880` 字节和 SHA-256 `639e43243c620a308f968213df9e00f2f8f62332f7adbaa7a7eeb9783057c690`，再在 Debian 11 bullseye/glibc 2.31 基线上编译。镜像提供 `python3`/`python`、pip、venv、curl、jq、zip/unzip；Git、OpenSSH、ripgrep、Node 和 procps 沿用既有能力。为控制镜像体积和供应链，镜像不保留 gcc/make 等编译器，也不直接烘焙业务第三方库，并通过 `PIP_NO_INDEX=1` 禁止默认访问公网索引。

首批通用第三方库固定为 pandas `3.0.3`、openpyxl `3.1.5`、XlsxWriter `3.2.9`、python-docx `1.2.0`、jsonschema `4.26.0`、orjson `3.11.9` 及完整传递依赖。`deploy/internal/python-libs/requirements-linux-amd64.lock` 对每个 Python 3.13 / Linux amd64 wheel 固定 SHA-256；`package-python-libs.sh` 只下载二进制 wheel，断网安装到独立 `site-packages` 后执行 Excel、Word、pandas、标准 `json`、JSON Schema 和 orjson 功能 smoke，再生成 `FILES.sha256`。归档必须由交付镜像内的 Linux GNU tar 生成并复核；Mac `bsdtar` 可能隐藏自身写入的 `._*` AppleDouble/PAX 成员，目标机出现 `Unsafe or unexpected archive entry` 时不得跳过校验或重算 SHA，必须换用原始、通过 Linux 成员检查的归档。目标机使用下列命令独立部署，脚本先断网验证候选目录，再原子替换 `/data/testagent/python-libs`、只读挂载并重启 worker：

```bash
deploy/internal/deploy-python-libs.sh \
  --archive /data/testagent/dist/test-agent-python-libs-py313-linux-amd64.tar.gz \
  --checksum /data/testagent/dist/test-agent-python-libs-py313-linux-amd64.tar.gz.sha256
```

后续增加第三方包必须修改入口清单、重新生成全量哈希锁并重新运行独立打包和断网验收；企业目标机不得执行公网 `pip install`。

同一 worker/programs 批次固定携带官方 Codex CLI `0.145.0` Linux amd64 musl 和官方同标签
bubblewrap，分别校验归档/可执行文件 SHA-256，并包含 Codex Apache-2.0 LICENSE/NOTICE 与
bubblewrap COPYING；MCP SDK 精确锁定 `1.29.0`。企业目标机不会下载这些依赖，Codex 与
OpenCode 一样必须成对替换 image/programs。启动器只在当前 OpenCode 用户 HOME 下生成不含
API key 的官方 `config.toml` 以接入企业 Responses 代理，随后直接执行 `codex mcp-server`；
不再提供工具改名、参数过滤或固定 workspace 的协议门面。应用启用和现场能力验收见
`docs/deployment/codex-whitebox-mcp.md`。

1.17.8 紧急回滚包使用同一官方 baseline 资产和对应 Tool lockfile，可在外网构建机执行：

```bash
OPENCODE_VERSION=1.17.8 \
OPENCODE_RELEASE_COMMIT=11e47f91496005aab4d7c5a2d0a7da5d2651b4ac \
OPENCODE_ASSET_SIZE=54769220 \
OPENCODE_ASSET_SHA256=9b34bf34bdc66ea34ddd5858a131febf28b6247693acbfb5fb5c9ad94d90388b \
OPENCODE_BINARY_SHA256=not-recorded \
OPENCODE_RUNTIME_PACKAGE_JSON=deploy/internal/opencode-node-runtime-1.17.8.package.json \
OPENCODE_RUNTIME_PACKAGE_LOCK=deploy/internal/opencode-node-runtime-1.17.8.package-lock.json \
TEST_AGENT_OPENCODE_WORKER_IMAGE=test-agent-opencode-worker:1.17.8 \
deploy/internal/package-release.sh --opencode-only --output-dir deploy/internal/dist-opencode-1.17.8
```

回滚时先加载 1.17.8 image、解压同批次 programs，再通过平台停止并重启用户进程；不删除 session 目录、manager state 或数据库记录。启动器会依据随包 `VERSION` 移除 1.17.8 不支持的 `subagent_depth`，而 1.18.4 继续强制深度 2。完整差异和验证结论见 `docs/deployment/opencode-upgrade-1.18.4.md`。

## 自定义 Tool 离线依赖

`test-agent-programs.tar.gz` 已内置与 OpenCode `1.18.4` 锁定的自定义 Tool 基线：`@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod` 及其全部传递依赖；Node 22 自带的 `fetch`、`URL`、`AbortController` 等标准 API 不需要额外包。OpenCode 启动时不会联网安装依赖，而会为 XDG 全局配置、公共配置和项目 `.opencode` 建立非覆盖式 package/lockfile 与模块链接；配置目录已有同名文件时保留现有版本。

标准后台部署脚本会调用 `verify-opencode-tool-runtime.sh`，在 `included` programs 解压前后以及 `reuse` 现场复用时核对 runtime manifest、lockfile、全部固定直接依赖的包元数据和入口文件；`@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod` 缺失、为空、未锁定或版本不符都会在服务变更前失败。专项校验可执行 `tools/verify-opencode-tool-runtime-deploy.sh`。

运行依赖的 Git 忽略清单以 `deploy/internal/opencode-runtime.gitignore` 为单一来源，固定包含 `node_modules`、`package.json`、`package-lock.json`、`bun.lock` 和 `.gitignore`。后台升级脚本会对已经初始化的标准公共配置目录幂等补齐缺失规则，不覆盖管理员已有规则；新增节点尚未 clone 公共仓库时不会提前创建目录，第一个 OpenCode 进程会在创建 package/lockfile 链接前补齐同一清单。因此升级、扩容或重复启动后，这些运行文件不会让公共仓库误报本地变更，`agents/**`、`skills/**`、`tools/**` 和用户维护的 OpenCode 配置仍按原 Git 规则检测。忽略规则不会自动取消已经跟踪的文件，也不会删除任何未跟踪文件。

升级或新增后台完成后，在该后台验证：

```bash
cd /data/testagent/data/agent-opencode/.config
git check-ignore -v \
  opencode/package.json \
  opencode/package-lock.json \
  opencode/node_modules
git status --short --untracked-files=all
```

前三个路径应命中 `opencode/.gitignore`，最后一条不应出现上述运行文件；真实 Agent、Skill、Tool 或公共配置改动仍应显示。若 `git ls-files -- opencode/package.json opencode/package-lock.json` 有输出，说明文件已被历史提交跟踪，部署脚本会保留现场，必须先人工确认仓库内容再决定是否从版本控制中移除。

worker 不再从 OpenCode 源码生成 Node bundle，而是下载并校验上游官方 `opencode-linux-x64-baseline.tar.gz`。`/usr/local/lib/opencode/RELEASE` 固定记录 asset、归档 SHA、二进制 SHA 和 release commit；源码快照仅用于审计。Node 22 只承载轻量启动器和自定义 Tool 离线依赖。

这套基线覆盖使用官方 `tool(...)`、schema、SDK 类型和 Effect/Zod 的 Tool。`axios`、数据库驱动或企业私有 SDK 等任意业务依赖不会被猜测加入；新增这类 import 时，必须同步修改 `opencode-node-runtime.package.json` 和 lockfile，在外网 Mac 重新打完整企业包。升级依赖不能只替换 Tool 文件，必须同时解压新 programs、导入新 worker 镜像并重启 worker；标准 `deploy-internal-release.sh` 已按该顺序执行。

Agent 配置热加载不修改 OpenCode 的配置目录解析：公共配置继续由 `OPENCODE_CONFIG_DIR` 提供，应用配置由当前个人 workspace 的 `.opencode` 提供；平台在 Git 发布阶段同步个人 worktree，再调用 OpenCode 原生 `/global/dispose`。官方程序启动器只做离线依赖链接、离线开关、`subagent_depth=2` 和信号转发，不包含公共个人或应用共享路径映射，也不需要在 `docker.env` 手工拼接个人物理路径。

## 中转机与目标服务器交付目录

企业内部中转机只在 `~/Desktop/mimoagent/0709` 放置、校验并分发 U 盘交付物；禁止把中转机目录写成 `/data/0709`。固定文件为：

```text
~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip
~/Desktop/mimoagent/0709/test-agent-two-backend-complete.zip.sha256
~/Desktop/mimoagent/0709/test-agent-redis-offline.zip
~/Desktop/mimoagent/0709/test-agent-redis-offline.zip.sha256
```

中转机校验命令：

```bash
cd ~/Desktop/mimoagent/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
sha256sum -c test-agent-redis-offline.zip.sha256
unzip -t test-agent-two-backend-complete.zip
unzip -t test-agent-redis-offline.zip
```

目标服务器 `.20/.4/.114/.2` 才统一使用 `/data/0709/` 作为交付物接收和校验目录。平台文件名保持不变：

```text
/data/0709/test-agent-two-backend-complete.zip
/data/0709/test-agent-two-backend-complete.zip.sha256
```

每台服务器开始部署前只校验本机对应的外层包。例如平台节点执行：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -t test-agent-two-backend-complete.zip
```

`/data/0709/` 只作为离线交付物上传和校验目录；部署脚本仍把运行文件安装到 `/data/testagent/`，两者不要混用。

## 现场配置轻量敏感采集

需要基于现场真实配置生成逐节点部署脚本时，使用
[`collect-multi-backend-context.sh`](collect-multi-backend-context.sh) 分别采集前端和后台。
脚本只读部署手册约定的配置路径，只在临时目录及指定输出目录创建文件；它必须显式传入
`--include-sensitive`，并会保留 env 中的原始密码和 token。生成的归档和校验文件权限固定为
`0600`，压缩包强制不超过 `1 MiB`；超过时会删除归档并失败，不会交付部分结果。

脚本明确不采集 JAR/lib、JAR 内 RSA 私钥、日志、Docker inspect/镜像、programs、worker 镜像、
数据库转储、业务数据或已部署前端。

将脚本复制到三台服务器后执行：

```bash
# 122.233.30.2
bash /data/0709/collect-multi-backend-context.sh frontend \
  --include-sensitive \
  --node-label 122-233-30-2 \
  --output-dir /data/0709

# 122.233.30.4
bash /data/0709/collect-multi-backend-context.sh backend \
  --include-sensitive \
  --node-label 122-233-30-4 \
  --output-dir /data/0709

# 122.233.30.114
bash /data/0709/collect-multi-backend-context.sh backend \
  --include-sensitive \
  --node-label 122-233-30-114 \
  --output-dir /data/0709
```

每台会生成一对文件：

```text
test-agent-config-SENSITIVE-<role>-<node>-<timestamp>.tar.gz
test-agent-config-SENSITIVE-<role>-<node>-<timestamp>.tar.gz.sha256
```

后台归档只包含原始 `backend.env`、`docker.env`、`.serverid/.serverhost` 和 systemd 有效 unit；
前端归档只包含原始 `nginx.env`、实体 Nginx 主配置及活动 `test-agent.conf`。脚本不会探测网络，
不会启动、停止或重启任何服务。

## 标准目录

```text
/data/testagent/
  config/
    backend.env
    docker.env
    nginx.env        # 仅前端 Nginx 服务器
  data/
  deploy/internal/
  dist/
  frontend/
  programs/
```

企业交付 JAR/ZIP 包含平台 RSA 私钥，必须按密钥交付物限制读取、复制和留存；替换内置密钥会让既有数据库 SSH key 密文无法解密，除非用户重新保存 SSH key。

企业后端采用 `test-agent-app.jar` 瘦启动器与 `dist/backend/lib/` 外置依赖。Flyway migration
实际打进 `test-agent-persistence-0.1.0-SNAPSHOT.jar`；打包、外层封装、节点预校验和安装后
复验均锁定工具盒子企业 migration SHA-256
`777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`，并比较发布包与安装后
persistence JAR 的完整 SHA。只校验外层 ZIP 或 app JAR 不能证明数据库资源已更新。

## 首次部署与版本升级顺序

首次部署时 Java 需要先写 `.serverid/.serverhost`，无论单后台还是多后台，每个后端节点都按以下顺序部署：

1. 从两台后台确认外部 `122.210.106.43:3306` 可达，两份 `backend.env` 使用同一个 JDBC 地址、账号密码和 XXL access token。
2. 替换 Java JAR、`backend/lib/` 和随包 XXL 上游许可证材料。
3. 夜间迁移升级先停止全部旧 Java，再启动新版本；平台 PostgreSQL 使用晚于已交付 `V20260721213000` 的 `V20260722130000`，兼容已经部署过上午版本的存量库，不需要开启 Flyway `outOfOrder`。随后确认 PostgreSQL migration、外部 MySQL 上 XXL Flyway V4、Admin health，以及 15 分钟分发/5 分钟补偿任务均启用且无旧 runner。
4. 确认本机 `/data/testagent/data/.serverid` 和 `.serverhost`。
5. 导入 worker 镜像、解压 programs。
6. 启动本机唯一 worker，等待当前结构化日志 `event=manager_config_update status=applied`；部署脚本同时兼容旧版 `manager config update applied`。
7. 配置/重载 Nginx 同源 `/xxl-job-admin/` 代理，初始化公共 OpenCode 并完成 iframe SSO/executor 验收。

已有环境升级“用户绑定端口复用与无主进程展示”版本时，身份文件和 manager state 已存在，顺序改为“manager → Java 后端 → 前端”：先逐台更新 worker/manager 并确认 `stopOwned` capability 和心跳恢复，再滚动更新 Java，全部 Java 就绪后最后部署一次前端。混合版本中的未知命令或错误只允许报错并保留原 binding，不得迁移端口；不要在滚动窗口内同时对同一用户执行人工重启与初始化。该版本不变更 `backend.env`、`docker.env`、数据库结构、SSE 或 generated SDK，也不自动处理存量重复/无主进程。

扩容时只在新 Linux 启动一套 Java/worker，将新节点同时加入 `TEST_AGENT_NGINX_BACKENDS` 和 `TEST_AGENT_NGINX_XXL_JOB_ADMINS` 后执行 Nginx 无停机 reload；这两个 Nginx upstream 变量不是 Java 配置，旧 Java 不需要修改环境或重启。当前 `.4 + .114` 双后台交付为每台 worker 发布 `14096-15095` 共 1000 个端口坐标；页面全局通用参数 `OPENCODE_MANAGER_MAX_PROCESSES=30` 会分别热推到两台 manager，使每台后台实际最多运行 30 个用户 OpenCode 进程。其它部署仍按各自节点包配置。manager 异常时优先核对数据根目录、manager token、`.serverid/.serverhost` 和本机端口池。

首次部署不要先启动 worker 再修 Java 身份文件；上述 manager 优先顺序只适用于身份文件和 state 已存在的升级。

## 配置模板

- Java：[backend.env.example](backend.env.example)
- worker/构建：[env.example](env.example)
- 前端 Nginx：[nginx.env.example](nginx.env.example)、[configure-nginx.sh](configure-nginx.sh)
- XXL MySQL：当前生产直接使用外部实例；[mysql.env.example](mysql.env.example) 和 [deploy-xxl-job-mysql.sh](deploy-xxl-job-mysql.sh) 仅作为其它隔离环境的容器备用方案

当前企业浏览器入口固定为 HTTP，因此 Java 模板显式设置 `TEST_AGENT_XXL_JOB_COOKIE_SECURE=false`；基础应用默认仍为 `true`，HTTPS 环境不得复制该例外。两台后台必须保持一致，诊断脚本会输出脱敏的 `COOKIE_SECURE` 状态并拒绝缺失或错误值。
- `.4 + .114` 逐机配置包：[deploy-multi-backend-node.sh](deploy-multi-backend-node.sh)，支持
  `--validate-only`、正式部署和 `--verify-only`，内部复用标准后台/前端部署脚本
- 一键入口：平台包使用 [deploy-backend-node.sh](deploy-backend-node.sh)、
  [deploy-frontend-node.sh](deploy-frontend-node.sh)；本机 IP 自动识别且校验、部署、复验输出统一落盘
- 新后台初始化：[init-backend-node-config.sh](init-backend-node-config.sh) 自动派生本机
  `backend.env`、`docker.env`；[register-backend-on-frontend.sh](register-backend-on-frontend.sh)
  在前端登记新后台并更新打包的 `nginx.env`
- 公共模型配置：[opencode.jsonc.example](opencode.jsonc.example)

单后台的 `configure-single-deployment.sh frontend` 会用临时 `.conf` 实测候选目录是否加载新文件，避免把“显式 include 某一个现有文件”的同级目录误判为通配目录。首次配置还必须显式传入两套工具节点 `host:port`；后续重跑会保留现有 `nginx.env` 中的值，绝不静默改为本机端口。当前 `.2` 已确认显式加载专用 `/data/apps/nginx/conf/test-agent.conf`，检查并备份后应通过 `--gateway-conf` 明确复用该文件；只有它还承载其他系统、不能由本应用接管时，才由 Nginx 管理方增加专用通配目录。具体命令见 [单后台配置脚本执行单](SINGLE-BACKEND-CONFIGURATION.md)。

同一个 Nginx `server` 块需要同时监听多个端口时，保留主端口 `TEST_AGENT_NGINX_LISTEN_PORT`，并在 `TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS` 中填写逗号分隔的附加端口。当前域名链路继续落到实体 `:80`，IP 直连增加 `:9996`；渲染脚本会拒绝非法或重复端口。

历史链接兼容：

- [OPERATION-MANUAL.md](OPERATION-MANUAL.md) 转到单后台文档。
- [README-two-backend-122-233-30-114.md](README-two-backend-122-233-30-114.md) 转到多后台文档。

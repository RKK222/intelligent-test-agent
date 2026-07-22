# 企业内部署文档入口

当前代码支持单后台和完整多后台部署。两种模式使用同一套 Mac 离线交付物、平台 PostgreSQL、独立共享 XXL MySQL、Redis 运行态、Java→manager 控制协议和内部模型代理。每个 Java 进程同时运行平台 WebFlux、独立 Admin 子端口和 executor；executor 注册不使用 Linux 亲和，夜间任务由 XXL 扫描后按任务固化的服务器通过公共 Java 路由分发。一次性 WebSocket ticket 继续保存在签发 JVM；页面从 `/processes/me` 获得用户 binding 后，会给后续 OpenCode、会话、Run、SSE 和本地工作区请求携带页面内存中的 `linuxServerId`，Nginx 用静态白名单把已知 ID 精确首跳到一机一 Java 的目标节点，缺失或未知 ID 仍走 `least_conn`，后端权威路由继续兜底。workspace PTY、文件和 Agent 配置进度沿用既有固定节点方式；标准生产部署中，服务器 PTY 也复用同一静态路由表固定到签发 Java，不依赖 sticky。

企业交付模板默认设置 `TEST_AGENT_SERVER_TERMINAL_ENABLED=true`，并要求 `TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=wss://<前端入口>`；应用本身在缺少该显式配置时仍保持关闭。上线时确认 systemd Java 的 `User=` 就是期望的运维用户，终端只继承该用户权限，不使用 `sudo` 或额外授权。标准入口的前端 `nginx.env` 必须开启 TLS、配置证书路径，并以 `linuxServerId=host:port` 填写统一的 `TEST_AGENT_NGINX_SERVER_ROUTES`。旧 `TEST_AGENT_NGINX_TERMINAL_ROUTES` 只用于升级兼容，新配置不得与新键并存。当前现场明确选择 HTTP、不能使用 HTTPS，因此单后台和 `.4 + .114` 多后台都按对应文档显式允许 `ws://`，并接受登录数据和终端内容明文传输、浏览器网段必须直达各 Java `:8080` 的风险；该现场例外不改变通用 WSS 安全默认。

请选择对应文档：

- [单后台部署](SINGLE-BACKEND.md)：一个 Java 后端和一个 `opencode-worker`，当前现场示例为 `122.233.30.114`；包含可整文件替换的生产配置。
- [多后台部署](MULTI-BACKEND.md)：两个或更多 Java/worker 节点，包含 `.4 + .114` 各自的完整配置、部署和验收示例。

底层 Java、manager、Redis 路由设计见 [后端部署说明](../../docs/deployment/backend.md)。

## 共同前提

- Mac 构建机允许联网；企业服务器完全离线。
- 企业内不使用 Docker Compose；worker 由 `opencode-worker-docker.sh` 管理，XXL MySQL 由 `deploy-xxl-job-mysql.sh` 管理。
- Java 读取 `/data/testagent/config/backend.env`。
- Java 固定读取交付 JAR 内置的 `classpath:rsa-private.key`；`backend.env` 不再接受外置 RSA 路径，多后台必须部署同一 JAR。
- 所有 Java 连接 `122.233.30.147:3306` 上独立的 XXL MySQL 容器，并使用同一个强随机 XXL access token；MySQL 与平台 PostgreSQL 进程和数据目录分开，但部署在同一台数据库服务器。
- 每个 Java 的 Admin 固定与同 JVM executor 配对，executor 注册地址复用平台 advertised host；同机多 Java 的 Admin/executor 端口必须唯一，所有 Admin 必须能访问所有 executor。前端 Nginx 把 `/xxl-job-admin/` 同源代理到各 Admin 子端口。
- worker 读取 `/data/testagent/config/docker.env`。
- Java 的 `SYS_DATA_ROOT_DIR` 必须与本机 worker 的 `TEST_AGENT_DATA_ROOT` 一致。
- 每个稳定 `TEST_AGENT_LINUX_SERVER_ID` 只运行一个 worker，不配置人工 `containerId/managerId`。
- 企业模型供应商地址和上游 token 由数据库及管理页面维护，不写入 `docker.env`。
- 正式模型链路为 `OpenCode → 本机 Java:8080 → 企业内部模型:9070`，不使用 19070 relay 或 host network。

## Mac 打包

从仓库根目录执行：

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

标准发布 ZIP、三台应用节点配置包和数据库服务器 MySQL 配置包齐全后，用固定外层封装脚本生成 U 盘交付物：

```bash
deploy/internal/package-two-backend-complete.sh \
  --release-archive deploy/internal/dist/test-agent-internal-release.zip \
  --nodes-dir /path/to/prepared-node-packages \
  --output-dir /path/to/usb-output
```

以后外层只使用固定的一套名称，不添加日期、`v2`、`v3` 等后缀；重复打包会无交互覆盖旧文件：

```text
test-agent-two-backend-complete.zip
test-agent-two-backend-complete.zip.sha256
```

ZIP 内顶层目录同样固定为 `test-agent-two-backend-complete/`，包含内层标准发布 ZIP、三台应用节点包、数据库服务器 MySQL 节点包及各自 SHA，
并在顶层提供无需传 IP 参数的 `deploy-backend-node.sh`、`deploy-frontend-node.sh`、`deploy-mysql-node.sh`。脚本从本机网卡取 IP，
连续执行节点包校验、预校验、正式部署和部署后校验，完整输出保存在交付目录上一层的
`deploy-<本机IP>.log`。企业内部中转机只需接收上面这一个 ZIP 和配套 SHA 文件，后续 `scp` 命令不再随版本修改。
封装脚本可复用仍含 `TEST_AGENT_NGINX_TERMINAL_ROUTES` 的旧前端节点包：它只在临时副本中迁移为
`TEST_AGENT_NGINX_SERVER_ROUTES`，不会修改源敏感包或输出 env 内容；缺少路由键、重复定义或新旧键并存会直接失败。

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
deploy/internal/dist/mysql_8.4-linux-amd64.tar
deploy/internal/dist/frontend/
```

完整 zip 同时包含 `deploy/internal/` 下的配置模板、部署脚本、Nginx 模板、模型配置示例和本部署文档。企业服务器只执行校验、解压、`docker load` 和服务启停，不执行 Maven、pnpm、Docker build 或联网下载。

## 自定义 Tool 离线依赖

`test-agent-programs.tar.gz` 已内置与 OpenCode `1.17.8` 锁定的自定义 Tool 基线：`@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod` 及其全部传递依赖；Node 22 自带的 `fetch`、`URL`、`AbortController` 等标准 API 不需要额外包。OpenCode 启动时不会联网安装依赖，而会为公共配置 `tools/` 和项目 `.opencode/tools/` 建立指向 `/data/testagent/programs/opencode/node_modules` 的非覆盖式链接；配置目录已有同名包时保留现有版本，链接目录由 `.gitignore` 排除，不应提交到公共仓库。

Node 兼容 bundle 保留 provider `chunkTimeout` 的分片超时保护。上游 SSE 长时间无数据时，当前包会把 `SSE read timed out` 交给既有会话错误/重试流程，并显式消费底层流取消失败；该超时不得再以未处理 Promise 拒绝结束整个用户 opencode server。若日志出现 `triggerUncaughtException`、`ResponseStreamError: SSE read timed out` 后紧接 `Node.js v22.23.1` 且进程退出，说明仍在运行未包含此兼容修复的旧 worker 镜像；调整 `chunkTimeout` 只能改变触发时长，必须在外网 Mac 重打完整包、导入新 worker 镜像并重启对应用户进程。

这套基线覆盖使用官方 `tool(...)`、schema、SDK 类型和 Effect/Zod 的 Tool。`axios`、数据库驱动或企业私有 SDK 等任意业务依赖不会被猜测加入；新增这类 import 时，必须同步修改 `opencode-node-runtime.package.json` 和 lockfile，在外网 Mac 重新打完整企业包。升级依赖不能只替换 Tool 文件，必须同时解压新 programs、导入新 worker 镜像并重启 worker；标准 `deploy-internal-release.sh` 已按该顺序执行。

Agent 配置热加载不修改 OpenCode 的配置目录解析：公共配置继续由 `OPENCODE_CONFIG_DIR` 提供，应用配置由当前个人 workspace 的 `.opencode` 提供；平台在 Git 发布阶段同步个人 worktree，再调用 OpenCode 原生 `/global/dispose`。`opencode-node-compat.patch` 只保留既有企业离线 Node 依赖兼容内容，不再包含公共个人或应用共享路径映射，也不需要在 `docker.env` 手工拼接个人物理路径。

## 统一上传目录

企业内所有目标服务器统一把完整 ZIP 和 SHA-256 校验文件上传到 `/data/0709/`，文件名保持不变：

```text
/data/0709/test-agent-internal-release.zip
/data/0709/test-agent-internal-release.zip.sha256
```

单后台时上传到前端 `.2`、后台 `.114` 和数据库 `.147`；多后台时上传到 `.147`、前端及每个后台节点。每台服务器开始部署前都先执行：

```bash
cd /data/0709
sha256sum -c test-agent-internal-release.zip.sha256
unzip -t test-agent-internal-release.zip
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
    mysql.env        # 仅 122.233.30.147 数据库服务器
  data/
  deploy/internal/
  dist/
  frontend/
  programs/
```

企业交付 JAR/ZIP 包含平台 RSA 私钥，必须按密钥交付物限制读取、复制和留存；替换内置密钥会让既有数据库 SSH key 密文无法解密，除非用户重新保存 SSH key。

## 首次部署与版本升级顺序

首次部署时 Java 需要先写 `.serverid/.serverhost`，无论单后台还是多后台，每个后端节点都按以下顺序部署：

1. 在 `122.233.30.147` 执行 `deploy-mysql-node.sh`，离线导入 MySQL 8.4 linux/amd64 镜像，初始化 `xxl_job` 库和最小权限账号；密码由打包阶段安全生成并同步写入两个后台节点包。
2. 替换 Java JAR、`backend/lib/` 和随包 XXL 上游许可证材料。
3. 夜间迁移升级先停止全部旧 Java，再启动新版本；平台 PostgreSQL 使用晚于已交付 `V20260721213000` 的 `V20260722130000`，兼容已经部署过上午版本的存量库，不需要开启 Flyway `outOfOrder`。随后确认 PostgreSQL migration、XXL Flyway V4、Admin health，以及 15 分钟分发/5 分钟补偿任务均启用且无旧 runner。
4. 确认本机 `/data/testagent/data/.serverid` 和 `.serverhost`。
5. 导入 worker 镜像、解压 programs。
6. 启动本机唯一 worker，等待当前结构化日志 `event=manager_config_update status=applied`；部署脚本同时兼容旧版 `manager config update applied`。
7. 配置/重载 Nginx 同源 `/xxl-job-admin/` 代理，初始化公共 OpenCode 并完成 iframe SSO/executor 验收。

已有环境升级“用户绑定端口复用与无主进程展示”版本时，身份文件和 manager state 已存在，顺序改为“manager → Java 后端 → 前端”：先逐台更新 worker/manager 并确认 `stopOwned` capability 和心跳恢复，再滚动更新 Java，全部 Java 就绪后最后部署一次前端。混合版本中的未知命令或错误只允许报错并保留原 binding，不得迁移端口；不要在滚动窗口内同时对同一用户执行人工重启与初始化。该版本不变更 `backend.env`、`docker.env`、数据库结构、SSE 或 generated SDK，也不自动处理存量重复/无主进程。

扩容时只在新 Linux 启动一套 Java/worker，将新节点同时加入 `TEST_AGENT_NGINX_BACKENDS` 和 `TEST_AGENT_NGINX_XXL_JOB_ADMINS` 后执行 Nginx 无停机 reload；这两个 Nginx upstream 变量不是 Java 配置，旧 Java 不需要修改环境或重启。manager 异常时优先核对数据根目录、manager token、`.serverid/.serverhost` 和 `4096-4105` 端口池。

首次部署不要先启动 worker 再修 Java 身份文件；上述 manager 优先顺序只适用于身份文件和 state 已存在的升级。

## 配置模板

- Java：[backend.env.example](backend.env.example)
- worker/构建：[env.example](env.example)
- 前端 Nginx：[nginx.env.example](nginx.env.example)、[configure-nginx.sh](configure-nginx.sh)
- XXL MySQL：[mysql.env.example](mysql.env.example)、[deploy-xxl-job-mysql.sh](deploy-xxl-job-mysql.sh)，默认数据目录 `/data/testagent/mysql`，重复部署不会删除已有数据
- `.4 + .114` 逐机配置包：[deploy-multi-backend-node.sh](deploy-multi-backend-node.sh)，支持
  `--validate-only`、正式部署和 `--verify-only`，内部复用标准后台/前端部署脚本
- 完整包一键入口：[deploy-backend-node.sh](deploy-backend-node.sh)、
  [deploy-frontend-node.sh](deploy-frontend-node.sh)、[deploy-mysql-node.sh](deploy-mysql-node.sh)，本机 IP 自动识别且校验、部署、复验输出统一落盘
- 新后台初始化：[init-backend-node-config.sh](init-backend-node-config.sh) 自动派生本机
  `backend.env`、`docker.env`；[register-backend-on-frontend.sh](register-backend-on-frontend.sh)
  在前端登记新后台并更新打包的 `nginx.env`
- 公共模型配置：[opencode.jsonc.example](opencode.jsonc.example)

单后台的 `configure-single-deployment.sh frontend` 会用临时 `.conf` 实测候选目录是否加载新文件，避免把“显式 include 某一个现有文件”的同级目录误判为通配目录。当前 `.2` 已确认显式加载专用 `/data/apps/nginx/conf/test-agent.conf`，检查并备份后应通过 `--gateway-conf` 明确复用该文件；只有它还承载其他系统、不能由本应用接管时，才由 Nginx 管理方增加专用通配目录。具体命令见 [单后台配置脚本执行单](SINGLE-BACKEND-CONFIGURATION.md)。

同一个 Nginx `server` 块需要同时监听多个端口时，保留主端口 `TEST_AGENT_NGINX_LISTEN_PORT`，并在 `TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS` 中填写逗号分隔的附加端口。当前域名链路继续落到实体 `:80`，IP 直连增加 `:9996`；渲染脚本会拒绝非法或重复端口。

历史链接兼容：

- [OPERATION-MANUAL.md](OPERATION-MANUAL.md) 转到单后台文档。
- [README-two-backend-122-233-30-114.md](README-two-backend-122-233-30-114.md) 转到多后台文档。

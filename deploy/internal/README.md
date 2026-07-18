# 企业内部署文档入口

当前代码支持单后台和完整多后台部署。两种模式使用同一套 Mac 离线交付物、数据库结构、Redis 运行态、Java→manager 控制协议和内部模型代理；一次性 WebSocket ticket 继续保存在签发 JVM。workspace PTY、文件和 Agent 配置进度沿用既有固定节点方式；服务器 PTY 通过 HTTPS Nginx 的精确 `linuxServerId` location 固定到签发 Java，不依赖 sticky。

服务器终端默认关闭。上线时先在每个后端 `backend.env` 配置 `TEST_AGENT_SERVER_TERMINAL_ENABLED=true` 和 `TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=wss://<前端入口>`，确认 systemd Java 的 `User=` 就是期望的运维用户；终端只继承该用户权限，不使用 `sudo` 或额外授权。再在前端 `nginx.env` 开启 TLS、配置证书路径，并以 `linuxServerId=host:port` 填写 `TEST_AGENT_NGINX_TERMINAL_ROUTES`。重新执行 `configure-nginx.sh` 后先启动/验证 Java，再启动 worker，最后在“选择服务器工作空间”弹窗切换到“服务器终端”，点击连接并在弹出的目标确认框中确认。

请选择对应文档：

- [单后台部署](SINGLE-BACKEND.md)：一个 Java 后端和一个 `opencode-worker`，当前现场示例为 `122.233.30.114`；包含可整文件替换的生产配置。
- [多后台部署](MULTI-BACKEND.md)：两个或更多 Java/worker 节点，包含 `.4 + .114` 各自的完整配置、部署和验收示例。

底层 Java、manager、Redis 路由设计见 [后端部署说明](../../docs/deployment/backend.md)。

## 共同前提

- Mac 构建机允许联网；企业服务器完全离线。
- 企业内不使用 Docker Compose，worker 由 `opencode-worker-docker.sh` 管理。
- Java 读取 `/data/testagent/config/backend.env`。
- Java 通过 `backend.env` 中的 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH` 读取持久 RSA 私钥；文件默认 `/data/testagent/config/ssh-rsa-private.key`、权限 0600，多后台必须内容一致。
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

交付物：

```text
deploy/internal/dist/test-agent-internal-release.zip
deploy/internal/dist/test-agent-internal-release.zip.sha256
deploy/internal/dist/backend/test-agent-app.jar
deploy/internal/dist/backend/lib/
deploy/internal/dist/test-agent-frontend-dist.tar.gz
deploy/internal/dist/test-agent-programs.tar.gz
deploy/internal/dist/test-agent-opencode-worker_internal-linux-amd64.tar
deploy/internal/dist/frontend/
```

完整 zip 同时包含 `deploy/internal/` 下的配置模板、部署脚本、Nginx 模板、模型配置示例和本部署文档。企业服务器只执行校验、解压、`docker load` 和服务启停，不执行 Maven、pnpm、Docker build 或联网下载。

## 自定义 Tool 离线依赖

`test-agent-programs.tar.gz` 已内置与 OpenCode `1.17.8` 锁定的自定义 Tool 基线：`@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod` 及其全部传递依赖；Node 22 自带的 `fetch`、`URL`、`AbortController` 等标准 API 不需要额外包。OpenCode 启动时不会联网安装依赖，而会为公共配置 `tools/` 和项目 `.opencode/tools/` 建立指向 `/data/testagent/programs/opencode/node_modules` 的非覆盖式链接；配置目录已有同名包时保留现有版本，链接目录由 `.gitignore` 排除，不应提交到公共仓库。

这套基线覆盖使用官方 `tool(...)`、schema、SDK 类型和 Effect/Zod 的 Tool。`axios`、数据库驱动或企业私有 SDK 等任意业务依赖不会被猜测加入；新增这类 import 时，必须同步修改 `opencode-node-runtime.package.json` 和 lockfile，在外网 Mac 重新打完整企业包。升级依赖不能只替换 Tool 文件，必须同时解压新 programs、导入新 worker 镜像并重启 worker；标准 `deploy-internal-release.sh` 已按该顺序执行。

Agent 配置热加载不修改 OpenCode 的配置目录解析：公共配置继续由 `OPENCODE_CONFIG_DIR` 提供，应用配置由当前个人 workspace 的 `.opencode` 提供；平台在 Git 发布阶段同步个人 worktree，再调用 OpenCode 原生 `/global/dispose`。`opencode-node-compat.patch` 只保留既有企业离线 Node 依赖兼容内容，不再包含公共个人或应用共享路径映射，也不需要在 `docker.env` 手工拼接个人物理路径。

## 统一上传目录

企业内所有目标服务器统一把完整 ZIP 和 SHA-256 校验文件上传到 `/data/0709/`，文件名保持不变：

```text
/data/0709/test-agent-internal-release.zip
/data/0709/test-agent-internal-release.zip.sha256
```

单后台时上传到前端 `.2` 和后台 `.114`；多后台时上传到前端及每个后台节点。每台服务器开始部署前都先执行：

```bash
cd /data/0709
sha256sum -c test-agent-internal-release.zip.sha256
unzip -t test-agent-internal-release.zip
```

`/data/0709/` 只作为离线交付物上传和校验目录；部署脚本仍把运行文件安装到 `/data/testagent/`，两者不要混用。

## 标准目录

```text
/data/testagent/
  config/
    backend.env
    docker.env
    nginx.env        # 仅前端 Nginx 服务器
    ssh-rsa-private.key  # 仅 Java 后台；不进入交付包，多后台内容一致
  data/
  deploy/internal/
  dist/
  frontend/
  programs/
```

## 固定启动顺序

无论单后台还是多后台，每个后端节点都按以下顺序部署：

1. 替换 Java JAR 和 `backend/lib/`。
2. 启动 Java，确认 health/readiness。
3. 确认本机 `/data/testagent/data/.serverid` 和 `.serverhost`。
4. 导入 worker 镜像、解压 programs。
5. 启动本机唯一 worker，等待 `manager config update applied`。
6. 初始化本服务器公共 OpenCode 配置并验证用户进程。

不要先启动 worker 再修 Java 身份文件。

## 配置模板

- Java：[backend.env.example](backend.env.example)
- worker/构建：[env.example](env.example)
- 前端 Nginx：[nginx.env.example](nginx.env.example)、[configure-nginx.sh](configure-nginx.sh)
- 公共模型配置：[opencode.jsonc.example](opencode.jsonc.example)

历史链接兼容：

- [OPERATION-MANUAL.md](OPERATION-MANUAL.md) 转到单后台文档。
- [README-two-backend-122-233-30-114.md](README-two-backend-122-233-30-114.md) 转到多后台文档。

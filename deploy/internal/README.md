# 企业内部署文档入口

当前代码支持单后台和完整多后台部署。两种模式使用同一套 Mac 离线交付物、数据库结构、Redis 运行态、Java→manager 控制协议和内部模型代理；多后台的一次性 WebSocket ticket 继续保存在签发 JVM，但 PTY、文件和 Agent 配置进度都已把后续连接固定到签发 Java，不依赖 Nginx sticky。

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

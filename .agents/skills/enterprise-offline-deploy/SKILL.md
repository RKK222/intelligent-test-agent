---
name: enterprise-offline-deploy
description: Use whenever the user asks about enterprise/internal/offline deployment, packaging on Mac, release artifacts, enterprise staging-host transfer, deploy/internal/package-release.sh, backend.env, docker.env, opencode-worker, opencode-manager, or how to deploy this project in a network-isolated enterprise environment. Distinguish the external build Mac from the enterprise staging host, and always provide explicit step-by-step deployment instructions.
---

# 企业内离线部署说明

本技能用于 `/Users/kaka/Desktop/intelligent-test-agent` 的企业内部署问答。用户提到“企业内部署”“内网部署”“离线部署”“Mac 打包”“完全不能联网”“opencode worker”“opencode manager port”“backend.env”“docker.env”时必须使用。

## 固定前提

- 打包机是 Mac，允许联网，用来拉 Maven、pnpm、Docker base image、npm/opencode 包等构建依赖。
- 企业内部署环境完全不能联网，只能接收 Mac 打好的交付物。
- 当前现场通过 U 盘把完整交付物导入企业内部中转机；中转机固定交付目录是 `~/Desktop/mimoagent/0709`，不得写成 `/data/0709`。后续 `scp` 从该目录发起；只有 `.20/.4/.114/.2` 等目标服务器的接收目录是 `/data/0709`。
- Mac 只负责构建交付物。交付物已进入企业内部中转机后，不得再把现场传输步骤描述为“从 Mac scp”。
- 企业内不使用 Docker Compose；`opencode-worker` 用 `deploy/internal/opencode-worker-docker.sh` 纯 Docker 命令管理。
- 企业目标机可能使用未启用 experimental features 的旧 Docker daemon。Redis 离线镜像在加载后通过 `docker image inspect` 校验 `linux/amd64`；目标机执行 `docker run` 时不得再强制传 `--platform`。`--platform linux/amd64` 只用于 Mac 外网构建机拉取、构建或验证固定架构镜像。
- Redis 服务器 `.20` 通过 Docker 发布 `6379` 时必须持久化 `net.ipv4.ip_forward=1`。Redis 容器本机 `healthy`、`0.0.0.0:6379` 和本机 TCP 成功都不能替代 `.4/.114` 跨机验证；`deploy-redis.sh deploy/verify` 会拒绝转发值为 `0` 的宿主机，但不会擅自修改系统网络策略。
- 企业内不要使用根目录 `.env.local`、`.env.test` 作为生产配置。
- Java 后端读取 `/data/testagent/config/backend.env`。
- Java 的 SSH 混合加密 RSA 私钥固定内置在交付 JAR 的 `classpath:rsa-private.key`；`backend.env` 不配置外置路径，多后台必须部署同一 JAR。交付 JAR/ZIP 按密钥交付物受控留存。
- worker/打包配置读取 `/data/testagent/config/docker.env`，模板来自 `deploy/internal/env.example`。
- Java 的 `SYS_DATA_ROOT_DIR` 必须与 worker 的 `TEST_AGENT_DATA_ROOT` 指向同一个宿主机目录，默认 `/data/testagent/data`。
- 新版不再配置 `OPENCODE_MANAGER_ID`、`OPENCODE_MANAGER_SERVER_IP_FILE`、`OPENCODE_MANAGER_LINUX_SERVER_ID`。
- 当前前端实体 Nginx 安装在 `/data/apps/nginx`；单后台现场先运行 `configure-single-deployment.sh frontend` 生成 `nginx.env`，不要用 PATH 中可能读取 `/root/conf/nginx.conf` 的其他 `nginx`。

## 每次回答必须包含

回答企业内部署问题时，不要只给单条命令。必须覆盖：

1. 说明 Mac 打包前提和当前完成状态；如果用户已把包导入企业内部中转机，明确该阶段已完成，从中转机校验和 `scp` 开始，不要求用户重跑 Mac 打包。
2. 打包产物清单。
3. 每个产物传到企业内哪台服务器、哪个路径。
4. 企业内需要准备和修改的配置文件：`backend.env`、`docker.env`、必要时 `nginx.env` 或 Nginx conf。
5. 启动/升级顺序：先 Java，确认 `.serverid/.serverhost`，再 worker。
6. 验证命令和预期现象。
7. `opencode-manager` 端口或连接报错时的优先排查点。

## 现场操作说明偏好

用户每次部署都需要一步一步的执行单，回答必须遵守：

1. 每一步先写明当前操作机器，例如“企业内部中转机”“122.233.30.4 后台”“122.233.30.114 后台”“122.233.30.2 前端”。
2. 明确写出进入的绝对目录、完整文件名和完整命令，不使用前后不一致的 `BASE`、`WORK` 等假定目录。
3. 命令按实际顺序逐条给出，不用循环、批量伪代码或“同上”省略三台机器的操作。
4. 每个关键命令后写预期输出或成功条件；任一步失败时明确要求停止，不继续下一台。
5. 顺序固定为：中转机校验并 `scp` → `.4` 后台 → `.114` 后台 → `.2` 前端 → 浏览器业务验收；滚动部署有特殊前提时必须说明。
6. 区分“外层 U 盘完整包”“内层完整发布 ZIP”“节点专属配置包”，明确每台服务器需要哪一对文件及落盘路径。
7. 不要求用户回传真实数据库密码、token、Cookie、RSA 私钥或其他密钥；诊断输出只展示状态、长度或哈希。
8. 中转机直接在 `~/Desktop/mimoagent/0709` 校验并向目标服务器 `scp`，不在中转机创建 `/data/0709`。只有目标服务器需要把本机已校验的明确文件复制到 `/data/0709` 时，才使用 `/bin/cp -f <源文件> /data/0709/`；禁止扩大为 `cp -rf` 覆盖目录。
9. 后续双后台 U 盘外层交付固定为 `test-agent-two-backend-complete.zip` 和 `test-agent-two-backend-complete.zip.sha256`，包内顶层固定为 `test-agent-two-backend-complete/`；不再添加日期、`v2`、`v3` 或临时目录名。一个 ZIP 内必须包含内层标准发布 ZIP 和三台节点包，SHA 文件只作为这个唯一完整包的传输校验。

## 标准目录

企业内统一使用：

```text
/data/testagent/
  config/
    backend.env
    docker.env
    nginx.env
  data/
  deploy/internal/
  dist/
  frontend/
  programs/
```

## Mac 打包命令

从 Mac 仓库根目录执行：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh
```

本地 Mac 默认输出到：

```text
deploy/internal/dist/
```

如果需要指定输出目录：

```bash
deploy/internal/package-release.sh --output-dir /path/to/dist
```

以上命令只在外部联网 Mac 重新构建交付物时执行。包已通过 U 盘进入企业内部中转机后，现场步骤从中转机上的 SHA256 校验和 `scp` 开始。

标准发布 ZIP 与三台节点包齐全后，使用 `deploy/internal/package-two-backend-complete.sh` 生成固定外层完整包。企业内部中转机每次只接收固定名 ZIP 和配套 SHA，不再按日期或版本改变命令。

## 打包产物

必须说明这些产物：

```text
deploy/internal/dist/backend/test-agent-app.jar
deploy/internal/dist/test-agent-frontend-dist.tar.gz
deploy/internal/dist/test-agent-programs.tar.gz
deploy/internal/dist/test-agent-opencode-worker_internal-linux-amd64.tar
deploy/internal/dist/frontend/
```

还要同步 `deploy/internal/` 目录到企业内，用于 Nginx 模板和 `opencode-worker-docker.sh`。

## 企业内配置文件

### `/data/testagent/config/backend.env`

这是 Java 后端配置。必须提醒用户至少检查：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=<后端服务器IP或域名>
TEST_AGENT_LINUX_SERVER_ID=<稳定服务器ID>
SYS_DATA_ROOT_DIR=/data/testagent/data

TEST_AGENT_DB_URL=jdbc:postgresql://<pg-host>:<port>/<db>
TEST_AGENT_DB_USERNAME=<user>
TEST_AGENT_DB_PASSWORD=<password>

TEST_AGENT_REDIS_HOST=<redis-host>
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://<前端入口>
TEST_AGENT_API_TOKEN=
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<manager-token>
TEST_AGENT_INTERNAL_PROXY_API_KEY=<random-internal-proxy-api-key>

TEST_AGENT_SERVER_BROADCAST_ENABLED=true
TEST_AGENT_MODEL_CATALOG_SOURCE=internal
```

交付 JAR 必须包含 `BOOT-INF/classes/rsa-private.key`；共享数据库的所有 Java 必须部署同一 JAR。升级不得替换内置密钥，否则用户需要重新添加个人 SSH key。

### `/data/testagent/config/docker.env`

这是 worker 和打包配置。必须提醒用户至少检查：

```dotenv
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<必须和backend.env一致>
TEST_AGENT_DATA_ROOT=/data/testagent/data
TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs
TEST_AGENT_OPENCODE_WORKER_IMAGE=test-agent-opencode-worker:internal

OPENCODE_WORKER_BACKEND_PORT=8080
OPENCODE_WORKER_PORT_START=4096
OPENCODE_WORKER_PORT_END=5095

VITE_TEST_AGENT_API_BASE_URL=
```

当前 `.4 + .114` 企业包的端口池必须是宿主机可访问的 `4096-5095`，Docker 内外保持同号映射，不要做 `14096:4096`。每台提供 1000 个端口坐标；数据库通用参数 `OPENCODE_MANAGER_MAX_PROCESSES` 也必须由超级管理员调到 `1000`，否则实际并发仍受较小参数值限制。
`TEST_AGENT_INTERNAL_PROXY_API_KEY` 是 Java 内部模型代理鉴权 key，只配置在 `backend.env`，不要放到 `docker.env`；Java 会在启动用户 opencode server 时通过 manager command 注入给子进程。

### 单后台配置脚本

交付 ZIP 内的 `deploy/internal/configure-single-deployment.sh` 用于当前 `.2 + .114` 单后台现场：

```bash
# 122.233.30.114：保留当前数据库密码及 token，重建 backend.env/docker.env
bash deploy/internal/configure-single-deployment.sh backend

# 122.233.30.2：探测 /data/apps/nginx 实际 include 目录并重建 nginx.env
bash deploy/internal/configure-single-deployment.sh frontend --nginx-home /data/apps/nginx
```

后台角色要求现有 `backend.env` 中已有数据库密码和内部代理 key，且两份 env 的 manager token 一致；任一条件不满足时必须在写文件前失败。前端角色生成的 `nginx.env` 明确设置 `/data/apps/nginx/sbin/nginx`、prefix、主配置和 binary reload；`configure-nginx.sh` 仍保留 PATH nginx + systemd 作为其他标准安装环境的兼容兜底。

## 标准部署顺序

1. 中转机在 `~/Desktop/mimoagent/0709` 校验固定名 ZIP/SHA，然后分别 `scp` 到目标服务器 `/data/0709`。
2. 若现网仍是 Redis 5，先在 `.20` 确认 `net.ipv4.ip_forward=1`，再停 `.4/.114` Java，完成盘点、最终 RDB、可恢复备份和 Redis 7.4.9 升级；原数据目录不删除。Redis 本机验证通过后，必须从 `.4`、`.114` 分别确认 `.20:6379` TCP 可达，任一失败不得启动 Java。
3. 在 `.4` 执行后台一键入口，内部先启动 Java、写入身份文件，再导入 programs/worker 并启动 manager。
4. 确认 `.4` Java 写出：

```bash
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost
```

5. `.4` 全部通过后在 `.114` 执行后台入口，并确认本机 `.serverid/.serverhost`。
6. 两台后台通过后，最后在 `.2` 部署前端并 reload Nginx。一键入口已封装下列 worker 操作，只在排障时手工执行：

```bash
docker load -i /data/testagent/dist/test-agent-opencode-worker_internal-linux-amd64.tar
tar -C /data/testagent -xzf /data/testagent/dist/test-agent-programs.tar.gz
cd /data/testagent/deploy/internal
./opencode-worker-docker.sh --env-file /data/testagent/config/docker.env restart
```

7. 验证：

```bash
curl -fsS http://<后端服务器>:8080/actuator/health
curl -fsS http://<后端服务器>:8080/actuator/health/readiness
curl -fsS http://<前端入口>/
docker logs --tail 120 test-agent-opencode-worker
```

当前 worker 日志期望看到 `event=manager_config_update status=applied`；旧版可能输出 `manager config update applied`，部署验证必须兼容两种格式。

## 常见问题提醒

- `opencode-manager` 报端口配置缺失：不要手工直接跑 `opencode-manager run`；生产用 `opencode-worker-docker.sh`。端口写在 `docker.env` 的 `OPENCODE_WORKER_PORT_START/END`。
- Redis 部署报 `"--platform" is only supported on a Docker daemon with experimental features enabled`：先确认目标机为 `x86_64`、镜像检查为 `linux/amd64`、没有同名残留容器且数据目录仍只有原 `dump.rdb`；使用当前不带运行期 `--platform` 的 `deploy-redis.sh` 重试，不开启 daemon experimental features，不删除 RDB/AOF，不直接加 `--replace-existing`。
- Redis 部署报 `can't open config file ... permission denied`：这是 Linux bind mount 读取 0600 宿主机配置的 UID 权限问题，不要把含密码的配置改成 0644；临时将配置复制为 `/data/testagent/redis/config/redis.conf`、设为 `0400` 并赋予镜像 `redis` UID/GID `999:1000` 后用该路径重试。新脚本会在容器内复制配置并用 `setpriv` 切换到 redis 用户，后续无需人工复制。
- Redis 容器 `healthy`、宿主机监听 `0.0.0.0:6379`、`.20` 本机 TCP 成功，但 `.4/.114` 连接超时：先检查 `.20` 的 `sysctl net.ipv4.ip_forward`，值为 `0` 是 Docker DNAT 无法转发到容器的直接原因；先用 `sysctl -w net.ipv4.ip_forward=1` 验证并通过企业批准的 sysctl 配置持久化。抓包能看到远端 SYN、没有 SYN-ACK 时先查该值和 `FORWARD/DOCKER-USER`，完全看不到 SYN 才查上游 VLAN/ACL；不要优先新增 `INPUT` 规则或反复重启 Java。
- manager 等待 `.serverhost` 或连接旧 IP：检查 `backend.env` 的 `SYS_DATA_ROOT_DIR` 是否等于 `docker.env` 的 `TEST_AGENT_DATA_ROOT`，并确认 Java 已先启动并写出 `.serverid/.serverhost`。
- 数据库通用参数 `SYS_DATA_ROOT_DIR` 仍是 Linux 默认 `/data/.testagent`：企业内部署需要在系统管理通用参数或数据库中改为 `/data/testagent/data`，否则 Java/worker 共享目录会错位。
- 公共配置目录未初始化：超级管理员进入“系统管理 -> 配置管理 -> opencode公共配置管理”初始化，确保 `OPENCODE_PUBLIC_CONFIG_DIR` 指向的目录存在且非空。
- 企业内不能联网：不要在企业内执行需要拉依赖的构建命令；企业内只执行 `docker load`、解压、启动 Java、启动 worker、Nginx reload。

## 回答风格

优先给可复制的逐条命令和明确检查项。默认用户需要完整的一步步操作，不把多台服务器压缩成循环或省略步骤。需要覆盖已存在的明确交付文件时使用 `/bin/cp -f`，不使用可能被 `cp -i` alias 改写的裸 `cp`。涉及 token、数据库密码、模型密钥时使用占位符，不要要求用户把真实密钥发回聊天。

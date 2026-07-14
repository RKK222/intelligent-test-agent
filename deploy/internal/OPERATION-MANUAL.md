# TestAgent 企业内离线升级操作手册

## 1. 交付目标与目录

本次交付面向三台服务器：

| 服务器 | 作用 | 主要操作 |
|---|---|---|
| `122.233.30.2` | 前端实体 Nginx | 更新静态资源并 reload Nginx。 |
| `122.233.30.4` | Java 后端 A + opencode-worker A | 更新 jar、外挂程序和 worker 镜像。 |
| `122.233.30.114` | Java 后端 B + opencode-worker B | 更新 jar、外挂程序和 worker 镜像。 |

推荐给三台服务器分发同一个 `test-agent-internal-release.zip`。后端服务器最终使用的外挂程序目录为：

```text
/data/testagent/programs/
├── bin/opencode-manager        # 单个 linux/amd64 二进制
└── opencode/                   # OpenCode Node 运行目录，必须整体保留
    ├── bin/opencode
    ├── server/
    ├── node_modules/
    ├── LICENSE
    └── VERSION
```

OpenCode 入口仍是 `opencode/bin/opencode`，但它不是旧的 Bun 单文件程序；它通过 worker 镜像内的 Node 22 加载同目录 server bundle。manager 可以单独替换，OpenCode 必须整体替换 `opencode/` 目录。

## 2. 上线前检查

在 Mac 打包机仓库根目录生成完整包：

```bash
deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

确认存在：

```text
deploy/internal/dist/test-agent-internal-release.zip
deploy/internal/dist/test-agent-internal-release.zip.sha256
deploy/internal/dist/test-agent-opencode-worker_internal-linux-amd64.tar
deploy/internal/dist/test-agent-programs.tar.gz
deploy/internal/dist/backend/test-agent-app.jar
deploy/internal/dist/test-agent-frontend-dist.tar.gz
```

将完整 zip 和打包脚本同时生成的 `.sha256` 文件上传到三台服务器的 `/data/0709/`，在每台服务器校验：

```bash
cd /data/0709
sha256sum -c test-agent-internal-release.zip.sha256
unzip -t test-agent-internal-release.zip
```

后端 A 的 `/data/testagent/config/backend.env` 至少确认：

```dotenv
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.4
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-4
SYS_DATA_ROOT_DIR=/data/testagent/data
```

后端 B 对应改为：

```dotenv
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-114
SYS_DATA_ROOT_DIR=/data/testagent/data
```

两台后端的 `backend.env` 与 `docker.env` 中 manager token 必须一致；`docker.env` 的 `TEST_AGENT_DATA_ROOT=/data/testagent/data`，端口池继续保持宿主机端口与容器端口一一对应。不要把 token、数据库密码或模型密钥写入交付 zip。

升级前保留当前镜像回滚标签：

```bash
docker image inspect test-agent-opencode-worker:internal >/dev/null 2>&1 \
  && docker tag test-agent-opencode-worker:internal test-agent-opencode-worker:rollback-$(date +%Y%m%d%H%M%S) \
  || true
```

## 3. 前端服务器 `122.233.30.2`

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-frontend.sh \
  > /tmp/deploy-internal-frontend.sh

bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --validate-only

bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip
```

验收：

```bash
nginx -t
curl -fsS http://122.233.30.2/health
curl -fsS http://122.233.30.2/ >/dev/null
```

## 4. 后端/worker A `122.233.30.4`

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-release.sh \
  > /tmp/deploy-internal-release.sh

bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --validate-only

bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.4 \
  --skip-frontend
```

## 5. 后端/worker B `122.233.30.114`

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-release.sh \
  > /tmp/deploy-internal-release.sh

bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --validate-only

bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.114 \
  --skip-frontend
```

## 6. 两台后端逐台验收

以下命令在对应后端机器执行，把 `<本机IP>` 替换为 `122.233.30.4` 或 `122.233.30.114`：

```bash
curl -fsS http://<本机IP>:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost

docker exec test-agent-opencode-worker sh -lc \
  '"$OPENCODE_BIN" --version; readlink -f "$OPENCODE_BIN"; node --version; ! command -v bun'

docker logs --tail 200 test-agent-opencode-worker | \
  egrep 'config update applied|websocket|serverhost|serverid|OPENCODE_UNAVAILABLE'
```

期望结果：

- readiness 返回 `UP`；
- `.serverhost` 等于本机真实 IP；
- `opencode --version` 输出 `1.17.8` 且退出码为 `0`；
- OpenCode 入口位于 `/usr/local/lib/opencode-node/` 或外挂 `/data/testagent/programs/opencode/`；
- `command -v bun` 找不到 Bun；
- manager 日志出现 `manager config update applied`，没有持续 WebSocket 重连错误。

超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”，确认本机 `/data/testagent/data/agent-opencode/.config/opencode/` 已初始化且非空；`.configdev` 为空不影响企业公共配置主路径。随后从运行管理启动或重启一个用户 OpenCode 进程，确认 `baseUrl` 是 `http://<本机IP>:4096` 这类真实 IP 地址，并检查：

```bash
tail -n 200 /data/testagent/data/agent-opencode/manager/worker/logs/4096.log
curl -fsS http://127.0.0.1:4096/global/health
```

## 7. 后续只升级 manager

新 manager 二进制放到后端机器 `/tmp/opencode-manager` 后执行：

```bash
cp -a /data/testagent/programs/bin/opencode-manager \
  /data/testagent/programs/bin/opencode-manager.bak.$(date +%Y%m%d%H%M%S)
install -m 0755 /tmp/opencode-manager \
  /data/testagent/programs/bin/opencode-manager.new
mv -f /data/testagent/programs/bin/opencode-manager.new \
  /data/testagent/programs/bin/opencode-manager
docker restart test-agent-opencode-worker
docker logs --tail 120 test-agent-opencode-worker
```

这条路径不会替换 OpenCode 文件夹，也不需要重新 `docker load`。重启 worker 是推荐操作，因为容器内只有一个常驻 manager，Docker 会同时清理其旧用户子进程和 state 对应的实际进程，再由平台按需拉起。

## 8. 后续只升级 OpenCode

OpenCode 必须整体替换目录。先把新 `programs/opencode/` 解压到 `/tmp/programs/opencode/`，然后执行：

```bash
docker stop test-agent-opencode-worker
mv /data/testagent/programs/opencode \
  /data/testagent/programs/opencode.bak.$(date +%Y%m%d%H%M%S)
mv /tmp/programs/opencode /data/testagent/programs/opencode
docker start test-agent-opencode-worker
docker exec test-agent-opencode-worker \
  /data/testagent/programs/opencode/bin/opencode --version
docker logs --tail 120 test-agent-opencode-worker
```

不要只替换 `opencode/bin/opencode`，否则 launcher 与 `server/`、`node_modules/` 版本不一致。

## 9. 回滚

manager-only 回滚：恢复最近的 `opencode-manager.bak.*`，再 `docker restart test-agent-opencode-worker`。

OpenCode-only 回滚：停止 worker，把最近的 `opencode.bak.*` 目录恢复为 `/data/testagent/programs/opencode`，再启动 worker。

全量回滚：使用上线前保留的 worker 回滚镜像标签、部署脚本生成的 jar/前端/程序备份，按“先 Java，后 worker”的顺序恢复。回滚后再次检查 `.serverid/.serverhost`，不得把 `.serverid` 当作 HTTP host。

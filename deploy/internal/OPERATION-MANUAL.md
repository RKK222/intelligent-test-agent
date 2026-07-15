# TestAgent 企业内 114 单后端升级手册

当前上线目标：前端 `122.233.30.2`，Java 后端和 `opencode-worker` 均在 `122.233.30.114`。完整配置、模型初始化和排障说明见 `deploy/internal/README.md`。

正式模型链路：`OpenCode → 122.233.30.114:8080 → ai-code.sdc.icbc:9070`。保留 8080 和 4096-4105 端口池；9070 仅作为 Java 出站地址，不发布 19070 relay，不启用 host network。

## 1. Mac 打包

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

确认：

```text
deploy/internal/dist/test-agent-internal-release.zip
deploy/internal/dist/test-agent-internal-release.zip.sha256
deploy/internal/dist/backend/test-agent-app.jar
deploy/internal/dist/backend/lib/
deploy/internal/dist/test-agent-frontend-dist.tar.gz
deploy/internal/dist/test-agent-programs.tar.gz
deploy/internal/dist/test-agent-opencode-worker_internal-linux-amd64.tar
```

把 zip 和 sha256 分别传到：

```text
122.233.30.2:/data/0709/
122.233.30.114:/data/0709/
```

企业内服务器只做校验、解压和启动，不执行联网构建。

## 2. 上线前检查

两台服务器执行：

```bash
cd /data/0709
sha256sum -c test-agent-internal-release.zip.sha256
unzip -t test-agent-internal-release.zip
```

114 确认 `/data/testagent/config/backend.env`：

```dotenv
SPRING_PROFILES_ACTIVE=prod
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-114
SYS_DATA_ROOT_DIR=/data/testagent/data
TEST_AGENT_INTERNAL_PROXY_API_KEY=<随机内部代理 key>
```

两台后端的 `backend.env` 与 `docker.env` 中 manager token 必须一致；`docker.env` 的 `TEST_AGENT_DATA_ROOT=/data/testagent/data`，端口池继续保持宿主机端口与容器端口一一对应。每台服务器的 `TEST_AGENT_LINUX_SERVER_ID` 必须全局唯一且长期稳定，每个该 ID 只启动一个 worker；无需且不得配置人工 `containerId/managerId`。不要把 token、数据库密码或模型密钥写入交付 zip。
同时确认：

- `backend.env` 与 `docker.env` 的 `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 完全一致；
- `docker.env` 的 `TEST_AGENT_DATA_ROOT=/data/testagent/data`；
- `docker.env` 不包含内部代理 key 或企业模型上游 token；
- 超级管理员“内部模型供应商”页面已有启用的 `qwen-prod`、`deepseek-prod`，且显示 Token 已配置；
- 公共配置仓库 `opencode.jsonc` 已使用 `deploy/internal/opencode.jsonc.example` 的内容。

## 3. 更新前端

在 `122.233.30.2`：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-frontend.sh \
  > /tmp/deploy-internal-frontend.sh

bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --validate-only

bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip

nginx -t
curl -fsS http://122.233.30.2/health
```

Nginx upstream 只应指向 `122.233.30.114:8080`。

## 4. 更新 114 后端与 worker

在 `122.233.30.114`：

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

脚本先重启 Java，校验 `.serverid/.serverhost`，再导入镜像并重启 worker。不要颠倒顺序。

## 5. 验收

在 114：

```bash
systemctl status test-agent-backend --no-pager
journalctl -u test-agent-backend -n 120 --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost

cd /data/testagent/deploy/internal
./opencode-worker-docker.sh --env-file /data/testagent/config/docker.env status
docker logs --tail 200 test-agent-opencode-worker | \
  egrep 'config update applied|websocket|serverhost|serverid|OPENCODE_UNAVAILABLE'
```

期望：

```text
.serverid   = test-agent-backend-122-233-30-114
.serverhost = 122.233.30.114
manager config update applied
```

从运行管理重启一个用户 opencode 进程后检查：

```bash
curl -fsS http://127.0.0.1:4096/global/health
curl -fsS http://127.0.0.1:4096/api/provider
curl -fsS http://127.0.0.1:4096/api/model
```

供应商只应包含 `icbc-qwen`、`icbc-deepseek`；模型应包含 `Qwen3.6-27B`、`DeepSeek-V4-Flash-W8A8`。再从前端分别选择两个模型发起真实对话。

## 6. 回滚与注意事项

升级前保留当前 jar、`backend/lib/`、programs 目录和 worker 镜像标签。全量回滚时按“Java → 身份文件 → worker”的顺序恢复。只替换公共 `opencode.jsonc` 不会自动重启现有用户进程。

如果 provider header 与数据库 `provider_id` 不一致，Java 代理会拒绝；如果数据库 token 未配置，代理同样会拒绝。不要用旧环境变量或把 token 写入 `docker.env` 绕过数据库配置。

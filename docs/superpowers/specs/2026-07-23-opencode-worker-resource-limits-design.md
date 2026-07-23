# OpenCode Worker 容器资源限制设计

## 目标

企业内部署通过 `deploy/internal/opencode-worker-docker.sh` 创建 worker 容器时，固定设置以下资源限制：

```text
pids=8192
nofile soft=262144, hard=262144
nproc soft=8192, hard=8192
```

限制值不从 `docker.env` 读取，确保所有企业后台节点使用一致配置。

## 实现设计

在 `opencode-worker-docker.sh` 的唯一 `docker run` 命令中，紧跟现有 `--privileged` 参数加入：

```bash
--pids-limit=8192 \
--ulimit nofile=262144:262144 \
--ulimit nproc=8192:8192 \
```

不修改 worker 镜像、Entrypoint、端口池、挂载目录、环境变量或健康检查。`opencode-manager` 及其创建的 OpenCode 子进程继承容器的文件句柄和用户进程限制；Docker cgroup 对容器内全部进程执行 8192 PID 上限。

## 兼容性与影响

- 企业目标 Docker 18.09 支持 `--pids-limit` 和 `--ulimit`；不引入 Docker Compose 或新镜像依赖。
- `nofile=262144:262144` 提高容器文件句柄容量，避免依赖宿主 Docker daemon 的默认值。
- `nproc=8192:8192` 和 `pids=8192` 为容器增加显式上界。当前 manager 业务并发上限最多为 20 个 OpenCode server，8192 为 Node 工作线程和子进程保留充足余量，不改变正常业务容量。
- 参数只在重新创建容器时生效。执行 worker `restart` 会短暂终止该容器内的 manager 和 OpenCode 进程，因此企业多后台必须逐节点滚动操作，当前节点验证通过后才能处理下一节点。
- 不涉及 HTTP API、RunEvent、数据库、Flyway、generated SDK、安全凭据或 `backend.env`/`docker.env` 结构变更。

## 测试设计

先扩展 `tools/verify-dev-scripts.sh` 的 mock Docker 测试，要求捕获的 `docker run` 命令精确包含三个固定参数。修改生产脚本前运行该测试并确认它因缺少参数失败，再加入最小实现使测试通过。

完成后执行：

1. `bash -n deploy/internal/opencode-worker-docker.sh`，验证 Bash 语法。
2. `tools/verify-dev-scripts.sh`，验证启动参数回归测试。
3. 使用本机 Docker Desktop 和当前 worker 镜像创建临时容器，通过 `docker inspect` 验证 `PidsLimit=8192`、`nofile=262144:262144`、`nproc=8192:8192`，并读取容器 `/proc/1/limits` 验证生效值。
4. `git diff --check`，验证补丁格式。

若本机镜像或 Docker 服务不可用，必须明确记录未完成的真实容器验证，不以静态脚本检查替代。

## 文档与企业升级

同步更新 `deploy/internal/README.md`、`deploy/internal/SINGLE-BACKEND.md`、`deploy/internal/MULTI-BACKEND.md` 和 `docs/deployment/backend.md`，记录固定限制、生效方式及验证命令。

正式离线包仍由联网 Mac 执行 `deploy/internal/package-release.sh` 生成，交付物名称和目录结构保持不变。现场升级不修改两台后台的 `backend.env` 或 `docker.env`；先确认 Java 健康和 `.serverid/.serverhost`，再逐节点替换 `deploy/internal/`、重启 worker，并通过 `docker inspect`、容器 limits、manager 配置日志和 Java readiness 验证后继续下一节点。

## 回滚

若旧 Docker 现场拒绝新参数或容器启动失败，停止升级，不处理下一节点；恢复上一版 `opencode-worker-docker.sh` 并重新创建当前 worker 容器。数据目录、programs、manager state 和数据库记录均保持不变，不执行删除操作。

# OpenCode Worker Container Resource Limits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 固定为企业 worker 容器设置 `pids=8192`、`nofile=262144:262144` 和 `nproc=8192:8192`，并通过自动化与真实 Docker 验证保证升级不改变既有业务配置。

**Architecture:** 只修改企业统一入口 `deploy/internal/opencode-worker-docker.sh` 的唯一 `docker run` 命令，不新增环境变量或镜像层。现有 `tools/verify-dev-scripts.sh` mock Docker 测试负责锁定命令参数，企业部署文档负责声明滚动重建和运行时核验方式。

**Tech Stack:** Bash、Docker 18.09 兼容 CLI、systemd 部署文档、Git。

## Global Constraints

- 固定参数必须精确为 `--pids-limit=8192`、`--ulimit nofile=262144:262144`、`--ulimit nproc=8192:8192`。
- 不新增或修改 `docker.env`、`backend.env` 参数。
- 不修改 worker 镜像、Entrypoint、端口池、挂载、健康检查、API、事件、数据库或 generated SDK。
- 企业多后台必须逐节点重建 worker；一台验证成功后才能操作下一台。
- 不创建新 Git 分支；只提交本任务文件，提交信息使用中文。

---

### Task 1: 以测试驱动固定 Docker 资源参数

**Files:**
- Modify: `tools/verify-dev-scripts.sh:240-281`
- Modify: `deploy/internal/opencode-worker-docker.sh:98-129`

**Interfaces:**
- Consumes: `opencode-worker-docker.sh --env-file <path> restart` 现有命令行接口和 mock `docker` 可执行文件。
- Produces: `docker run` 固定携带三个资源限制参数；不改变脚本命令行或 env 接口。

- [x] **Step 1: 写入失败断言**

在 `tools/verify-dev-scripts.sh` 调用 worker 脚本后加入精确参数断言：

```bash
worker_docker_run="$(grep '^run ' "${worker_docker_calls}")"
if [[ " ${worker_docker_run} " != *" --pids-limit=8192 "* ]]; then
  cat "${worker_docker_calls}" >&2
  fail "worker docker script should set pids limit to 8192"
fi
if [[ " ${worker_docker_run} " != *" --ulimit nofile=262144:262144 "* ]]; then
  cat "${worker_docker_calls}" >&2
  fail "worker docker script should set nofile soft and hard limits to 262144"
fi
if [[ " ${worker_docker_run} " != *" --ulimit nproc=8192:8192 "* ]]; then
  cat "${worker_docker_calls}" >&2
  fail "worker docker script should set nproc soft and hard limits to 8192"
fi
```

- [x] **Step 2: 运行测试并确认 RED**

Run: `tools/verify-dev-scripts.sh`

Expected: FAIL，首个稳定错误为 `worker docker script should set pids limit to 8192`，证明测试捕获的是尚未实现的参数。

- [x] **Step 3: 加入最小生产实现**

在 `opencode-worker-docker.sh` 的 `--privileged` 后加入：

```bash
    --pids-limit=8192 \
    --ulimit nofile=262144:262144 \
    --ulimit nproc=8192:8192 \
```

- [x] **Step 4: 运行测试并确认 GREEN**

Run: `bash -n deploy/internal/opencode-worker-docker.sh`

Expected: exit 0，无输出。

Run: `tools/verify-dev-scripts.sh`

Expected: exit 0，末行包含 `Development script verification passed.`。

- [x] **Step 5: 提交脚本与测试**

```bash
git add deploy/internal/opencode-worker-docker.sh tools/verify-dev-scripts.sh
git commit -m "部署：固定 worker 容器资源限制"
```

### Task 2: 同步部署文档并验证真实容器

**Files:**
- Modify: `deploy/internal/README.md`
- Modify: `deploy/internal/SINGLE-BACKEND.md`
- Modify: `deploy/internal/MULTI-BACKEND.md`
- Modify: `docs/deployment/backend.md`
- Modify: `.agents/session-log.huangzhenren.md`
- Include: `docs/superpowers/plans/2026-07-23-opencode-worker-resource-limits.md`

**Interfaces:**
- Consumes: Task 1 固定后的 worker 启动参数和现有 `restart/status/logs` 操作入口。
- Produces: 企业部署、验证和回滚文档；不产生新 API 或配置字段。

- [x] **Step 1: 在企业部署入口说明固定限制**

在 `deploy/internal/README.md` worker 共同前提附近加入：

```markdown
- `opencode-worker-docker.sh` 固定为 worker 容器设置 `--pids-limit=8192`、`nofile=262144:262144` 和 `nproc=8192:8192`；这些值不从 `docker.env` 覆盖。脚本升级后必须重建容器才会生效。
```

- [x] **Step 2: 在单/多后台手册加入运行时核验**

在两份手册的 worker 验证部分分别加入：

```bash
docker inspect --format 'PidsLimit={{.HostConfig.PidsLimit}} Ulimits={{json .HostConfig.Ulimits}}' test-agent-opencode-worker
docker exec test-agent-opencode-worker sh -lc "grep -E 'Max processes|Max open files' /proc/1/limits"
```

并声明预期：`PidsLimit=8192`，inspect 中包含 `nofile 262144/262144` 与 `nproc 8192/8192`，容器 limits 显示最大打开文件数 `262144`、最大用户进程数 `8192`。多后台按 `.4`、`.114` 顺序逐台重建并验证，当前节点失败时停止。

- [x] **Step 3: 更新后端部署排障文档**

在 `docs/deployment/backend.md` worker 兼容说明中记录资源限制、只在容器重建后生效，以及参数缺失时优先确认现场使用的 `opencode-worker-docker.sh` 是否为当前版本。

- [x] **Step 4: 用本机 Docker Desktop 验证真实 HostConfig 和进程 limits**

创建临时 env 和数据目录，使用当前镜像启动唯一命名的验证容器：

```bash
tmp_dir="$(mktemp -d /tmp/test-agent-worker-limits.XXXXXX)"
mkdir -p "${tmp_dir}/data" "${tmp_dir}/programs"
printf '%s\n' \
  'TEST_AGENT_OPENCODE_MANAGER_TOKEN=verify-token' \
  "TEST_AGENT_DATA_ROOT=${tmp_dir}/data" \
  "TEST_AGENT_PROGRAM_ROOT=${tmp_dir}/programs" \
  'TEST_AGENT_OPENCODE_WORKER_IMAGE=test-agent-opencode-worker:1.18.4' \
  'OPENCODE_WORKER_BACKEND_PORT=8080' \
  'OPENCODE_WORKER_PORT_START=42096' \
  'OPENCODE_WORKER_PORT_END=42115' >"${tmp_dir}/docker.env"
bash deploy/internal/opencode-worker-docker.sh \
  --env-file "${tmp_dir}/docker.env" \
  --name test-agent-opencode-worker-limits-verify \
  start
docker inspect --format '{{.HostConfig.PidsLimit}} {{json .HostConfig.Ulimits}}' test-agent-opencode-worker-limits-verify
docker exec test-agent-opencode-worker-limits-verify sh -lc "grep -E 'Max processes|Max open files' /proc/1/limits"
```

Expected: inspect 显示 PID 上限 `8192`，两个 ulimit 名称及其精确 soft/hard 值；`/proc/1/limits` 显示 `Max processes 8192 8192` 和 `Max open files 262144 262144`。

验证完成后只删除明确命名的临时容器和 `mktemp` 返回目录：

```bash
docker rm -f test-agent-opencode-worker-limits-verify
rm -rf "${tmp_dir}"
```

- [x] **Step 5: 运行最终静态校验**

Run: `tools/verify-dev-scripts.sh`

Expected: PASS。

Run: `git diff --check`

Expected: exit 0，无输出。

- [x] **Step 6: 更新会话日志并提交**

在 `.agents/session-log.huangzhenren.md` 追加一条 `Why / What / How / Result`，记录三个固定值、Docker 18.09 兼容判断、真实 Docker 验证结果和逐节点重建要求。提交前重新回顾全部 `.agents/session-log*.md` 近期条目并确认没有覆盖其他开发者成果。

```bash
git add docs/superpowers/plans/2026-07-23-opencode-worker-resource-limits.md \
  deploy/internal/README.md \
  deploy/internal/SINGLE-BACKEND.md \
  deploy/internal/MULTI-BACKEND.md \
  docs/deployment/backend.md \
  .agents/session-log.huangzhenren.md
git commit -m "文档：补充 worker 资源限制部署说明"
```

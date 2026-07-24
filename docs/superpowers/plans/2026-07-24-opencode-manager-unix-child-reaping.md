# OpenCode Manager Unix 子进程回收实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让非 Windows opencode-manager 正确回收已退出的直属 OpenCode 子进程，避免 zombie PID 阻断 `stopOwned` 停止确认。

**Architecture:** 保留现有 manager 生命周期锁、进程组信号、PID health 和 state 删除规则，只在 Unix `OSStarter` 启动成功后异步调用 `exec.Cmd.Wait()`。真实子进程测试直接复现“快速退出后 PID 仍被 signal 0 判活”，证明修复前失败、修复后被 OS 回收；Java 公共停止链路不改。

**Tech Stack:** Go 1.23+、`os/exec`、Go `testing`、现有 `health.DefaultProcessAlive`、本地三服务联调环境。

## Global Constraints

- 只修改与 Unix 子进程回收直接相关的最小范围，不重构 manager 生命周期架构。
- Windows `process_windows.go` 保持不变；PowerShell 包装进程不得套用 Unix `Wait()` 行为。
- 不绕过 `OpencodeProcessStopService`、`stopOwned` 或 UCID + PID 实例校验，不降低“确认 PID 退出后才删除 state”的安全边界。
- 不修改 HTTP API、RunEvent、DTO、数据库、Flyway、SQL、generated SDK、权限或 `.env.local` / `.env.test`。
- 人工维护代码的新增非显然逻辑使用中文注释。
- 不新建 Git 分支；所有提交信息使用中文。

---

### Task 1: 用真实子进程回归锁定并修复 Unix zombie

**Files:**
- Create: `opencode-manager/internal/process/process_unix_test.go`
- Modify: `opencode-manager/internal/process/process_unix.go:12-31`
- Modify: `opencode-manager/README.md:105-125`

**Interfaces:**
- Consumes: `OSStarter.Start(context.Context, StartSpec) (int, error)` 和 `health.DefaultProcessAlive(pid int) bool`。
- Produces: `OSStarter.Start` 保持签名和非阻塞返回语义不变，但保证每个成功启动的 Unix 直属子进程最终执行一次 `command.Wait()`。

- [ ] **Step 1: 新增会快速退出的真实 Unix 子进程测试**

```go
//go:build !windows

package process

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/enterprise/test-agent/opencode-manager/internal/health"
)

func TestOSStarterReapsExitedChildProcess(t *testing.T) {
	pid, err := (OSStarter{}).Start(context.Background(), StartSpec{
		Command: "/bin/sh",
		Args:    []string{"-c", "exit 0"},
		LogPath: filepath.Join(t.TempDir(), "child.log"),
	})
	if err != nil {
		t.Fatalf("start child process: %v", err)
	}

	deadline := time.Now().Add(time.Second)
	for health.DefaultProcessAlive(pid) && time.Now().Before(deadline) {
		time.Sleep(10 * time.Millisecond)
	}
	if health.DefaultProcessAlive(pid) {
		t.Fatalf("exited child pid %d must be reaped instead of remaining alive as zombie", pid)
	}
}
```

- [ ] **Step 2: 运行定向测试并确认 RED 原因正确**

Run: `cd opencode-manager && go test ./internal/process -run '^TestOSStarterReapsExitedChildProcess$' -count=1 -v`

Expected: FAIL，失败信息包含 `must be reaped instead of remaining alive as zombie`；失败来自退出子进程未调用 `Wait()`，不是编译、路径或 shell 启动错误。

- [ ] **Step 3: 在 Unix 启动器加入最小异步回收实现**

在 `command.Start()` 成功分支和 PID 返回之间加入：

```go
	// Unix 子进程必须由创建它的 manager 调用 Wait 回收，否则退出后会以 zombie 保留 PID，
	// 使基于 signal 0 的停止确认误判为仍存活；异步等待保持 Start 的非阻塞语义。
	go func() {
		_ = command.Wait()
	}()
```

不缓存 `exec.Cmd`，不修改 `OSSignaler`、`DefaultProcessAlive` 或 `Manager.stopRecord`。

- [ ] **Step 4: 运行定向测试并确认 GREEN**

Run: `cd opencode-manager && go test ./internal/process -run '^TestOSStarterReapsExitedChildProcess$' -count=1 -v`

Expected: PASS，快速退出子进程在 1 秒上限内被回收。

- [ ] **Step 5: 更新 manager 稳定文档**

在 `opencode-manager/README.md` 的生命周期语义中明确加入：

```markdown
非 Windows manager 在 `Start` 成功后异步等待并回收自己创建的 OpenCode 直属子进程，避免退出进程残留为 zombie。该等待不阻塞启动响应；停止链路仍在 SIGTERM/SIGKILL 后通过 PID 消失确认退出，只有确认后才删除 state。
```

- [ ] **Step 6: 运行 manager 全量回归和格式校验**

Run: `cd opencode-manager && gofmt -w internal/process/process_unix.go internal/process/process_unix_test.go && go test ./...`

Expected: 所有 Go 包 PASS。

Run: `git diff --check`

Expected: 无输出，退出码 0。

- [ ] **Step 7: 提交代码、测试和稳定文档**

```bash
git add opencode-manager/internal/process/process_unix.go \
  opencode-manager/internal/process/process_unix_test.go \
  opencode-manager/README.md
git commit -m "修复 manager Unix 子进程停止失败"
```

### Task 2: 重启本地服务并验证真实运行管理停止链路

**Files:**
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: Task 1 构建出的新版 `opencode-manager`、现有 `restart-dev-services.sh`、运行管理 HTTP/UI 链路。
- Produces: backend/manager/frontend 健康的本地环境，以及一次 `stopOwned -> STOPPED` 的真实证据。

- [ ] **Step 1: 按项目 restart skill 完整重启三服务**

Run: `./restart-dev-services.sh --profile test --env-file .env.test`

Expected: backend、opencode-manager、frontend 均重新构建并启动；旧 manager 退出后，原 zombie PID 由系统回收，旧 state 按脚本既有清理语义移除。

- [ ] **Step 2: 检查服务和控制面健康**

Run: `curl -fsS http://127.0.0.1:8080/actuator/health && curl -fsS http://127.0.0.1:8080/actuator/health/readiness && curl -fsSI http://127.0.0.1:3000/`

Expected: backend health/readiness 为 `UP`，前端返回成功 HTTP 状态。

Run: `rg -n 'manager_websocket_connected|manager_registered|status=error|status=failed' .tmp/dev-services/opencode-manager.log | tail -30`

Expected: 新 manager 已连接/注册；没有与本次启动相关的持续连接失败。

- [ ] **Step 3: 通过真实运行管理链路停止受管进程**

在已登录的本地前端进入“运行管理”，确保存在一个受管 OpenCode 进程；若重启已清空进程，先通过现有用户初始化链路创建一个进程。点击该进程“停止”，等待页面刷新。

Expected: 停止请求成功，目标进程显示已停止或从 manager 活跃列表消失，不再弹出 `OPENCODE_BAD_GATEWAY`。

- [ ] **Step 4: 核对 manager、OS 和 state 三方证据**

Run: `rg -n 'command=stopOwned' .tmp/dev-services/opencode-manager.log | tail -10`

Expected: 最新命令退出行为 `status=STOPPED`，不包含 `process did not exit after force kill`。

Run: `lsof -nP -iTCP:4098 -sTCP:LISTEN`

Expected: 无监听。

Run: `test ! -e .tmp/dev-services/opencode-manager-state/processes/4098.json`

Expected: 退出码 0，manager state 已删除。

- [ ] **Step 5: 更新本机会话日志并提交**

若本次根因、修复和真实验证结论对后续开发有价值，在 `.agents/session-log.huangzhenren.md` 追加一个会话级条目，内容包含：

```markdown
### 2026-07-24 - 修复 manager Unix zombie 阻断停止确认

- Why: Unix `OSStarter` 启动后未调用 `Wait()`，退出 OpenCode 子进程成为 zombie，signal 0 误判存活并导致 `stopOwned` 返回失败。
- What: Unix 启动器异步回收直属子进程，新增真实进程回归并同步 manager README。
- How: 记录 RED/GREEN、`go test ./...`、三服务重启和真实运行管理停止的关键结果。
- Result: 记录停止回包、端口、state、API/事件/数据库/安全兼容性结论和剩余风险。
```

提交前再次回顾所有 `.agents/session-log*.md` 的近期条目，确认没有覆盖或误合并他人成果，然后执行：

```bash
git add .agents/session-log.huangzhenren.md
git commit -m "记录 manager 子进程回收验证结果"
```

- [ ] **Step 6: 最终核验提交和工作区**

Run: `git status --short && git log -3 --oneline`

Expected: 工作区无本次任务未提交文件；最近提交包含设计、实现和会话验证记录，且均为中文提交信息。

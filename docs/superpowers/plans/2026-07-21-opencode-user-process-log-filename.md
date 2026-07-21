# OpenCode 用户进程日志文件名实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让每次用户 OpenCode server 真实启动都生成 `{统一认证号}-{UTC启动时间}-{端口}.log` 独立日志文件，并保持新旧 Java、manager、state 和本地 CLI 的滚动兼容。

**Architecture:** Java 公共启动链路把可空 `unifiedAuthId` 作为 `opencode-manager.v1` command 的新增字段显式下发；Go manager 校验它与稳定 `users/{unifiedAuthId}` session 路径一致，安全编码文件名并把同一启动时间写入日志名和本地 state。旧消息或旧 state 优先从稳定 session 路径恢复身份，无法恢复时继续使用 `{port}.log`。

**Tech Stack:** Java 21、Spring/Jackson、JUnit 5/AssertJ、Go 1.23、Go `testing`、manager WebSocket JSON 协议、Markdown 稳定文档。

## Global Constraints

- 每次真实启动使用 `logs/{统一认证号}-{启动时间}-{端口}.log`；restart 必须生成新文件，旧 `{port}.log` 不迁移、不删除。
- 启动时间固定使用 UTC 纳秒格式 `20060102T150405.000000000Z`，并与 `ProcessRecord.startedAt` 完全一致。
- `unifiedAuthId` 是 `opencode-manager.v1` 的可选新增字段；不得提升协议版本或破坏旧 Java/manager/state/CLI。
- manager 不访问数据库；Java 只能通过公共 `OpencodeProcessStartupService` 启动用户进程。
- ASCII 字母、数字、下划线、短横线原样保留；其它 UTF-8 字节编码为 `%HH`；超长值使用可读前缀加完整 SHA-256，单文件名必须小于 255 字节。
- 统一认证号只能出现在受控 manager state 文件名/state 中，不得新增到日志正文、HTTP 响应、RunEvent、前端状态或 manager 生命周期日志。
- 不修改 HTTP API、SSE/RunEvent、数据库/Flyway、generated SDK、前端和 `.env.local`。
- 只修改任务直接相关文件；保留工作区已有的 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除状态，不暂存、不恢复。
- 人工维护代码新增或复杂逻辑必须有中文注释；提交信息使用中文。

---

## File Map

- Create `opencode-manager/internal/process/log_path.go`：统一认证号恢复、安全编码、长度收敛和日志路径生成。
- Create `opencode-manager/internal/process/log_path_test.go`：固定时间、特殊字符、超长输入、身份不一致和 legacy fallback 测试。
- Modify `opencode-manager/internal/process/process.go`：启动请求/spec 字段、同一启动时间、state 写入、restart 身份传递。
- Modify `opencode-manager/internal/state/store.go` and test：持久化可空 `unifiedAuthId` 并验证旧 JSON。
- Modify `opencode-manager/internal/control/protocol.go`, `supervisor.go` and test：可选控制字段透传。
- Modify Java start command、startup service、assignment service、manager message、socket gateway 及四组测试。
- Modify stable docs：manager/runtime README、后端部署、安全规范。
- Modify `.agents/session-log.huangzhenren.md`：记录最终结果与风险。

---

### Task 1: Go 日志文件名与 state 生命周期

**Files:**
- Create: `opencode-manager/internal/process/log_path.go`
- Create: `opencode-manager/internal/process/log_path_test.go`
- Modify: `opencode-manager/internal/process/process.go:42-92,203-251,303-324,360-377,430-455`
- Modify: `opencode-manager/internal/state/store.go:15-25`
- Modify: `opencode-manager/internal/state/store_test.go:8-45`

**Interfaces:**
- Consumes: `config.Config.StateDir`、`config.Config.LogPath(port int)`、`StartRequest.SessionPath`。
- Produces: `StartRequest.UnifiedAuthID string`、`StartSpec.UnifiedAuthID string`、`StartSpec.StartedAt time.Time`、`ProcessRecord.UnifiedAuthID string`、`buildStartSpec(config.Config, StartRequest, time.Time) (StartSpec, error)`。

- [ ] **Step 1: 写失败测试**

在 `log_path_test.go` 添加：

```go
func TestBuildStartSpecUsesUnifiedAuthIDTimestampAndPortForLogPath(t *testing.T) {
	cfg := testConfig(t)
	startedAt := time.Date(2026, 7, 21, 8, 15, 30, 123456789, time.UTC)
	spec, err := buildStartSpec(cfg, StartRequest{
		Port: 4096, UnifiedAuthID: "DEV_888888888",
		SessionPath: "/tmp/sessions/users/DEV_888888888",
		TraceID: "trace_1234567890abcdef",
	}, startedAt)
	if err != nil {
		t.Fatalf("buildStartSpec returned error: %v", err)
	}
	want := filepath.Join(cfg.StateDir, "logs",
		"DEV_888888888-20260721T081530.123456789Z-4096.log")
	if spec.LogPath != want || !spec.StartedAt.Equal(startedAt) ||
		spec.UnifiedAuthID != "DEV_888888888" {
		t.Fatalf("unexpected start spec: %#v", spec)
	}
}

func TestBuildStartSpecRejectsMismatchedUnifiedAuthID(t *testing.T) {
	_, err := buildStartSpec(testConfig(t), StartRequest{
		Port: 4096, UnifiedAuthID: "U001",
		SessionPath: "/tmp/sessions/users/U002",
		TraceID: "trace_1234567890abcdef",
	}, time.Date(2026, 7, 21, 8, 15, 30, 0, time.UTC))
	if err == nil || !strings.Contains(err.Error(), "does not match session path") {
		t.Fatalf("expected identity mismatch, got %v", err)
	}
}
```

继续覆盖：

- 空显式字段从 `/users/{id}` 恢复。
- 普通 CLI session 路径仍使用 `cfg.LogPath(port)`。
- Unicode、路径分隔符、CRLF 使用 `%HH`；超长输入 basename 不超过 255 字节并含 `-sha256-`。
- restart 后 state `startedAt` 能精确重建当前日志 basename。
- state JSON 保存新字段，旧 JSON 缺字段仍可读取。

特殊字符、长度与旧 state 使用以下断言，不用只检查“没有报错”：

```go
func TestSafeLogIdentityEncodesUnsafeBytesAndBoundsLongValues(t *testing.T) {
	encoded := safeLogIdentity("用户/..\\\r\n")
	if encoded != "%E7%94%A8%E6%88%B7%2F%2E%2E%5C%0D%0A" {
		t.Fatalf("unexpected encoded identity %q", encoded)
	}
	first := safeLogIdentity(strings.Repeat("A", 255))
	second := safeLogIdentity(strings.Repeat("A", 254) + "B")
	if first == second || !strings.Contains(first, "-sha256-") || len(first) > 160 {
		t.Fatalf("long identities were not safely bounded: %q / %q", first, second)
	}
}

func TestFileStoreReadsLegacyRecordWithoutUnifiedAuthID(t *testing.T) {
	store := NewFileStore(t.TempDir())
	legacy := ProcessRecord{Port: 4096, PID: 12345, StartedAt: time.Now().UTC(), TraceID: "trace_old"}
	if err := store.Save(legacy); err != nil {
		t.Fatalf("save legacy record: %v", err)
	}
	loaded, ok, err := store.Get(4096)
	if err != nil || !ok || loaded.UnifiedAuthID != "" {
		t.Fatalf("legacy record compatibility failed: %#v ok=%t err=%v", loaded, ok, err)
	}
}
```

- [ ] **Step 2: 运行测试确认先失败**

```bash
cd opencode-manager
go test ./internal/process ./internal/state
```

Expected: FAIL，出现 `undefined: buildStartSpec` 或 `StartRequest has no field UnifiedAuthID`。

- [ ] **Step 3: 实现安全文件名 helper**

在 `log_path.go` 实现：

```go
const (
	logTimestampLayout        = "20060102T150405.000000000Z"
	maxEncodedLogIdentity     = 160
	readableLogIdentityPrefix = 80
)

func resolveUnifiedAuthID(explicit, sessionPath string) (string, error) {
	explicit = strings.TrimSpace(explicit)
	derived := unifiedAuthIDFromSessionPath(sessionPath)
	if explicit != "" && derived != "" && explicit != derived {
		return "", fmt.Errorf("unifiedAuthId does not match session path")
	}
	if explicit != "" {
		return explicit, nil
	}
	return derived, nil
}

func unifiedAuthIDFromSessionPath(sessionPath string) string {
	cleaned := filepath.Clean(strings.TrimSpace(sessionPath))
	if cleaned == "." || filepath.Base(filepath.Dir(cleaned)) != "users" {
		return ""
	}
	identity := strings.TrimSpace(filepath.Base(cleaned))
	if identity == "" || identity == "." || identity == ".." {
		return ""
	}
	return identity
}

func safeLogIdentity(identity string) string {
	parts := make([]string, 0, len(identity))
	for _, value := range []byte(identity) {
		if isSafeLogIdentityByte(value) {
			parts = append(parts, string([]byte{value}))
		} else {
			parts = append(parts, fmt.Sprintf("%%%02X", value))
		}
	}
	encoded := strings.Join(parts, "")
	if len(encoded) <= maxEncodedLogIdentity {
		return encoded
	}
	var prefix strings.Builder
	for _, part := range parts {
		if prefix.Len()+len(part) > readableLogIdentityPrefix {
			break
		}
		prefix.WriteString(part)
	}
	digest := sha256.Sum256([]byte(identity))
	return prefix.String() + "-sha256-" + hex.EncodeToString(digest[:])
}

func isSafeLogIdentityByte(value byte) bool {
	return value >= 'a' && value <= 'z' ||
		value >= 'A' && value <= 'Z' ||
		value >= '0' && value <= '9' ||
		value == '_' || value == '-'
}
```

添加中文注释解释 `%HH`、长度上限、SHA-256 与 legacy fallback；错误消息不得含原始认证号。

- [ ] **Step 4: 串起 BuildStartSpec、state 和 restart**

保留公开兼容入口：

```go
func BuildStartSpec(cfg config.Config, request StartRequest) (StartSpec, error) {
	return buildStartSpec(cfg, request, time.Now().UTC())
}
```

`buildStartSpec` 复用既有命令、session、config 和 env 构造，并加入：

```go
startedAt = startedAt.UTC()
if startedAt.IsZero() {
	return StartSpec{}, fmt.Errorf("startedAt is required")
}
unifiedAuthID, err := resolveUnifiedAuthID(request.UnifiedAuthID, sessionPath)
if err != nil {
	return StartSpec{}, err
}
logPath := cfg.LogPath(request.Port)
if unifiedAuthID != "" {
	fileName := fmt.Sprintf("%s-%s-%d.log",
		safeLogIdentity(unifiedAuthID),
		startedAt.Format(logTimestampLayout),
		request.Port)
	logPath = filepath.Join(cfg.StateDir, "logs", fileName)
}
```

在 `StartRequest` 的 `Port` 后插入：

```go
UnifiedAuthID string
```

在 `StartSpec` 的 `LogPath` 后插入：

```go
UnifiedAuthID string
StartedAt time.Time
```

在 `ProcessRecord` 的 `BaseURL` 后插入：

```go
UnifiedAuthID string `json:"unifiedAuthId,omitempty"`
```

`StartSpec` 增加 `UnifiedAuthID`、`StartedAt`；`Manager.Start` 调用 `buildStartSpec(..., time.Now().UTC())`，state 使用 `spec.StartedAt`，不能第二次取时钟。`Restart` 和 `withDerivedStartCommands` 传递 record 的 `UnifiedAuthID`；旧 state 空字段由 session 路径恢复。

- [ ] **Step 5: 运行 Go 测试**

```bash
cd opencode-manager
go test ./internal/process ./internal/state
go test ./...
```

Expected: 两条命令 PASS。

- [ ] **Step 6: 提交**

```bash
git add opencode-manager/internal/process/log_path.go   opencode-manager/internal/process/log_path_test.go   opencode-manager/internal/process/process.go   opencode-manager/internal/process/process_test.go   opencode-manager/internal/state/store.go   opencode-manager/internal/state/store_test.go
git commit -m "按用户和启动时间生成进程日志文件"
```

---

### Task 2: Go manager 控制协议透传统一认证号

**Files:**
- Modify: `opencode-manager/internal/control/protocol.go:45-86`
- Modify: `opencode-manager/internal/control/supervisor.go:251-260`
- Modify: `opencode-manager/internal/control/supervisor_test.go:45-91`

**Interfaces:**
- Consumes: Task 1 的 `process.StartRequest.UnifiedAuthID`。
- Produces: `control.Message.UnifiedAuthID string`，JSON 名为 `unifiedAuthId,omitempty`。

- [ ] **Step 1: 写失败测试**

扩展现有 start dispatch：

```go
result, err := supervisor.dispatchProcessCommand(context.Background(), Message{
	Command: "start", Port: 4096,
	UnifiedAuthID: "DEV_888888888",
	SessionPath: "/tmp/opencode-session/users/DEV_888888888",
	TraceID: "trace_1234567890abcdef",
}, time.Second)
if err != nil {
	t.Fatalf("dispatchProcessCommand returned error: %v", err)
}
if starter.specs[0].UnifiedAuthID != "DEV_888888888" {
	t.Fatalf("expected identity to reach start spec: %#v", starter.specs[0])
}
```

- [ ] **Step 2: 确认测试失败**

```bash
cd opencode-manager
go test ./internal/control
```

Expected: FAIL，`Message has no field UnifiedAuthID`。

- [ ] **Step 3: 添加并透传可选字段**

`Message` 在 session/config 字段附近添加：

```go
UnifiedAuthID string `json:"unifiedAuthId,omitempty"`
```

start 分发改为：

```go
return s.manager.Start(ctx, process.StartRequest{
	Port: message.Port,
	UnifiedAuthID: message.UnifiedAuthID,
	SessionPath: message.SessionPath,
	ConfigPath: message.ConfigPath,
	Environment: message.Environment,
	TraceID: message.TraceID,
})
```

不得把该字段加入 `manager_command_entry/exit` 日志。

- [ ] **Step 4: 测试并提交**

```bash
cd opencode-manager
go test ./...
cd ..
git add opencode-manager/internal/control/protocol.go   opencode-manager/internal/control/supervisor.go   opencode-manager/internal/control/supervisor_test.go
git commit -m "扩展管理进程启动身份协议"
```

Expected: Go 全量 PASS，提交只含三个 control 文件。

---

### Task 3: Java 公共启动链路下发统一认证号

**Files:**
- Modify `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/OpencodeProcessStartCommand.java`
- Modify `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/OpencodeProcessStartupService.java`
- Modify `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/UserOpencodeProcessAssignmentService.java`
- Modify `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/socket/ManagerControlMessage.java`
- Modify `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/socket/SocketOpencodeProcessManagerGateway.java`
- Test `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/OpencodeProcessStartupServiceTest.java`
- Test `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/UserOpencodeProcessAssignmentServiceTest.java`
- Test `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/socket/ManagerControlMessageCodecTest.java`
- Test `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/socket/SocketOpencodeProcessManagerGatewayTest.java`

**Interfaces:**
- Consumes: Task 2 的 wire 字段。
- Produces: `OpencodeProcessStartCommand.unifiedAuthId()`、`ManagerControlMessage.unifiedAuthId()`；旧 command 工厂保持源码兼容。

- [ ] **Step 1: 写 Java 失败测试**

公共启动与用户初始化分别断言：

```java
assertThat(gateway.startCommands).singleElement().satisfies(command ->
        assertThat(command.unifiedAuthId()).isEqualTo("ucid_001"));
```

把 `OpencodeProcessStartupServiceTest` 的 session fixture 改为：

```java
"/data/opencode/session/users/ucid_001"
```

codec 使用新增重载：

```java
ManagerControlMessage command = ManagerControlMessage.command(
        "cmd_1234567890abcdef", "start", 4096,
        "/data/opencode/session/users/ucid_001",
        "/data/opencode/session/users/ucid_001/.testagent-runtime/current-public-config",
        "ucid_001", Map.of(), 10_000, "trace_1234567890abcdef");
String payload = codec.encode(command);
assertThat(new ObjectMapper().readTree(payload).path("unifiedAuthId").asText())
        .isEqualTo("ucid_001");
assertThat(codec.decode(payload).unifiedAuthId()).isEqualTo("ucid_001");
```

Socket gateway 捕获帧并断言同值；五个直接 `new OpencodeProcessStartCommand(...)` 调用补入明确认证号。

- [ ] **Step 2: 确认测试失败**

```bash
cd backend
mvn -pl test-agent-opencode-runtime -am   -Dtest='OpencodeProcessStartupServiceTest,UserOpencodeProcessAssignmentServiceTest,ManagerControlMessageCodecTest,SocketOpencodeProcessManagerGatewayTest'   -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，出现 `cannot find symbol: method unifiedAuthId()` 或 command 重载不存在。

- [ ] **Step 3: 扩展启动命令并解析身份**

`OpencodeProcessStartCommand` 在 `UserId userId` 后增加 `String unifiedAuthId`，canonical constructor 使用：

```java
unifiedAuthId = unifiedAuthId == null || unifiedAuthId.isBlank()
        ? null
        : unifiedAuthId.trim();
```

`UserOpencodeProcessAssignmentService.startCommand` 传 `userUnifiedAuthPathSegment(userId)`。

`OpencodeProcessStartupService` 新增中文 Javadoc helper：优先 `UserRepository`；查不到时仅从 parent basename 为 `users` 的规范化 session 路径恢复，不能回退到平台 `userId`：

```java
private String logUnifiedAuthId(OpencodeProcessStartupRequest request) {
    if (userRepository != null) {
        String value = userRepository.findByUserId(request.userId())
                .map(User::unifiedAuthId)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .orElse(null);
        if (value != null) {
            return value;
        }
    }
    Path sessionPath = Path.of(request.sessionPath()).normalize();
    Path parent = sessionPath.getParent();
    return parent != null
                    && parent.getFileName() != null
                    && "users".equals(parent.getFileName().toString())
                    && sessionPath.getFileName() != null
            ? sessionPath.getFileName().toString().trim()
            : null;
}
```

构造最终 command 时把 `logUnifiedAuthId(request)` 放在 `userId` 后；不要改变既有 `ENTERPRISE_UCID` 环境兼容 helper。

- [ ] **Step 4: 扩展 ManagerControlMessage**

record 在 `configPath` 后加入 `String unifiedAuthId`。`withBuildVersion` 原样传递；非 start factory 在同位置传 `null`。

保留旧 command 重载并委托新重载：

```java
public static ManagerControlMessage command(
        String commandId, String command, int port,
        String sessionPath, String configPath, String unifiedAuthId,
        Map<String, String> environment, long timeoutMillis, String traceId) {
    return new ManagerControlMessage(
            ManagerControlProtocol.TYPE_COMMAND,
            ManagerControlProtocol.VERSION,
            traceId,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null,
            List.of(), Map.of(), null,
            commandId, command, port, timeoutMillis,
            null, null, null, sessionPath, configPath, unifiedAuthId,
            null, null, null, null, null, null, null, null, environment, null);
}
```

现有带 session/config 但不带身份的重载委托上述方法并传 `null`。

- [ ] **Step 5: Socket gateway 下发字段**

```java
ManagerControlMessage result = send(command.containerId(), ManagerControlMessage.command(
        RuntimeIdGenerator.managerCommandId(),
        "start",
        command.port(),
        command.sessionPath(),
        command.configPath(),
        command.unifiedAuthId(),
        command.environment(),
        settings.commandTimeout().toMillis(),
        command.traceId()));
```

不得把认证号加入 Java 日志或错误响应。

- [ ] **Step 6: 运行定向与模块测试**

```bash
cd backend
mvn -pl test-agent-opencode-runtime -am   -Dtest='OpencodeProcessStartupServiceTest,UserOpencodeProcessAssignmentServiceTest,ManagerControlMessageCodecTest,SocketOpencodeProcessManagerGatewayTest'   -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl test-agent-opencode-runtime -am test
```

Expected: 定向测试 PASS；模块 reactor PASS。若第二条被任务外失败阻断，记录首个失败测试和错误。

- [ ] **Step 7: 提交**

```bash
git add backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/OpencodeProcessStartCommand.java   backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/OpencodeProcessStartupService.java   backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/UserOpencodeProcessAssignmentService.java   backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/socket/ManagerControlMessage.java   backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/socket/SocketOpencodeProcessManagerGateway.java   backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/OpencodeProcessStartupServiceTest.java   backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/UserOpencodeProcessAssignmentServiceTest.java   backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/socket/ManagerControlMessageCodecTest.java   backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/process/socket/SocketOpencodeProcessManagerGatewayTest.java
git commit -m "下发用户进程日志统一认证号"
```

---

### Task 4: 稳定文档、安全说明与最终验证

**Files:**
- Modify `opencode-manager/README.md:20-130`
- Modify `backend/test-agent-opencode-runtime/README.md`
- Modify `docs/deployment/backend.md:149-171,249-258`
- Modify `docs/standards/security.md:83-94`
- Modify `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: Tasks 1-3 的最终行为和真实验证结果。
- Produces: 运维路径说明、安全边界和本会话交接记录。

- [ ] **Step 1: 更新稳定文档**

统一写法：

```text
{OPENCODE_MANAGER_STATE_DIR}/logs/{safeUnifiedAuthId}-{yyyyMMddTHHmmss.nnnnnnnnnZ}-{port}.log
```

企业纯 Docker 示例：

```text
/data/testagent/data/agent-opencode/manager/worker/logs/DEV_888888888-20260721T081530.123456789Z-4096.log
```

明确旧 `{port}.log` 不迁移；`ls -1t <stateDir>/logs/<统一认证号>-*.log` 查最近启动；特殊字符编码和超长 hash；manager 自身日志路径不变；对外传输文件名也需脱敏；无 HTTP API、RunEvent、数据库和环境变量变化。

- [ ] **Step 2: 运行最终校验**

```bash
tools/verify-ai-docs.sh
cd opencode-manager
go test ./...
cd ../backend
mvn -pl test-agent-opencode-runtime -am   -Dtest='OpencodeProcessStartupServiceTest,UserOpencodeProcessAssignmentServiceTest,ManagerControlMessageCodecTest,SocketOpencodeProcessManagerGatewayTest'   -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl test-agent-opencode-runtime -am test
cd ..
git diff --check
rg -n '<<<<<<<|=======|>>>>>>>' opencode-manager   backend/test-agent-opencode-runtime docs/deployment/backend.md docs/standards/security.md
```

Expected: docs、Go 全量、Java 定向和格式校验通过；Java 模块全量通过或记录明确任务外阻断；冲突搜索无输出。

- [ ] **Step 3: 更新 session log**

在 `.agents/session-log.huangzhenren.md` 追加一条 `2026-07-21 - 按用户和启动实例拆分 OpenCode 进程日志`，按 `Why / What / How / Result` 记录：

- 端口变化/复用导致日志难定位和混写。
- 新文件名、协议/state/restart 兼容、特殊字符/长度保护。
- 实际执行的 Go/Java/docs 命令和结果。
- 旧日志不迁移；真实企业 worker 仍需部署后观察。
- 无 API、事件、数据库、前端、generated SDK 和环境配置影响。

- [ ] **Step 4: 回顾 session logs、暂存并提交**

```bash
git status --short
git add opencode-manager/README.md   backend/test-agent-opencode-runtime/README.md   docs/deployment/backend.md docs/standards/security.md   .agents/session-log.huangzhenren.md
git diff --cached --name-only
git commit -m "同步用户进程日志运维文档"
```

Expected: 暂存区仅五个文件；任务外 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除始终不暂存。提交后 `git status --short` 只保留该任务外删除。

---

## Final Acceptance

- [ ] 新 Java + 新 manager 的 `StartSpec.LogPath` 精确符合 `{safeUnifiedAuthId}-{UTC纳秒时间}-{port}.log`。
- [ ] restart 生成新文件，state `startedAt` 与文件名一致；旧 state/旧 Java/CLI 安全 fallback。
- [ ] stdout/stderr 仍共同写入单次启动文件，manager 自身日志路径不变。
- [ ] Go 全量和 Java 定向测试通过，Java 模块全量结果有证据。
- [ ] 稳定文档和安全说明同步，无 HTTP API、RunEvent、数据库、前端、generated SDK 或环境配置变化。
- [ ] 所有本任务提交使用中文信息，最终工作区只保留进入任务前已有的无关删除。

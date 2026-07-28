# 核心领域模型

本文档描述平台核心领域模型的概念、关系和设计要点。

## 应用源码快照

应用代码库的源码物化使用独立 `domain.appsource` 模型，不复用应用版本工作区或引用资产副本：

- `AppSourceRepositorySlot` 每个 repositoryId 一行，通过 active/pending generation、`nextGeneration` 和 `lockVersion` 串行化代次分配。
- `AppSourceSnapshot` 以 repositoryId+generation 标识，冻结仓库英文名、`PERSONAL/TEAM`、owner、分支、目标提交和 `List<AppSourceSelectedPath>`；接受后只允许状态与 64 位十六进制索引摘要推进，选择内容不修改。
- `AppSourceReplica` 以 repositoryId+generation+linuxServerId 标识，初始化只在记录不存在时插入，后续变更必须通过 generation、lease owner、绝对 lease deadline 和合法状态流转共同隔离过期 worker。
- 副本 claim 原子绑定精确 operationId、非终态 operation 和同服务器可领取步骤；过期 `RUNNING` 副本可作为旧 attempt 接管锚点，随后统一重置时间线，不需要先写步骤。
- `AppSourceOperation` 记录用户意图与 target generation，全局/服务器 `AppSourceOperationStep` 分开投影进度且终态不可被迟到执行者回退；cleanup 以每服务器绝对 `deleteAt` 独立认领，失败可退避，旧任务可被新代次 supersede。
- `AppSourceRecentSelection` 每用户唯一且不保存 workspaceId，避免用户进程换服务器后继续指向旧物理副本。

物化保留时长只允许 1–72 整小时，默认 48 小时；领域对象与 PostgreSQL 约束均强制 `expiresAt = acceptedAt + integerHours`。数据库路径保存 `appsource:` 逻辑值，`ManagedWorkspacePathResolver` 通过只读 `OPENCODE_APP_SOURCE_ROOT` 解析当前服务器物理根，同时保留旧绝对/相对路径兼容。

---

## Session 与 Run

### 概念对比

| 维度 | Session（会话） | Run（运行） |
|------|----------------|-------------|
| **定义** | 一个对话容器，承载多轮交互上下文 | 一次具体的执行任务 |
| **类比** | 类似一个「聊天窗口」或「项目会话」 | 类似一次「对话提交」或「任务执行」 |
| **生命周期** | 长期存在，可跨越多次 Run | 短期，从启动到结束（成功/失败/取消） |
| **状态** | `ACTIVE` / `ARCHIVED` | `PENDING` → `RUNNING` → `SUCCEEDED`/`FAILED`/`CANCELLED` |
| **关系** | 一个 Session 可包含多个 Run | 一个 Run 属于一个 Session |

### 数据模型关系

```text
┌─────────────────────────────────────────────────────────────┐
│                       Workspace                              │
│                      (工作空间)                               │
└──────────────────────────┬──────────────────────────────────┘
                           │ 1:N
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                        Session                               │
│   - sessionId, title, status, pinned                        │
│   - opencodeSessionId (远端会话映射)                          │
│   - 生命周期：长期，手动归档                                   │
└──────────────────────────┬──────────────────────────────────┘
                           │ 1:N
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│     Run #1      │ │     Run #2      │ │     Run #3      │
│  (第一次提问)    │ │  (第二次提问)    │ │  (第三次提问)    │
│  SUCCEEDED      │ │  FAILED         │ │  RUNNING        │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 关系：一个 Session 对应 N 个 Run（1:N）

```text
Session 1 : Run N

一个 Session 可以有多次 Run：
- 用户第1次提问 → 创建 Run #1 → 执行完成
- 用户第2次提问 → 创建 Run #2 → 执行完成
- 用户第3次提问 → 创建 Run #3 → 正在执行...
```

### 代码模型

#### Run 关联 Session

```java
public record Run(
        RunId runId,
        SessionId sessionId,  // Run 关联到 Session
        WorkspaceId workspaceId,
        RunStatus status,
        ...
) { }
```

#### 启动 Run 时关联 Session

```java
public Run startRun(String agentId, StartRunInput input, String traceId) {
    SessionId sessionId = input.sessionId();  // Run 必须属于某个 Session
    Session session = findSession(sessionId); // 先找到 Session

    // 创建新的 Run
    Run pending = new Run(
            new RunId(...),
            session.sessionId(),  // Run 关联到 Session
            workspace.workspaceId(),
            RunStatus.PENDING,
            ...);
    runRepository.save(pending);

    // 保存用户消息到 Session
    saveUserMessage(session.sessionId(), prompt, traceId, now);

    // 订阅 agent 事件流，直到 Run 结束
    subscribeAgentEvents(runtime, running, ...);
}
```

### 典型使用场景

```text
用户进入工作空间，创建 Session：
  POST /api/internal/platform/workspace-management/workspaces/{id}/sessions
  → Session(id=ses_001, title="修复登录Bug", status=ACTIVE)

用户第1次提问：
  POST /api/internal/agent/opencode/sessions/ses_001/runs
  → Run(id=run_001, sessionId=ses_001, status=RUNNING → SUCCEEDED)

用户第2次追问：
  POST /api/internal/agent/opencode/sessions/ses_001/runs
  → Run(id=run_002, sessionId=ses_001, status=RUNNING)

用户归档会话：
  DELETE /api/internal/platform/workspace-management/sessions/ses_001
  → Session(id=ses_001, status=ARCHIVED)
```

### 关键设计点

| 设计点 | 说明 |
|--------|------|
| **Session 会话粘滞** | 同一个 Session 的多次 Run 会粘滞到同一个 opencode 执行节点，保证上下文连贯 |
| **远端会话映射** | Session 首次启动 Run 时才创建远端 opencode session，后续 Run 复用 |
| **消息历史** | `SessionMessage` 表保存用户消息历史，assistant 消息通过 `RunEvent` 恢复 |
| **Run 状态机** | Run 有完整状态流转：`PENDING → RUNNING → SUCCEEDED/FAILED/CANCELLED` |
| **Session 归档** | Session 归档后查询接口按不存在处理，但数据保留供审计 |

### 状态机

#### Session 状态

```text
ACTIVE ──(archive)──→ ARCHIVED
```

- `ACTIVE`：活跃状态，可以创建 Run
- `ARCHIVED`：归档状态，查询接口按不存在处理

#### Run 状态

```text
                  ┌───────────────┐
                  │   PENDING     │
                  └───────┬───────┘
                          │
           ┌──────────────┼──────────────┐
           │              │              │
           ▼              ▼              ▼
    ┌───────────┐  ┌───────────┐  ┌───────────┐
    │ CANCELLED │  │  RUNNING  │  │  FAILED   │
    └───────────┘  └─────┬─────┘  └───────────┘
                          │
           ┌──────────────┼──────────────┐
           │              │              │
           ▼              ▼              ▼
    ┌───────────┐  ┌───────────┐  ┌───────────┐
    │ CANCELLED │  │ SUCCEEDED │  │  FAILED   │
    └───────────┘  └───────────┘  └───────────┘
           ▲
           │
    ┌───────────┐
    │ CANCELLING│
    └───────────┘
```

状态流转规则：

| 当前状态 | 允许的下一状态 |
|----------|----------------|
| `PENDING` | `RUNNING`, `CANCELLED`, `FAILED` |
| `RUNNING` | `CANCELLING`, `SUCCEEDED`, `FAILED` |
| `CANCELLING` | `CANCELLED`, `FAILED` |
| `SUCCEEDED` | 终态，不允许流转 |
| `FAILED` | 终态，不允许流转 |
| `CANCELLED` | 终态，不允许流转 |

### 总结

- **Session** = 对话容器，长期存在，一个 Session 包含**多次** Run
- **Run** = 一次执行任务，短期生命周期，一个 Run 属于**一个** Session
- **关系** = 1 : N（一个 Session 对应多个 Run）

---

## 两个 Runtime 模块

### test-agent-agent-runtime（Agent 运行时抽象层）

**定位**：基础设施层，定义多 agent 的统一接口、注册表和观测包装。

```
test-agent-agent-runtime
├── AgentRuntime              ← 多 agent 统一接口（契约）
├── AgentRuntimeRegistry      ← agent 注册表，按 agentId 查找实现
├── OpencodeAgentRuntime      ← opencode 的具体适配实现
├── OtherAgentRuntime         ← 未注册 agent 的占位实现
├── ObservedAgentRuntime      ← 统一日志/指标包装（装饰器）
└── 各种 Command/Result       ← 平台稳定模型（如 AgentStartRunCommand）
```

核心是 **`AgentRuntime` 接口**，定义了 agent 能做的 8 种操作：

```java
public interface AgentRuntime {
    String agentId();                                          // 返回 agent 标识
    Mono<AgentCreateSessionResult> createSession(...);         // 创建远端会话
    Mono<AgentStartRunResult> startRun(...);                   // 启动一次运行
    Mono<AgentCancelResult> cancelSession(...);                // 取消运行
    Flux<RunEventDraft> streamRunEvents(...);                  // 订阅事件流
    Mono<AgentDiffResult> getDiff(...);                        // 获取 Diff
    Mono<AgentRejectDiffResult> rejectDiff(...);               // 拒绝 Diff
    Mono<AgentRuntimeResult> runtime(...);                     // 受控代理请求
    Mono<AgentSessionMessagesResult> sessionMessages(...);     // 读取会话消息
}
```

**它不处理业务逻辑**，只做三件事：

1. **定义契约**：平台与任意 agent（opencode 或其他）之间的统一接口
2. **管理注册**：`AgentRuntimeRegistry` 集中维护所有 agent 实现，按 `agentId` 查找
3. **统一观测**：`ObservedAgentRuntime` 包装真实实现，自动打日志和指标

### test-agent-opencode-runtime（Opencode 运行态业务编排层）

**定位**：业务编排层，编排 Session、Run、RunEvent、Terminal 等完整业务流程。

```
test-agent-opencode-runtime
├── run/
│   ├── RunApplicationService              ← Run 编排核心（启动/取消）
│   ├── RunDiffApplicationService          ← Diff 编排
│   ├── RunEventPersistencePolicy          ← 事件持久化策略
│   ├── RunMessageRecoveryService          ← 消息恢复
│   └── StartRunInput                      ← 启动参数
├── session/
│   └── SessionApplicationService          ← Session 编排（创建/归档）
├── terminal/
│   ├── TerminalApplicationService         ← PTY 终端编排
│   ├── TerminalProcessFactory             ← 进程工厂
│   ├── TerminalTicketStore                ← ticket 管理
│   ├── TerminalInputRateLimiter           ← 输入限流
│   ├── TerminalOutputLimiter              ← 输出限流
│   └── ...                                ← 编解码、审计、活跃注册
└── runtime/
    └── OpencodeRuntimeApplicationService  ← opencode 代理 API 编排
```

**它不定义接口，而是使用 `AgentRuntime` 来编排业务**。例如 `RunApplicationService.startRun()` 的流程：

```
RunApplicationService.startRun()
  1. 查询 Session / Workspace
  2. 创建 Run 记录（PENDING）
  3. 调用 agentRuntimeRegistry.require(agentId) 找到 AgentRuntime
  4. 路由到目标执行节点，保存 routing decision
  5. 调用 runtime.createSession() 创建/复用远端会话
  6. 调用 runtime.startRun() 启动远端执行
  7. 调用 runtime.streamRunEvents() 订阅事件流
  8. 按事件类型分发：终态更新 Run / 持久化 / 实时推送 SSE
```

### 对比总结

| 维度 | test-agent-agent-runtime | test-agent-opencode-runtime |
|------|--------------------------|-----------------------------|
| **定位** | 基础设施层（接口/注册表） | 业务编排层（流程/状态） |
| **核心** | `AgentRuntime` 接口 + `Registry` | `RunApplicationService` + `SessionApplicationService` |
| **关注** | "agent 能做什么" | "业务需要 agent 做什么" |
| **依赖** | 依赖 `test-agent-opencode-client`（调用 SDK） | 依赖 `test-agent-agent-runtime`（使用接口） |
| **扩展方式** | 新增 `AgentRuntime` 实现 | 新增 ApplicationService |
| **是否编排业务** | 否 | 是 |
| **是否定义接口** | 是 | 否 |

**依赖方向**：

```text
test-agent-opencode-runtime  →  test-agent-agent-runtime  →  test-agent-opencode-client  →  generated SDK
     (编排业务)                      (接口/注册)                   (门面封装)                    (生成代码)
```

---

## Persistence 与 Event 的关系

### 两个模块的职责

| 模块 | 职责 |
|------|------|
| **test-agent-persistence** | 数据库 Repository 实现、Flyway 迁移、TokenStore（含 Redis/InMemory 实现） |
| **test-agent-event** | RunEvent 追加、SSE 流服务、事件回放（replay）、实时事件总线（live bus） |

### 通过 Domain 层接口解耦

两个模块不直接依赖对方，都依赖 `test-agent-domain` 中的 **`RunEventRepository` 接口**：

```text
┌──────────────────────────────────────────────────────────────────────────┐
│  test-agent-domain                                                       │
│    RunEventRepository (接口)   ← 定义 append() 和 findByRunIdAfter()      │
│    RunEvent / RunEventDraft   (领域模型)                                 │
└────────────────────────┬───────────────────────────────┬─────────────────┘
                         │ 实现                           │ 使用
                         ▼                               ▼
┌──────────────────────────────────┐  ┌──────────────────────────────────┐
│  test-agent-persistence          │  │  test-agent-event                │
│  JdbcRunEventRepository          │  │  RunEventAppender                │
│    ↳ 实现 append()               │  │    ↳ 调用 Repository 追加事件    │
│    ↳ 实现 findByRunIdAfter()     │  │  RunEventSseStreamService        │
│    ↳ SQL insert / select         │  │    ↳ durable replay → Repository │
│    ↳ 分配 evt_id + seq           │  │    ↳ live bus → 进程内实时推送    │
│  JdbcXxxRepository (其他实体)     │  │  RunEventReplayService           │
│  Flyway migration 脚本           │  │  RunEventLiveBus                 │
└──────────────────────────────────┘  └──────────────────────────────────┘
```

`RunEventRepository` 接口定义极其精简：

```java
public interface RunEventRepository {
    RunEvent append(RunEventDraft draft);                    // 追加事件，分配 eventId/seq
    List<RunEvent> findByRunIdAfter(RunId runId, long lastSeq, int limit);  // 增量回放
}
```

### 完整数据流

```
RunApplicationService (opencode-runtime)
    │
    │  eventAppender.append(draft)
    ▼
RunEventAppender (event 模块)
    │
    │  runEventRepository.append(draft)    ← 调用 domain 接口
    ▼
JdbcRunEventRepository (persistence 模块)
    │
    │  ① 写入 run_events 表 (event_id, seq, type, payload_json...)
    │  ② 返回 RunEvent (已分配 eventId + seq)
    ▼
RunEventAppender
    │
    │  liveBus.publishDurable(event)       ← 进程内广播给所有 SSE 订阅者
    ▼
RunEventLiveBus (event 模块)
    │
    ├──→ 推送当前进程内所有 SSE 长连接 → 前端实时收到事件
    │
    └──→ (断线重连时)
          RunEventSseStreamService
            │  replayService.replayAfter(runId, lastSeq, limit)
            ▼
          RunEventReplayService
            │  runEventRepository.findByRunIdAfter()  ← 再次调用 persistence
            ▼
          JdbcRunEventRepository: SELECT ... FROM run_events WHERE seq > :lastSeq
```

### 两阶段事件流

```
Agent 事件流 (opencode)
  │
  │ streamRunEvents()
  ▼
RunApplicationService (编排层)
  │
  ├── 终态事件 (RUN_SUCCEEDED / RUN_FAILED)
  │     → runRepository.save(更新 Run 状态)
  │     → eventAppender.append(持久化 + 实时推送)
  │
  ├── 瞬态事件 (delta / part 更新)
  │     → RunEventPersistencePolicy 判断：
  │         ├── 不持久化 → liveBus.publishTransient(仅实时通道)
  │         └── 可持久化 → eventAppender.append(持久化 + 实时推送)
  │
  └── 工具事件 (tool start/finish)
        → RunEventPersistencePolicy.sanitizeForPersistence()
        → 只保留摘要字段（tool、status、messageId），丢弃大段输入输出
        → eventAppender.append(清洗后的持久化事件)
```

### RunEventPersistencePolicy 策略

```java
public class RunEventPersistencePolicy {
    // 这些类型只走实时通道，不落库
    private static final Set<RunEventType> TRANSIENT_ONLY_TYPES = Set.of(
            RunEventType.ASSISTANT_MESSAGE_DELTA,
            RunEventType.MESSAGE_UPDATED,
            RunEventType.MESSAGE_REMOVED,
            RunEventType.MESSAGE_PART_UPDATED,
            RunEventType.MESSAGE_PART_REMOVED,
            RunEventType.MESSAGE_PART_DELTA
    );

    // 工具事件只保留这些轻量字段
    private static final Set<String> TOOL_SUMMARY_KEYS = Set.of(
            "tool", "callID", "callId", "messageID", "messageId",
            "partID", "partId", "sessionID", "sessionId",
            "status", "title", "error", "summary", "rawType", "rawEventId"
    );
}
```

| 事件类型 | 持久化 | 说明 |
|----------|--------|------|
| `ASSISTANT_MESSAGE_DELTA` | ❌ 瞬态 | 流式打字内容，无需审计 |
| `MESSAGE_PART_UPDATED` | ❌ 瞬态 | 实时 part 更新 |
| `TOOL_STARTED` / `TOOL_FINISHED` | ✅ 清洗后落库 | 只保留摘要字段，去掉大段输入输出 |
| `RUN_CREATED` / `RUN_STARTED` | ✅ 落库 | 运行状态变更必须审计 |
| `DIFF_PROPOSED` | ✅ 落库 | Diff 变化需要回放 |

### 关键设计

| 设计点 | 说明 |
|--------|------|
| **接口依赖** | Event 模块调用 `RunEventRepository` 接口，不直接依赖 Persistence 模块 |
| **持久化实现** | Persistence 模块的 `JdbcRunEventRepository` 实现该接口，处理 `run_events` 表 |
| **双通道推送** | 事件先落库，再通过 `RunEventLiveBus` 进程内广播给 SSE 连接 |
| **断线续传** | SSE 断线后通过 `RunEventReplayService` 从 `lastSeq` 增量回放 |
| **去重设计** | durable 事件同时走 live bus 和轮询回放两条路径，前端按 `eventId` 幂等去重 |
| **事件清洗** | 工具事件落库前剥离 raw payload，只保留可审计摘要，兼顾性能和安全 |
| **seq 分配** | `JdbcRunEventRepository` 通过 `select max(seq)+1` + 唯一约束重试确保单调递增 |

### 总结

**Persistence 和 Event 的关系可以概括为：**

- **Event 模块"管事件"**：追加、回放、SSE 推送、实时总线，面向业务层提供事件能力
- **Persistence 模块"存事件"**：提供 `RunEventRepository` 的 JDBC 实现，处理 `run_events` 表的 SQL 写入和查询
- **通过 Domain 层接口解耦**：Event 调用接口、Persistence 实现接口，两者不直接依赖
- **数据流向**：业务编排层 → Event 模块（`RunEventAppender`） → Persistence 模块（`JdbcRunEventRepository`）落库 → Event 模块（`RunEventLiveBus`）推送给 SSE 连接

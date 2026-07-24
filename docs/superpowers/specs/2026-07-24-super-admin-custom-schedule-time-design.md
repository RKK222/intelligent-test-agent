# 超级管理员自定义定时执行时间设计

## 1. 目标

在现有夜间执行能力中增加仅供 `SUPER_ADMIN` 使用的测试调度模式，使超级管理员可以在白天或夜间选择未来 24 小时内任意一分钟作为计划启动时间，方便验证 XXL-JOB 分发、固定服务器路由、Run 启动、状态更新和会话展示。

普通用户的夜间 15 分钟容量时段、任务列表、会话锁和执行结果展示保持不变。本功能不建立新的任务表、调度框架、执行队列或 Run 链路。

## 2. 已确认方案

采用“双模式 + XXL 每分钟扫描”：

- `NIGHT_WINDOW`：普通夜间模式，继续使用北京时间 `21:00–次日 07:00`、15 分钟容量时段和 `NIGHT_EXECUTION_SLOT_CAPACITY`。
- `ADMIN_CUSTOM`：超级管理员测试模式，允许全天选择精确到分钟的计划时间，不占用夜间容量。
- XXL 分发任务由每 15 分钟扫描调整为每分钟扫描；两种模式都由同一个任务 handler、数据库扫描、目标 Java 内部接口和普通 Run 启动链路处理。

不采用为每个测试任务创建 XXL 动态任务的方案，避免污染 XXL 任务表和引入额外创建、删除、审计状态。不把自定义时间映射到最近的夜间容量桶，避免显示时间与容量语义不一致。

## 3. 用户交互

### 3.1 普通用户

普通用户点击输入区左侧的定时执行图标后，仍只看到现有夜间时段选择器：

- 北京时间，每 15 分钟一个启动时间段。
- 展示系统推荐、已预约数量和容量已满状态。
- 创建和调整操作仍只能选择服务端返回的可用夜间时段。

前端不向普通用户展示测试模式入口，但权限边界仍由后端强制执行。

### 3.2 超级管理员

超级管理员打开同一个定时执行面板时，标题下方增加双模式切换：

- `夜间时段`
- `测试时间`

“测试时间”模式包含：

- `1 分钟后`、`3 分钟后`、`5 分钟后`三个快捷选项。
- 一个精确到分钟的北京时间日期时间输入框。
- 默认选择“1 分钟后”。
- 可选范围为下一完整分钟至未来 24 小时；秒和毫秒固定为零。
- 确认区只显示一个计划启动时间，不显示 15 分钟范围或容量。
- 辅助说明为“北京时间；到达计划时间后通常在 1 分钟内发起”。该说明表达扫描粒度，不承诺秒级启动 SLA。

自定义时间即使落在 `21:00–07:00` 内，也保持 `ADMIN_CUSTOM` 模式，不占用普通夜间容量。

### 3.3 待执行任务和调整

- 待执行列表、当前会话锁定卡和成功反馈为自定义任务展示“测试定时”标识及精确到分钟的时间。
- `NIGHT_WINDOW` 继续显示“日期 + 15 分钟启动范围”。
- 调整任务时保持任务原有模式，不在调整过程中切换模式：夜间任务仍选择夜间时段，自定义任务仍选择精确时间。
- 调整 `ADMIN_CUSTOM` 任务仍要求当前用户具备 `SUPER_ADMIN`；角色被移除后，任务 owner 仍可取消任务，但不能再设置新的自定义时间。
- 同一会话只能有一个 `SCHEDULED` 或 `DISPATCHING` 任务，现有发送锁和取消后解锁语义不变。

## 4. API 与权限

### 4.1 协议

夜间任务创建请求增加可选字段：

```json
{
  "scheduleMode": "NIGHT_WINDOW",
  "slotStart": "2026-07-24T13:15:00Z"
}
```

- `scheduleMode` 允许 `NIGHT_WINDOW`、`ADMIN_CUSTOM`。
- 字段缺失时按 `NIGHT_WINDOW` 处理，兼容旧前端和既有调用方。
- `slotStart` 继续作为权威计划启动时间，不新增含义重复的 `scheduledAt`。
- 调整请求继续只提交 `slotStart`；后端从已有任务读取模式，禁止借调整接口切换模式。
- 任务响应增加 `scheduleMode`。前端读取旧响应缺失该字段时按 `NIGHT_WINDOW` 展示。

公共 API 路径、任务状态枚举和 RunEvent SSE 类型不变。

### 4.2 服务端权限

Controller 必须读取完整认证主体，并把是否具有 `SUPER_ADMIN` 的事实传给应用服务：

- 创建 `ADMIN_CUSTOM` 时，非超级管理员返回统一 `FORBIDDEN`。
- 调整已有 `ADMIN_CUSTOM` 任务时再次校验 `SUPER_ADMIN`。
- owner 始终取认证主体；超级管理员不能通过本接口为其他用户创建或修改任务。
- 查询、取消、失败卡关闭继续按 owner 隔离，不因测试模式扩大权限。

前端的模式隐藏只用于交互体验，不能替代上述后端校验。

## 5. 时间与容量规则

### 5.1 夜间模式

`NIGHT_WINDOW` 完全复用现有窗口计算和容量预留：

- `slot_start`、`slot_end` 为 15 分钟时段。
- `window_end` 为次日 07:00。
- 创建、调整、取消和最终调度失败按现有规则预留或释放容量。

### 5.2 超级管理员测试模式

`ADMIN_CUSTOM` 使用以下规则：

- `slot_start` 为精确到分钟的计划启动时间。
- `slot_end = slot_start + 1 分钟`，仅用于数据模型完整性和展示边界，不表示容量桶。
- `window_end = slot_start + 15 分钟`，作为目标服务器短暂离线或 HTTP 分发失败时的重试宽限期。
- 创建和调整不写 `night_execution_slot_reservations`。
- 取消、失败、Run 受理和终态清理不释放容量，避免误删相同时间的真实夜间占位。
- 创建和调整时间必须满足：秒/纳秒为零、时间不早于服务端计算的下一完整分钟、且不晚于当前时间加 24 小时。

所有时间边界以服务端 `Clock` 和北京时间规则校验，不能信任浏览器本地时区或客户端 min/max 属性。

## 6. 数据模型与兼容性

PostgreSQL 通过新的 Flyway migration 为 `night_execution_tasks` 增加：

```text
schedule_mode varchar(32) not null default 'NIGHT_WINDOW'
```

- 既有数据自动保持 `NIGHT_WINDOW`。
- 新字段进入领域模型、MyBatis result map、insert/update SQL 和任务响应。
- 所有新增或修改的关系型 SQL 继续使用 MyBatis XML mapper。
- 不修改已执行 migration，不删除 `scheduled_task_run_id` 等历史兼容字段。
- 不修改 generated SDK。

任务状态仍为 `SCHEDULED → DISPATCHING → DISPATCHED` 或取消/最终调度失败。`DISPATCHED` 仍只表示普通 Run 已受理，不表示模型执行成功。

## 7. XXL-JOB 与执行链路

新增 XXL MySQL Flyway migration 更新已注册任务 `opencode-runtime.night-execution-dispatch`：

```text
0 0/1 * * * ? *
```

其它配置保持：`GLOBAL_MUTEX`、`ROUND`、`DISCARD_LATER`、`DO_NOTHING`、XXL 重试次数 `0`。

每轮仍执行同一条有界扫描：

- `status = SCHEDULED`
- `slot_start <= now`
- `window_end > now`
- 最多 500 条，按 `slot_start、created_at` 排序。
- 按固定 `target_linux_server_id` 分组，每批最多 50，最多并发调用 8 台服务器。

普通夜间任务因扫描频率提高，会在 15 分钟时段开始后通常 1 分钟内被认领，但其产品语义仍是“在所选 15 分钟时间段内启动”。自定义任务按分钟到期。两者继续复用内部批量接口、attemptId、租约、心跳、Run 锚点幂等、远端受理探测和 5 分钟补偿，不新增专属线程池或持久队列。

## 8. 错误处理与展示

- 非超级管理员提交 `ADMIN_CUSTOM`：`403 FORBIDDEN`。
- 时间包含秒/毫秒、早于下一分钟或超过 24 小时：`400 VALIDATION_ERROR`。
- 调整时任务已被认领：沿用 `409 CONFLICT`。
- 自定义任务超过 15 分钟宽限期仍没有 Run 锚点：由补偿更新为 `FAILED`，错误卡沿用现有安全错误格式。
- Run 已创建但响应丢失：继续通过稳定 `sessionId + clientRequestId` 锚点修复为 `DISPATCHED`，不得创建第二个 Run。
- 日志不得记录 prompt、parts、token 或完整请求体；新增模式和计划分钟不是敏感字段，但日志仍只在必要的结构化调度记录中使用。

## 9. 测试与验收

### 9.1 前端

- 普通用户看不到模式切换，原夜间时段交互和请求保持兼容。
- 超级管理员可切换到测试时间，快捷选项和日期时间输入均生成北京时间、秒/毫秒为零的 `slotStart`。
- 非法范围禁用确认并显示明确原因。
- 创建请求携带 `ADMIN_CUSTOM`，普通请求缺失或携带 `NIGHT_WINDOW` 均可工作。
- 自定义待执行任务、当前会话卡和调整面板显示“测试定时”与单个精确时间。

### 9.2 后端

- 旧创建请求缺少 `scheduleMode` 时按 `NIGHT_WINDOW`。
- 普通用户直接伪造 `ADMIN_CUSTOM` 被拒绝，且业务服务未创建任务或会话。
- 超级管理员可创建白天任务；秒/纳秒、过去时间和超过 24 小时均被拒绝。
- 自定义创建、调整、取消、Run 受理和失败均不写入或释放夜间容量。
- 模式通过 MyBatis 持久化并在响应中返回；既有 migration 数据默认为 `NIGHT_WINDOW`。
- 自定义任务沿用会话锁、固定目标服务器和 Run 幂等。

### 9.3 XXL 与集成

- MySQL migration 把分发 Cron 更新为每分钟且不改变其它策略。
- 每分钟扫描同时处理到期的 `NIGHT_WINDOW` 和 `ADMIN_CUSTOM`。
- 自定义任务在计划分钟到达前不分发，到达后可分发，超过 15 分钟且无锚点时最终失败。
- 运行目标模块测试、PostgreSQL/MySQL Flyway 测试、前端组件与 backend-api 测试、前后端生产构建，并在本地测试环境验证后端、XXL Admin 与 executor 正常启动。

## 10. 文档同步

实施时同步更新：

- `docs/api/http-api.md`
- `docs/architecture/xxl-job-integration.md`
- `docs/deployment/database.md`
- `docs/standards/security.md`
- `docs/testing/xxl-job-integration.md`
- 后端 runtime、API、persistence、XXL integration README/PACKAGE
- 前端 agent-web、backend-api、shared-types README/PACKAGE

`docs/api/event-stream.md` 只需说明本功能不新增 RunEvent 类型；若现有文档已经准确表达该边界，则不重复扩写。

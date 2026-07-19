# 夜间任务容量通用参数化设计

## 背景与目标

夜间异步执行已按北京时间 21:00 至次日 07:00、每 15 分钟一个启动时段运行。现有时段容量来自环境变量 `TEST_AGENT_NIGHT_EXECUTION_SLOT_CAPACITY`，不便于超级管理员在线调整，也不能在修改后主动刷新所有 Java 后端实例。

本次改造目标：

- 将夜间任务每时段全局上限迁移到现有通用参数体系。
- 通过 Flyway 将参数初始化为 `20`，由超级管理员在“系统管理 → 通用参数管理”修改。
- 每个 Java 实例启动时加载参数到本机内存；修改后复用现有通用参数跨服务器广播链路刷新各实例内存。
- 完全移除环境变量入口，不保留环境变量、Spring 配置或代码默认值兜底。

## 已确认决策

1. 通用参数英文名固定为 `NIGHT_EXECUTION_SLOT_CAPACITY`，使用 `platform=all`。
2. 参数中文名为“夜间任务每时段任务上限”，初始值为 `20`，`editable=true`。
3. 继续复用现有通用参数管理页面、`SUPER_ADMIN` 权限、更新审计、数据库存储和服务器广播，不新增管理 API 或专用前端页面。
4. 运行时只为该参数建立专用 JVM 内存快照，不给全部通用参数增加缓存，也不改变其他参数按需查库的行为。
5. 参数值必须是正整数；管理 API 在写库前校验，非法值返回统一 `VALIDATION_ERROR`。
6. 应用启动时参数缺失或非法视为部署数据损坏，阻止实例进入可用状态，避免各服务器容量不一致。
7. 运行中收到刷新事件但重新读取失败时保留上一份有效值并记录安全日志；不把缓存清空为不可用，也不回退到 `20`。
8. 调低容量不取消已经预约的任务；当现有预约数大于或等于新上限时，该时段不再接受新任务。调高后新容量立即可用。

## 方案选择

采用“夜间容量专用内存注册表”方案：运行时组件从 `CommonParameterValues` 读取数据库权威值，启动时装载，参数广播到达时原子替换快照，夜间时段查询、创建和改期统一读取该快照。

不采用以下方案：

- 每次容量计算直接查询数据库：能获得最新值，但不满足启动加载到内存的要求，并增加热点数据库读取。
- 缓存全部通用参数：范围过大，会改变现有通用参数直读数据库及变量展开语义。
- 保留环境变量作为兜底：会形成数据库值与实例部署值两套来源，无法保证集群一致。

## 数据与初始化

新增 Flyway migration，向 `common_parameters` 插入一条生产必需基础系统参数：

| 字段 | 值 |
| --- | --- |
| `parameter_id` | `param_night_execution_slot_capacity_all` |
| `parameter_english` | `NIGHT_EXECUTION_SLOT_CAPACITY` |
| `parameter_chinese` | `夜间任务每时段任务上限` |
| `parameter_value` | `20` |
| `platform` | `all` |
| `editable` | `true` |

该 migration 只初始化系统参数，不写入测试、演示或环境专属数据。依赖现有 `(parameter_english, platform)` 唯一约束保证单一权威值。

移除以下旧配置入口：

- `.env.local.example` 中的 `TEST_AGENT_NIGHT_EXECUTION_SLOT_CAPACITY`。
- `application.yml` 中的 `test-agent.night-execution.slot-capacity` 绑定。
- 部署文档中的环境变量说明和示例。
- 现有 `NightExecutionProperties` 对该环境配置的读取职责。

## 运行时内存模型

在 `test-agent-opencode-runtime` 的夜间任务包新增容量注册表，职责保持单一：

- 使用 `volatile` 不可变快照或等价原子引用保存当前正整数容量及加载元数据。
- 监听 `ApplicationReadyEvent`，通过 `CommonParameterValues.resolvedValue(englishName, ParameterPlatform.ALL)` 从数据库读取并校验容量。
- 监听 `CommonParameterReloadedEvent`。事件英文名为 `NIGHT_EXECUTION_SLOT_CAPACITY` 时刷新；英文名为空的批量刷新事件也重新读取，保证兼容现有广播契约。
- 与当前参数无关的刷新事件直接忽略。
- 对外只暴露读取当前容量的方法，夜间任务应用服务和时段查询不得继续读取 Spring 属性或自行查库。

启动加载必须成功。参数缺失、空白、非数字、溢出或小于等于零时抛出明确但不包含敏感配置的启动异常。

运行中刷新采用“先读取并校验、后替换”顺序。读取或校验失败时保留旧快照并记录 `englishName`、`traceId` 和安全错误分类；日志不记录任意参数原始值。刷新成功后记录新容量和 traceId，便于多服务器排查。

## 修改与跨服务器刷新

现有通用参数修改链路保持不变：

1. `SUPER_ADMIN` 在通用参数管理页提交新值。
2. `CommonParameterManagementApplicationService` 根据参数英文名执行正整数校验，通过后更新数据库并写修改日志。
3. 服务发布 `CommonParameterUpdatedEvent`。
4. `CommonParameterUpdateBroadcaster` 先发布现有服务器广播，再向本实例发布 `CommonParameterReloadedEvent`。
5. 其他 Java 实例收到广播后也发布本地 `CommonParameterReloadedEvent`。
6. 各实例的夜间容量注册表直接从数据库读取最新值并原子替换本机快照。

广播 payload 只携带参数英文名、平台、参数 ID、traceId 和实例元数据，不携带参数值；符合现有服务器广播安全边界。数据库仍是权威来源，广播只负责触发刷新。

如果单个远端实例因临时数据库或广播故障未刷新，它继续使用上一份有效值并产生日志告警；实例重启时会重新加载数据库值。现有服务器广播是集群实时刷新通道，本次不新增第二套消息协议或确认 API。

## 容量语义

夜间窗口、15 分钟时段、推荐算法和数据库占位原子性保持不变，仅把容量来源替换为内存注册表。

- 查询时段：响应中的 `capacity` 和 `available` 使用调用实例当前内存值计算。
- 创建任务：事务内条件占位使用同一次操作读取到的当前容量。
- 调整时段：释放旧占位并占用新时段时使用当前容量。
- 调低容量：保留已存在的预约和任务状态；`reservedCount >= capacity` 时拒绝新增预约。
- 调高容量：后续查询、创建和改期立即使用新上限。
- 执行、取消、顺延和最终失败的容量释放规则不变。

参数刷新不会回溯修改已持久化任务，也不会取消 scheduler 运行记录、会话锁或正在执行的 Run。

## 错误处理

- 管理 API 收到空白、非整数、溢出或非正数：返回 `VALIDATION_ERROR`，不更新数据库、不写成功修改日志、不发布刷新事件。
- 启动加载失败：实例启动失败，并输出参数名和安全原因；不使用环境变量或默认值继续运行。
- 运行中刷新失败：保留旧值，记录告警；已有夜间任务和普通会话继续运行。
- 容量下降导致目标时段已满：继续返回现有 `CONFLICT` 和最新时段详情，不新增错误码。

## 测试

### 后端单元与集成测试

- Flyway/MyBatis 集成测试确认参数以 `20/all/editable=true` 初始化并能通过现有仓储读取。
- 通用参数管理服务测试确认正整数可保存，空白、非数字、溢出、零和负数被拒绝，失败时不发布更新事件。
- 容量注册表测试覆盖启动加载成功、缺失/非法启动失败、匹配事件刷新、空英文名批量刷新、无关事件忽略、运行中刷新失败保留旧值。
- 夜间任务应用服务测试确认时段查询、创建和改期读取注册表的动态值。
- 容量下降到已预约数以下时拒绝新任务但保留已有任务；容量上升后可以继续预约。
- 通用参数广播既有测试继续验证本地和远端事件，确保本次监听方可以接收同一事件契约。

### 回归验证

- 后端相关模块定向测试。
- `mvn clean package -DskipTests`。
- 若管理页面无代码变更，运行现有通用参数页面相关前端测试、typecheck 和 build，确认新 editable 参数无需专用 UI 即可修改。

## 文档与兼容性

同步更新：

- `backend/README.md`、configuration-management/runtime/persistence/app 模块 README 与 PACKAGE 说明。
- `docs/api/http-api.md` 的通用参数校验说明和夜间任务容量来源。
- `docs/deployment/backend.md`，移除旧环境变量并说明在线配置和跨实例刷新。
- `docs/deployment/database.md`，登记通用参数初始化 migration。
- `docs/standards/backend.md`，明确夜间容量是经用户确认的专用内存缓存例外。
- `docs/standards/security.md`，确认权限、审计、广播 payload 和安全日志约束不变。
- 原夜间任务设计文档保留历史决策，不作为稳定运行配置说明；稳定文档以本次实现后的 README、API 和部署文档为准。

兼容性影响：

- 不新增或删除 HTTP 路径，不修改 DTO 或 RunEvent。
- `NIGHT_EXECUTION_SLOT_CAPACITY` 是新增可编辑基础参数，数据库升级后自动具备初始值 `20`。
- 删除环境变量入口属于部署配置变更；升级后即使继续设置旧环境变量也不会生效，部署脚本和示例必须同步清理。
- 普通发送、scheduler CRON/MANUAL/USER_PLAN、夜间任务状态、会话锁和 Run 执行链路均保持原语义。

## 验收标准

1. 新数据库或完成 migration 的数据库存在唯一的全局容量参数，值为 `20` 且可由超级管理员修改。
2. 每个 Java 实例启动后内存容量与数据库值一致；缺失或非法时实例不能进入可用状态。
3. 参数修改成功后，本实例和通过现有服务器广播连接的其他实例都从数据库刷新内存值。
4. 修改后的容量立即影响时段查询、创建和改期，不影响已有预约或已执行任务。
5. 环境变量、Spring 绑定、配置模板和部署文档不再包含 `TEST_AGENT_NIGHT_EXECUTION_SLOT_CAPACITY`。
6. 不新增管理页面、管理 API、RunEvent、关系表或平行广播机制。

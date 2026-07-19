# 通用参数内存值查询与手工刷新设计

## 目标

保持现有“多数通用参数按需直读数据库”的默认语义，仅让明确声明为 JVM 内存参数的组件进入统一注册表。超级管理员可以在通用参数页面查看所有在线 Java 进程实际生效的内存值，并按数据库当前值刷新全部或指定 Java，获得逐进程确认结果。

首个注册项为 `NIGHT_EXECUTION_SLOT_CAPACITY/all`。它继续以数据库为唯一权威来源，启动时严格加载，运行期刷新失败时保留上一份有效容量。

## 组件边界

- `test-agent-domain` 定义内存参数条目 SPI、不可变状态和刷新结果；条目键由英文名与平台组成。
- `test-agent-configuration-management` 聚合全部 SPI Bean，校验键唯一，负责启动加载、自动事件匹配刷新、本机查询和手工全量刷新。
- `test-agent-opencode-runtime` 的夜间容量注册表实现 SPI；夜间任务查询、创建、改期与补偿只读取该原子快照。
- `test-agent-api` 复用 `BackendJavaRouteResolver` 和 `BackendHttpForwarder`，按 `backendProcessId` 精确访问每个在线 Java，不使用 Redis 参数快照或私有转发器。
- 前端继续通过 `shared-types` 与 `backend-api` 访问平台 HTTP API，在通用参数页面按需打开诊断抽屉。

## 状态与刷新语义

每个内存参数状态包含：英文名、平台、上次成功读取的数据库展开值、实际内存生效值、成功加载时间、最近刷新尝试时间、刷新状态和安全错误说明。

- 启动加载任一注册项失败时阻止 Java 进入可用状态，不使用环境变量或代码默认值。
- 自动更新事件仅刷新英文名和平台匹配的注册项；空英文名事件刷新全部注册项。
- 手工刷新直接调用目标 Java 的本机注册表，不发布二次广播、不改数据库、不写参数修改历史。
- 运行期刷新失败时保留上一份成功值，只更新最近尝试时间和失败状态；日志不记录参数值或原始异常。
- 注册键重复视为装配错误并阻止启动。

## API 与集群聚合

Base URL：`/api/internal/platform/configuration-management/common-parameters`

- `GET /memory-values`：查询全部在线 Java。
- `GET /memory-values/{backendProcessId}`：查询指定 Java。
- `POST /memory-values/refresh`：刷新全部在线 Java。
- `POST /memory-values/{backendProcessId}/refresh`：刷新指定 Java。

接口仅允许 `SUPER_ADMIN`。集群操作最多处理 500 个在线 Java，固定并发 8、单进程超时 10 秒，结果按服务器 ID、后端进程 ID 排序。部分失败仍返回 HTTP 200，并用 `SUCCESS/PARTIAL/FAILED/UNAVAILABLE` 逐进程表达；未知或已离线的单进程请求返回统一 503。

## 前端交互

通用参数工具栏增加“查看内存加载值”。抽屉只在打开时请求，按 Java 卡片展示进程、服务器、加载源值、内存生效值、时间和状态；提供“刷新全部 Java”和“刷新此 Java”。集群部分失败以汇总警告和失败卡片展示，成功卡片仍保留。

视觉继续沿用现有白底、Element Plus、等宽参数值与紧凑运维表格，仅以进程状态条强化多 Java 对比，不引入新的设计体系或常驻轮询。

## 兼容性与安全

- 新增 API 和 DTO，不修改既有参数列表、更新、修改历史或 RunEvent 契约。
- 不新增 Redis key、关系表或后端到后端文件代理。
- 具体内存值只向超级管理员返回；日志与错误明细不得包含未校验值、密钥或堆栈。
- `RepositoryCommonParameterValues` 保持数据库直读；未实现 SPI 的参数不会进入 JVM 缓存或诊断结果。

## 验收

后端测试覆盖注册表、夜间容量、精确 Java 路由、四个 API、部分失败和权限；前端测试覆盖按需查询、空/错误/部分失败、全部刷新与单进程刷新。完成时运行相关 Maven 测试、后端打包、前端测试/typecheck/build、文档与 session log 自检，并只提交本任务文件。

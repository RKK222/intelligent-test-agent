# Task 5 实施报告：稳定文档、综合验证与最终审计

## 结论

应用源码固定提交快照已完成最终交付审计。领域、持久化、多服务器物化、HTTP/独立进度 WebSocket、工作台源码模式和到期清理的稳定文档已形成闭环；定向测试、后端与前端根测试、类型检查、生产构建及 Chromium/mobile Playwright 均通过。

本任务没有修改 OpenCode 1.18.4 快照、generated SDK、`.env*` 或 `toolbox-source/it-tools/**`。综合测试发现并修复一处 AppSource 分支与远端主线自动合并产生的测试装配回归：`ReferenceRepositoryContextTest` 同一 dispatcher Bean 被注册两次；最小修复只删除重复 import 和重复 `.withBean`，保留 AppSource 新增依赖。

## 稳定文档

- `docs/deployment/backend.md`：补充 `OPENCODE_APP_SOURCE_ROOT` 多服务器本机挂载、三倍容量建议、物化补偿 worker、租约、XXL V6 每分钟清理、监控和人工处置边界；同步 Admin V6 与八条周期任务现状。
- `docs/testing/app-source-snapshot.md`：新增综合自动化矩阵、多服务器人工验收、数据库/容量/日志检查，并明确源码进度 WebSocket 不产生 RunEvent/SSE。
- `docs/README.md`：登记新的应用源码快照测试入口。
- `frontend/packages/backend-api/src/PACKAGE.md`：补充 operationId ECMAScript 空白规范化、AbortSignal/AbortError、严格进度帧校验和关闭语义。
- 复核既有 domain、workspace-management、API、frontend、backend-api/shared-types README/PACKAGE，以及模块地图、HTTP API、event-stream、database、security；内容已覆盖当前实现，无需重复改写。

## 代码与提交边界审计

- 从功能提交集合审计 154 个唯一路径，未发现 `opencode-source/opencode-1.18.4/**`、generated SDK、`.env*` 或 `toolbox-source/it-tools/**` 变更。
- 新增关系型 SQL 只存在于 `V20260728103000__create_app_source_snapshot_tables.sql`、`AppSourceMapper.xml` 和独立 XXL Flyway V6；业务运行 SQL 全部通过 MyBatis XML，没有新增 JDBC SQL 或 MyBatis 注解 SQL。
- AppSource Controller 未直接依赖 Repository 或 generated SDK；业务层未依赖 generated SDK；源码文件继续走平台文件 WebSocket route/ticket/RPC，没有新增后端到后端文件 HTTP 代理。
- 未发现业务入口绕过公共 OpenCode route/start/stop/status 服务，也未修改 manager 路由、进程 `startedAt` 或 OpenCode 上游快照。
- 新增复杂实现已保留中文注释；API、事件、数据库、安全、兼容性和部署文档已由前序任务同步，本任务补足部署与综合测试入口。

## 合并回归定位与修复

后端根测试首次执行只有 `ReferenceRepositoryContextTest.productionConstructorWiresControllerServiceRepositoryAndReconcilerWithoutGitBean` 失败，异常为 `BeanDefinitionOverrideException`，同名 `referenceRepositoryReplicaTaskDispatcher` 已存在。

根因证据：

1. 当前文件有两条相同 import 和两次相同 `.withBean(ReferenceRepositoryReplicaTaskDispatcher.class, ...)`。
2. AppSource 父分支 `cc73c33bc` 为新 `AppSourceRepositoryHistory` 构造依赖补入一份 dispatcher；远端主线父分支 `6779d77ed` 也独立补入一份 dispatcher。
3. 合并提交 `78fb0bf2a` 自动保留双方文本，未产生文本冲突但形成语义重复；两个父分支单独都只有一份。
4. 单独运行该测试稳定得到 1 test / 1 failure；删除第二份重复注册后同命令 1/1 通过，验证单一根因假设。

修复后 `test-agent-app -am` 回归和后端根全量均通过。此修复只清理测试装配重复，不改变生产 Bean 图或业务行为。

## 最终验证

### 后端

- AppSource 定向 reactor：领域、配置、H2/MyBatis、PostgreSQL、物化、恢复、API、进度票据/WebSocket、XXL、SQL/Flyway 约束测试全部通过。
- PostgreSQL 16 Testcontainers：`MyBatisAppSourcePostgresqlIntegrationTest` 1/1，通过，0 skipped。
- MySQL 8.4 Testcontainers：`XxlJobMysqlMigrationTest` 3/3，通过，0 skipped。
- 合并回归 RED：`ReferenceRepositoryContextTest` 1 test / 1 failure，稳定复现同名 Bean 重复注册。
- 合并回归 GREEN：同一测试 1/1；`mvn -q -DappLogDir=target/log -pl test-agent-app -am test` 通过。
- 根全量 `mvn -q -DappLogDir=target/log test`：exit 0；fresh Surefire 为 351 suites / 2198 tests / 0 failures / 0 errors / 19 conditional skips。上述 PostgreSQL、MySQL 关键生产方言测试均为 0 skipped。

JDK 21 沙箱内 Mockito 5.23.0 不能自附加时，定向测试曾出现 28 个初始化错误；使用显式 Mockito `-javaagent` 后同一测试链通过。最终根测试在允许 JVM 自附加和 Docker 的本机环境执行，不依赖该沙箱规避。

### 前端

- AppSource 定向 Vitest：9 files / 122 tests，通过。
- 相关 Playwright：Chromium + mobile 22/22，通过，覆盖 tree drift、socket epoch、指数退避、CONNECTING abort、跨模式迟到结果、Diff 保存门禁和 failure epoch。
- 根 `corepack pnpm test`：104 files / 1691 passed / 1 skipped，通过；仅有既有 jsdom Canvas `getContext` 提示。
- 根 `corepack pnpm typecheck`：13/14 workspace scope 全部通过。
- 根 `corepack pnpm build`：user-manual 与 agent-web 生产构建通过；仅保留既有大 chunk 提示。

### 静态与文档

- `tools/verify-ai-docs.sh`：通过。
- `git diff --check`：通过。
- 已回顾全部 `.agents/session-log*.md` 的近期变更、已知问题和未完成事项，未发现本次范围覆盖或丢弃其他开发者成果，也未发现残留冲突标记。

## 影响与兼容性

- **API：** 功能整体新增应用源码列表、tree snapshot、materialize/retry/open/operation/ticket 等平台接口；本任务只补文档与测试装配修复，不再改变 wire。
- **事件：** 应用源码进度使用独立只读 WebSocket，不新增或复用 RunEvent/SSE；断开观察不取消后台 operation。
- **数据库：** 功能整体新增 AppSource Flyway 表、约束和 MyBatis XML；本任务不新增 migration、字段或 SQL。
- **性能：** 每 Java 默认 2 个物化 worker、256 pending key、5 秒/64 条补偿扫描；清理每分钟触发并按 32 条/300 秒租约处理。部署文档已要求三倍容量和 backlog/磁盘监控。
- **安全：** 物理目录与保留索引 fail closed，文件访问逐 RPC 实时鉴权；进度票据一次性且绑定用户/operation/JVM/Origin，payload 与日志不返回物理路径、凭据或原始 Git stderr。
- **兼容性：** tree 旧数组接口保留，新增 envelope/DTO 字段均为 additive；operationId 前后端使用同一空白规范化；TEAM 不可降为 PERSONAL，旧 generation 写回受 fence 拒绝。

## 未完成事项与风险

- 自动化已经覆盖生产 PostgreSQL/MySQL 方言，但没有在本机搭建真实双 Java/双 Linux 服务器执行完整 Git、副本恢复和磁盘清理人工验收；上线前仍需按新增测试文档逐项执行。
- 根后端 19 项跳过属于现有条件化 fixture；AppSource PostgreSQL 与 XXL MySQL 关键验收均实际运行且没有跳过。
- 前端构建的大 chunk 警告与 jsdom Canvas 提示为既有非阻断输出，本任务未扩大范围处理。
- 本次只提交 AppSource 收尾文档、综合报告、会话日志和合并回归最小测试修复，不包含工具盒子源码或其它并行成果。

## Fix Round 1

- 按生产实现修正进度帧契约说明：每个非法入站帧都会单独转换为安全的 `WEBSOCKET_MESSAGE_INVALID` 回调，原始 payload 和解析/校验错误细节不会暴露；未修改连接行为。
- 修正 APP_SOURCE Run Diff 验收语义：普通源码路径仍写入 Workspace，只有 PUBLIC/WORKSPACE Agent 配置路径在 DiffViewer、父组件和 mutation 门禁被阻止并保持零条 `agent-config.write`；未修改生产代码。

## 最终整功能审查修复波次

最终整功能审查提出的 C1/I1/I2/I3 已在唯一一轮修复中闭环，范围只包含并发终态收敛、retry 幂等、跨关联应用进度授权、持久化执行时间线及其直接测试和稳定文档。

### C1：终态串行收敛与 stranded 补偿

- replica success/failure 先以 lease CAS 写入本服务器结果，再按一致锁序锁定 repository slot；全体 replica 聚合、snapshot/slot/operation 终态和 pending 清理在同一事务完成。
- 首个 READY 提升时先失效旧 ACTIVE，再激活新 PENDING，避免 PostgreSQL 部分唯一约束冲突；并发失败、混合 READY/FAILED 和旧 generation/expiry 保留均有真实 PostgreSQL 断言。
- dispatcher 扫描“operation 仍在途但 replica 已全终态”的 stranded 记录，并在相同 slot 锁下幂等重算，不重做 Git、不推进非终态 replica。新增 `(status, accepted_at, operation_id)` 索引支撑周期扫描。

### I1：retry operationId 不可变身份幂等

- service 在计算动态 FAILED/STALE targets 前查询既有 operation，并在完成当前 repository、关联应用和成员权限校验后，仅按 route app、repository、actor、类型和 expected generation 匹配重放。
- registrar 在 repository 锁内使用同一不可变身份判断，覆盖两个首次请求都未看到 operation、但第二个请求落锁时 targets 已变化的竞态。
- PENDING、已 claim RUNNING、SUCCEEDED、active generation 已替换/过期后的历史重放均返回原 operation；不同用户、仓库、应用、类型或 generation 仍返回冲突。

### I2：按任一当前关联应用实时授权

- TEAM operation 的 HTTP 查询、ticket 签发、ticket 消费、WebSocket 首帧和后续轮询，均允许 repository 任一当前启用关联应用的当前成员访问。
- PERSONAL 仍限制 owner，或满足关联应用成员条件的 APP_ADMIN/SUPER_ADMIN；普通跨应用成员不可读取。
- repository 解除关联、应用禁用或成员撤销后立即拒绝新 ticket，并使已签 ticket/既有 WebSocket 的后续实时复核失败。HTTP 和 WebSocket wire 均未改变。

### I3：完整、低敏、可恢复的持久化步骤

- materialization 与 retry 为每台目标服务器登记同一组 13 个稳定步骤：`QUEUED`、`LEASE_CLAIM`、`LOCAL_LOCK`、`STAGING`、`SHALLOW_CLONE`、`FETCH_FIXED_COMMIT`、`SPARSE_CHECKOUT`、`VALIDATE`、`REMOVE_GIT_METADATA`、`WRITE_INDEX`、`ATOMIC_REPLACE`、`REGISTER_WORKSPACE`、`COMPLETE`。
- progress recorder 在持有当前 replica lease 时执行 attempt reset 和步骤 CAS；旧 attempt 失去 lease 后不能覆盖新 attempt。legacy `RETRY_QUEUED` 可被新 worker 接管，未知 legacy 非终态步骤会安全终结。
- 失败步骤写固定低敏摘要，后续步骤 SKIPPED；result recorder 在 replica 终态 CAS 后同事务补齐剩余步骤，确保终态 operation 不残留 PENDING/RUNNING。真实 materializer 原始敏感 Git 错误不会进入持久化 summary。

### 本波次验证

- 后端跨模块定向 reactor 通过，覆盖 application service、registrar、worker、materializer、result/progress recorder、dispatcher、API/WebSocket、H2/MyBatis 和 SQL/Flyway 约束。
- PostgreSQL 16 Testcontainers：持久层 2/2、应用层三服务器 barrier 收敛 2/2，均实际运行且 0 skipped；H2 migration 也验证新增索引和 lease-fenced reset SQL。
- 前端 AppSource 定向 Vitest：9 files / 122 tests；全 workspace typecheck：13/14 scope 通过。wire 未变，按修复要求未重复执行全量 Playwright。
- 后端根全量最终 `exit 0`，fresh Surefire 为 354 suites / 2225 tests / 0 failures / 0 errors / 19 conditional skips。诊断复跑期间，既有 `RunRuntimeLossConvergenceSchedulerTest` 曾在 1 秒窗口偶发只观察到一次重试；同一测试类在相同权限下隔离复跑 5/5、最终根全量再次通过，确认与本波次无关的时序抖动。
- `tools/verify-ai-docs.sh`、SQL/Flyway 约束、`git diff --check`、禁止路径和冲突标记检查通过。

### 影响、兼容性与剩余风险

- API wire 与 RunEvent/SSE 均不变；进度仍使用独立只读 WebSocket，但授权语义扩展为 repository 任一当前关联应用的实时权限。
- 数据库新增一条仅含索引的 Flyway migration，并新增/调整 MyBatis XML 查询与 CAS；没有新增 JDBC SQL 或测试数据 migration。
- 并发聚合增加 repository slot 行锁，换取同一 repository 终态严格串行；扫描使用 status 前导索引，未扩大到全表无索引排序。
- 未修改 OpenCode 快照、generated SDK、`.env*`、工具盒子或无关模块。真实双 Java/双 Linux 端到端部署验收仍按人工清单在上线前执行；既有 runtime scheduler 1 秒时序测试保留偶发风险，本波次未越界修改。

## 最终复审二次闭环

独立复审在 `eeb2d1c95` 上发现的 C1/I1/I2 已按唯一一轮二次修复闭环；范围只包含 retry 终态门禁、磁盘 completion 不可逆点、materialization 并发 operationId 幂等及其直接测试和稳定文档。

### C1：retry SERVER steps 终态门禁

- `RETRY_REPLICAS` 不再用沿用旧状态的 generation replicas 单独判断本次 retry 已完成；result success/failure 和直接 stranded recovery 在 repository slot 锁内读取本 operation 的 SERVER steps，存在任一 `PENDING/RUNNING` 目标时保持 operation 非终态。
- MyBatis stranded SQL 区分 operation 类型：DOWNLOAD/UPDATE 保留按全副本终态恢复历史脏状态；RETRY 必须存在 SERVER steps 且全部终态，离线目标跨多个扫描周期不会被提前补成失败或跳过。
- H2/MyBatis、dispatcher 周期恢复和 PostgreSQL 三服务器 barrier 均覆盖 active A=READY、B/C=FAILED，retry B/C 时 B 完成而 C 离线；C 步骤保持 PENDING，恢复后可 claim 并最终收敛。

### I1：completion 后 backup 清理 best-effort

- target→backup、staging→target 或数据库 completion 失败仍尽力删除新 target 并恢复旧目录。
- completion 成功返回成为不可逆发布点；之后 backup cleaner 的 I/O 或运行时失败被安全忽略，新 target 和新索引继续保留，遗留同级 `.backup` 交给既有 cleanup worker。
- 文件系统失败注入断言 completion 只调用一次、物化成功返回、新 target 存在且旧 backup 可遗留；原 completion 失败回滚用例继续通过。

### I2：materialization 并发 operationId 幂等

- registrar 取得 repository 锁后先查询 existing operation，再读取 expected/pending generation 等可变 slot；existing 严格匹配 app、repository、actor、operation type、requestHash 和 source generation。
- 相同重放返回原 operation，并从首请求持久化 SERVER steps（兼容同冻结 generation replicas）恢复目标服务器，不采用第二次请求实时观察到的 targets；缺少任何冻结目标时 fail closed。
- PostgreSQL 真实事务 barrier 证明两个同 operationId 请求得到同 operation/generation/冻结目标，cleanup、snapshot、replica 和 13 步只写一次；不同 app/user/type/hash 的单元测试均冲突。

### 二次闭环验证与影响

- 组合后端定向 10 个测试类共 71/71，通过且 0 failures/errors/skips；其中 PostgreSQL 持久层 2/2、应用层 4/4 实际运行。
- 后端根 `mvn -q -DappLogDir=target/log test` 最终 `exit 0`；fresh Surefire 为 354 suites / 2234 tests / 0 failures / 0 errors / 19 conditional skips。
- 前端 wire 未变，按约定执行 AppSource 定向 Vitest 9 files / 122 tests 与全 workspace typecheck 13/14 scope，全部通过，未重复执行 Playwright。
- `tools/verify-ai-docs.sh`、SQL/Flyway 约束、diff、冲突标记、禁止路径和日志审计通过。本轮只调整既有 MyBatis XML，不新增 migration、表、字段、JDBC SQL、HTTP/事件 wire 或前端代码。
- completion 后短时遗留 backup 会增加有限磁盘占用，沿用既有每分钟 cleanup 和磁盘/backlog 告警；真实双 Java/双 Linux 的完整 Git、离线恢复和磁盘清理仍按人工验收清单在上线前执行。

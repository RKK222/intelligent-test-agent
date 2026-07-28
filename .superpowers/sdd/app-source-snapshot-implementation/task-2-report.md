# Task 2 实施报告：源码物化、分布式副本、打开权限与清理

## 结论

Task 2 已完成。实现位于 `test-agent-workspace-management`，未复用引用资产库领域语义，未修改 OpenCode 快照、generated SDK 或 `.env.local`。关系型运行 SQL 全部位于 `AppSourceMapper.xml`；PostgreSQL 结构沿用 Task 1 migration，本任务只新增 XXL MySQL V6 基础任务 registration。

## 业务实现

- `AppSourceApplicationService` 实现仓库列表、普通成员 branches/tree、固定 commit 物化、同 generation 失败副本重试、open、recent query/clear。每个入口实时校验 enabled 应用、有效成员、当前关联和 `APPLICATION_CODE_REPOSITORY`；仓库摘要直接提供占用人 userId、姓名和统一认证号。
- 物化先在事务外解析分支 commit、验证 exact selection，再由 `AppSourceMaterializationRegistrar` 在事务内锁代码库。目标服务器 cleanup task 是第一条持久化写，随后才保存 snapshot/operation/replica/step/slot；事务返回后才调用 dispatcher。
- PERSONAL 目标必须来自当前用户 ACTIVE binding、RUNNING 进程和实时 heartbeat；TEAM 目标冻结受理时 `liveBackendServerIds()`。到期个人占用立即对新请求释放，TEAM→PERSONAL 拒绝，PERSONAL→TEAM 使用新 generation 干净重拉。
- `DefaultAppSourceReplicaTaskDispatcher` 使用本机总容量有界队列并按 repository/generation/server 去重；Redis 广播只含安全 ID 并只负责唤醒。`AppSourceReplicaWorker` 只认领当前服务器数据库租约，过期/陈旧 generation 在 Git 前静默跳过。
- `AppSourceGitMaterializer` 使用同根 staging、浅 clone、`sparse-checkout --no-cone --stdin`、固定 commit；支持 FILE/DIRECTORY/`.` 和特殊路径，不递归子模块，拒绝越根 symlink，删除 `.git`，原子写索引和替换目录。数据库完成回调与磁盘发布共用同一文件锁；写回失败恢复旧目录。
- `AppSourceReplicaResultRecorder` 在首台 READY 后提升 pending generation；旧 snapshot 过期并把所有历史目标 cleanup task 提前到期。部分成功只开放 READY 服务器，全失败保留旧 active/expiry。同 generation retry 不改变 commit、selection 或 expiry。
- 每个 generation/server 注册新的 `appsource:<repositoryEnglishName>` Runtime Workspace。`AppSourceWorkspaceOpener` 只开放当前 Java 服务器 READY 副本，并按数据库权威 SHA 修复缺失/损坏索引。
- `ManagedConversationWorkspaceAccessAuthorizer` 增加 replica→snapshot→enabled linked app 实时回溯：TEAM 同机有效成员可写，PERSONAL 仅 owner 可写，撤权、过期或非 READY 均失败关闭，`SUPER_ADMIN` 不旁路。`WorkspaceApplicationService` 的写授权统一先执行该 authorizer。
- `.testagent-appsource-index.json` 在文件列表/搜索中隐藏，读取、分片、状态、写入、上传、复制、移动、重命名和删除均拒绝。
- `AppSourceCleanupTaskHandler` 以 `workspace-management.app-source-cleanup` 每分钟执行全局低敏唤醒；每台 Java 的 worker 仅扫描本机 due task，使用数据库租约和同根文件锁删除源码、对应 generation staging 和 backup，保留/修复索引并归档旧 Workspace。全局 slot 已前进但本机没有新 generation READY 副本时仍删除本机旧源码；本机新 READY 副本存在时 generation fence 保留新内容。
- XXL MySQL `V6__register_app_source_cleanup_task.sql` 注册每分钟、`GLOBAL_MUTEX`、`ROUND + DISCARD_LATER + DO_NOTHING + retry=0` 的第八个任务。

## TDD RED / GREEN 证据

生产行为均先以失败断言确认缺口，再做最小实现。代表性证据如下：

1. operationId 幂等：RED 时相同请求仍重复解析 Git/登记；GREEN 后同 payload 直接返回既有 operation，不同 payload 在 Git 前 `CONFLICT`。
2. Git 物化：特殊路径 selection RED 为不匹配，根 `.` RED 被归一化为空，已有目录替换 RED，发布/数据库完成失败 RED 未恢复旧目录；GREEN 后四类 fixture 均通过。
3. 实时权限：撤销 TEAM 成员 RED 时无异常；GREEN 后拒绝。随后补充 TEAM 同机有效成员成功、PERSONAL 非 owner 拒绝。
4. 隐藏索引：RED 时列表/读取/写入仍暴露；GREEN 后列表/搜索隐藏且所有文件动作拒绝。重命名普通文件为索引名也先 RED 后 GREEN。
5. 副本 worker：过期 generation RED 时进入 ERROR/异常；GREEN 后在租约和 Git 前返回 `SKIPPED_STALE`。
6. dispatcher：`maxPending=1` 时第二个不同 key RED 仍执行；GREEN 后容量约束覆盖运行中与排队中的全部 key，并保持相同 key 去重。
7. cleanup generation fence：全局已有新 generation、但本服务器没有新 READY 副本时 RED 留下旧源码；GREEN 后删除旧源码，只有本机新 READY 才保护共享根。
8. 磁盘/数据库原子边界：RED 时 materializer 缺少同锁完成回调；GREEN 后数据库完成失败会把磁盘恢复到旧目录。
9. 占用人展示契约：RED 编译明确缺少 `ownerName()` / `ownerUnifiedAuthId()`；GREEN 后仓库摘要从业务层返回姓名和统一认证号，API 层无需查询 Repository。
10. MyBatis XML：H2 真实数据库测试最初分别暴露非法 fixture ID、Workspace 外键缺失；补齐合法生产关系后，仓库行锁、runtime Workspace 反查、cleanup 到期提前和 Replica 清理终态全部执行真实 XML。PostgreSQL 用例进一步执行生产方言行锁、due scan/提前和双 owner cleanup 租约 fencing。
11. 绝对租约时间：replica/cleanup RED 均显示完成写回复用了认领时刻；GREEN 后每个 cleanup 逐任务签发新 lease，文件/Git 完成与失败写回重新取权威时钟，过期旧 worker 由数据库拒绝。
12. expiry/cleanup 竞态：RED 时副本在 snapshot 到期后仍先保存 Workspace，cleanup 也会删除持有未过期物化 lease 的目录；GREEN 后结果事务在首写前复核 snapshot 未过期，cleanup 遇活租约按退避重试。离线 `PENDING` replica 清理最初 XML 更新 0 行，GREEN 后可推进 `CLEANED`，但未过期 `RUNNING` 仍受 lease 保护。
13. fixed commit：真实 Git fixture 在受理提交后继续推进 branch，异步 worker 仍物化冻结的旧 commit；该 fixture 直接通过，确认既有浅克隆/checkout 路径没有漂移到新 HEAD。

Mockito 在当前 JDK 默认自附加不可用，相关命令显式使用：

```text
-DargLine=-javaagent:/Users/huang/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar
```

## 最终验证

- `mvn -q -pl test-agent-workspace-management -am -DargLine=... test`：通过。
- `mvn -q -pl test-agent-app -am -Dtest=AppSourceContextTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=... test`：通过；使用完整生产 bean 图，未以 mock 绕过构造器依赖。
- `mvn -q -pl test-agent-persistence -am -Dtest=MyBatisAppSourceRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，H2 PostgreSQL 模式 10/10。
- 沙箱外 `mvn -q -pl test-agent-persistence -am -Dtest=MyBatisAppSourcePostgresqlIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，PostgreSQL 16 Testcontainers 1/1，0 skipped；最终复验包含离线 PENDING replica 清理。
- 沙箱外 `mvn -q -pl test-agent-xxl-job-integration -am -Dtest=XxlJobMysqlMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，MySQL 8.4 Testcontainers 3/3，0 skipped。
- `mvn -q -pl test-agent-app -am -DskipTests package`：通过。
- `git diff --check`：通过。

## 文档同步

- 工程/包：`backend/README.md`、`test-agent-workspace-management/README.md`、workspace `PACKAGE.md`、`test-agent-xxl-job-integration/README.md`。
- 架构：`module-map.md`、`dependency-rules.md`、`xxl-job-integration.md`。
- 契约与运维：`http-api.md`（只同步既有文件 WebSocket 的隐藏索引/实时授权行为；Task 3 新 HTTP API 尚未实现）、`database.md`、`security.md`、`xxl-job-integration.md` 测试验收。
- 未修改 `event-stream.md`：本任务只增加内部低敏广播，不新增 RunEvent/SSE 或面向浏览器的进度 WebSocket；后者属于 Task 3。

## 影响与兼容性

- API：无新增 HTTP Controller；既有文件 WebSocket 对平台索引增加拒绝规则，属于安全收紧。
- 事件：只新增内部 `app-source.replica-requested` / `app-source.cleanup-requested` 唤醒，不新增 RunEvent。
- 数据库：PostgreSQL 无新结构；新增四个 MyBatis XML 运行查询/更新和真实 H2/PG 覆盖。XXL MySQL 向后兼容新增 V6/第八个任务。
- 性能：dispatcher 总容量有界；Git/文件 IO 不在 HTTP 或 Redis listener 的物化链路执行；cleanup 批次默认 32，租约默认 300 秒。
- 安全：SSH key 只在 Git 命令期解密；广播、数据库步骤、错误和索引不含凭据/物理根/原始 stderr；隐藏索引失败关闭；成员关系每次实时复核。
- 兼容性：新增记录字段和 repository 方法均为内部向后兼容扩展；旧快照数据保留，旧 Java 不访问新 XXL task。

## 未完成事项与风险

- Task 3 的 HTTP/进度 WebSocket/DTO、文件路由入口集成以及前端不在本任务范围；当前已提供业务数据契约和 authorizer 能力供其接入。
- 未执行全仓所有 Maven 模块测试；已执行 workspace 全模块依赖测试、真实 PostgreSQL/MySQL 集成、Spring 装配和 app 聚合编译。
- 按 Task 2 协作指令未更新 `.agents/session-log.*.md`；会话日志留给最终汇总任务统一写入，未修改冻结的 `.agents/session-log.md`。

## 独立复审修复（Round 1）

独立复审提出 2 个 Critical、2 个 Important 和 1 个 Minor 问题。本轮逐项先复现行为缺口，再做最小修复；没有修改 OpenCode 快照、generated SDK、数据库结构、XXL migration 或 `.env.local`。

1. **Critical：副本唤醒不是持久事实。** 根因是 dispatcher 只消费进程内队列与 Redis 唤醒，队列拒绝、广播丢失或 Java 退出后没有恢复入口。新增按本机服务器查询 `PENDING` 与租约已到期 `RUNNING` replica 的 MyBatis XML，dispatcher 启动即扫描并默认每 5 秒补偿，数据库租约继续负责最终并发控制。断言级 RED 在基线 `47c1c1879` 的独立临时 archive 中运行 `queueRejectedWakeMustEventuallyRunAfterCapacityBecomesAvailable`，结果为 `expected true, actual false`（1 test / 1 failure）；当前 `periodicRecoveryEventuallyDispatchesReplicaRejectedByFullQueue` GREEN 通过，并覆盖启动时恢复丢失广播。
2. **Critical：托管根祖先 symlink 可重定向 IO。** 根因是原实现主要在 clone 后检查快照内部链接，没有固定检查 target/root/staging/backup/index/lock 的既有祖先。新增公共 `AppSourcePathGuard`：先解析配置根父级的受信真实锚点，再对配置根自身及其下路径逐段 `NOFOLLOW_LINKS` 校验，并在创建后、文件锁内和破坏性操作前复核；materializer、worker、opener/index 与 cleanup 共用。首轮 3 个行为测试在修复前分别证明物化会写出托管根、打开会在根外修复索引、清理会删除根外内容（3 tests / 3 failures）；自审再增加“配置根自身是 symlink、仓库目录尚未创建”用例，修复前同样断言失败。最终 4/4 GREEN 通过。macOS `/var -> /private/var` 只在配置根父级真实锚点解析阶段允许，不把系统链接误判为运行态可替换组件。
3. **Important：浅克隆未保证冻结 SHA 可取，Git 凭据生命周期不完整。** 根因是只依赖受理分支的 depth=1 tip，分支推进后旧冻结提交可能不可达；后续 checkout 也没有显式沿用临时 key。现在 clone 后使用同一临时 SSH 凭据显式 `fetch --depth 1 --filter=blob:none origin <frozenCommit>`，checkout、rev-parse 与浅仓库校验继续复用该凭据；sparse pattern 写入是本地 stdin 命令。行为 RED 捕获网络阶段只有 `clone:key, checkout:null`，期待 `clone:key, fetch:key, checkout:key`；GREEN 后三阶段凭据相同、分支推进后仍物化旧冻结内容，且物化普通文件不含私钥。
4. **Important：retry operation 与 server step 未精确绑定。** 根因是 worker 使用仓库级最新 operation，多个服务器/重试交错时可能完成错误操作；登记器也未阻止同 generation/server 的重叠非终态步骤。新增 repository/generation/server 到 `PENDING/RUNNING` operation step 的精确查询；worker 只写回该 operation，登记器在既有仓库行锁内拒绝重叠重试。RED 分别证明第二个同服务器重试未被拒绝、server-a worker 写回了 server-b 最新 operation（2 tests / 2 failures）；GREEN 后均通过。
5. **Minor：recent/clear 把瞬时故障当失效并绕过当前授权。** 根因是 recent 捕获任意 `PlatformException` 后删偏好，clear 直接删除。现在 recent 只对 `FORBIDDEN/NOT_FOUND/CONFLICT` 删除确定性失效偏好，Git/文件系统/`INTERNAL_ERROR` 原样返回并保留；clear 必须携带当前服务器并复用完整 open 鉴权，成功后才删除。RED 证明内部错误仍被吞掉和撤权后仍可 clear（2 tests / 2 failures）；GREEN 后两项通过，既有撤权 recent 自动清理测试也继续通过。

### Round 1 数据库与集成验证

- `MyBatisAppSourceRepositoryIntegrationTest`：H2 PostgreSQL 模式 11/11，覆盖 claimable replica 的本机/PENDING/过期及等于当前时刻的 RUNNING、异机与有效租约排除，以及精确 in-flight operation step 查询和 operation 终态排除。
- `MyBatisAppSourcePostgresqlIntegrationTest`：PostgreSQL 16 Testcontainers 1/1、0 skipped，生产 XML/方言覆盖上述 claimable 与 operation-step 查询，并保留行锁、cleanup 扫描/提前、双 owner 租约 fencing 与生产约束验证。
- `test-agent-workspace-management -am test`：通过；含 dispatcher 周期恢复、路径守卫、冻结提交凭据、operation 绑定和 recent/clear 全部回归。
- `AppSourceContextTest`：通过，使用生产 Spring bean 图验证 dispatcher 新 repository 依赖装配。
- `XxlJobMysqlMigrationTest`：MySQL 8.4 Testcontainers 3/3、0 skipped；本轮未变更 XXL migration，确认 Task 2 既有注册不回退。

### Round 1 影响评估

- API：未新增或变更 HTTP/事件 DTO；内部 `clearRecent` 业务方法增加当前服务器参数，Task 3 接口接入时必须传 route 解析后的目标服务器。
- 事件：广播结构不变，仍只是低敏唤醒；数据库周期扫描补齐广播丢失、队列拒绝与进程重启。
- 数据库：无 migration；新增 MyBatis XML 的 claimable replica 和 in-flight operation-step 只读查询，H2/PG 均验证。
- 性能：每实例默认 5 秒、64 条本机副本扫描，结果仍进入既有总容量有界且去重 dispatcher；索引范围限定服务器与状态/租约条件。
- 安全：SSH 私钥覆盖所有可能触网的固定提交 Git 阶段且不落盘；托管路径逐段拒绝运行态 symlink；recent 不再掩盖内部故障，clear 不再绕过授权。
- 兼容性：已有数据库结构、广播 payload 和外部 API 不变；新增 repository 方法与 dispatcher 构造依赖由 Spring 自动装配，旧数据可由补偿扫描直接恢复。
- 会话日志：按 Task 2 协作约束仍不更新 `.agents/session-log.*.md`，但提交前会重新回顾全部近期日志并把复审证据保存在本报告。

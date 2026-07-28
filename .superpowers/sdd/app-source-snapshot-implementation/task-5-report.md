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

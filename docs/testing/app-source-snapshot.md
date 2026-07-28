# 应用源码快照测试与验收

本文是应用源码固定提交快照从领域、数据库、多服务器物化、HTTP/进度 WebSocket 到工作台的综合验收入口。源码普通文件仍通过平台 Workspace 文件 WebSocket 读写；源码进度 WebSocket 是独立、只读的数据库观察通道，不属于 RunEvent、SSE 或 opencode raw event，也不新增 RunEvent wire name。

## 自动化回归

后端定向回归：

```bash
cd backend
mvn -q -DappLogDir=target/log -pl test-agent-app -am \
  -Dtest=AppSourceDomainTest,AppSourceOperationIdTest,ManagedWorkspacePathResolverTest,ConfigurationManagementApplicationServiceTest,MyBatisAppSourceRepositoryIntegrationTest,MyBatisAppSourcePostgresqlIntegrationTest,AppSourceApplicationServiceTest,AppSourceGitMaterializerTest,AppSourceWorkspaceAccessTest,AppSourceContextTest,AppSourceRetryRecoveryIntegrationTest,AppSourceControllerTest,AppSourceOperationControllerTest,AppSourceOperationTicketServiceTest,AppSourceOperationTicketStoreTest,AppSourceOperationWebSocketHandlerTest,AppSourceWebSocketOriginTest,AppSourceWebSocketConfigTest,AppSourceApiContextTest,XxlJobMysqlMigrationTest,PersistenceSqlConventionTest,FlywayMigrationNamingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

PostgreSQL 16 与 MySQL 8.4 Testcontainers 必须实际运行且为 `0 skipped`，才能作为生产 JSONB/部分唯一索引/延迟外键和 XXL V6 的验收证据。Docker 不可用导致的跳过只能记录为环境限制，不能视为数据库验收通过。

前端定向回归：

```bash
cd frontend
corepack pnpm exec vitest run \
  packages/backend-api/tests/app-source.test.ts \
  apps/agent-web/tests/AppSourcePicker.test.ts \
  apps/agent-web/tests/AppSourceDialog.test.ts \
  apps/agent-web/tests/app-source-workspace.test.ts \
  apps/agent-web/tests/FigmaEditorArea.test.ts \
  apps/agent-web/tests/agent-skill-hub.test.ts \
  apps/agent-web/tests/FigmaShell.test.ts \
  apps/agent-web/tests/WorkbenchFooter.test.ts \
  apps/agent-web/tests/figma-file-explorer.test.ts
corepack pnpm playwright test apps/agent-web/tests/workbench.spec.ts \
  --project=chromium --project=mobile \
  --grep "application source|source progress|recent source|late source repository|drifting lazy|stale socket epoch|exponential backoff|connecting progress socket|stale terminal apply|pending managed version|starting a managed version|DiffViewer save emit|entering source mode closes|failed socket epoch|source Run Diff save dispatch" \
  --reporter=line
```

最终交付还必须执行后端根测试，以及前端根 `test`、`typecheck`、`build`；定向命令不能替代全量验证。

## 自动化覆盖矩阵

| 维度 | 必须证明的事实 |
| --- | --- |
| 领域与幂等 | operationId 的 ECMAScript 空白规范化、1–128 长度和点段/路径分隔拒绝；保留期只接受 1–72 整小时且默认 48；状态、generation、lockVersion 和 lease 只前进。 |
| 数据库 | 七类表、JSONB 结构化路径、固定提交和 64 位十六进制索引摘要；GLOBAL/SERVER 步骤唯一；cleanup 允许作为业务事务第一写；运行 SQL 仅在 `AppSourceMapper.xml`，无新增 JDBC 或注解 SQL。 |
| 物化与恢复 | branch 与 tree 使用同一次固定 commit；浅克隆、冻结 SHA fetch、no-cone sparse checkout、删除 `.git`、原子替换与数据库写回失败回滚；广播丢失、队列拒绝、Java 重启、离线服务器恢复都由数据库扫描补偿。 |
| 生命周期 | PERSONAL 固定当前用户服务器，TEAM 冻结受理时在线集合；至少一台 READY 才提升，部分失败可打开 READY 服务器，全失败保留旧 active；同 generation 只重试 `FAILED/STALE` 且拒绝重叠非终态步骤；TEAM 不可降为 PERSONAL。 |
| 文件安全 | 根目录及祖先/目标符号链接 fail closed；`.testagent-appsource-index.json` 不出现在列表/搜索且所有外部读写操作拒绝；缺失或损坏索引按数据库摘要原子修复；文件 ticket 和每条 RPC 都重新校验成员、generation、expiry、READY replica 和服务器 affinity。 |
| API 与观察 | 普通成员、owner、APP_ADMIN 和撤权边界；tree 旧数组与 `includeCommit=true` envelope 兼容；ticket 一次性、60 秒、容量有界并绑定 operation/user/JVM/精确 Origin；重连首帧来自数据库 snapshot，断开观察不取消后台 operation，payload 不含物理路径、凭据或原始 Git stderr。 |
| 前端并发 | 明确 `MANAGED/APP_SOURCE`、全仓库四步选择、默认 48 小时、四态颜色与 owner；source selection/tree/progress 分别使用 authority/epoch，迟到请求和旧 socket 帧不能覆盖新选择；250ms 至 4s 有界退避，CONNECTING 可由 AbortSignal 释放。 |
| 能力与兼容 | 应用源码普通文件可写；APP_SOURCE Run Diff 的普通源码路径必须产生 Workspace 文件写，PUBLIC/WORKSPACE Agent 配置路径则在 DiffViewer `writable`、父组件 handler 和 mutation 门禁被阻止，并且必须产生零条 `agent-config.write`。Git、应用 Agent/Skill/Hub 发布、宠物配置重载和版本选择禁用；recent 的确定性失效清除与暂时错误保留；旧前端可忽略 additive 字段，旧 tree 方法保持数组。 |
| 清理 | XXL V6 恰好注册第八条每分钟 `workspace-management.app-source-cleanup`；每服务器数据库租约、generation fence 和文件锁阻止旧清理误删新副本；离线任务保留，成功后归档 Runtime Workspace，失败安全退避。 |

## 多服务器人工验收

1. 在两台 Linux 各启动至少一个 Java，共用 PostgreSQL、Redis 和 XXL MySQL，但分别挂载本机 `OPENCODE_APP_SOURCE_ROOT`；确认两台 Java 的稳定 `linuxServerId` 不同，目录均为普通物理目录且可读写。
2. 以普通应用成员选择 PERSONAL，移动远端分支后再提交旧 `expectedTreeCommit`，确认返回 `CONFLICT` 且磁盘、slot 和 operation 没有半成品；重新取 tree snapshot 后下载，只在当前用户 READY 进程服务器产生副本。
3. 以 APP_ADMIN 创建 TEAM 快照，在一台服务器制造 Git 暂时失败，确认另一台 READY 后 operation 为 `PARTIAL_FAILED`、成功服务器可打开、失败服务器不可打开；修复后使用同 generation retry，最终两台都 READY 且 commit/selection/expiry 不变。
4. 下载过程中关闭弹窗或断开 WebSocket，再重新打开操作；确认后台继续执行，新 ticket 首帧是最新数据库 snapshot，浏览器网络面板没有 RunEvent/SSE 请求因该操作新增。撤销成员权限后，已有观察和文件 socket 都必须安全失败。
5. 在源码 Workspace 修改一个普通文件并保存，确认文件 WebSocket 成功；打开 APP_SOURCE Run Diff，保存普通源码路径并确认产生对应的 Workspace 文件写；分别尝试保存 PUBLIC/WORKSPACE Agent 配置路径，确认 DiffViewer `writable`、父组件 handler 和 mutation 门禁阻止写入，且 `agent-config.write` 始终为零。另尝试读取或改写保留索引、打开 Git Changes、应用 Agent/Hub 发布和宠物配置重载，确认这些不允许的能力均被拒绝且磁盘无越权变化。
6. 把一台服务器停机至快照过期，确认在线服务器由每分钟 XXL 唤醒完成清理并归档 Workspace，离线服务器 cleanup 保持待处理；恢复该服务器后再次触发，确认旧源码、staging/backup 被清理，不删除随后建立的新 generation。
7. 在进度连接的 250ms/500ms/1s 退避窗口快速切换应用、仓库和托管工作区，确认旧 snapshot、step、terminal、tree 和 recent 响应都不能覆盖当前选择，且浏览器中不存在遗留 timer/socket。

## 数据、容量与日志检查

- PostgreSQL 中一个 repository 只有一个 active generation；非终态 operation/step 与 replica 对应一致，不存在无父 cleanup、终态步骤回退或过期 lease 写回。
- XXL MySQL 中 `xxl_job_info` 恰好八条；应用源码清理使用每分钟 Cron、`ROUND + DISCARD_LATER + DO_NOTHING + GLOBAL_MUTEX + retry=0` 和空 payload。
- 每台服务器核对 active 源码、同目录 staging/backup、锁文件和数据库 replica/cleanup；磁盘告警必须早于无法创建同体量 staging，cleanup backlog 持续增长时优先恢复 XXL/executor 和目标 Java，不手工递归删除未知路径。
- API、WebSocket、广播和日志只允许 operationId、repositoryId、generation、linuxServerId、traceId 与安全摘要；不得出现 SSH 私钥、Authorization、物理源码根、完整命令 stderr、文件正文或堆栈。
- 工作台操作不得产生新的 Run、Session、RunEvent 或用户级 runtime-state 事件；进度只由 AppSource 独立 WebSocket 传输，普通文件内容继续只走 Workspace 文件 WebSocket。

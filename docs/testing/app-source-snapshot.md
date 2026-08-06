# 应用源码快照测试与验收

本文是应用源码固定提交快照从领域、数据库、多服务器物化、HTTP/进度 WebSocket 到工作台的综合验收入口。源码普通文件仍通过平台 Workspace 文件 WebSocket 读写；源码进度 WebSocket 是独立、只读的数据库观察通道，不属于 RunEvent、SSE 或 opencode raw event，也不新增 RunEvent wire name。

## 自动化回归

后端定向回归：

```bash
cd backend
mvn -q -DappLogDir=target/log -pl test-agent-app -am \
  -Dtest=AppSourceDomainTest,AppSourceOperationIdTest,ManagedWorkspacePathResolverTest,ConfigurationManagementApplicationServiceTest,MyBatisAppSourceRepositoryIntegrationTest,MyBatisAppSourcePostgresqlIntegrationTest,AppSourceApplicationServiceTest,AppSourceGitMaterializerTest,AppSourceMaterializationRegistrarTest,AppSourceReplicaRetryRegistrarTest,AppSourceReplicaProgressRecorderTest,AppSourceReplicaResultRecorderTest,AppSourceReplicaWorkerTest,DefaultAppSourceReplicaTaskDispatcherTest,AppSourceWorkspaceAccessTest,AppSourceContextTest,AppSourceRetryRecoveryIntegrationTest,AppSourceReplicaConvergencePostgresqlIntegrationTest,AppSourceCrossApplicationProgressAuthorizationTest,AppSourceControllerTest,AppSourceOperationControllerTest,AppSourceOperationTicketServiceTest,AppSourceOperationTicketStoreTest,AppSourceOperationWebSocketHandlerTest,AppSourceWebSocketOriginTest,AppSourceWebSocketConfigTest,AppSourceApiContextTest,XxlJobMysqlMigrationTest,PersistenceSqlConventionTest,FlywayMigrationNamingTest \
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
| 领域与幂等 | operationId 的 ECMAScript 空白规范化、1–128 长度和点段/路径分隔拒绝；retry 重放仅匹配 route app/repository/actor/type/expected generation，不依赖动态失败服务器；materialization 重放在 slot 校验前严格匹配 app/repository/actor/type/hash/source generation，并返回首请求 SERVER steps/replicas 冻结目标；并发同 ID 只写一组 cleanup/snapshot/replica/steps。保留期只接受 1–168 整小时且默认 48；续期按 expected generation 串行更新快照、索引摘要和 cleanup 时间，清理已开始时拒绝；状态、generation、lockVersion 和 lease 只前进。 |
| 数据库 | 七类表、JSONB 结构化路径、固定提交和 64 位十六进制索引摘要；GLOBAL/SERVER 步骤唯一；副本 claim 单条 UPDATE 原子绑定精确 operationId、operation 非终态和同服务器可领取步骤，PG 并发屏障证明读取后 retry 终态化会拒绝迟到 claim 且副本不变；过期 `RUNNING` 旧 attempt 可在 operation 未终态时接管并统一 reset；活租约步骤更新/reset、旧步骤领取、DOWNLOAD/UPDATE stranded 兼容恢复与 RETRY SERVER steps 全终态门禁、status 前导索引；cleanup 允许作为业务事务第一写；运行 SQL 仅在 `AppSourceMapper.xml`，无新增 JDBC 或注解 SQL。 |
| 物化与恢复 | 每服务器固定 13 步，动作前 RUNNING、动作后 SUCCEEDED，失败当前步 FAILED/后续 SKIPPED且摘要低敏；新 lease 清除旧 attempt 时间/终态，旧 owner 失租后零后续写；branch 与 tree 使用同一次固定 commit；浅克隆、冻结 SHA fetch、no-cone sparse checkout、删除 `.git`、原子替换与数据库 completion 前失败回滚，completion 后 backup 清理失败仍保留新 target 并成功返回；旧 cleanup 在新 READY generation 接管共享根后仍于同一文件锁内回收标准 UUID backup 残留，重复更新不累积且不触碰 target/staging/其它仓库、非 UUID 或相似后缀；广播丢失、队列拒绝、Java 重启、普通 stranded 和离线 retry 目标恢复都由数据库扫描补偿，离线目标步骤终态前不得提前收敛。 |
| 生命周期 | PERSONAL 固定当前用户服务器，TEAM 冻结受理时在线集合；至少一台 READY 才提升，旧 ACTIVE 先失效再激活新代；三服务器最后两台并发以 slot 行锁串行收敛，部分失败可打开 READY 服务器，全失败保留旧 active/expiry；同 generation 只重试 `FAILED/STALE`；TEAM 不可降为 PERSONAL。 |
| 文件安全 | 根目录及祖先/目标符号链接 fail closed；`.testagent-appsource-index.json` 不出现在列表/搜索且所有外部读写操作拒绝；缺失或损坏索引按数据库摘要原子修复；文件 ticket 和每条 RPC 都重新校验成员、generation、expiry、READY replica 和服务器 affinity。 |
| API 与观察 | TEAM 按 repository 任一当前启用关联应用成员跨 app 观察，PERSONAL 保留 owner/成员管理员边界，GET/ticket/WS 每次实时复核撤权/解除关联/禁用；tree 旧数组与 `includeCommit=true` envelope 兼容；列表 additive 返回 `acceptedAt/maxRetentionHours`，PATCH retention 使用 generation 乐观校验；ticket 一次性、60 秒、容量有界并绑定 operation/user/JVM/精确 Origin；重连首帧来自数据库 snapshot，断开观察不取消后台 operation，payload 不含物理路径、凭据或原始 Git stderr；WebSocket wire 不变。 |
| 前端并发与大树 | 明确 `MANAGED/APP_SOURCE`、全仓库四步选择、默认 48 小时、服务端 168 小时上限、当前 generation 直接调整保留期、四态颜色与 owner；未过期 generation 的续期用例必须固定系统时间，不能让写死的到期日随真实日期推进而漂移；分支选择支持输入检索和虚拟滚动，Teleported 下拉层高于源码弹框遮罩，输入后可用回车选中首个匹配项，分支响应不等待目录树才解除 loading；远端树只渲染已展开层，1000 个折叠后代不进入 DOM，目录勾选压缩为单个 `DIRECTORY` exact path；source selection/tree/progress 分别使用 authority/epoch，迟到请求和旧 socket 帧不能覆盖新选择；250ms 至 4s 有界退避，CONNECTING 可由 AbortSignal 释放。 |
| 能力与兼容 | 应用源码普通文件可写；APP_SOURCE Run Diff 的普通源码路径必须产生 Workspace 文件写，PUBLIC/WORKSPACE Agent 配置路径则在 DiffViewer `writable`、父组件 handler 和 mutation 门禁被阻止，并且必须产生零条 `agent-config.write`。Git、应用 Agent/Skill/Hub 发布、宠物配置重载和版本选择禁用；recent 的确定性失效清除与暂时错误保留；旧前端可忽略 additive 字段，旧 tree 方法保持数组。 |
| 清理 | XXL V6 恰好注册第八条每分钟 `workspace-management.app-source-cleanup`；每服务器数据库租约、generation fence 和文件锁阻止旧清理误删新副本；离线任务保留，成功后归档 Runtime Workspace，失败安全退避。 |

## 多服务器人工验收

1. 在两台 Linux 各启动至少一个 Java，共用 PostgreSQL、Redis 和 XXL MySQL，但分别挂载本机 `OPENCODE_APP_SOURCE_ROOT`；确认两台 Java 的稳定 `linuxServerId` 不同，目录均为普通物理目录且可读写。
2. 以普通应用成员选择 PERSONAL，移动远端分支后再提交旧 `expectedTreeCommit`，确认返回 `CONFLICT` 且磁盘、slot 和 operation 没有半成品；重新取 tree snapshot 后下载，只在当前用户 READY 进程服务器产生副本。
3. 以 APP_ADMIN 创建 TEAM 快照，在两台服务器制造失败后对同 generation retry；先只恢复其中一台，让另一台保持离线并跨过多个 5 秒扫描周期，确认 operation 仍非终态、离线 SERVER steps 仍 PENDING。再恢复离线服务器，确认其可 claim 且最终两台 READY，commit/selection/expiry 不变；同时重复提交相同 materialization operationId，确认返回首请求 operation/generation/冻结目标且数据库没有第二组写入。
4. 下载过程中关闭弹窗或断开 WebSocket，再重新打开操作；确认后台继续执行，新 ticket 首帧是最新数据库 snapshot，浏览器网络面板没有 RunEvent/SSE 请求因该操作新增。撤销成员权限后，已有观察和文件 socket 都必须安全失败。
5. 在源码 Workspace 修改一个普通文件并保存，确认文件 WebSocket 成功；打开 APP_SOURCE Run Diff，保存普通源码路径并确认产生对应的 Workspace 文件写；分别尝试保存 PUBLIC/WORKSPACE Agent 配置路径，确认 DiffViewer `writable`、父组件 handler 和 mutation 门禁阻止写入，且 `agent-config.write` 始终为零。另尝试读取或改写保留索引、打开 Git Changes、应用 Agent/Hub 发布和宠物配置重载，确认这些不允许的能力均被拒绝且磁盘无越权变化。
6. 把一台服务器停机至快照过期，确认在线服务器由每分钟 XXL 唤醒完成清理并归档 Workspace，离线服务器 cleanup 保持待处理；恢复该服务器后再次触发，确认旧源码、generation staging 和多次更新遗留的标准 UUID backup 均被清理，不删除随后建立的新 generation、当前 target、其它仓库目录、非 UUID `.backup` 或 `.backup.tmp` 等相似名称。
7. 在进度连接的 250ms/500ms/1s 退避窗口快速切换应用、仓库和托管工作区，确认旧 snapshot、step、terminal、tree 和 recent 响应都不能覆盖当前选择，且浏览器中不存在遗留 timer/socket。
8. 使用至少 500 条分支和包含 1000 个以上后代节点的代码库，确认分支框可输入子串检索、下拉选项不被源码弹框遮挡，并可在输入后用回车选中首个匹配分支、触发对应目录树刷新；分支返回后即使根目录请求仍在进行，分支框也保持可用。根目录初始只显示当前层，勾选父目录后后代显示为已包含，提交 payload 只含该父目录一条 `DIRECTORY` 路径，连续勾选不出现明显卡顿。

## 分支成功但目录超时排查

1. 在浏览器网络面板区分 `/branches` 与 `/tree?includeCommit=true`：前者 `200` 且后者超时，不能再按“分支加载失败”处理。记录页面目录错误中的 traceId。
2. 确认请求最终路由到当前用户绑定服务器；在该 Java 日志按 traceId 查 `git_command_start/success/slow/failed/timeout/unavailable`。目录快照应先出现一次 `ls-remote`，再出现一次 `archive --remote`。
3. 使用 Java 运行用户和当前用户同一 SSH 凭据做只读验证：`git ls-remote --heads <有效URL>` 检查引用权限，`git archive --format=tar --remote=<有效URL> <分支或提交> >/dev/null` 检查 archive 能力和传输耗时。禁止把私钥、完整 stderr 或带 Token URL 粘贴到工单。
4. 分支命令失败时优先检查 SSH Key、内部模式有效 URL 中的统一认证号、DNS/端口和代码库权限；只有 archive 失败时检查远端是否允许 `git-upload-archive`、仓库体量和链路吞吐。浏览器局部等待已覆盖 70/130 秒，企业 Nginx `/api` 为 300 秒，修改全局超时不是首选处置。

## 数据、容量与日志检查

- PostgreSQL 中一个 repository 只有一个 active generation；非终态 operation/step 与 replica 对应一致，不存在无父 cleanup、终态步骤回退或过期 lease 写回。
- XXL MySQL 中 `xxl_job_info` 恰好九条；应用源码清理使用每分钟 Cron，个人工作区搬迁使用每 30 分钟 Cron，两者都保持 `ROUND + DISCARD_LATER + DO_NOTHING + GLOBAL_MUTEX + retry=0` 和空 payload。
- 每台服务器核对 active 源码、同目录 staging/backup、锁文件和数据库 replica/cleanup；磁盘告警必须早于无法创建同体量 staging，cleanup backlog 持续增长时优先恢复 XXL/executor 和目标 Java，不手工递归删除未知路径。
- API、WebSocket、广播和日志只允许 operationId、repositoryId、generation、linuxServerId、traceId 与安全摘要；不得出现 SSH 私钥、Authorization、物理源码根、完整命令 stderr、文件正文或堆栈。
- 工作台操作不得产生新的 Run、Session、RunEvent 或用户级 runtime-state 事件；进度只由 AppSource 独立 WebSocket 传输，普通文件内容继续只走 Workspace 文件 WebSocket。

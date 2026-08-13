# AI 编码约定

本文档是 Codex 和 Claude 修改本项目时必须先阅读的入口规范。任何任务都必须先理解相关文档和代码边界，再做最小范围修改。

## 必读顺序

1. 先读 `docs/README.md`，确认任务类型对应的规范文档。
2. 研发流程先读 `docs/guides/ai-workflow.md` 和 `docs/guides/self-checklist.md`。
3. 后端任务读 `backend/README.md`、目标模块 `README.md`，再读 `docs/standards/backend.md` 和 `docs/architecture/dependency-rules.md`。
4. 前端任务读 `frontend/README.md`、目标 app/package `README.md`，再读 `docs/standards/frontend.md`。
5. 找功能定位代码读 `docs/architecture/module-map.md`。
6. API、事件、数据库、安全、部署相关任务必须读 `docs/api/`、`docs/deployment/`、`docs/standards/security.md` 对应文档。

根目录 `requirements/` 下的历史设计、需求草案和阶段计划**不作为编码依据**，仅供追溯。

## 长期分支策略

1. `dev` 是大功能和部署演进分支。凡是新增部署节点、服务、进程、容器、中间件或强制环境变量，改变端口、启动顺序、网络拓扑、安装升级/回滚流程，或者需要多模块协同升级的功能，必须先进入 `dev`；边界不清时默认进入 `dev`。
2. `release` 是当前可部署拓扑上的交付维护分支，只接受 Bug 修复和可在现有节点、服务、依赖、配置与升级路径内完成的小功能。修复现有部署脚本可以进入 `release`，但不得借修复之名引入新节点、新运行服务或新的强制部署依赖。
3. `dev` 功能只有在明确批准进入交付范围，并补齐部署、升级、回滚、兼容性和真实环境验证后，才可按功能选择性合入 `release`；禁止为图省事把包含未交付大功能的整个 `dev` 合入 `release`。
4. `release` 上完成的 Bug 修复和小功能必须同步回 `dev`，避免后续大版本回归已修问题。涉及同一代码的双分支提交应先完成 `release`，再合并或 cherry-pick 到 `dev` 并解决差异。
5. `main` 保持长期稳定基线，不作为日常大功能开发入口；其更新按明确的基线发布决策执行。
6. 开始修改前必须先声明目标分支并按 `docs/guides/ai-workflow.md` 判断交付影响；提交前按 `docs/guides/self-checklist.md` 复核没有跨越分支边界。

## OpenCode 源码边界

本项目严格禁止直接修改 OpenCode 源码。`opencode-source/opencode-1.18.4/` 只读用于源码审计、行为对照和 OpenAPI 兼容性分析，不得提交其中的源码、测试、配置、构建脚本、资源或临时补丁；平台适配必须放在本项目的后端、前端、worker 启动器或受控配置层。OpenCode 升级只能按 `docs/deployment/opencode-upgrade-1.18.4.md` 获取干净上游快照，不能在快照上本地修补。详细边界见 `docs/standards/opencode.md`。

## 强制规则

1. 只改与任务直接相关的最小范围，不允许顺手重构无关代码。
2. 修改前必须先分析需要改的代码、文档和测试位置。
3. 修改后必须同步更新稳定文档，包括工程 README、模块/包 README、API 文档和测试说明。
4. 人工维护代码的新增或复杂修改必须有中文注释；generated SDK 不手工补注释。
5. API 必须有文档，新增或变更 API 必须同步更新 `docs/api/http-api.md` 和 `docs/api/event-stream.md`。
6. Controller 不得直接调用 generated SDK 或 Repository。
7. 业务层不得依赖 `test-agent-opencode-sdk-generated`。
8. 前端不得直连 opencode server，必须通过 `backend-api` 调用 `test-agent-app`，实时事件必须通过 RunEvent SSE。
9. 工作区文件和 Agent 配置文件的目录列表、读取、写入必须走平台文件 WebSocket route/ticket/RPC 模式；跨服务器文件操作不得新增后端到后端 HTTP 文件代理。
10. 涉及 opencode-manager 路由、Java 到 manager 控制、用户 opencode 进程服务器归属、运行管理 containerId 路由、Agent 配置或文件 WebSocket 目标后端选择的新增/修改，必须复用公共路由程序：`BackendJavaRouteResolver` 负责目标 Java 选择，`BackendHttpForwarder` 负责 Java->Java HTTP 转发，目标 Java 再通过 `OpencodeProcessManagerGateway` 控制本服务器 manager；禁止自行扫描 Redis 快照、手写转发器、防循环 header 变体、本机降级或本地绕过。
11. 涉及 opencode server 启动、重启后拉起、端口复用或启动成功状态回写的新增/修改，必须调用 `OpencodeProcessStartupService` 这一公共启动程序；禁止在业务入口直接调用 `OpencodeProcessManagerGateway.startProcess()` 后自行写入进程、binding、heartbeat 或 `ExecutionNode`，启动成功必须以公共程序完成 manager state/PID 与 opencode HTTP health 检查为准。涉及 opencode server 停止、停止后状态回写或运行管理停止命令的新增/修改，必须调用 `OpencodeProcessStopService` 这一公共停止程序；禁止在业务入口直接调用 `OpencodeProcessManagerGateway.stopProcess()` 后自行判定停止成功或写入进程状态，平台已记录进程的停止成功必须以公共程序完成 manager stop 和停止后 health 不健康确认为准。涉及 opencode server 状态查询、健康探测、状态回写或 Redis heartbeat 刷新的新增/修改，必须调用 `OpencodeProcessStatusQueryService`；禁止业务入口直接调用 `OpencodeProcessManagerGateway.checkHealth()` 后自行映射 `RUNNING/STOPPED/UNHEALTHY/FAILED`。进程身份中的 `startedAt` 必须以 manager 实际创建该进程时记录的时间为唯一权威值；Java 接收 `start/restart` 回包后的本机时间只表示“观察时间”，禁止作为进程启动时间持久化或参与身份匹配。manager 回包暂不携带启动时间时，公共启动程序必须按 PID 查询并取得 manager state 的启动时间后再写入，不能用 `Instant.now()` 补造。
12. generated SDK 不能手改，只能通过 `tools/generate-opencode-java-sdk.sh` 重新生成后同步。
13. 数据库结构变更必须有 Flyway migration，不能只改实体或 Repository，并同步 `docs/deployment/database.md`。
14. Flyway migration 只能承载表结构变更、兼容性数据迁移和生产必需的基础字典/系统参数，禁止写入测试、演示或个人开发数据；此类数据必须放在测试 fixture、`test-agent-test-support` 或显式本地开发脚本中，不能随生产 migration 发布。
15. API、DTO、事件类型、数据库字段变更必须考虑向后兼容。
16. 鉴权、限流、日志脱敏和密钥管理必须按 `docs/standards/security.md` 执行。
17. 错误必须统一格式返回，不能把任意异常直接抛给前端。
18. 关键流程必须携带或生成 traceId，禁止用 `System.out.println` 作为正式日志。
19. 完成前必须按 `docs/guides/self-checklist.md` 自检。
20. 后端新增文件前必须先按 `docs/architecture/module-map.md` 和 `docs/architecture/dependency-rules.md` 分析是否已有合适工程；没有合适工程时，按业务边界新建 Maven module 后再落文件。模块 README 即包级说明。
21. **未经用户明确要求，不得修改 `.env.local` 等环境配置文件**。此类文件包含敏感的数据库连接、API 密钥等，仅在用户明确指示时方可修改。
22. 每次会话收尾时，如果本次出现了值得保留给后续开发者/智能体的新增信息（例如新的坑、验证结论、外部状态变化或明确决策），按本机提交者身份写入对应的 `.agents/session-log.{id}.md`，用 `Why / What / How / Result` 说明本次变更；同一会话内的零散小改动合并为一条，不要按文件或命令频繁记账。`{id}` 取本仓库 `git config user.name`，转小写、连续非 `[a-z0-9]` 字符折叠为单个 `-` 并去首尾 `-`，结果为空时回退 `hostname -s` 同样清洗（如 `huangzhenren`）。每位提交者只写自己的文件，不再向已冻结的共享 `.agents/session-log.md` 追加，避免多人共写单文件冲突。这些文件属于仓库内容，应随本次 git 提交一起保留，必要时可与其他改动一并推送远程。
23. 每次提交代码前，必须先回顾所有 `.agents/session-log*.md`（含已冻结的 `.agents/session-log.md` 旧档和各 `.agents/session-log.{id}.md`）中近期条目记录的变更、坑点和未完成事项，确认本次暂存内容不会覆盖、丢弃或误合并其他开发者/智能体已经提交的成果；如发现冲突或残留合并标记，必须先处理或明确说明风险。
24. 后续新增或修改关系型数据库 SQL 必须通过 MyBatis XML mapper 实现；存量 `Jdbc*Repository` 仅作为迁移窗口保留，不得继续新增 JDBC SQL。Redis、Flyway migration 和 Druid 连接池不受此条限制。
25. V18 之后新增 Flyway migration 统一使用 `VyyyyMMddHHmmss__table_name_description.sql`：版本取创建时的本地时间戳，双下划线后先写实际表名，再写简短描述；涉及多张表时，按 SQL 实际变更顺序取第一张表。当前开发协作没有共享数据库、中央 migration 编号服务或自动合并门禁，每个开发者使用需要保留的个人本地数据库，因此文件时间戳只能区分开发期候选，不能协调并行分支的最终执行顺序。多人或多分支合并到交付分支时，集成人必须收集所有待合并 migration 和相关个人本地库、稳定库、企业库的 `flyway_schema_history` 已执行版本/checksum：只有从未在任何需要保留的数据库执行的 migration 才能重命名或重排，并确保最终版本严格递增且高于已部署基线；一旦在个人本地持久库、共享库、稳定库或企业库执行，即使文件名不符合新规则，或只改注释、空白、改成 `IF NOT EXISTS`，也必须保留原始版本、文件名和字节并用 SHA-256/测试锁定。若已执行的并行历史无法直接组成同一严格递增主链，必须复用现有 Flyway 兼容装配，以隔离 compatibility location 和更高版本前向 migration 显式解析每套历史；只有数据库所有者明确同意废弃并重建该个人库后，才可将其中历史按“未执行”处理。不得新建第二套迁移器。打包前必须用真实 PostgreSQL 覆盖每套已知历史的“已部署基线 → 当前 HEAD”升级，并校验最终 JAR 内 migration 字节与已测试源码一致；只测空库不算通过。发现未知 checksum、版本倒序或环境分叉必须停止发布并制定显式兼容方案，禁止用 Flyway `outOfOrder`、`repair` 或手工修改历史表掩盖问题。

## 完成标准

每次修改结束前，AI 必须说明：

- 修改了哪些代码或文档。
- 执行了哪些测试或校验命令。
- 哪些文档已同步更新。
- 是否涉及 API、事件、数据库、性能、安全或兼容性。
- 是否存在未完成事项或风险。
- 是否已按需更新本机对应的 `.agents/session-log.{id}.md` 并纳入本次提交（不再追加到已冻结的 `.agents/session-log.md`）。
- 不要随意新建git分支，除非我明确的告诉你需要新建。完成后必须自动提交 git 且 commit 信息用中文。

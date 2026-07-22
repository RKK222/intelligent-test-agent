# Session Log - huangzhenren

> 按提交者 `git config user.name` 分文件维护，新增条目置于 `## Entries` 顶部。
> 提交前需回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md`）的近期条目。

## Entries

### 2026-07-22 - 新增企业 XXL-JOB 只读排查手册

- Why:
  - 企业双后台 XXL-JOB 管理页、SSO、Admin、executor、Redis 与共享 MySQL 缺少可直接复制到现场执行、能明确停止边界并避免主动重放或状态变更的统一排查入口。
- What:
  - 固化前端 `122.233.30.2`、后台 `122.233.30.4/122.233.30.114`、Redis `122.233.30.20`、MySQL `122.233.30.148` 拓扑，交付入口、前端 Nginx、后台 Java/Admin/executor 三个可独立携带的自包含 Bash 诊断脚本。
  - 新增受静态边界校验的 MySQL 只读检查 SQL、十五章企业排查手册、文档索引与临时夹具 verifier；三个现场脚本保留少量重复，以保证单文件复制后无需依赖共享库即可执行。
- How:
  - 诊断严格限制为 DNS/HTTP readiness、有效 Nginx 配置读取、systemd/端口/进程/日志、TCP 可达与只读数据库查询；禁止 SSO 重放、Redis 票据或会话读取、任务触发、服务或容器生命周期变更、配置写入和 SQL DML。
  - URL query/fragment、认证头和常见敏感键在输出前统一脱敏；本条不记录任何现场凭据值或摘要。verifier 只使用临时 fake 命令、配置、日志与 SQL 夹具，不访问五个企业地址。
- Result:
  - 三个诊断脚本与 verifier 的 Bash 语法检查、完整行为 verifier、AI 文档校验均退出 `0`；危险操作、SQL 写操作与冲突标记扫描均无匹配，任务路径及全工作树 `git diff --check` 均退出 `0`。
  - 未修改运行时代码、HTTP API、RunEvent、数据库结构/Flyway、环境配置或 generated SDK；尚未在企业五台目标机器执行，现场网络、进程和数据状态仍需按手册由授权人员只读取证。

### 2026-07-22 - 升级 OpenCode 1.18.4 官方 baseline 与 Java SDK

- Why:
  - 项目运行时、源码审计快照和 generated Java SDK 仍基于 OpenCode 1.17.8；用户要求升级到最新稳定版、重新使用最新 OpenAPI Generator 校验影响，并明确企业 Linux 程序必须直接使用官方 `opencode-linux-x64-baseline.tar.gz`，不能从源码构建。
- What:
  - 将审计源码快照、plugin/SDK 依赖及本机 OpenCode 更新到 1.18.4；企业 worker 下载并校验官方 baseline 归档/二进制 SHA，启动器只执行官方程序，源码不参与二进制构建。保留 1.17.8 官方 baseline 回滚 image/programs。
  - OpenAPI Generator 固定为最新稳定版 7.24.0，重新同步 generator 工程和后端 generated SDK；规范由 150/339/175 个 paths/schemas/operations 增至 162/472/188，新增 13 个 operation、无删除，平台消费的 39 个 operation 契约未破坏。生成脚本统一清理生成器输出的行尾空格，generated 源码不手改。
  - 1.18.2+ 启动配置固定 `subagent_depth=2`，后端和前端补齐 root → child → grandchild 的 scope 归属与状态隔离；1.17.8 回滚启动器会删除旧版不支持的字段。离线运行时继续固定依赖、禁用自动安装，并保留 `includeUsage=false` 兼容要求。
  - 重写企业 worker/package 链路以固定官方 asset、大小、归档/程序 SHA、glibc 2.31、静态 tini/ripgrep 和 Node/Go 基础镜像；完整包同时携带 backend、frontend、programs、worker image，未操作企业服务器。
- How:
  - 对比 1.17.8 与 1.18.4 官方 `/doc`、release asset 和源码 tag `49c69c5ed3ccf706b61b3febb43c8aaff7f8325e`；官方 1.18.4 baseline 归档 SHA-256 为 `4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc`，程序 SHA-256 为 `6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5`。
  - 当前与回滚 worker 均通过断网 serve/health、Tool 链接、RELEASE 元数据、深度配置和优雅停止冒烟；launcher 5/5、前端嵌套 scope 21/21、升级相关后端 Linux JDK 定向 28/28 通过。此前健康本机 JDK 下 client/runtime reactor 为 773 项全通过。
  - 前端 lint、typecheck、生产 build 通过；全量 Vitest 为 1542 passed / 1 skipped / 1 failed，唯一失败仍是既有 `DirectoryRows.test.ts` 把 role=`radio` 的“上传”按 role=`button` 查询。Linux/musl 全后端运行通过 runtime 前各模块和 runtime 702/706，3 项仅因 glibc PTY/`/bin/bash` 不存在失败，1 项既有 1 秒重试断言单独复跑通过；本机 JDK 的 `libattach.dylib`/`libinstrument.dylib` 代码签名异常不属于本次代码。
- Result:
  - 完整离线包为 `deploy/internal/dist-1.18.4/test-agent-internal-release.zip`，SHA-256 `1f7baecd9877aedc82ab45e167975f10680ccb6aeeffb08142e6451af1da98e1`；1.17.8 回滚 image tar SHA-256 为 `a2fdfc588f2d3166cc26f8f9fd61daa9e937487ec93c037cf8cd8841f9c5cf8d`。两者只在本机生成，未部署企业节点。
  - 已同步 OpenCode client/runtime/generated SDK、前端、内部部署、HTTP/SSE 索引、模块图与测试文档；平台 HTTP API、RunEvent wire、数据库/Flyway、关系型 SQL、鉴权和安全边界未变，没有修改 `.env.local`。本机外部 OpenCode 配置只补充兼容深度并保留原内容。
- Next:
  - 企业现场按文档同时替换 image/programs 并滚动重启用户 OpenCode 进程；如需恢复两个全量测试套件全绿，应另行修复既有 `DirectoryRows` 断言，并重装/修复本机 JDK 签名或在 glibc Linux JDK 环境执行后端全量测试。

### 2026-07-22 - 优化权限请求展示与待处理提醒

- Why:
  - 平台把 OpenCode `permission.asked` 只显示为 `external_directory + permission id`，遗漏原生 `patterns[]` 路径与中文说明；运行态摘要和历史铃铛又只识别 question，child Agent 待授权时无法定位。
- What:
  - 前端新增共享 permission 展示转换，对齐 OpenCode 1.17.8 的中文标题/14 类说明，优先保留去重后的 `patterns[]` 并兼容旧 `pattern/title/description`；Figma 卡和 RuntimeDock 统一警告图标、代码路径及“拒绝 / 始终允许 / 允许一次”，不展示内部 type/requestId。
  - `PermissionRequest.patterns`、运行态 `permissionCount` 和 `PERMISSION` attention 以可选/开放字符串方式向后兼容；历史入口统计 question+permission，任意 attention 的会话卡显示铃铛，pending permission 只在 sessionId 精确匹配的 child task 状态前显示可访问动态 Bell，回复后 reducer 清除。
  - 历史恢复把根 permission HTTP 快照限制在 root scope，保留 session tree child 请求；`run.snapshot.reset` 递归投影 root 交互的远端 Session ID 并保留 child scope。
  - 后端 Redis/legacy MyBatis 摘要支持 permission asked/replied。Redis 用类型化 SHA-256 key 的独立 Hash + order ZSET 保存最多 1024 个未决交互，字节计入 32 MiB/非快照预算，同 ID question/permission 独立、回复最新项后恢复更早项、终态清空；legacy SQL 按 Run seq 收敛，H2 与 PostgreSQL jsonb 均兼容真实 `sessionID -> requestID` 顺序且不误取嵌套 id。
- How:
  - TDD 覆盖用户提供的真实 external_directory 事件、多/旧 pattern、未知类型、三个动作、历史 root/child 恢复、并行 child 精确铃铛、旧摘要兼容、SSE 刷新、Redis 同/跨类型并发与容量、H2/真实 PostgreSQL 时钟回拨和请求 ID 提取；多轮只读代码审查发现的 legacy 无 ID、reset 投影、并发 attention、容量与 seq/JSON 方言边界均已补回归。
- Result:
  - 前端目标 Vitest 431 passed / 1 skipped，mock Playwright 桌面/移动 6 passed，lint、typecheck、生产 build 通过；全量 Vitest 的唯一失败仍是既有 `DirectoryRows.test.ts` 用 role=`button` 查询实际 role=`radio` 的“上传”，相关文件未修改。
  - 后端真实 Redis 9 项、H2 legacy 4 项、真实 PostgreSQL jsonb/Flyway 1 项及 domain/runtime/API 定向测试通过；20 模块 `mvn clean package -DskipTests` 成功。全量 `mvn test` 仍仅被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 H2 `TIMESTAMPTZ` setup 基线阻断，真实 PostgreSQL 完整 Flyway 链已通过。
  - 已同步 HTTP/SSE、模块图、测试场景和前后端模块 README/PACKAGE；新增字段均向后兼容，不新增 RunEvent wire name、reply 协议、数据库结构/Flyway、generated SDK、环境配置或鉴权变化。物理路径仅在已授权交互卡显示，不进入通知/日志；无未完成编码项。
- Next:
  - 单独修复既有 H2 migration 与 `DirectoryRows` 基线后可恢复两个全量套件全绿，本功能无需追加迁移。

### 2026-07-22 - 修复主子智能体切换滚动位置

- Why:
  - 主、子 Agent 共用 `.figma-chat-scroll`；切换到较短的子时间线时浏览器会压缩共享 `scrollTop`，返回主 Agent 后因此落到首条消息。
- What:
  - `FigmaChatPanel` 按 `root` / `subagent:<sessionId>` 保存组件内存快照，分别记录滚动位置、离开时是否在底部、固定大小的可见正文摘要和新内容提示状态；首次进入或离开时在底部均跟随最新底部，上滑视图恢复原位置。
  - 正文摘要只聚合 `opencodeTimelineState` 当前 scope 可见的消息身份、正文/text part 和流式可见长度，并缓存未变消息摘要；反馈、工具状态和其它子 Agent 输出不会推动当前视口。
  - 延迟滚动增加 scope、代次、viewport 归属和 pending restore 防护；wheel、touch、pointer 用户意图可接管恢复，布局 clamp、快速往返、根终态/history watcher 和跨 scope 残留意图不会覆盖目标快照。
  - 同步 `frontend/README.md`、`frontend/apps/agent-web/README.md`、`frontend/apps/agent-web/src/PACKAGE.md`，并新增独立滚动回归文件，避免混入同一工作树中并行的 permission/attention 测试改动。
- How:
  - TDD 覆盖主/子位置独立恢复、首次滚底、inactive scope 新正文、其它子 Agent 隔离、快速切换、真实会话重置、草稿首次取得 ID、浏览器 clamp、root/child pending restore、用户主动接管和跨 scope 用户意图清理，共 20 项；多轮只读任务审查最终无 Critical/Important finding。
  - `corepack pnpm test --run apps/agent-web/tests/FigmaChatPanel.test.ts` 为 134 passed / 1 skipped；滚动专项为 20 passed；`corepack pnpm lint`、`corepack pnpm typecheck`、agent-web 生产 build 和 `git diff --check` 通过。
- Result:
  - 主、子 Agent 在当前页面生命周期内各自保留阅读位置，后台并行输出和过期延迟回调不再改变当前视图；快照不持久化。
  - 前端全量 Vitest 为 1527 passed / 1 skipped / 1 failed；唯一失败是任务外既有 `DirectoryRows.test.ts` 将 role=`radio` 的“上传”按 role=`button` 查询，单独复跑稳定失败，相关文件本次无改动且近期 session log 已有记录。
  - 未修改公共组件接口、CSS、HTTP API、RunEvent、数据库、后端、安全、环境配置或 generated SDK；无新增依赖。
- Next:
  - 单独修复 `DirectoryRows.test.ts` 的可访问角色断言后恢复前端全量绿灯；本滚动功能无未完成实现项。

### 2026-07-21 - 引用配置自动写入外部目录权限

- Why:
  - 引用路径位于工作区外部时，OpenCode 会发出 `external_directory` 权限询问；仅写 `references` 会让已选引用仍被全局 `ask` 兜底拦截。
- What:
  - 引用 JSONC 补丁在同一正文中同时维护当前 alias 与 `permission.external_directory["{path}/*"] = "allow"`，同路径 `ask/deny` 强制覆盖，并确保精确 allow 位于最后一条匹配规则；合法的根权限和外部目录字符串简写展开后保留原 `*` 兜底，非法结构用中文校验错误整次拒绝。
  - 弹窗新增权限漂移状态：存量引用的权限缺失、冲突或被后置宽泛规则覆盖时，无需修改描述即可更新；保存仍只重读一次最新正文、生成一次补丁并写盘一次，成功后清除漂移状态。
  - 同步前端/agent-web README、组件 PACKAGE、引用配置用户手册和应用 worktree 测试说明；授权范围固定为当前所选根层 SDD 目录，不扫描其它引用、不写仓库级或全局 `* allow`。
- How:
  - 复用 `jsonc-parser` 的 JSONC 字段编辑与语法树，只在规则重排时移除精确属性正文并保留周围注释；TDD 覆盖空文件、新增/更新、简写、宽泛规则、同路径冲突、非法结构、CRLF、注释/未知字段、幂等、按钮漂移和并发保存 fencing。
- Result:
  - 两个目标测试文件 84 项通过；前端 typecheck、lint、用户手册及生产 build、`git diff --check` 通过。前端全量 Vitest 为 1484 passed / 1 skipped / 1 failed；唯一失败仍是既有 `DirectoryRows` 用 `button` 查询实际 `radio` 角色的“上传”，本轮开始前已复现且相关文件未修改。
  - 不涉及后端、HTTP API、RunEvent/SSE、数据库/SQL、generated SDK、环境配置、性能或鉴权模型；权限变化仅写入当前个人 worktree 的 OpenCode JSONC。工作树中并行的 manager/后端日志功能改动和既有 `.config` 删除未纳入本次范围。

### 2026-07-21 - 按用户和启动实例拆分 OpenCode 进程日志

- Why:
  - 用户 opencode server 日志原来只按端口写入 `{port}.log`；端口会变化、复用，同一端口的多次启动也会混写，难以按用户定位一次具体启动。
- What:
  - Java 公共启动链路新增可选 `unifiedAuthId`，优先读取用户仓储中的统一认证号，缺失时只允许从已校验的 `.../users/{unifiedAuthId}` session 路径派生；manager WebSocket `start` 透传该字段，协议版本保持 `opencode-manager.v1`。
  - Go manager 将单次启动日志改为 `{safeUnifiedAuthId}-{yyyyMMddTHHmmss.nnnnnnnnnZ}-{port}.log`，UTC 启动时间与 state `startedAt` 使用同一时刻，stdout/stderr 共同写入该文件；restart 保留用户身份但生成新文件。
  - 文件名对非安全 UTF-8 字节使用大写 `%HH`，超长身份使用有界前缀和完整 SHA-256；显式身份必须能由稳定 `users/{id}` session 路径验证，路径不稳定或身份不一致时都返回不含原始身份的通用错误。旧 state、旧 Java 和本地 CLI 继续兼容，无法派生身份时仍写 `{port}.log`，已有旧日志不迁移、不删除。
  - 同步 manager/runtime README、后端部署说明和安全脱敏边界；同时把 `tools/verify-ai-docs.sh` 中已过时的外部服务断言更新为包含正文已有的 `XXL MySQL`。
- How:
  - 复用 `OpencodeProcessStartupService` 公共启动程序和既有 manager socket gateway；统一认证号写入 manager 本地 state 供 restart 恢复，不新增业务旁路、HTTP 文件代理或数据库持久化。
  - Go 以 TDD 覆盖精确文件名、特殊字符、超长身份、session 路径校验、restart 新文件及旧 JSON；Java 覆盖仓储优先级、安全路径 fallback、assignment 下发、JSON 编解码和旧构造器兼容。
- Result:
  - `go test -count=1 ./...` 全量通过；Java 日志链路定向 64 项、runtime 全量 638 项和下游 `ManagerControlWebSocketHandlerTest` 5 项通过。runtime 全量首次在无关的 1 秒定时重试断言上瞬时失败，单测复跑 5 项和随后全量复跑均通过，未修改该无关测试或运行代码。
  - `tools/verify-ai-docs.sh`、`git diff --check` 和目标目录冲突标记扫描通过。文档已明确企业纯 Docker 路径、最近日志定位、旧文件兼容与文件名身份脱敏。
  - 不涉及 HTTP API、RunEvent、数据库/Flyway、关系型 SQL、前端、generated SDK、环境变量或 `.env.local`；manager 自身 `manager.log/manager-error.log` 路径不变。真实企业 worker 仍需在部署新 Java/manager 后观察一次启动、同端口 restart 和日志采集/保留策略。

### 2026-07-21 - 同步 main 并保留两侧功能

- Why:
  - 本地 `main` 相对 `origin/main` 领先 9 个、落后 44 个提交，直接推送会被拒绝；本地 XXL-JOB/夜间执行改造与远端 Git 发布恢复、Nginx 精确路由、内置 RSA、用户安全删除等改动存在交叉，冲突处理不能整文件取舍。
- What:
  - 将 9 个本地提交逐个 rebase 到远端 `0fb851e15`，逐段合并会话日志、模块图、部署与安全文档；保留远端 `TEST_AGENT_NGINX_SERVER_ROUTES`、JAR 内置 RSA、Git 企业邮箱及用户 Token 批量撤销，同时保留本地 XXL Admin/executor、地址派生、嵌入页样式和夜间 attempt/租约/补偿迁移。
  - 合并 Redis Token 两侧能力时补齐批量撤销对应 SHA-256 session marker 的删除，并新增回归断言，避免删除用户后 XXL 会话 marker 残留。
  - 同步保留 HTTP/RunEvent、数据库、部署、安全、模块与测试文档；未修改真实 `.env.local`，也未手改 generated SDK 或 XXL 上游源码。
- How:
  - 以 `git range-diff` 确认原 9 个提交与重写后的 9 个提交一一对应；全局检查无冲突标记，推送前再次 fetch 后确认远端未前进且当前历史可普通 fast-forward。
  - 后端 19 模块跳过测试 clean package、XXL/真实 MySQL 31 项、夜间与 Run 96 项、内部路由 API 13 项、持久化 23 项、应用配置 14 项通过；前端 lint/typecheck、XXL 8 项、排除已记录 `DirectoryRows` 基线文件后的 1440 项（1 skipped）及生产构建通过；企业 Nginx、单后台和开发脚本校验通过。
- Result:
  - 远端与本地功能已在同一线性 `main` 历史中保留，无需 force push；已知 `DirectoryRows` 角色断言基线仍未扩大范围修改，XXL-JOB 3.4.2 vendored 上游源码/资源自带的空白格式继续原样保留。
  - 工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除始终保持未暂存，不纳入提交或推送。

### 2026-07-21 - 修复 Git 推送身份与提交进度终态

- Why:
  - 企业 SCM 拒绝平台生成的 `统一认证号@testagent.local` committer，导致应用 Agent 已在个人 worktree 本地提交、但投影到 feature 分支后 push 失败；前端随后按 clean worktree 刷新，文件从 Diff 消失且失败步骤仍显示 RUNNING。应用 workspace 仅本地提交成功时也因步骤终态只看 step 序号而持续转圈。
- What:
  - 平台 Git 单次提交身份改为 `统一认证号@mails.icbc`，匹配企业 SCM 已登记邮箱；继续只注入当前 Git 命令，不修改仓库或服务器全局配置。
  - 应用 Agent 在本地提交成功后先保留文件白名单和提交前 patch；远端发布失败时以“待推送”跨 5 秒 Diff 轮询保留，点击文件仍可查看差异，重新推送只重放 publish，不重复本地 commit。HTTP 失败与仅本地提交成功都会把当前进度步骤收敛为 FAILED/SUCCEEDED，不再残留 RUNNING。
  - 同步 common/workspace-management、HTTP API、agent-web README/PACKAGE，并新增真实 Git 身份断言和进度/失败重试组件回归。
- How:
  - 复用现有个人 worktree `commit -> publish` 两阶段协议、Agent 配置 operation progress 和定时 Diff 查询；没有新增恢复接口、第二套 Git 状态或裸 Git 推送路径。发布成功后清除待推送快照，同一路径产生新工作树改动或切换工作区时使旧快照失效。
- Result:
  - GitChangesPanel 40 项、common 真实 Git 9 项、workspace-management 聚焦 98 项通过；前端 lint、typecheck、agent-web 生产构建与后端 18 模块跳过测试打包通过，`git diff --check` 通过。
  - 前端全量 Vitest 为 1450 passed / 1 skipped / 1 failed；唯一失败是既有 `DirectoryRows` 测试用 `button` 查询实际 `radio` 角色的“上传”，单文件复跑稳定失败且相关文件未修改，本次未扩大范围处理。
  - 使用 `.env.test` / `test` profile 重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 与登录 CORS 正常、manager WebSocket 已连接。本次只改变 Git committer 邮箱规则和前端失败恢复状态，不新增或变更 HTTP/RunEvent/数据库/SQL/权限/generated SDK/环境配置。
- Pitfalls:
  - 修复部署前已经失败的操作没有当前页面内存中的待推送快照，但个人 HEAD 中的本地提交仍在；应使用原 `personalWorkspaceId` 和原文件白名单直接调用平台 `POST /personal-workspaces/{id}/publish` 恢复，不能再次调用 commit，也不能手工 `git push` 绕过版本目标、广播与 rollout。

### 2026-07-21 - 修复夜间补偿服务启动装配失败

- Why:
  - 夜间执行迁移后，`NightExecutionReconcileService` 同时保留生产构造器和包级测试构造器，却未明确 Spring 注入入口，fat JAR 启动时报 `No default constructor found`。
- What:
  - 为五参数生产构造器增加 `@Autowired`，保持用于稳定 attemptId 测试的六参数构造器为包级可见，不改补偿业务逻辑。
  - 新增真实 `AnnotationConfigApplicationContext` 回归，注册全部依赖后验证 Spring 能选择生产构造器并创建 Bean。
- How:
  - TDD 先确认新增测试稳定复现相同 `BeanCreationException`，再用同目录 `NightExecutionDispatchService` 已采用的多构造器装配模式完成最小修复。
- Result:
  - `NightExecutionReconcileServiceTest` 6 项和全部 `NightExecution*Test` 28 项通过；本地重启完成后后端 readiness、XXL Admin readiness 均为 `UP`，前端返回 200，日志出现新的 `Started TestAgentApplication`，XXL V4 migration、Admin 与 executor 正常启动且未再出现本次构造器异常。
  - 重启流程完成后端 20 模块 clean package 和前端生产构建；不涉及 API、事件、数据库、配置、安全或兼容性文档变更。工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除继续保持未暂存。

### 2026-07-21 - 夜间执行迁移至 XXL-JOB

- Why:
  - 公司夜间算力任务需要废弃应用内 `USER_PLAN` 扫描，统一由 XXL-JOB 每 15 分钟分发，并在 HTTP 超时、响应丢失、Java 崩溃和补偿重试下保证同一夜间任务只创建一个普通 Run。
- What:
  - 新增 XXL MySQL V4 注册 `opencode-runtime.night-execution-dispatch`，按到期时间最多扫描 500 条 `SCHEDULED`，固定服务器分组、每批 50、最多并发 8 台 Java；保留 5 分钟 `night-execution-reconcile`。
  - 新增精确内部接口 `/api/internal/platform/opencode-runtime/night-execution/internal-dispatch`，只传 `linuxServerId/taskIds`，复用 `BackendJavaRouteResolver`、`BackendHttpForwarder` 和普通 `startScheduledRun`；接口只等待 Run 受理，不等待执行终态。
  - 夜间任务增加 attempt、精确 backend owner、租约和版本字段；Run 锚点增加 Scheduled dispatch attempt/租约/受理标记。认领、续租、回退、失败和完成均以 `taskId + DISPATCHING + attemptId` fencing，Run 受理后夜间状态保持 `DISPATCHED`，实际结果继续由既有会话、Run 与 RunEvent SSE 展示。
  - 增加每分钟 in-flight 续租、本机 owner watchdog 和跨 Java 补偿；恢复 legacy Scheduled Run 前通过稳定消息 ID 探测远端受理状态，`ACCEPTED` 仅补标记、`NOT_ACCEPTED` 才提交、`UNKNOWN` 不重投，且通用 stale Run 扫描排除尚未确认交接的夜间锚点。
  - 删除旧 `ScheduledTaskRunner`、`ScheduledUserPlanService`、affinity、管理/诊断和启动配置；夜间创建、改期、取消不再维护 `USER_PLAN`。保留 XXL 公共 handler/context/result、Redis 全局锁、历史清理以及可空历史 `scheduled_task_run_id`。
  - 新增 PostgreSQL migration `V20260721134000` 和 MyBatis XML SQL；同步根/后端/模块 README、PACKAGE、HTTP API、RunEvent、数据库、部署、XXL 架构、安全和测试文档。公共夜间 API、前端待执行 Tab、DTO 与 SSE 保持兼容，`NIGHT_EXECUTION_SLOT_CAPACITY` 仍为通用参数默认 20。
- How:
  - 内部路径仅精确豁免普通用户鉴权，再由 Controller 对 `XXL-JOB-ACCESS-TOKEN` 做常量时间校验；跨服务器不传 prompt、附件或用户敏感内容，目标 Java 从共享数据库读取完整输入。
  - Run 接受锚点以稳定 `sessionId + clientRequestId` 和 Redis claim 幂等；分发采用至少一次传输，业务语义由数据库唯一锚点、attempt 租约与远端受理探测收敛为只创建一个 Run。
  - PostgreSQL 全部新增关系型业务 SQL 落在 MyBatis XML；没有修改 generated SDK 或真实 `.env.local`。工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除继续保持未暂存，不纳入提交。
- Result:
  - scheduler/runtime/XXL/API/persistence/app 组合定向套件通过（对应模块分别 2/97/3/16/20/14 项）；真实 PostgreSQL Docker 环境成功执行完整 Flyway 链并通过持久化集成测试；后端 20 模块 `clean package -DskipTests` 通过，`git diff --check` 通过。
  - 扩大到 H2 全量 `clean verify` 仍被已发布且未修改的 `V20260717173000__create_public_agent_config_rollouts.sql` 中 PostgreSQL `TIMESTAMPTZ`/局部表达式索引阻断（167 tests、0 failures、67 setup errors、17 skipped）；同一完整 migration 链已在真实 PostgreSQL 通过，未改写历史迁移以免破坏 checksum。
  - 本机未配置真实多 Java/XXL 网络故障环境，未执行三服务物理端到端；HTTP 超时、响应丢失、崩溃、租约与补偿边界由单元及持久化测试覆盖。远端消息长期不可判定时会安全延迟恢复而不重复发送，这是当前剩余的可用性权衡，无任务内未完成编码项。

### 2026-07-21 - 优化 XXL-JOB 定时任务管理页面头部样式

- Why:
  - 优化定时任务管理页面头部的高度、标题文案和刷新按钮布局，使其更加紧凑、直观且具备更好的视觉效果。
- What:
  - 去除定时任务管理页头部左上角的 kicker 文本 `SCHEDULER / XXL-JOB`，使标题仅保留 `定时任务管理`。
  - 将头部工具栏的 `min-height` 从 `58px` 减小至 `44px`，并收紧 padding 为 `6px 14px 6px 16px`。
  - 将“重新加载”按钮改为仅显示旋转刷新图标（去除文字，增加 `aria-label="重新加载"` 保证无障碍访问与测试兼容），并将按钮尺寸固定为 `32px * 32px` 的正方形。
- How:
  - 仅修改 `ScheduledTaskManagementPanel.vue`，并在 `xxl-job-management-panel.test.ts` 中继续使用带有 `aria-label` 的角色匹配，无需修改测试文件。
  - 执行 `corepack pnpm test xxl-job`、`corepack pnpm lint` 和 `corepack pnpm typecheck` 确认代码、类型定义和测试全部通过。
- Result:
  - 头部高度明显减小，左上角文案仅显示“定时任务管理”，“重新加载”为单图标按钮，交互和无障碍特征正常。

### 2026-07-21 - 修复 XXL-JOB 嵌入页样式并改为横向导航

- Why:
  - 根 `.gitignore` 的通用 `dist/` 规则误忽略 XXL-JOB 3.4.2 AdminLTE 发布资源，真实 Admin 请求核心 CSS/JS 返回 404，嵌入页因此退化为项目符号导航；平台还需要在不修改上游模板和 Java 源码的前提下提供紧凑横向导航。
- What:
  - 为上游 AdminLTE `dist` 增加精确例外并恢复 3.4.2 原版 `AdminLTE.min.css`、`_all-skins.min.css`、`adminlte.min.js`；integration 提供仅在 `test-agent-xxl-embedded` 根 class 下生效的嵌入态样式。
  - 前端 iframe 每次加载后幂等识别并装饰 XXL shell：六个菜单单行横排，窄屏横向滚动，当前账号改为只读文本；SSO 中转页、错误页和不可访问文档保持无操作，直接访问 Admin 仍保留完整原生布局。
  - 同步 upstream/integration、frontend/agent-web README、PACKAGE、XXL 架构与测试文档；HTTP API、RunEvent、数据库、权限和 SSO 协议不变。
- How:
  - TDD 先以真实 Admin 静态资源 404 和前端缺失装饰器复现失败，再补最小实现；三份上游资源的 SHA-256 与 XXL-JOB 3.4.2 官方文件一致，真实 Admin 与 Vite 代理均验证正确状态码、MIME 和非空内容。
  - integration 全量 36 项、应用打包、前端 lint/typecheck、87 个文件 1434 passed / 1 skipped、生产 build 均通过；无参数重启使用默认 `test` profile，8080/18080 readiness 为 200、9999 正常监听。
- Result:
  - 真实浏览器确认六个菜单同一水平线、选中态与原生页签切换正常、账号无下拉、窄屏可横向滚动；平台重新加载会重新签票并恢复装饰，票据未进入顶层 URL，控制台无 AdminLTE 404。直接访问 Admin 不注入平台样式。
  - 后端全量复跑仍仅在既有 persistence H2 基线被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 阻断；另一次运行态调度测试的并发时序抖动独立连续复跑 5 次通过，未改无关代码。
  - 未修改 `.env.local`、XXL 上游模板/Java、API、事件、数据库或安全协议；工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除保持未暂存，不纳入本次提交。

### 2026-07-21 - 自动派生 XXL-JOB 多后端节点地址

- Why:
  - executor 依赖静态 Admin 列表和显式注册地址时，新增 Linux 节点会迫使旧 Java 同步修改配置并重启；当前部署约束为每台 Linux 仅一个 Java，Admin 与同 JVM executor 固定配对，可直接复用平台已具备的 advertised host 解析。
- What:
  - integration 模块新增内部端点解析器：本机 Admin 固定派生为 loopback 地址，executor 注册地址从 `BackendInstanceIdentity.listenUrl()` 的 host 与 executor 端口生成；非法监听地址拒绝启动且错误不回显原始 URL。
  - executor 生命周期只探测本机 Admin readiness，恢复后只启动一次；删除 `adminAddresses`、`address`、`ip` 三个 executor 配置字段及对应三个环境变量，不保留预发布兼容入口。所有 Admin 继续共享 XXL MySQL，调度与注册地址均不引入 Linux 服务器亲和。
  - 同步本地示例、企业单/多后台模板、后端与 integration README，以及架构、部署、安全和测试文档；按用户明确接受的风险把现有本地 access token 默认值纳入基础配置，生产模板仍强制要求通过外部环境变量覆盖，文档与日志不重复明文。
- How:
  - TDD 先以缺失端点解析器的编译失败固化 IPv4、内部 DNS、context path 规整、非法地址脱敏、Admin 恢复和 Spring 实际装配预期，再完成实现；`mvn -f backend/pom.xml -pl test-agent-xxl-job-integration -am test` 通过，integration 36 项全过，真实 MySQL/Admin 测试验证第三节点加入时前两个注册地址无需重新注册仍被共享保留。
  - `mvn -f backend/pom.xml -pl test-agent-app -am -DskipTests package`、Compose 配置校验和无参数 `sh restart-dev-services.sh` 均通过；重启脚本使用默认 `test` profile，平台与 Admin readiness 均为 HTTP 200，同一 Java PID 监听 8080、18080、9999。
- Result:
  - 本地 MySQL 注册地址等于平台解析 host 加 executor 端口，当前启动区间无首次注册 `Connection refused` 或 `registry error`；新增 Linux 只需启动新 Java 并把普通 backend 与 XXL Admin 加入中央 Nginx upstream 后 reload，无需修改或重启旧 Java。
  - 后端全量 `mvn test` 仍仅在既有 persistence H2 基线被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 阻断（165 tests、76 errors、17 skipped），本次未扩大范围修改。
  - 未修改 `.env.local`、XXL 上游源码、HTTP API、RunEvent 或数据库结构；地址配置的破坏性删除为预发布阶段的明确收口。工作树原有 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除保持未暂存，不纳入本次提交。

### 2026-07-21 - 消除 XXL executor 启动注册竞态

- Why:
  - 同 JVM 中 executor 的 `SmartInitializingSingleton` 回调早于异步 Admin 子上下文完成监听，首次注册会立即请求尚未启动的 `127.0.0.1:18080`，产生一次 `Connection refused`；XXL 上游约 30 秒后可自愈，但启动日志存在可避免的错误。
- What:
  - integration 模块用最小上游扩展覆盖 executor 自动初始化入口，新增独立 `SmartLifecycle` daemon 协调器；以 250 毫秒～5 秒退避探测配置列表中各 Admin 的 `/actuator/health/readiness`，任意一个返回 HTTP 200 后才启动 9999 和注册线程，同一进程最多启动一次。
  - 全部 Admin 不可用时平台 WebFlux/8080 和主 readiness 继续启动，executor 端口保持关闭并等待恢复；未修改 XXL 上游源码、现有环境变量、`.env.test`、API、事件、数据库或安全协议。
  - 同步后端/集成模块 README、模块图、XXL 架构、部署和测试文档；新增多 Admin readiness、非法地址/重定向、延迟/幂等启动、关闭和真实 Spring 装配回归。
- How:
  - TDD 先以缺失 readiness probe、延迟 executor 和生命周期协调器的编译失败固化预期，再完成实现；`mvn -f backend/pom.xml -pl test-agent-xxl-job-integration -am test` 通过，integration 31 项全过（含 MySQL 8.4、真实 Admin Tomcat/readiness）。
  - 无参数执行 `sh restart-dev-services.sh`，默认 `test` profile；日志顺序为平台 Netty 8080 → Admin Tomcat 18080/readiness → executor 9999/注册线程，最新启动区间无 `registry error`、`Connection refused` 或构造器错误，MySQL 注册行持续更新。
- Result:
  - 8080、18080、9999 与 MySQL 13306 均监听，平台/Admin readiness 均为 HTTP 200；executor 注册地址为 `http://127.0.0.1:9999`，不含 Linux 亲和信息。
  - 后端跳过测试 clean package 和前端生产构建由重启脚本通过。后端全量测试仍仅在既有 persistence H2 基线被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 阻断（165 tests、76 errors、17 skipped），XXL 模块已先通过。
  - 工作树原有 `application.yml` 本地配置修改及 `backend/${SYS_DATA_ROOT_DIR}/agent-opencode/.config` 删除保持未暂存、未纳入本次提交；本次不拉取、不 rebase、不推送远端。

### 2026-07-21 - 修复 XXL-JOB 后端启动失败

- Why:
  - XXL 上游依赖 JAR 根目录的 `application.properties` 被平台主 Spring Boot 上下文自动加载，可能以 MySQL/Hikari 通用配置污染平台 PostgreSQL/Druid；隔离后，后端又依次暴露两个多构造器组件未明确注入构造器，以及 Admin 子上下文继承平台 Redis readiness 成员而无法启动的问题。
- What:
  - 构建时把未修改的上游 `application.properties` 重定位到 `META-INF/xxl-job-admin-upstream/`，由 Admin launcher 显式低优先级加载；平台运行配置继续高优先级覆盖，上游 Freemarker、调度超时等默认项仍生效。
  - 为 `RedisXxlJobSsoTicketService` 与 `XxlJobScheduledTaskAdapter` 的公开生产构造器增加 `@Autowired`，保留包级测试构造器；新增真实 Spring 上下文装配回归，防止再次退回无参实例化。
  - Admin 子上下文把 readiness 固定为仅检查独立 MySQL `db`，不继承主应用 `readinessState,db,redis`；同步上游模块、集成模块、架构与测试文档。
- How:
  - TDD 分别复现两个组件的 `No default constructor found`，以及 Admin 子上下文的 `Health contributor 'redis' ... does not exist`，完成最小修复后定向复跑转绿。
  - `test-agent-xxl-job-integration` Reactor 全量通过，集成模块 25 项测试无失败；`test-agent-app` 跳过测试打包成功。无参数执行 `sh restart-dev-services.sh`，确认默认读取 `.env.test` 并使用 `test` profile。
- Result:
  - 同一 Java PID 已监听平台 Netty `8080`、XXL Admin Tomcat `18080` 和 executor `9999`；平台与 Admin readiness 均为 200。日志确认平台连接 PostgreSQL `15432`、XXL 连接 MySQL `13306`，未再出现本次启动的无参构造器、Redis health group 或 MySQL `3306` 误连；MySQL 中为 6 个任务、1 个 `test-agent-backend` 组，executor 注册时间持续刷新且地址不含 Linux 亲和信息。
  - 后端全量 `mvn test` 仍在未修改的 persistence 基线被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 与 H2 不兼容阻断（165 tests、76 errors、17 skipped）；本次按既定范围未修改该历史 migration。
  - 未修改 `.env.test`/`.env.local`、HTTP API、RunEvent、数据库结构、任务数据或安全协议；仅收紧 Spring 配置作用域和组件装配，兼容既有 XXL 行为。

### 2026-07-20 - 修复 XXL Admin 初始健康状态空时间

- Why:
  - 合入 main 前重新执行完整 XXL integration 测试时，`XxlJobAdminHealthIndicatorTest` 暴露 Admin 首次启动尝试前 `checkedAt=null` 被传入 Spring Boot 4 `Health.Builder`，导致健康查询抛出 `IllegalArgumentException`。
- What:
  - XXL Admin 为 DOWN 且尚未完成首次检查时省略 `checkedAt` detail；保留初始安全原因、后续检查时间、独立 DOWN/UP 状态以及不参与平台 readiness 的既有语义。
  - 回归测试显式要求初始健康查询不抛异常、返回 DOWN、保留“尚未启动”原因且不包含空 `checkedAt`。
- How:
  - TDD 先将运行时异常收敛为明确断言失败，再做空值条件化的最小修复；定向测试经历 RED→GREEN。
  - 重新执行 `mvn -f backend/pom.xml -pl test-agent-xxl-job-integration -am -q clean test`，退出码 0；前端 `corepack pnpm test` 为 87 个文件、1430 passed / 1 skipped。
- Result:
  - 初始 Admin health 可稳定返回 DOWN，不再因尚无检查时间抛异常；不涉及 API、事件、数据库、配置、权限或文档契约变化。

### 2026-07-20 - 将平台周期任务迁移至 XXL-JOB 3.4.2

- Why:
  - 平台周期任务需要统一改由 XXL-JOB 管理和可视化，同时保留 `USER_PLAN`、`executionAffinity` 及夜间一次性计划的既有调度语义；XXL executor 必须随所有 Java 进程注册且不绑定稳定 Linux 服务器。
  - XXL MySQL 需与平台 PostgreSQL 隔离，平台超级管理员通过同源 iframe 免登录进入 Admin，并在平台会话失效时同步失效 XXL 会话。
- What:
  - 新增未做业务改动的 `test-agent-xxl-job-admin-upstream`（固定 XXL-JOB 3.4.2、上游 commit、GPL-3.0 LICENSE 与升级说明）和平台扩展 `test-agent-xxl-job-integration`；每个平台 Java 进程保持 WebFlux 主上下文，同时运行独立 Servlet Admin 子上下文及 executor，Admin/MySQL 故障只上报独立 health DOWN 并指数退避重试。
  - 新增仅 `SUPER_ADMIN` 可申请的一次性 Redis SSO ticket、SHA-256 session marker、按平台用户 JIT upsert 的 XXL 账号与自定义 `LoginStore`；禁用原生登录/改密/用户写操作，iframe 使用隐藏表单 POST 和显式 ready `postMessage` 握手，设置 SAMEORIGIN/CSP、HttpOnly/Secure/SameSite cookie 并扩充日志脱敏。
  - MySQL Flyway 使用顶层独立 `classpath:xxl-job/db/migration`：V1 上游基础表无示例管理员，V2 增加平台用户/任务键，V3 初始化自动注册组 `test-agent-backend` 与六条周期任务；统一 handler 支持 `GLOBAL_MUTEX`、`ALLOW_OVERLAP`、锁续租、停止中断和错误收敛，参数中不含 `linuxServerId`/亲和字段。
  - 旧 runner 仅同步、扫描和执行 `USER_PLAN`，移除本地终态投影 retry ticker；旧管理 API 统一返回 `410 API_GONE`。前端管理页改为同源 XXL iframe；本地 Compose 增加 MySQL 8.4/13306，Vite/Nginx、启动脚本、企业离线包配置及 API/数据库/部署/安全/架构/测试文档同步更新，未修改 `.env.local`。
- How:
  - 以 TDD 覆盖 SSO 权限、一次消费/过期、marker 失效、JIT 幂等与改名、原生入口封禁、handler 参数/互斥/重叠/续租/停止/脱敏，以及前端签票、刷新重签、故障与登出；使用 MySQL 8.4、Redis 7 Testcontainers 验证全新/重复/并发 Flyway、六条任务、executor 组和独立 Admin HTTP 行为。
  - `mvn -f backend/pom.xml -pl test-agent-xxl-job-integration -am clean test` 通过，integration 22 项全过；`mvn -f backend/pom.xml -pl test-agent-app -am clean -DskipTests package` 通过并生成约 102 MB 单应用 JAR，检查确认包含独立 migration 与 SSO 模板。
  - 前端依次执行 lint、typecheck、test、build：87 个测试文件、1430 passed / 1 skipped，生产构建仅保留既有大 chunk 提示；Compose config、内部 Nginx 单/多后端、单机配置和开发脚本校验均通过。
  - 平台自研文件执行 `git diff --cached --check` 无错误；原样保存的 XXL 上游目录保留 3.4.2 自带尾随空格，因此未对该 vendored 目录做格式清洗，避免破坏与上游源码的直接比对。
- Result:
  - 周期任务已迁移到 XXL-JOB 管理面与所有 Java executor；执行器使用自动注册和轮询，不含 Linux 亲和。夜间一次性 `USER_PLAN` 继续按原 `executionAffinity` 执行，滚动发布期间两入口复用同一 Redis 锁键。
  - 后端全量 `mvn test` 仍在未改动的 persistence 基线被已发布 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 与 H2 不兼容阻断（165 tests、76 errors、17 skipped），已在干净 main 复现；Playwright 全量也仍被既有 `.agent-root-row` 隐藏基线阻断，XXL 相关 Vitest 均通过。
  - 未新增 RunEvent/SSE；涉及内部 HTTP API、独立 MySQL schema、Redis 会话 marker、GPL-3.0 上游交付和安全响应头。真实生产双 Java/共享 MySQL/网络与人工页面触发尚需按验收文档执行，本次未推送远端。
### 2026-07-19 - 优化会话列表样式与布局

- Why:
  - 用户的会话列表单项垂直高度偏大，限制了一屏内能展示的会话数量；且顶部的“会话列表”标题栏占用了空间，用户希望移除该标题栏，并将关闭按钮移至选项卡右侧以使布局更紧凑，同时希望抽屉宽度更宽。
- What:
  - 修改 `FigmaChatPanel.vue` 中会话卡片的 CSS 样式，重构了历史会话抽屉的 DOM 结构与样式，并增加了抽屉的最大宽度限制：
    - 移除了顶部的 `<header class="figma-chat-drawer-header">`。
    - 将带有 `aria-label="关闭会话列表抽屉"` 属性的关闭按钮移入选项卡面板 `.figma-chat-history-tabs` 中，并为其添加新的定位样式使其靠右对齐。
    - 微调了卡片 padding、margin 以及列表 gap 属性，进一步压缩垂直空隙。
    - 修改 `session-list-drawer.ts`，将抽屉最大宽度 `DRAWER_MAX_WIDTH` 从 360 像素提升至 400 像素，并同步更新了 `session-list-drawer.test.ts` 中对应的断言。
- How:
  - 将 `.figma-chat-history-list` 的 `gap` 从 `8px` 调整为 `6px`。
  - 将 `.figma-chat-history-card` 的 `padding` 从 `12px` 调整为 `8px 12px`，其内 icon 与 content 的 `gap` 从 `12px` 调整为 `8px`。
  - 将 `.figma-chat-history-card-title-row` 和 `.figma-chat-history-card-context` 的 `margin-bottom` 从 `4px` 调整为 `2px`。
  - 移除了历史抽屉标题，在 `figma-chat-history-tabs` 中追加 close 按钮，利用 `margin-left: auto` 和 `align-self: center` 样式使其在选项卡右侧居中对齐，并保持 hover 背景切换效果。
  - 修改 `session-list-drawer.ts` 中 `const DRAWER_MAX_WIDTH = 400;`，并调整对应单测中的 `width` 与计算出的 `left` 坐标值。
- Result:
  - 成功缩减了每个历史会话列表项的高度，移除了无用的标题栏，抽屉宽度调整为 400px，整体界面布局更加紧凑实用。
  - 运行单元测试 `vitest run FigmaChatPanel` (132 tests) 和 `vitest run session-list-drawer` (4 tests) 全部通过。
  - 前端 `typecheck` 验证无类型报错。
- Verification:
  - 在 `frontend` 目录下执行 `corepack pnpm test FigmaChatPanel` 和 `corepack pnpm --filter @test-agent/agent-web typecheck`。

### 2026-07-19 - 修复内存通用参数早于 Flyway 加载

- Why:
  - 项目通过最高优先级 `ApplicationRunner` 执行运行态 Flyway，而内存通用参数注册表原先在 Spring 单例装配完成时查库；新环境尚未执行参数种子 migration，因而以“数据库值缺失”阻止启动。
- What:
  - 移除 `CommonParameterMemoryRegistry` 的 `SmartInitializingSingleton` 启动回调，新增紧随 `DatabaseMigrationRunner` 的 `CommonParameterMemoryStartupRunner`，在 migration 完成后、scheduler 等默认业务 Runner 前继续执行严格加载。
  - 同步 app、configuration-management 与后端部署文档中的启动生命周期说明；严格失败语义、参数值、API、事件和数据库结构均不变。
- How:
  - TDD 先用缺失 runner 的编译失败固化预期，再验证新 runner 调用注册表、顺序为 `HIGHEST_PRECEDENCE + 1`，并确认注册表不再实现单例初始化回调。
- Result:
  - 聚焦回归测试和后端跳过测试打包通过；按 `.env.test` 重启后，日志确认 Flyway 成功应用 `V20260719210000`，随后加载 `NIGHT_EXECUTION_SLOT_CAPACITY/all`，readiness 为 `UP`。
  - 测试库参数为 `20/all/editable=true`。不涉及新增 API、RunEvent、数据库结构、环境配置、安全或兼容性变化；仅修正已有 migration 与内存加载的启动先后顺序。

### 2026-07-19 - 优化对话页会话列表交互

- Why:
  - 主对话顶部“对话 / 待执行任务”页签占用内容空间，历史会话选择后列表自动关闭，连续查找会话效率较低；原始输出页面缓存也需要明确的内存上限。
- What:
  - 主区域固定展示当前会话；顶部“消息列表”统一改为“会话列表”，会话与待执行任务迁入 Teleport 非模态抽屉。桌面抽屉贴在右侧对话栏左侧，窄屏退化为视口内覆盖，并随栏位、滚动与窗口尺寸更新。
  - 会话/任务选择继续复用既有回调且不关闭抽屉，当前会话增加选中态；抽屉可由再次点击“会话列表”入口、关闭按钮、Esc 或右侧栏收起关闭，每次重新打开默认进入会话页签，并补齐入口 `aria-expanded` 与 WAI-ARIA Tab 键盘交互。
  - 每个 Session 的原始输出内存缓存改为有界追加，只保留最新 2000 条；继续沿用既有 Session 隔离、脱敏、正文截断和刷新清空规则。
  - 同步 frontend、agent-web、agent-chat、模块地图和内置对话手册；更新 mock/real E2E 选择器与关键连续切换场景。
- How:
  - 新增纯函数 `resolveSessionListDrawerPlacement` 统一桌面/窄屏定位；`FigmaChatPanel` 仅增加内部可选 `panelVisible` 属性，现有 `select-session`、夜间任务事件和会话加载流程不变。
  - TDD 覆盖主页签移除、抽屉双页签/入口二次点击收起/关闭边界/当前项/键盘操作/窄屏定位，以及 2001 条原始输出保留最后 2000 条和顺序不变。
- Result:
  - `corepack pnpm lint`、`typecheck`、`test`、`build` 全部通过；Vitest 86 个文件共 1420 passed / 1 skipped。关键 Playwright 场景在 Chromium/mobile 4/4 通过，另两个抽屉保持开启后的精确选择器用例 2/2 通过。
  - 完整 mock E2E 已执行，但在本次未改动的 Agent/文件树基线用例中持续失败（`.agent-root-row` 隐藏、mock Agent 目录缺失）；手动停止时为 5 passed、15 failed、2 interrupted、178 did not run，退出码 130。本任务会话列表关键路径不受影响。
  - 不涉及 HTTP/SSE API、DTO、RunEvent、数据库、后端会话逻辑、权限、安全、环境配置、generated SDK 或新依赖；构建只保留既有大 chunk 提示。

### 2026-07-19 - 支持查询与手工刷新 Java 内存通用参数

- Why:
  - 多 Java 部署中，超级管理员缺少核对各 JVM 实际加载值及在广播遗漏、运行期读取失败后按数据库值定点恢复的能力；多数通用参数仍应保持按需直读数据库，不能扩大为通用缓存。
- What:
  - 新增显式 `CommonParameterMemoryEntry` 领域 SPI 与本机注册表，按英文名/平台唯一排序，启动严格加载，匹配广播或手工操作时查库刷新；运行期失败保留上一有效值并返回安全状态。夜间容量成为首个注册项，保留正整数校验、动态消费者和默认种子 `20/all/editable=true`，删除旧环境变量/yaml 入口。
  - 公共 Java 路由器增加 `backendProcessId` 精确选择；新增四个仅 `SUPER_ADMIN` 可用的查询/刷新接口，集群操作最多 500 个进程、并发 8、单进程 10 秒，同服务器多个 Java 独立返回，单进程离线统一 503，集群部分失败仍返回逐进程结果。
  - 通用参数页增加按需加载的 JVM 内存值抽屉与全部/单进程刷新；同步共享类型、backend-api、HTTP API、数据库/部署/安全/后端规范、模块图和前后端 README/PACKAGE。`event-stream.md` 经检查无需修改。
- How:
  - 跨 Java 请求复用 `BackendJavaRouteResolver` 与 `BackendHttpForwarder`，保留 routed header 防二次转发；手工刷新直接读取目标 JVM 的数据库参数，不写修改历史、不重复广播。响应与结构化日志不记录底层异常，`sourceValue`/`memoryValue` 纳入 JSON 日志脱敏。
  - TDD 覆盖注册键唯一性、排序、启动/运行失败语义、事件匹配、夜间容量缺失/空白/非正整数/溢出、精确路由、四个接口、超时/离线/部分失败、远端空响应以及前端懒加载、展示和刷新提示。
- Result:
  - API、配置管理和运行时相关 Maven 模块全量测试通过；聚焦回归通过，后端 18 模块 `mvn clean package -DskipTests` 成功。前端聚焦 Vitest 84 项及 shared-types、backend-api、agent-web typecheck 通过。
  - 扩大到 persistence 全量仍被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 与 H2 不兼容阻断（76 errors）；全量前端 test/build 又被同工作树并行、未纳入本提交的 Figma/AgentWorkbench 改动失败阻断，任务相关聚焦套件保持通过。
  - 涉及新增内部 HTTP API、一个生产必需通用参数种子 migration、最多 500×单进程响应的有界并发聚合和超级管理员诊断面；不新增 RunEvent/SSE、Redis 参数快照、业务表、generated SDK 或真实环境文件。仍建议在真实多 Java 环境人工验证同服务器多进程、离线恢复和广播后收敛。
- Verification:
  - `mvn -q -pl test-agent-api,test-agent-configuration-management,test-agent-opencode-runtime -am test`
  - 聚焦 `CommonParameterMemory*`、`NightExecution*`、路由、migration 与脱敏测试；`mvn clean package -DskipTests`
  - `corepack pnpm exec vitest run apps/agent-web/tests/general-param-management-panel.test.ts packages/backend-api/tests/backend-api.test.ts`
  - shared-types、backend-api、agent-web typecheck，`git diff --check`、冲突标记/旧配置引用扫描和全部 session log 近期条目回顾。
- Next:
  - 并行前端改动和既有 H2 migration 基线修复后重跑全量前端与 persistence；在真实多 Java 部署完成查询、全部/定点刷新及离线 503 人工验收。

### 2026-07-19 - 优化引用资产库多服务器同步耗时

- Why:
  - 引用资产 generation 建档后只发布 Redis 广播，而发布者会忽略自身事件；发起 Java 的本机副本因此常等到默认 60 秒补偿扫描才开始。瞬时 Git 失败的 5 秒退避也可能被同一扫描周期放大。
- What:
  - workspace-management 新增按 `repositoryId + generation` 去重的本机有界异步调度器，默认两个 worker、最多保留 256 个 key，支持立即和按 `nextRetryAt` 调度；队列饱和时拒绝 caller-runs，并由既有补偿扫描恢复。
  - 初始化、同步、分支切换和指针核验在广播其它 Java 的同时立即提交本机任务；Redis 消费者与 60 秒补偿器只负责排队，不再在线程内执行阻塞 Git。瞬时失败写入 `RETRY_WAIT` 后直接安排退避到期重试。
  - 已有可信、干净、同源仓库在实际分支和 HEAD 已等于固定目标时走无操作快速路径，跳过 fetch、提交解析、祖先校验和 reset；增加排队、租约认领、Git 和总体任务耗时的脱敏结构化日志。
  - 同步 workspace-management README/PACKAGE、模块图、后端部署和内部广播文档；未修改 HTTP API/DTO、广播 payload、RunEvent、数据库/MyBatis、前端、manager、generated SDK 或环境配置。
- How:
  - 保留数据库 generation、租约 token/CAS fencing、本机文件锁和 60 秒补偿扫描作为一致性边界；调度器只改变唤醒与线程承载，不改变脏仓库、origin 冲突、分支分叉或租约丢失的安全阻断规则。
  - TDD 覆盖立即执行、去重、延迟任务被更早唤醒替换、并发上限、容量拒绝、关闭、四类操作本机提交、广播 listener 非阻塞、首次 5 秒定向重试和已对齐 HEAD 无操作路径。
- Result:
  - workspace-management reactor 全量通过：common 87、domain 78、workspace-management 230 项；引用资产 Controller 定向 3 项通过，聚焦调度/服务测试 82 项通过。
  - `test-agent-api` 全量共运行 325 项，其中 324 项通过；唯一失败为并行未提交的通用参数内存化改动新增 `SensitiveDataMaskerTest.mask_commonParameterMemoryValues`，其测试和被测脱敏器均不在本次提交范围，已保留未暂存。
  - 本机没有真实双服务器环境；各节点即时进入 `PROCESSING` 和正常小仓库 3 秒内页面收敛仍需部署环境人工验收。
- Verification:
  - `mvn -pl test-agent-workspace-management -am test`
  - `mvn -pl test-agent-api -am test`（上述任务外单项失败）
  - `mvn -pl test-agent-api -am -Dtest='ReferenceRepositoryControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `git diff --check`、全部 session log 近期条目回顾和精确暂存差异审查。
- Next:
  - 在真实双服务器部署执行 generation 到 `PROCESSING/READY` 的耗时验收；通用参数内存化任务需单独修复其脱敏测试。

### 2026-07-19 - 修复引用文件定位到当前文件失效

- Why:
  - 编辑器页脚“定位到当前文件”和标签双击沿用普通工作区相对路径展开逻辑，但引用 tab 使用 `workspace-reference:` 合成身份，导致折叠的引用祖先目录无法展开和滚动。
- What:
  - 新增纯函数按组合文件树的稳定节点 ID 和“父目录 ID → 子节点”缓存反向恢复祖先链；缺失、不完整或循环链路失败关闭，不按展示路径猜测。
  - `AgentWorkbench` 对引用 tab 使用打开文件时保存的稳定叶子节点 ID，从根到叶展开祖先；普通工作区文件继续使用原相对路径逻辑，两类路径都等待展开完成后再高亮滚动。
  - 同步 agent-web 与 file-explorer README，明确合并引用、非合并引用及同名冲突的精确定位规则。
- How:
  - TDD 先增加合并引用、非合并别名根、同名工作区/引用和异常父链回归并确认 4 项失败，再实现纯函数；随后增加 `AgentWorkbench` 接入断言并确认失败，再完成稳定节点展开。
  - 保持 `WorkbenchFooter`、`FigmaEditorArea` 的事件签名和 `EditorTab` 数据结构不变，没有新增接口或全局定位服务。
- Result:
  - 引用文件可通过页脚按钮或标签双击精确展开到实际引用节点；同名冲突不会误选工作区副本，普通文件定位还消除了未等待异步目录展开就提前滚动的时序问题。
  - 不涉及 HTTP API、RunEvent、数据库、backend、OpenCode manager、generated SDK、依赖、安全或环境配置。
- Verification:
  - 定向 Vitest 3 个文件 31/31 通过；全量 Vitest 85 个文件 1406 passed / 1 skipped。
  - 全仓 `corepack pnpm typecheck`、`corepack pnpm lint`、生产 `corepack pnpm build` 和 `git diff --check` 通过；构建仅保留既有大 chunk 警告。
  - 首次把全量 Vitest 与 typecheck/lint 并行运行时，`opencode-timeline` 一项出现时序失败；该用例定向复跑通过，随后独立全量 Vitest 复跑全部通过。

### 2026-07-19 - Flowchart 快捷图形选中后立即收起菜单

- Why:
  - Flowchart 快捷建连选择备选图形后只发出了建连事件，没有清空当前快捷箭头状态，Teleport 备选菜单会继续遮挡画布。
- What:
  - 快捷图形选中时先清理菜单关闭/箭头悬停定时器并复位活动箭头，再沿用原有 `quickConnect` 数据创建节点和连线；四向快捷箭头、端口与建连规则不变。
  - 同步前端总览和 editor 包 README，明确备选图形选中后菜单立即收起。
- How:
  - TDD 先新增“选择快捷图形后立即关闭备选菜单”组件用例并确认旧实现失败；代码审查进一步覆盖节点/箭头离开、进入 Teleport 菜单再点击的真实路径，确保菜单和已离开节点的四向箭头同步收敛。
- Result:
  - MermaidVisualEditor 组件测试 121/121、前端全量 Vitest 1401 passed / 1 skipped、lint、typecheck、production build 和 `git diff --check` 通过；构建只保留既有大 chunk 警告。
  - 不涉及 API、RunEvent、数据库、性能、安全、依赖或环境配置。

### 2026-07-19 - 修复 scheduler PostgreSQL Map 别名导致的启动失败

- Why:
  - scheduler 与夜间任务仓储从 JDBC 迁移到 MyBatis `resultType="map"` 后使用未引用的驼峰 SQL 别名；PostgreSQL 将 `taskKey` 折叠为 `taskkey`，Java 读取 `row.get("taskKey")` 得到空值，启动同步代码注册任务时触发 `taskKey must not be null`。
- What:
  - `ScheduledTaskMapper.xml` 与 `NightExecutionTaskMapper.xml` 的全部驼峰 Map 别名改为双引号精确别名，覆盖任务、计划、运行记录、夜间任务和容量统计读取。
  - 两组 MyBatis 集成测试改用 H2 `DATABASE_TO_LOWER=true` 模拟 PostgreSQL 标识符折叠规则；同步 persistence README 的测试覆盖说明。
- How:
  - TDD 先在旧 Mapper 上复现 scheduler `taskKey` 和夜间任务 `taskId` NPE（4 个用例均失败），再做最小 XML 修复并复跑相同用例。
  - 未修改 migration、数据库结构、API、RunEvent、环境配置或 generated SDK。
- Result:
  - 两组定向集成测试 4/4 通过；后端 18 模块 `mvn clean package -Dmaven.test.skip=true` 成功。
  - 使用 `.env.test` / `test` profile 单独启动后端，Flyway 62 个 migration 校验成功、Redis 探测成功，`/actuator/health` 返回 `UP`，原始 NPE 未再出现。
  - persistence 全量测试仍被既有 `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（160 tests，76 errors），与本次别名修复无关；三服务脚本还因本机缺少 `go` 未启动 manager/frontend，本次仅验证后端。

### 2026-07-19 - 实施夜间异步执行任务

- Why:
  - 公司白天算力不足，需要用户在现有对话中预先提交北京时间 21:00 至次日 07:00 的一次性任务，并在 15 分钟容量时段内无人值守启动；已有会话待执行期间必须锁定普通对话，执行过程继续复用现有 Session/Run/RunEvent 展示。
- What:
  - scheduler 增加一次性 `USER_PLAN`、Linux 执行亲和、按运行 ID 分布式锁和有界 worker；既有 scheduler JDBC 仓储迁移为 MyBatis XML。夜间任务新增领域模型、Flyway/MyBatis 主表/会话锁/容量占位、创建/查询/改期/取消/失败卡关闭 API，以及 5 分钟恢复、同夜顺延、07:00 最终失败和 30 天清理。
  - 到期投递重新校验 owner、Session、Workspace 和权限，binding 迁移时重建亲和计划，通过公共 `UserOpencodeProcessAssignmentService.initialize` 启动进程，再调用既有 `RunApplicationService` 创建带 `SCHEDULED_TASK` 来源的 Run 和 USER 消息；手工发送、追加消息和归档在会话持锁期间由后端硬拦截。
  - 当前对话发送按钮左侧新增定时图标和 15 分钟时段选择；“对话 / 待执行任务”页签分页收齐全部任务，展示预览、计划时段、创建时间、状态和顺延次数，支持改期/内联确认取消。空白草稿事务内创建新 Session；任务启动后回到既有消息与 Run 展示，来源标签显示北京时间实际启动时间。
  - 创建请求纳入现有 opencode 后端路由并复用 32 MiB 请求体硬上限；容量竞争返回最新时段详情，前端立即重取选择器。同步 HTTP、事件、数据库、部署、安全、架构、前后端 README/PACKAGE 和内置用户手册；未新增 RunEvent 类型，也未修改 generated SDK 或真实环境配置。
- How:
  - 创建、改期、取消和终态迁移使用事务、条件更新、PostgreSQL advisory lock、时段原子占位及 `scheduledTaskRunId` fencing；完整 prompt/parts 只保存在业务表，响应、日志和 scheduler result 仅暴露安全预览，终态清除完整输入。
  - 列表使用 owner/status/slot 索引并由前端按后端最大 200 条逐页收齐；runner 的扫描批量、worker、队列和每时段容量均显式配置。`TEST_AGENT_NIGHT_EXECUTION_SLOT_CAPACITY` 缺失时应用继续启动，但夜间 API 失败关闭为 `NIGHT_EXECUTION_UNAVAILABLE`。
  - 完成前回顾 `.agents/session-log.md`、`.agents/session-log.huangzhenren.md` 和 `.agents/session-log.rkk222.md` 的近期条目，保留已提交的工作区目录移动、Mermaid 与引用资产成果；没有修改已发布的 `V20260717173000`。
- Result:
  - 后端夜间/runtime/scheduler/API/路由定向测试通过，`mvn -q clean package -DskipTests` 18 模块通过；前端全量 Vitest 85 文件为 1393 passed / 1 skipped，lint、typecheck、用户手册和生产 build 通过，桌面/移动夜间 Playwright 4/4 通过，构建仅保留既有大 chunk 提示。
  - 后端全量 `mvn test` 仍只在 persistence 被已发布 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `timestamptz` 与当前 H2 2.4 不兼容阻断（76 errors）；该问题已在前序日志和干净基线确认，生产 PostgreSQL 支持，本次按最小范围没有篡改历史 migration。
  - 涉及新增内部 HTTP API、兼容性 DTO 来源字段、两份 scheduler/夜间 Flyway 增量和运行时配置；不新增 SSE 事件、不改变鉴权角色、不输出敏感输入。生产启用前需配置正整数时段容量，并建议在真实多 Java/夜间窗口验证 binding 迁移、进程自动启动和算力容量。
- Verification:
  - `mvn -q -pl test-agent-opencode-runtime,test-agent-api -am -Dtest='NightExecution*Test,UserOpencodeBackendRoutingWebFilterTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -q -pl test-agent-persistence -am -Dtest='MyBatisNightExecutionTaskRepositoryIntegrationTest,MyBatisScheduledTaskRepositoryIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -q clean package -DskipTests`
  - `corepack pnpm test`、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build`
  - `corepack pnpm e2e --grep "night task"`
  - `git diff --check`、全部 session log 近期条目回顾和最终只读差异审查。

### 2026-07-19 - 支持工作区文件和目录拖拽移动

- Why:
  - 工作区文件树原先只能拖动普通文件，目录不能整体移动，且组合引用树中的只读来源缺少完整的拖源/落点阻断和移动后的状态收敛。
- What:
  - 文件树允许可写纯 `WORKSPACE` 文件和目录作为拖源，保留浏览器原生拖影并提供抓取、半透明源行和蓝色合法落点；只读、纯 `REFERENCE`、整棵 `MIXED` 不可拖，纯引用目录、文件行、当前父目录、自身和后代目录吞掉非法落下，带 `workspacePath` 的 `MIXED` 目录仍可接收工作区条目。
  - app 层沿用 `workspace.move` 与反向移动撤销，移动后迁移已打开子文件 Tab、活动/Diff/请求路径和展开路径，按新 `workspacePath` 补齐祖先并逐层认领组合树的新稳定 ID，再刷新 Git Diff；无快照的加载中 Tab 在新刷新代次建立后补读。
  - 后端 `WorkspaceFileService.moveFile` 支持普通文件和非空目录的一次整体移动，拒绝根、符号链接/特殊文件、越界、冲突和目录自身后代。Linux 从 `/` 逐段固定目录句柄后通过 JNA 直接调用内核 `renameat2(RENAME_NOREPLACE)`，macOS 使用 `renameatx_np`，Windows 使用已核对的源条目/目标父目录句柄和 `SetFileInformationByHandle`；目标并发创建不覆盖，校验后路径替换失败关闭。
  - 同步前后端 README、workspace/file-explorer 包说明、前后端规范和文件 WebSocket 协议文档；RPC 名称、请求/响应 DTO 与错误码集合不变。
- How:
  - 按 TDD 覆盖文件/目录拖源和视觉状态、根/目录合法落点、引用与 `MIXED` 边界、非法落点冒泡阻断、拖拽清理、目录移动后的 Tab/展开/撤销收敛，以及非空目录、同路径、根/后代/冲突/符号链接/特殊文件/越界、路径替换竞态和目标并发创建。
  - 项目实际镜像 `eclipse-temurin:21-jre-alpine` 使用 musl 且不导出 `renameat2` 包装函数，因此无需修改镜像，直接调用同一 Linux 内核 syscall；已在该镜像完成普通移动与目标不覆盖 smoke。完成前回顾全部 `.agents/session-log*.md` 近期条目并只暂存本任务文件，保留并行夜间任务等未提交改动。
- Result:
  - 前端定向 Vitest 40 项、目录移动 Playwright Chromium/mobile 2 项、全量 Vitest 1383 passed / 1 skipped、lint、typecheck 和生产 build 通过；构建仅保留既有大 chunk 提示。
  - 后端 workspace 定向 26 项、workspace/API reactor 测试通过，`mvn clean package -DskipTests` 18 模块通过；全量 `mvn test` 仅被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 PostgreSQL `TIMESTAMPTZ` 与当前 H2 不兼容阻断（persistence 76 errors），本次未修改该 migration。
  - 不涉及数据库、RunEvent SSE、generated SDK、跨服务器路由、鉴权模型或环境配置；新增 workspace 模块对既有版本 JNA 5.14.0 的直接编译依赖。macOS 与项目实际 Linux 镜像已运行验证；Windows 实现已编译并通过专项代码审查，但发布前仍建议在 Windows x64/arm64 验证普通/目录移动、目标冲突与 junction 竞态。
- Verification:
  - `corepack pnpm vitest run packages/file-explorer/tests/DirectoryRows.test.ts packages/file-explorer/tests/FileExplorer.test.ts apps/agent-web/tests/workspaceViewState.test.ts`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep 'workspace directory move'`
  - `corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm test`、`corepack pnpm build`
  - `mvn -q -pl test-agent-workspace-management -Dtest=WorkspaceFileServiceTest test`
  - `mvn -pl test-agent-api -am test`、`mvn test`、`mvn clean package -DskipTests`
  - 项目实际 Alpine JRE 镜像 Linux 安全移动 smoke、`git diff --check`、全部 session log 近期条目回顾和最终只读代码审查。
- Next:
  - 在真实 Windows x64/arm64 环境补充原生移动 smoke；既有 H2 migration 兼容问题另行修复，不属于本次拖拽移动范围。

### 2026-07-18 - 新增 Mermaid State Diagram 可视化编辑

- Why:
  - 现有 Mermaid 编辑器只支持 Flowchart 与 Sequence，无法安全编辑 State Diagram 的递归层级、并发区域和伪状态；直接复用 Flow 的扁平模型与重复边规则会破坏 State 语义。
- What:
  - 新增 `stateDiagram` / `stateDiagram-v2` 递归 Scope/Region 领域模型、自研 parser、规范化 serializer、严格校验、B1 紧凑 metadata 和分层 ELK 布局；支持开始/结束、普通状态与多行说明、自循环、标签转换、复合/嵌套状态、Choice、Fork/Join、并发 Region、Note、各层方向和限定直接样式。
  - 新增“概览 + 聚焦”State 画布：根层展示复合状态摘要，双击进入内部，面包屑返回，聚焦层同时展示全部并发 Region；支持元素点击/拖放创建、状态与转换就地编辑、拖线/端点重连、Note 属性和受限颜色编辑。
  - 把通用屏幕坐标拖线控制器抽象为可注入领域连接规则，Flow 保持原规则，State 允许同端点多转换并阻止跨 Scope/Region、伪状态非法方向和基数上限；外部包导出、Markdown 围栏并发保护、官方 Mermaid 双重校验与保存链路不变。
  - 扩展领域、布局、组件、Markdown 和 workbench Playwright 回归；同步前端总 README、editor README/PACKAGE、模块地图以及已批准的设计/执行计划。
- How:
  - 按 TDD 从解析/序列化/校验/metadata 开始，再实现递归布局、概览/聚焦画布和 Markdown 集成；无法安全映射的结构语法降级源码编辑，注释、复杂样式、class/accessibility 指令按 Scope 原样保留。
  - State 应用时先做完整领域校验，再生成规范源码并调用 Mermaid 11.16.0 官方 parser；任一步失败都保留草稿且不覆盖 Markdown。坐标与转换端口继续使用既有紧凑 envelope，损坏/重复 marker 原样保留且不制造第二个 marker。
  - 精确排除共享工作区中并行出现的 backend、scheduler、workspace、AgentWorkbench、file-explorer 等无关改动，只暂存 State 编辑器相关文件和本条记录。
- Result:
  - editor 全量 20 个测试文件、402 项通过；前端全量 Vitest 84 个文件通过（1383 passed / 1 skipped），13 个 workspace 的 lint/typecheck、用户手册和生产 build 通过。目标 workbench Playwright Chromium 1/1 通过，验证 Flowchart、Sequence、State 共用一次 workspace 保存链路。
  - 生产构建确认 `MermaidEditorDialog` 与 `elk.bundled` 仍为独立懒加载资源；只保留既有大 chunk 提示。未新增初始页面依赖，DOMPurify 边界未变。
  - 不涉及后端、HTTP API、RunEvent/广播、数据库、SQL、migration、generated SDK、环境配置、安全权限或外部包导出；Flowchart 与 Sequence 回归全部通过，无未完成编码项。
- Verification:
  - `corepack pnpm exec vitest run packages/editor/tests --reporter=dot`
  - `corepack pnpm test`、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --project=chromium -g "Markdown Mermaid Flowchart、Sequence 和 State"`
  - `git diff --check`、全部 session log 近期条目回顾和共享工作区暂存范围复核。
- Next:
  - 在真实浏览器使用超深嵌套、超长 Note/状态说明和大量并发 Region 做人工可用性验收；不影响本次交付范围。

### 2026-07-18 - 定稿夜间定时执行任务设计

- Why:
  - 白天算力不足，需要让用户在现有对话中提交一次性夜间任务，并在北京时间 21:00 至次日 07:00 的 15 分钟时段内无人值守执行。
- What:
  - 新增夜间任务设计规范，确认定时图标位于发送按钮左侧，取消“执行位置”选择；任务始终绑定当前对话，需要新会话时先使用现有新建对话入口。
  - 待执行任务采用当前对话锁定卡与“待执行任务”页签双重展示；同一 Session 只允许一个待执行任务，但其他会话仍可使用。
  - 明确全局时段硬容量、最空闲且尽量早的推荐、同夜窗口顺延、07:00 最终失败、进程自动启动、来源标识和失败卡关闭规则。
  - 技术设计复用 scheduler `USER_PLAN`、运行记录、Redis 锁和 handler；一次性正文放独立业务表，新增 MyBatis/Flyway、执行亲和和服务端内部 scheduled Run 入口，不使用 Cron 计划 payload 保存正文。
- How:
  - 结合现有 `FigmaChatPanel`/`AgentWorkbench`、Session/Run 来源字段、scheduler runner、用户进程公共启动与多 Java 路由约束，固定交互、API、状态机、数据安全、并发容量和测试验收边界。
  - 设计自检消除了“空 Session 最终失败即归档”与“失败状态可见”的冲突：最终失败先解除锁并保留可关闭卡片，关闭后才按是否为空决定归档。
- Result:
  - 本次仅新增设计文档和本条会话记录，未修改代码、API、RunEvent、数据库、环境配置或 generated SDK，也未触碰工作区内并行开发的已有修改。
  - 设计规划未来新增普通用户 HTTP API、来源 DTO 字段、Flyway/MyBatis 数据模型和 scheduler USER_PLAN 能力；待用户复核规范后再编写实施计划。
- Verification:
  - `git diff --check -- docs/superpowers/specs/2026-07-18-night-execution-task-design.md .agents/session-log.huangzhenren.md`
  - 规范占位符、内部一致性、范围和歧义自检；提交前回顾全部 `.agents/session-log*.md` 近期条目。
- Next:
  - 用户确认书面规范后，按 `superpowers:writing-plans` 生成可执行实施计划。

### 2026-07-18 - 增强 Mermaid SequenceDiagram 结构化编辑能力

- Why:
  - 既有 Sequence 编辑器只有参与者与平铺消息，无法安全表达控制片段、生命周期、激活、Note 和嵌套调用，也不能像 Flowchart 一样在画布、结构和属性之间完成可视化编辑。
- What:
  - 将 Sequence 领域模型升级为递归语句 AST，覆盖 8 类参与者、别名/box、10 种标准箭头、消息、Note、注释、显式与快捷激活、自调用、create/destroy、autonumber、多行文本，以及 `loop`、`alt/else`、`opt`、`par/and`、`critical/option`、`break`、`rect` 任意嵌套。
  - parser 改为 tokenizer + 显式容器栈，serializer 依据源码锚点、父容器和语义指纹最小差异回写；CRLF、缩进、大小写、旧紧凑坐标 metadata 与未修改快捷语法保持不变。半箭头、中央连接、Actor 菜单、标题/无障碍指令、`par_over`、分号串联和未知配置作为局部锁定纯文本无损保留。
  - 新增不可变命令/语义校验与确定性布局，统一处理重命名引用、跨分支移动、端点重绑、级联删除、分组连续性、分支生命周期与激活规则；`create` 保持全图唯一，互斥分支 `destroy` 状态隔离。
  - Sequence 使用单个专用 SVG 场景和“元素 / 结构 / 属性”右侧三标签；支持元素点击/拖放创建、生命线拖建消息、消息端点拖拽重绑、参与者横向拖序、结构树键盘/拖放、画布双向选中、就地编辑和带影响数的参与者级联删除确认。Vue Flow 仅复用视口能力。
  - 扩展 Markdown、组件、领域、命令、布局和 workbench mock E2E；将测试引导键同步到产品当前 `onboarding.v7`，恢复目标 Chromium/mobile 场景。同步设计、实施计划、前端总 README、editor README 与包级说明。
- How:
  - 按 TDD 分层补齐 parser/serializer、命令、布局、组件和保存链回归；最终复审发现互斥分支重复 `create` 漏检后，先用命令测试复现，再共享全图 `created` 集合并继续按分支克隆 `destroyed`，修复后复审无 P0/P1。
  - 精确排除同一工作树中并发出现的 backend、API/规范、AgentWorkbench、file-explorer、夜间执行设计等无关变更，只暂存本任务文件与本条记录。
- Result:
  - Sequence 定向 6 文件 89 项通过；前端全量 Vitest 81 个文件通过（1353 passed / 1 skipped），lint、13 个 workspace typecheck、用户手册与生产 build 通过；目标 workbench Playwright 在 Chromium/mobile 2/2 通过，构建仅保留既有大 chunk 提示。
  - 不涉及 HTTP API、RunEvent/广播、数据库、后端、manager、generated SDK、依赖版本、环境配置、安全权限或既有 Markdown 保存契约；高级语法首批按设计仅锁定保留，没有未完成的编码项。
- Verification:
  - `corepack pnpm exec vitest run packages/editor/tests/mermaid-sequence-domain.test.ts packages/editor/tests/mermaid-sequence-commands.test.ts packages/editor/tests/mermaid-sequence-layout.test.ts packages/editor/tests/SequenceVisualEditor.test.ts packages/editor/tests/MarkdownPreview.test.ts packages/editor/tests/mermaid-compact-metadata.test.ts --reporter=dot`
  - `corepack pnpm test`、`corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm build`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep "Markdown Mermaid Flowchart 和 Sequence"`
  - `git diff --check`、全部 session log 回顾、冲突标记扫描与最终只读代码复审。
- Next:
  - 在真实浏览器手工验证超深嵌套和超长多行文本的可用性；半箭头、中央连接、Actor 菜单等高级创建入口继续按既定后续范围评估。

### 2026-07-18 - 点击引用资产库展示真实同步进度

- Why:
  - 引用配置左侧已初始化资产库卡片实际调用 `synchronizeReferenceRepository`，但现有步骤弹层只绑定右侧 `/verify` 且只接受 `operation=VERIFY_POINTERS`，导致卡片同步期间页面没有可见进度。
- What:
  - 将核验专用弹层状态抽象为 `SYNCHRONIZE/VERIFY_POINTERS` 操作感知状态机；卡片在同步 POST 返回前打开“创建同步任务 → 各服务器同步 → 汇总同步结果”，右侧按钮继续展示只读核验步骤，两者不互相追加请求。
  - 同步模式逐服务器展示等待同步、同步中、已同步、同步失败、等待重试和离线延后；POST 失败可原地重试，活动任务可直接接管既有 generation，终态手动关闭后焦点回到触发卡片。
  - 弹层继续按仓库、operation、请求序号和 generation fencing，复用既有 2 秒 `/status` 轮询、父弹层锁定和焦点限制；未改后端接口或状态语义。
  - 同步前端工程/agent-web README、PACKAGE、引用配置用户手册以及方案/实施计划。
- How:
  - TDD 先增加 deferred 同步请求回归，确认原实现只因缺少 `资产库同步进度` 失败，再抽象进度状态、动态文案和触发焦点；补充同步请求失败重试、活动同步接管、未初始化兼容和卡片焦点恢复测试。
  - 全量校验首次并行执行时，`typecheck/lint` 同时写 VitePress `.temp` 引发临时模块竞争，agent-chat 异步 Markdown 用例也在资源竞争下超时；两项独立复跑及全量顺序复跑均通过，未修改无关代码。
- Result:
  - 引用配置组件测试 45/45 通过；前端全量 Vitest 79 个文件通过（1309 passed / 1 skipped），13 个项目 typecheck、lint、用户手册和生产 build 通过，构建仅保留既有大 chunk 提示。
  - 仅改变前端交互和稳定文档；不涉及 HTTP API、RunEvent/广播、数据库、后端、manager、generated SDK、环境配置或安全权限。Playwright 仍沿用上一条记录中的 onboarding v2/v7 既有基线，本次未扩大范围修改。
- Verification:
  - `corepack pnpm exec vitest run apps/agent-web/tests/reference-configuration-dialog.test.ts`
  - `corepack pnpm test`、`corepack pnpm typecheck`、`corepack pnpm lint`、`corepack pnpm build`
  - `git diff --check`、全部 session log 回顾和只读差异审查。
- Next:
  - 在真实多服务器环境点击左侧资产库卡片，人工确认长耗时同步的逐节点状态与后台实际 HEAD 收敛一致；Playwright 引导基线修复继续作为独立任务。

### 2026-07-18 - Service 日志切面改为仅在异常时打印

- Why:
  - `ServiceLoggingAspect` 对每个 `@Service` public 方法都打 `service_entry` + `service_exit(success)` 两条 INFO，正常调用产生大量噪声（如 `PublicAgentConfigRolloutService.claimPendingSync` 每 5 秒一条），单条 INFO 不携带结果或异常，诊断价值低。
- What:
  - 删除 `service_entry`、成功 `service_exit`、Mono `doOnSuccess`、Flux `service_stream_start/end`；仅保留异常时 ERROR 日志，并在其中补 `args` 字段（沿用 `argsSummary`，仅类型+轻量值，避免泄漏 prompt/token）。
  - 同步 `package-info.java` 与 `test-agent-api/README.md` 中该切面的描述。
- How:
  - 改 `logServiceCall`：正常返回静默；Mono/Flux 仅挂 `doOnError`；同步异常走 `catch` 记录后原样抛出。
  - `argsSummary` 改为仅在 `logError` 内惰性计算，去掉每次成功调用都算参数摘要的开销；`argsSummary`/`argSummary` 方法保留（有单测直接覆盖）。
- Result:
  - `mvn -pl test-agent-api -am test -Dtest=ServiceLoggingAspectTest -Dsurefire.failIfNoSpecifiedTests=false`：4 passed / 0 failures，BUILD SUCCESS。
  - 仅日志行为变化，无 API/事件/数据库/兼容性影响；事件名 `service_exit status=error` 保留，兼容既有日志查询。
- Pitfalls:
  - 正常调用不再有任何日志；原先依赖 `service_entry` 作为调用频率心跳的观测需改用其他来源。traceId=unknown 的后台调用异常仍会记录。
- Verification:
  - `mvn -pl test-agent-api -am test -Dtest=ServiceLoggingAspectTest -Dsurefire.failIfNoSpecifiedTests=false`
- Next:
  - None。

### 2026-07-18 - 引用资产 Git 指针核验进度与服务器路径

- Why:
  - “刷新 Git 指针”需要等待多服务器核验，原页面只有按钮转圈，用户无法判断任务创建、各节点处理和汇总所处阶段；已选仓库也缺少服务器绝对目录，现场排查不便。
- What:
  - 引用资产库统一状态响应新增可空 `repositoryPath`，由当前平台解析后的 `OPENCODE_REFERENCES_DIR` 与已校验英文名拼接并规范化；参数缺失、历史非法名称或旧响应缺字段时前端显示“服务器路径暂不可用”，不阻断仓库列表。
  - “刷新 Git 指针”在 POST 返回前打开三阶段嵌套弹层，展示创建核验任务、逐服务器核验和汇总结果；逐节点映射等待、处理中、完成、阻塞、等待重试与离线延后，并展示目标分支/HEAD、就绪数量、安全错误和 traceId。
  - 弹层按仓库 ID、请求序号与核验 generation fencing；执行期间禁止关闭弹层和外层引用配置页、拦截 Escape 并限制焦点，终态由用户手动关闭后恢复到当前刷新按钮。POST 失败可重试，状态轮询临时失败沿用 2 秒自动重试。
  - 同步 HTTP API、workspace/API README 与 PACKAGE、前端/backend-api 说明、模块地图、安全规范和引用配置用户手册；未新增接口、事件、广播、数据库、manager 协议或环境配置。
- How:
  - 继续复用既有 `/verify`、`/status` 与状态轮询；后端只在业务响应组装时派生路径，并捕获参数/旧数据路径错误返回空，不记录物理路径。
  - TDD 覆盖路径规范化、缺参、历史非法英文名、Controller JSON、旧响应兼容、弹层先于请求完成出现、三阶段/逐服务器状态、成功/失败/离线、轮询暂时失败、重试、关闭限制和焦点恢复。
  - 调试中确认 Vue 更新仓库快照会替换弹层与按钮 DOM，测试必须断言当前节点；业务状态实际已正确推进，未用放宽状态机掩盖失败。
- Result:
  - 后端定向 reactor 通过：workspace 47 项、API 3 项；前端全量 Vitest 79 个文件通过（1305 passed / 1 skipped），13 个项目 typecheck、lint、用户手册和生产 build 通过，构建仅保留既有大 chunk 提示。
  - 引用组合树 Playwright 的 Chromium/mobile 场景均渲染出目标节点，但当前 `HEAD` 的首次引导已使用 `v7`，E2E 基线仍写入 `test-agent.onboarding.v2`，`el-tour` 遮罩拦截点击后两项超时；本次未扩大范围修改该既有基线。
  - 仅增加向后兼容的可空 HTTP 响应字段；路径只对既有 `APP_ADMIN` 接口可见且不进入日志/错误。未修改 RunEvent、内部广播、Flyway/MyBatis、generated SDK、manager 或 `.env.local`。
- Verification:
  - `mvn -pl test-agent-workspace-management,test-agent-api -am -Dtest='ReferenceRepositoryApplicationServiceTest,ReferenceRepositoryControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `corepack pnpm test`、`corepack pnpm typecheck`、`corepack pnpm lint`、`corepack pnpm build`
  - `corepack pnpm e2e --grep "workspace tree merges references with source colors and exposes non-merged aliases"`（被上述既有 v2/v7 引导基线阻断）
  - `git diff --check`、session log 回顾与只读差异审查。
- Next:
  - 在独立任务中把 Playwright 的首次引导抑制键从硬编码旧版本改为与产品版本同步，再恢复引用组合树 Chromium/mobile 定向绿灯；真实多服务器环境仍需按上一条 session log 的计划完成实际路径与 HEAD 人工验收。

### 2026-07-18 - 引用资产分支切换与服务器指针核验

- Why:
  - 应用管理员需要在引用资产库初始化后受控切换分支并同步所有服务器，同时在配置页查看每台服务器实际 Git branch/HEAD，确认多节点是否真正收敛。
- What:
  - 新增 `switch-branch` 与只读 `verify` 内部 API、`SWITCH_BRANCH/VERIFY_POINTERS` 操作类型、逐服务器 `online/matchesTarget/verifiedAt/syncedAt` 响应，以及 `operation_type/verified_at` Flyway 增量迁移和 MyBatis XML 映射。
  - 分支切换以远端 `ls-remote` 固定目标提交并 CAS 推进 generation；worker 写回实际读取的 branch/HEAD，核验只读本地 Git 元数据。活动任务和历史副本由广播、租约 fencing、在线/离线补偿继续收敛。
  - 配置弹窗增加分支选择和旧/新分支二次确认、目标指针与逐服务器实际指针/在线状态/匹配状态/同步及核验时间、完整 HEAD 复制和“刷新 Git 指针”。成功切换后刷新工作区引用组合树。
  - 前端以选择代次、后端 generation、同代次请求发起序号三层 fencing 丢弃迟到状态；READY 先通知工作区刷新，再独立加载弹窗目录。关闭/重开、失败后重新同步、模糊请求结果和多仓库 pending 均保留有限补偿语义。
  - 最终审查补出并修复 single-branch clone 切换分支缺陷：切换时显式 fetch `+refs/heads/<branch>:refs/remotes/origin/<branch>`，不存在的本地分支从已固定提交创建，不依赖旧 fetchspec 的 `--track`；已有目标本地分支仍执行可快进校验。
  - 同步更新 HTTP、事件、数据库、后端部署、模块 README/PACKAGE、前端包说明和用户手册。
- How:
  - API 与业务层继续要求 `APP_ADMIN`（`SUPER_ADMIN` 继承），并复核应用关联和 `APPLICATION_ASSET_REPOSITORY` 类型；广播名称及仅含 `repositoryId/generation/traceId` 的安全 payload 保持不变，不进入 RunEvent SSE。
  - 数据库副本保留上一代实际指针快照，新 generation 不用目标值伪造实际值；`matchesTarget` 只对完整可信的 `READY` 观察为真。核验使用禁用 optional lock、untracked cache 和 fsmonitor 的只读 Git status。
  - TDD 先复现 single-branch 无共同历史分支切换失败、READY 目录请求期间关闭导致刷新丢失、同 generation 迟到回滚、被拒快照误消费 pending，以及重开首轮列表失败后补偿轮询停摆，再做最小修复；最终综合审查无 Critical/Important。
- Result:
  - 引用/Git/API/MyBatis/Flyway 定向 114 项通过，含真实 single-branch clone 切换到无共同历史分支；后端用户指定 Reactor 中除 persistence 外均成功，persistence 仍被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 与 H2 不兼容统一阻断（76 errors），本次引用 MyBatis/PostgreSQL/migration 用例均通过。
  - 前端全量 Vitest 77 个文件通过（1290 passed / 1 skipped），13 个项目 typecheck、lint、用户手册和生产 build 通过；构建仅有既有大 chunk 提示。manager `go test ./...` 的 6 个包通过。
  - 全量 Playwright 为 123 passed / 70 failed / 1 skipped；失败是近期日志已记录的 35 类 workbench 文件加载、Agent、Help、设置、模型和运行流旧基线在 Chromium/mobile 的成对失败，精确数量不能与此前提前停止的套件等同。本次引用组合树 Chromium/mobile 定向 2/2 通过。
  - 涉及新增内部 HTTP API、数据库兼容字段和既有内部广播消费语义；不修改广播 payload、RunEvent、generated SDK、`.env.local`、manager 协议或已运行 OpenCode 进程。显式 refspec 不经 shell，凭据仍只在发起/目标 worker 内使用。
- Verification:
  - `mvn -pl test-agent-api,test-agent-persistence -am -Dtest='GitWorkspaceServiceTest,GitWorkspaceServiceRealGitTest,ReferenceRepository*,MyBatisReferenceRepository*' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl test-agent-common,test-agent-domain,test-agent-workspace-management,test-agent-opencode-runtime,test-agent-persistence,test-agent-api -am test`（除上述既有 H2 migration 阻断外，其余目标模块成功）
  - `corepack pnpm test && corepack pnpm typecheck && corepack pnpm build`、`corepack pnpm lint`
  - `corepack pnpm e2e`、`corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep 'workspace tree merges references'`
  - `go test ./...`、`git diff --check`、冲突标记扫描和三轮只读代码审查。
- Next:
  - 在真实双服务器环境完成分支切换、部分节点离线/恢复、逐服务器 HEAD 展示与引用组合树刷新人工验收；另行修复既有 H2 migration 和 workbench E2E 基线。

### 2026-07-18 - 在应用工作区文件树展示引用目录

- Why:
  - 引用配置保存后，应用工作区文件树还只能读取物理工作区，无法按 `merge` 展示引用资产，也无法区分只读引用内容。
- What:
  - 新增 `workspace.view.list/read` 文件 WebSocket 组合视图：`merge=true` 按 `sdd-folder-name` 合并到工作区同名一级目录，纯引用文件/目录标记为蓝色只读；工作区已有同名目录返回 `MIXED` 并保持普通颜色。`merge=false` 以参考别名作为只读一级目录。
  - 后端每次从最新 JSONC 重建挂载，只接受能反向验证到当前应用资产库、本机同 generation READY 副本和平台引用根目录的配置；拒绝物理路径伪造、`.git`、路径穿越与符号链接。单引用失败转为局部 warning，每层最多 1000 项。
  - 工作区目录从 `WORKSPACE` 变为 `MIXED` 时保持稳定 ID；前端刷新按目录深度逐层用新父节点返回的 locator 重放展开状态，并汇总、去重所有已加载目录的 warnings/truncated，恢复后清除旧告警。
  - 引用文件可只读打开、重试、复制逻辑路径和加入对话上下文；保存、重命名、删除、移动、粘贴及撤销入口按来源关闭，普通工作区写操作继续走原 RPC。
  - 加固文件通道权限：托管工作区在 route、ticket 和每条 RPC 都实时校验有效应用成员；非托管 Workspace 默认拒绝，仅 `SUPER_ADMIN` 服务器工作空间兼容入口放行。会话运行上下文对历史非托管 Workspace 的 Session owner 兼容规则不变。
  - 同步 HTTP/文件 WebSocket 协议、安全规范、后端模块 README/PACKAGE、前端包说明和用户手册。
- How:
  - 组合视图继续走平台文件 WebSocket route/ticket/RPC，不新增 Java 间 HTTP 文件代理；引用副本读取复用既有本机安全目录服务，不把物理路径返回浏览器。
  - 前端复用现有 FileExplorer，通过稳定节点 ID 作为缓存/展开键，并保留原 `workspace.list/read/write` 兼容调用。
  - TDD 覆盖 merge/alias、颜色与写入口、JSONC/READY/路径安全、稳定 ID、刷新 locator、告警恢复、鉴权及读失败重试；最终只读复审未发现 Critical/Important 并批准。
- Result:
  - 后端 workspace/runtime/api 聚焦测试 49 项通过；用户指定 Reactor 中 common/domain/workspace/runtime/api 全量均通过。persistence 仍被既有 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `TIMESTAMPTZ` 与 H2 不兼容统一阻断（76 errors），本次未改数据库或该 migration。
  - 前端全量 Vitest 77 个文件通过（1269 passed / 1 skipped），13 个项目 typecheck、用户手册和生产 build 通过；构建仅有既有大 chunk 提示。manager `go test ./...` 通过。
  - 新增引用树 Playwright 在 Chromium/mobile 2 项通过；三个既有工作区切换/未选择工作区场景在两项目共 6 项仍超时或找不到旧空态文案，本次没有扩大范围修复这些既有 E2E 基线。
  - 涉及文件 WebSocket 协议与权限安全边界；不新增 HTTP 端点、RunEvent、数据库表/migration、manager 协议或环境配置，不修改 generated SDK。组合视图为懒加载且每层限 1000 项；真实双服务器人工验收仍沿用上一条引用同步功能的待办。
- Verification:
  - `mvn -pl test-agent-workspace-management,test-agent-opencode-runtime,test-agent-api -am -Dtest='WorkspaceViewApplicationServiceTest,ManagedConversationWorkspaceAccessAuthorizerTest,WorkspaceFileRoutingServiceTest,WorkspaceFileSocketTicketServiceTest,WorkspaceFileWebSocketHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl test-agent-common,test-agent-domain,test-agent-workspace-management,test-agent-opencode-runtime,test-agent-persistence,test-agent-api -am test`（除上述既有 H2 migration 阻断外，其余目标模块成功）
  - `corepack pnpm test && corepack pnpm typecheck && corepack pnpm build`
  - `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep 'workspace tree merges references'`
  - `go test ./...`
  - `git diff --check`、冲突标记扫描和最终只读代码复审。
- Next:
  - 单独修复既有 H2 migration 与 workbench E2E 工作区选择基线后重跑完整套件；在真实双服务器部署完成引用副本与组合文件树人工验收。

### 2026-07-18 - 增加引用配置与资产库多节点同步

- Why:
  - 应用管理员需要在个人工作区内初始化应用资产库、保证所有在线 Java 服务器副本收敛到同一远端提交，并把可选的首层 SDD 目录安全写入工作区 `.opencode/opencode.jsonc`。
- What:
  - 新增引用库状态/副本领域模型、MyBatis XML 仓储、Flyway `V20260718110000`、APP_ADMIN 内部 API 与 `reference-repository.sync-requested` 内部广播；状态机按固定远端分支 HEAD、代次、数据库租约和定时补偿完成多节点同步，离线节点延后补齐。
  - Git 副本初始化采用同级临时目录和原子改名；已有副本校验 origin、分支、干净状态和快进关系，拒绝未知目录、脏仓库、分叉、符号链接及路径穿越，不删除或强制覆盖冲突内容。
  - 前端仅在 APP_ADMIN/SUPER_ADMIN 的个人应用工作区显示引用配置入口；双栏弹窗覆盖分支选择、2 秒状态轮询、逐服务器错误、首层橙色目录选择及 JSONC 保存/更新。`jsonc-parser` 以最小补丁保留注释、尾逗号、未知字段，并阻止非法 JSONC、非对象 references、Git/字符串同名引用和路径冲突。
  - 新启动及平台管理重启的 OpenCode 进程由公共启动服务注入 `OPENCODE_REFERENCES_DIR`；manager 安全命令展示该非敏感变量，未改变 configUpdate 协议或既有进程。
  - 同步 HTTP API、事件、数据库、部署、模块 README/PACKAGE、manager/前端说明及用户手册；修正既有参数种子 migration 注释中的 Flyway 占位符字面量，使 PostgreSQL 全量迁移可解析，参数值语义未变。
- How:
  - 复用现有应用关联、分支查询、服务器广播、在线服务器快照、公共启动服务和平台文件 WebSocket route/ticket/RPC；凭据只在发起节点解密，广播不携带凭据、SSH key、文件内容且不进入 RunEvent SSE。
  - 代次 CAS、租约 token/fencing 与本机文件锁共同避免并发 worker 回写；临时网络错误指数退避，永久 Git 冲突等待管理员重新同步；目录树每层限制 1000 项。
- Result:
  - 引用功能后端定向 reactor 通过，相关 122 项测试全部通过（含真实 Git、安全路径、租约/补偿、MyBatis H2、Testcontainers PostgreSQL、权限 API、配置冻结、运行时注入和 Spring 装配）；`go test ./...` 通过。
  - 前端全量 Vitest 75 个文件通过（1247 passed / 1 skipped），typecheck、lint、用户手册和生产 build 通过。
  - 用户指定的后端全量命令在 persistence 被既有 `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（76 errors；引用库 PostgreSQL/MyBatis 用例本身通过）；前端 E2E 在既有 `workbench.spec.ts` Agent 树隐藏/`agents` 目录缺失处连续失败 14 项，随后停止，2 项中断、176 项未运行。本次未扩大范围修改这些基线问题。
  - 本机没有真实双服务器环境，离线恢复、跨 Java 租约互斥和副本收敛由测试覆盖，仍需在双服务器部署环境执行计划中的人工验收。
  - 涉及新增内部 HTTP API、内部广播、两张数据库表和兼容性配置字段；不修改 generated SDK、`.env.local`、OpenCode 源码或 manager 协议。既有运行进程不重启，引用目录在下次平台管理启动/重启后生效。
- Verification:
  - `mvn -pl test-agent-app -am '-Dtest=GitWorkspaceServiceRealGitTest,ReferenceRepository*,ConfigurationManagementApplicationServiceTest,ConfigurationManagementControllerTest,OpencodeProcessStartupServiceTest,RuntimeManagementCommandServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl test-agent-persistence -am -Dtest=MyBatisReferenceRepositoryRepositoryIntegrationTest,MyBatisReferenceRepositoryPostgresqlIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `go test ./...`
  - `corepack pnpm test && corepack pnpm typecheck && corepack pnpm build`、`corepack pnpm lint`
- Next:
  - 修复既有 H2 migration 兼容和 workbench E2E Agent 树基线后重跑完整套件；在真实双服务器环境完成离线恢复与 HEAD 一致性人工验收。

### 2026-07-18 - 新增引用资产通用参数

- Why:
  - 需要为引用资产（规格文档、参考素材等）提供统一根目录参数 `OPENCODE_REFERENCES_DIR`，并约定规格驱动（SDD）场景识别规格目录的名称清单参数 `REFERENCES_SDD_FOLDER_NAMES`，作为后续引用资产消费功能的配置基础。
- What:
  - 新增 Flyway migration `V20260718100000__seed_references_params.sql`，向 `common_parameters` 种子化两个 `all` 平台参数：
    - `OPENCODE_REFERENCES_DIR` = `${SYS_DATA_ROOT_DIR}/agent-opencode/references`，中文名「引用资产根目录」，`editable=false`（只读，不允许修改）。
    - `REFERENCES_SDD_FOLDER_NAMES` = `docs,spec`，中文名「规格驱动标准目录名称」，`editable=true`（可改）。
  - `OPENCODE_REFERENCES_DIR` 的值用 `'$' || '{SYS_DATA_ROOT_DIR}/...'` 拼接规避 Flyway `${...}` 占位符替换，复用 `all` 行引用平台参数 `SYS_DATA_ROOT_DIR` 的解析能力，与 `OPENCODE_SESSION_DIR` 等路径参数一致。
  - 同步 `CommonParameterSeedMigrationTest`（SQL 内容校验）、`MyBatisCommonParameterRepositoryIntegrationTest`（迁移后断言）、`docs/deployment/database.md`、`backend/test-agent-persistence/README.md`。
- How:
  - 沿用既有种子迁移模式：`all` 平台单行 + 显式 `editable` 列；`SYS_DATA_ROOT_DIR` 引用沿用 consolidate 迁移（V20260629230000）的字符串拼接写法。
  - 用户原始值 `docs,spce` 经确认改为 `docs,spec`（SDD 规格目录通用命名，`spce` 疑为笔误；该值 editable，可后续调整）。
- Result:
  - `CommonParameterSeedMigrationTest` 2 个方法通过（含新增 `referencesParamsSeedContainsAllPlatformDefaults`）；BUILD SUCCESS。
  - `MyBatisCommonParameterRepositoryIntegrationTest` 因预存在问题无法端到端验证（见 Pitfalls），新增断言按构造正确。
  - 未修改 API、RunEvent、DTO、前端、安全、环境配置或 generated SDK；无新增依赖。
- Pitfalls:
  - main HEAD 上跑全量 Flyway 的 H2 测试（含 `MyBatisCommonParameterRepositoryIntegrationTest`）被 `V20260717173000__create_public_agent_config_rollouts.sql` 的 `timestamptz` 类型阻断（H2 不识别，已用干净 HEAD 复跑确认预存在，提交 `7b0df66a9` 引入）；本次未修（AGENTS 规则 1 最小范围）。生产 PostgreSQL 支持 `timestamptz`，迁移可正常执行；待该 H2 兼容问题修复后，本迁移可在 H2 端到端验证。
  - 工作区存在前一会话（同一提交者，今日「工作空间创建只允许选择测试工作库」）未提交的前端改动；任务期间并发会话已将其提交为 `1fee2cc3f 修复工作空间版本库类型筛选`（含前端代码与前一会话 session log 条目）。本次改动与前端无关，未暂存、未触碰前端文件；本提交 session log diff 仅含本次新条目。
- Verification:
  - `mvn -f backend/pom.xml -pl test-agent-persistence -am test -Dtest=CommonParameterSeedMigrationTest` 通过。
  - 干净 HEAD（`git stash -u`）复跑确认 `timestamptz` 失败与本次改动无关。
- Next:
  - 后续若有引用资产消费方，按 `CommonParameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")` 读取根目录、按 `REFERENCES_SDD_FOLDER_NAMES` 逗号拆分识别规格目录。

### 2026-07-18 - 工作空间创建只允许选择测试工作库

- Why:
  - 应用工作空间创建下拉此前直接使用全部已关联版本库，应用代码库或应用资产库也会被展示并可能默认选中，与工作空间只能关联测试工作库的业务约束不一致。
- What:
  - “工作空间管理”的已关联版本库下拉改为只展示测试工作库，并在标签后提示“只能关联类型为测试工作库的版本库。”；“应用与版本库关联”页仍展示全部类型。
  - 默认版本库选择同步从过滤后的列表产生；没有已关联测试工作库时保持空选择且不读取分支。
  - 同步 agent-web README/PACKAGE、内置用户手册和组件回归测试。
- How:
  - 按 `repositoryType=TEST_WORK_REPOSITORY` 筛选；显式类型优先，只有类型缺失时才回退 `standard=true`，兼容历史数据但不让冲突旧字段覆盖新类型。
  - TDD 先确认混合类型下拉仍显示并默认读取应用代码库，再实现派生列表；将旧的非测试工作库创建用例替换为空选择回归。
- Result:
  - 前端全量 Vitest 72 个文件通过（1206 passed / 1 skipped）；用户手册、Vue TypeScript 检查和生产 Vite build 通过，构建仅保留既有大 chunk 提示；`git diff --check` 通过。
  - 未修改 API、RunEvent、DTO、数据库、后端、安全、环境配置或 generated SDK；无新增依赖。

### 2026-07-17 - 会话日志改为按提交者分文件

- Why:
  - 多人/多智能体同时修改单一 `.agents/session-log.md` 频繁冲突；实测两次读取间隔内顶部条目已变化，确认存在活跃并发写。按 `git config user.name` 分文件可消除并发写冲突。
- What:
  - 修改 `AGENTS.md` 规则 22/23 与完成标准、`docs/guides/self-checklist.md`、`docs/guides/ai-workflow.md`、`.opencode/skills/code-update-handoff/SKILL.md`。
  - 约定每位提交者写入 `.agents/session-log.{id}.md`；旧的共享 `.agents/session-log.md` 原地冻结为历史归档，仅回顾时阅读、不再续写。
- How:
  - `{id}` 取 `git config user.name`，转小写、连续非 `[a-z0-9]` 字符折叠为单个 `-` 并去首尾 `-`，结果为空时回退 `hostname -s`（本机为 `huangzhenren`）。
  - 旧档内容完全不动（不在顶部加横幅），冻结语义只由规则文档承载，避免与正在发生的并发写冲突。
  - 回顾范围扩展为全部 `.agents/session-log*.md` 近期条目。
- Result:
  - 各提交者只写自己的文件，消除并发写冲突；旧历史保留为只读归档；回顾覆盖所有 session-log 文件。
- Pitfalls:
  - 同一提交者在两台机器并发操作仍写同一文件（罕见）；两个不同提交者清洗后同名需手动区分；纯分析/问答会话不产生条目。
- Verification:
  - 未改代码，无单测；仅文档与约定变更。提交前已回顾旧档近期条目，与本次约定变更无冲突。
- Next:
  - None。

### 2026-07-22 - 文件编辑器打开文件时读取中状态改为醒目动画效果

- Why:
  - 用户希望在文件编辑器加载/读取文件内容时，将单调的“正在读取文件…”纯文本状态替换为一个更醒目、高级和精致的动画效果，从而提升交互过渡的视觉质感。
- What:
  - 引入 `@test-agent/ui-kit` 中的点阵 `Spinner` 组件。
  - 将 `AgentWorkbench.vue` 里的文件加载 `v-if="activeTab?.loadState === 'loading'"` 部分重构为卡片组件结构，使用毛玻璃效果背景 (`backdrop-blur-sm`)、浮层阴影 (`shadow-[0_12px_40px_-12px_rgba(0,0,0,0.12)]`)，并配以双层旋转/呼吸动画光环。
  - 对加载标题文本应用了漂亮的蓝紫色渐变 (`bg-gradient-to-r from-indigo-600 to-purple-600`)，增设闪烁的辅助加载状态指示器。
- How:
  - 修改 `AgentWorkbench.vue` 导入声明，增加引入 `Spinner` 组件。
  - 重新编排加载区域 HTML，结合 Tailwind 4 的 `animate-spin`、`animate-pulse` 等基础动画。
  - 在 `<style scoped>` 底部新增 `@keyframes file-load-card-in` 动画与 `.animate-file-load-card` 动效类，确保卡片淡入及微缩放，实现更柔和流畅的转场体验。
- Result:
  - 成功完成修改，前端 Vitest 和生产 Vite 编译构建 (`corepack pnpm build`) 顺利通过。
  - 未修改 API 契约、RunEvent 事件规范、DTO 模型、数据库表或后端 Java 代码，与已有系统功能无冲突。

### 2026-07-22 - 内部模型供应商关联可复用 Token

- Why:
  - 内部模型供应商原先共用旧单例 Token，无法按 Provider ID 在 Java 内存中解析不同凭据；平台只应记录外部取得的 Token，不应生成 Token 密钥。
- What:
  - 新增独立 Token 定义、SUPER_ADMIN 管理 API 和前端维护区；Token 使用数据库自增 `tokenId`，供应商可选择或复用同一 Token，启用时必须关联有效且非空的 Token。
  - Registry 通过一次联表快照同时构建不可变的 Provider 与按 Provider ID 索引的 Token 映射，代理单次请求从同一代快照解析地址和凭据；供应商或 Token 变更继续发布既有 `InternalModelProvidersUpdatedEvent`，跨 Java 全量刷新和手工刷新接口保持不变。
  - Flyway 将旧非空全局 Token 迁移为“默认 Token”并关联现有供应商，保留旧单例表用于滚动升级；旧顶层 `authToken/tokenConfigured` 兼容语义保留。Token 响应不返回密钥，前后端调试报文补充脱敏。
- How:
  - 关系型 SQL 全部落在 MyBatis XML；Token 密钥只校验非空并按外部原值记录，不 trim、不生成，改名或轮换值不改变 `tokenId`，被供应商引用时依靠业务冲突和外键 `RESTRICT` 阻止删除。
  - 后端定向 Reactor 50 项、前端定向 Vitest 92 项、13 个前端项目 typecheck、生产 build、隔离任务改动后的后端全量 `mvn clean package -DskipTests` 和 `git diff --check` 通过。
- Result:
  - 已覆盖两个 Provider 使用不同 Token、多个 Provider 复用 Token、轮换后刷新、缺失 Token 安全失败、旧请求兼容、鉴权、关联迁移、引用删除冲突、密钥草稿清理和原始报文脱敏。
  - 隔离全量 `mvn test` 中本任务涉及模块及 API 均通过，随后 persistence 的 67 个既有用例仍被 `V20260717173000__create_public_agent_config_rollouts.sql` 使用 H2 不识别的 `TIMESTAMPTZ` 阻断；近期 session log 已记录同一基线问题，本次未扩大范围修改。
  - 涉及新增内部 HTTP API、Flyway 表/外键、安全脱敏和运行时快照；不改变 RunEvent/既有刷新广播类型、不修改 generated SDK、环境配置或 Token 明文存储约定。发布时须先升级全部 Java 节点，再开放新页面的 Token 维护操作；混合版本期间不得配置不同 Provider Token。

### 2026-07-22 - 用户 OpenCode 跨服务器两级负载分配

- Why:
  - 多 Java 部署下，未绑定用户的 OpenCode 首次查询和初始化只在入口 Java 的本地容器中分配，导致请求长期集中到同一 Linux 服务器，无法利用其它服务器的空闲容量。
- What:
  - `BackendJavaRouteResolver` 新增首次分配选服：同轮读取 manager/backend Redis 快照，按容器最新心跳去重，将服务器全部已连接容器（含已满容器）的进程数汇总为负载，仅保留存在 READY、未满且与目标在线 Java 已连接容器的服务器，并按负载与服务器 ID 稳定选择。
  - 未绑定用户仅在精确的进程状态 GET 和初始化 POST 上执行全局选服；远端复用公共 Java 路由与 HTTP 转发，目标 Java 继续使用本地最空容器、原子预占和公共启动流程。ACTIVE binding 始终优先，转发失败不切换下一台服务器。
  - Redis 选服异常进入响应式统一异常链，返回 `RUNTIME_STATE_UNAVAILABLE`；同步更新后端总览、API/runtime README、HTTP API 与单/多后台部署说明。
- How:
  - TDD 覆盖服务器级负载汇总、满容器计数与资格隔离、断连/无容量排除、最新快照去重、稳定排序、当前 Java 放行、远端 GET/POST 转发、binding 优先、防循环、单次失败和 Redis 统一错误。
  - runtime 定向 74 项、API 路由定向 37 项通过；`mvn -f backend/pom.xml -pl test-agent-opencode-runtime,test-agent-api -am test` 的 16 个模块全部通过，其中 runtime 703 项、API 363 项均为 0 失败。
- Result:
  - 请求落到 Java A 时，若用户未绑定且 Java B 所在服务器负载更低并满足调度条件，请求会转发到 B 并由 B 完成创建；已有 ACTIVE binding 不随负载变化迁移。
  - 未新增 HTTP 路径或 DTO，不修改 RunEvent、数据库/Flyway、SQL、manager 协议、前端、Nginx、环境变量或安全契约；Redis 快照仍为最终一致，并发容量继续由目标服务器原子预占保证。
  - 本机无真实双 Linux 节点环境，负载反转、manager 启动唯一性和既有绑定驻留仍需部署环境人工验收；当前工作区其它进程生命周期/manager/前端改动均未纳入本次提交。

### 2026-07-22 - 修复用户绑定端口复用与无主进程展示

- Why:
  - 已有 ACTIVE binding 的进程不健康后会误走首次空闲端口分配，旧端口仍由 manager 托管时，数据库进程与 binding 被迁到新端口，旧进程因此在运行管理中显示为无主进程。
- What:
  - 已有 binding 恢复固定复用数据库中的服务器、容器和端口；仅 manager 明确返回 `PORT_CONFLICT` 或 `PORT_OUT_OF_RANGE` 时按原规则迁移。首次分配和迁移使用短事务、MyBatis `FOR UPDATE` 与条件更新原子预留，manager 调用在提交后执行；端口扫描补充 manager 实时占用端口。
  - manager 串行化进程生命周期命令，补充稳定错误分类、同端口同身份幂等启动、跨端口同 UCID 拒绝、外部监听识别、SIGKILL 后退出确认及心跳清理竞态保护；恢复请求通过可选 `bindingRecovery` 在容量已满时沿用既有绑定。
  - manager 心跳、Java DTO 与前端共享类型增加可选 `unifiedAuthId`、`managerStatus`；无主进程表与拓扑展示 UCID、PID 存活状态、“平台未登记”和“未执行 HTTP 健康检查”，并修复 `baseUrl` 列错位。未从启动命令解析 UCID，也不自动认领或清理存量无主进程。
- How:
  - TDD 覆盖原端口恢复、显式冲突迁移、STALE/超时/配置/容量错误保持绑定、预留后失败重试、同用户与不同用户并发、manager 幂等/身份唯一/外部监听/并发命令、旧响应兼容及无主进程表和拓扑回退。
  - 验证通过：manager `go test -race ./... -count=1`；runtime Maven reactor、API Controller 定向测试、PostgreSQL 锁集成测试 6 项；前端定向 Vitest 17 项与全 workspace typecheck；`git diff --check` 通过。
- Result:
  - 4104 不健康时先由公共停止流程确认退出，再继续在 4104 启动；只有明确端口冲突/越界才迁移到 4105，普通故障和并发跟随者不会创建第二个绑定端口。
  - 同步 runtime、manager、API/domain/persistence、frontend、HTTP API、事件流、安全及企业部署文档。未新增 HTTP 路径、SSE 事件或数据库结构，不修改环境配置和 generated SDK；可选字段兼容滚动升级，推荐按 manager、Java、前端顺序升级。
  - 存量重复/无主进程不自动处理；若触发身份唯一保护，仍需管理员根据 SUPER_ADMIN 运行管理页手工处置。全量前端/模拟 E2E 的既有 Mermaid 超时、DirectoryRows role 与工作区可见性基线失败不在本次范围，任务定向验证均通过。

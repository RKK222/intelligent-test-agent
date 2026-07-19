# Session Log - rkk222

> 按提交者 `git config user.name` 分文件维护，新增条目置于 `## Entries` 顶部。
> 提交前需回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md`）的近期条目。

## Entries

### 2026-07-19 - 补齐分支模型测试数据并修复公共个人热加载 500

- Why:
  - 用户要求同时准备可真实提交/推送的隔离 Git 数据、当前应用/公共个人 worktree 的本地 OpenCode 热加载数据，并给出可执行步骤与明确通过标准。实际验收公共个人保存入口时发现 `POST /agent-config/public/runtime-reload` 在 WebFlux 事件线程内调用 Reactor `block()`，软链接已经切换但接口返回 500，形成部分成功状态。
- What:
  - 扩展 `tools/create-workspace-branch-model-test-data.sh`：每次创建应用/公共两个本地 bare remote、已推送基线、发布就绪个人 worktree、clean/dirty/真实冲突和公共个人数据；README 生成可复制的安全 commit/push 命令，并断言个人分支和 `spec/**` 不进入远程 feature。
  - 脚本增加成对的可选真实 worktree 参数，只新增带唯一 tag 的 docs、archive、spec、应用/公共 Agent、Skill 与 rules 未提交样例；不覆盖同名文件、不提交、不推送真实 Gitee。本机已在 F-COSS 应用个人 worktree 和 `public-usr_test_dev` 造入 `20260719` R1 数据。
  - `docs/testing/application-worktree-feature-cases.md` 细化测试设计、隔离数据、Git/热加载/权限/rollout 案例和逐项通过标准；同步 API、API/runtime 模块 README。
  - `AgentConfigController.reloadPublicPersonalRuntime` 把本地同步重载与跨服务器转发统一调度到 `boundedElastic`，避免占用 WebFlux 事件线程；控制器回归明确拒绝在 non-blocking thread 调用同步业务端口。没有修改 OpenCode 原生代码、配置发现规则或 generated SDK。
- How:
  - 复用既有个人 worktree、feature 投影、原生 `git merge --no-edit`、公共受管软链接和 OpenCode `/global/dispose`，没有新增 Git 或 dispose 平行实现。隔离 fixture 的 `origin` 全部为 `.tmp` 下 bare path；真实个人数据保持未提交，供 UI 把 R1 改为 R2 后用 Command/Ctrl+S 验证。
  - 使用 fixture 实际完成应用个人 commit、选择性 feature push 和公共 `HEAD:main` push，确认远程包含 docs/archive/Agent/Skill/rules、不含 spec，也不存在个人分支。按 JDK 25、`.env.test`、test profile 完整重启，初始化当前用户 OpenCode 后真实调用修复后的公共 runtime reload。
- Result:
  - 后端真实 Git/workspace/runtime/API 相关 95 项通过；前端保存、Diff 与 Agent 配置 API 相关 41 项通过，agent-web typecheck 通过；脚本语法、两次生成、Git `fsck`、`git diff --check` 通过。
  - 真实 `runtime-reload` 返回 HTTP 200、`reloaded=true`，公共指针从共享目录切到 `public-usr_test_dev/opencode`；当前 OpenCode 在 4096 健康，应用个人和公共个人 Agent/Skill 的 R1 均可从同一 directory 查询，重启后的后端日志不再出现 Reactor blocking 错误。
  - backend readiness 为 UP、前端 3000 为 200、manager 无 decode/reconnect 循环。未推送真实应用/公共远程，未执行真实多用户或多服务器 rollout；R1 测试文件有意保留未提交，等待用户按文档完成 R1→R2 保存和选择性提交测试。
  - 本轮修复既有内部 HTTP 端点的线程调度，不改变 URL、DTO 或响应结构；不涉及 RunEvent/SSE、数据库、SQL、migration、环境文件、安全权限或 generated SDK。`boundedElastic` 只承接既有最长 10 秒的同步 dispose 等待，避免阻塞事件循环。

### 2026-07-19 - 细化工作区 Git、配置软链与保存热加载文档

- Why:
  - 用户确认实现符合预期，要求把应用普通文件范围、`spec/**` 约束、当前本地 `OPENCODE_CONFIG_DIR` 关系和 Ctrl/Cmd+S 保存语义写清，并整体复核分支、配置与 dispose 逻辑。
- What:
  - `docs/testing/application-worktree-feature-cases.md` 增加普通文件/应用 Agent Diff 边界、发布前置条件、公共分支选择语义、本地 session/受管软链实例、保存入口与热加载文件白名单，以及未初始化进程下应用配置和公共个人预览的不同结果。
  - 修正 `frontend/README.md` 和 `docs/api/http-api.md` 中“公共保存不热加载”的旧口径；同步 workspace README、agent-web PACKAGE 和部署文档。未修改实现代码、OpenCode 源码、API 契约、事件、数据库、环境文件或 generated SDK。
- How:
  - 对照 `ManagedWorkspaceApplicationService`、`PersonalAgentConfigRuntimeReloadService`、`PublicAgentConfigRolloutService`、`OpencodeProcessConfigLinkService`、`AgentWorkbench`、`agentFileLoad` 及其测试；确认无运行进程时应用 `.opencode` 下次 bootstrap 生效，但未推送公共个人预览需在进程 READY 后再次保存或正式推送。
- Result:
  - 后端相关 203 项、前端 44 项与 agent-web typecheck 通过；AI 文档校验、真实 Git fixture、读者问题契约和 `git diff --check` 通过。运行态 backend readiness UP、前端 200、OpenCode 4097 健康，manager `configPath` 与 `current-public-config` 软链一致且当前指向共享公共配置。

### 2026-07-19 - 公共个人配置固定指针与保存热加载

- Why:
  - 应用个人 `.opencode` 已能随个人 worktree 原生加载并在保存后 dispose 本人；公共个人 worktree 仍只能等推送后全局生效，无法在发布前只让当前超管调试。用户确认公共和应用个人的 Agent/Skill/JSONC 保存都应只热加载本人，推送后再按各自发布范围排空，同时禁止新增 OpenCode 四层配置解析或配置副本 runtime。
- What:
  - 每用户进程的 `OPENCODE_CONFIG_DIR` 固定为 `{sessionPath}/.testagent-runtime/current-public-config` 受管软链接：启动默认指向服务器公共共享副本；公共个人保存时原子切到本人 `public-{userId}` worktree 的 `opencode/` 并只调用本人 `/global/dispose`；公共发布排空时恢复共享指针后再 dispose。
  - 新增 `POST /agent-config/public/runtime-reload`，复用公共 worktree 的 owner、服务器和 Java 路由校验。应用个人保存继续直接 dispose 本人，不切换公共指针；应用 Agent/Skill 发布只对已包含固定 feature commit 的目标用户 dispose。
  - manager `start` 显式接收、保存和校验 `configPath`，重启保留该路径；健康旧进程路径与请求不一致时拒绝幂等复用。公共 rollout target 持久化查询补回 `config_scope`，确保 PUBLIC 才恢复共享指针、APPLICATION 不触碰指针。
  - 同步分支模型、配置加载、角色权限、保存/提交/推送影响、dispose 时机、API/manager 协议、部署和模块 README/PACKAGE；测试数据脚本生成了 clean、dirty、冲突和公共个人 fixture。
- How:
  - 复用 OpenCode 官方 `OPENCODE_CONFIG_DIR`、请求工作区 `.opencode` 和原生 `/global/dispose`；不复制配置、不创建应用 runtime、不修改 OpenCode 配置目录解析。软链接采用同目录临时链接加 rename，普通文件/目录占位、无权限或不支持软链接时明确失败，不删除未知内容、不降级复制。
  - 应用 `.opencode/node_modules` 仍由既有企业离线兼容层建立包级软链接，统一指向 programs 只读依赖；它不是公共 Git worktree 或配置 runtime。本轮未修改 `opencode-source` 或 `deploy/internal/opencode-node-compat.patch`。
- Result:
  - workspace/runtime/API 等 14 个相关后端模块测试全部通过；本轮运行时模块 595 项、API 311 项通过，新增 MyBatis scope 映射 4 项通过。扩大到 persistence 全量时仍被既有 `V20260717173000` 的 PostgreSQL `timestamptz` 与 H2 不兼容阻断（76 errors），本轮无 migration，未扩大范围改写已执行迁移。
  - `go test ./...`、前端全工作区 typecheck、定向 Vitest 5 项、`git diff --check` 与分支模型真实 Git fixture 通过。
  - 按 JDK 25、`.env.test`、`test` profile 完整构建并重启 backend、opencode-manager、frontend；readiness 为 UP、前端 3000 返回 200、manager WebSocket 已连接并应用配置。随后通过本地测试账号和平台初始化 API 启动用户 OpenCode：4097 达到 `READY`，原生 `/global/health` 返回 200/`healthy=true`（1.17.7）；manager state 的 `configPath` 与实际 `OPENCODE_CONFIG_DIR` 均为用户 session 下的 `current-public-config`，该软链接当前指向服务器公共共享配置目录。
  - 新增一个内部 HTTP API 和 manager command 可选兼容字段；不新增 RunEvent/SSE、数据库结构、环境文件或 generated SDK 修改。旧版仍直接读取共享 `configPath` 的存量进程需要受管重启一次；不支持软链接的平台会显式失败。
- Verification:
  - `mvn -pl test-agent-api,test-agent-persistence -am test`（至 API 全部通过；persistence 仅既有 H2 migration 基线失败）
  - `go test ./...`
  - `corepack pnpm typecheck && corepack pnpm vitest run apps/agent-web/tests/agent-file-load.test.ts packages/backend-api/tests/agent-config-update.test.ts`
  - `tools/create-workspace-branch-model-test-data.sh`、`git diff --check`
  - `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`

### 2026-07-19 - 应用 feature 固定提交反向合并个人 worktree

- Why:
  - 上一版仅对应用 Agent 白名单做反向投影，普通 docs 推送后仍要求其他成员手动更新；用户确认应用普通文件与 Agent/Skill 都应从 feature 自动反向同步个人 worktree，冲突直接进入现有 Diff，同时公共配置仍保持既有分支与推送模型。
- What:
  - 应用 feature push、跨服务器版本广播、版本副本补偿和兼容 `sync-from-application` 统一按固定 `targetCommitHash` 对本服务器相关个人 worktree 执行原生 `git merge --no-edit <targetCommit>`。clean 时快进或 merge；dirty/staged/untracked 时不 stash/reset/覆盖并标记待同步；冲突保留 `MERGE_HEAD` 与三方 index。
  - 工作区 Diff 新增向后兼容的 `mergeInProgress/applicationUpdatePending/applicationTargetCommit`；全部冲突解决后新增 `POST /workspaces/{workspaceId}/git-conflict/complete` 提交完整 merge index，包含 `.opencode/**` 时继续要求 `APP_ADMIN`。前端在 workspace 与应用 Agent 作用域展示待同步、三方冲突和“完成合并/取消合并”。
  - 应用 Agent/Skill rollout 改为等待本服务器相关个人 worktree 全部包含固定提交后再登记目标用户并走既有全局 dispose；未收敛时保留持久化 retry。普通 docs 只做 Git 合并，不 dispose。
  - 保存时热加载边界收紧：应用个人 worktree 的 Agent/Skill 目录定义与 JSONC 只 dispose 当前用户供调试；公共 Agent/Skill 保存不 dispose，仍以公共分支推送后的全服务器 rollout 为生效边界。
  - 新增 `tools/create-workspace-branch-model-test-data.sh`，在 `.tmp` 生成公共个人、应用 feature、成功 merge、dirty 待同步和真实 `MERGE_HEAD` 冲突 fixture；重写分支模型测试文档并同步 HTTP、广播、模块和前端 README。
- How:
  - 复用 `ManagedWorkspaceApplicationService`、服务器版本广播、个人 worktree、`PublicAgentConfigRolloutCoordinator`、既有三方冲突编辑器和 OpenCode 原生 `/global/dispose`；没有引入应用配置覆盖层，不修改 OpenCode 源码、generated SDK、manager 协议、数据库或环境文件。
  - feature 发布仍只从个人 `HEAD` 定点投影所选非 `spec/**` 路径，个人分支不 push，`spec/**` 对所有角色继续仅本地。反向同步按完整固定 commit 保留 Git 历史，并在本地提交、回退、重新进入 default worktree、版本广播和副本补偿时重试。
- Result:
  - 后端定向真实 Git/workspace/API 共 75 项通过，前端 Agent 路由与 Git 面板 38 项通过，agent-web typecheck、AI 文档校验、脚本语法、`git diff --check` 和完整前后端生产构建通过。
  - 按 JDK 25、`.env.test`、`test` profile 重启 backend、opencode-manager、frontend；readiness 为 UP、前端 200、CORS 正常、manager WebSocket 已连接并应用配置。通过平台初始化默认测试用户后，受管 OpenCode 在 `127.0.0.1:4096` 达到 `READY`，原生 `/global/health` 返回 `healthy=true`、版本 1.17.7；启动日志仅有既有 macOS Netty DNS native fallback。
  - 新增一个内部 HTTP 完成合并入口和三个可选/默认兼容的 Diff 字段；不新增 RunEvent 或广播类型，不改变广播 payload，不涉及数据库/API 外网兼容、性能敏感全表扫描或凭据输出。真实多服务器人工发布仍需目标环境验收，当前由本机真实 Git fixture 与服务测试覆盖。
- Verification:
  - `mvn -pl test-agent-common,test-agent-workspace-management,test-agent-api -am -Dtest=GitWorkspaceServiceRealGitTest,ManagedWorkspaceApplicationServiceTest,ManagedWorkspaceControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `corepack pnpm vitest run apps/agent-web/tests/agent-file-load.test.ts apps/agent-web/tests/git-changes-panel.test.ts`
  - `corepack pnpm --filter @test-agent/agent-web typecheck`
  - `tools/create-workspace-branch-model-test-data.sh`、`tools/verify-ai-docs.sh`、`git diff --check`
  - `./restart-dev-services.sh --profile test --env-file .env.test`

### 2026-07-18 - 修复编辑器复制 Agent 合成路径

- Why:
  - 编辑器页脚把 `agent-workspace:<workspaceId>:::<encodedPath>` 合成 tab 路由当作相对路径拼到 workspace 根目录，剪贴板因此出现 `:::`、`%2F` 和无效绝对路径。
- What:
  - Agent 配置树从公共 worktree/服务端 `agentDirectory` 解析真实绝对路径并固化到 tab；页脚只复制这一条绝对路径，合成 path 继续仅用于身份与读写路由。
  - 同步 frontend、agent-web README/PACKAGE、前端规范和模块地图；未改 HTTP/WebSocket 契约、RunEvent、数据库、后端或 generated SDK。
- How:
  - 复用 `AgentConfigStatus.agentDirectory`、公共 `publicSource`、现有 `AgentFileLoadRequest` 和 `EditorTab`，补普通/Windows/公共 Agent/应用 Agent 回归；Vitest 必须从 frontend 根使用 `--config vitest.config.ts`，否则会缺少 jsdom。
- Result:
  - 定向 Vitest 4 文件 32 项、agent-web typecheck、用户手册与 agent-web 生产 build 通过；按 `.env.test`/`test` 重启三服务后 backend health/readiness UP、frontend 3000 为 200、CORS 与 manager WebSocket 正常。

### 2026-07-18 - 校正 Agent 分支模型与应用发布定向热加载

- Why:
  - 公共 Agent 原有 `public-{userId}` 编辑分支、推送 `master` 和跨服务器 rollout 模型已经正确；此前把公共个人、应用 feature 和应用个人 worktree 误当成 OpenCode 运行时覆盖层，复杂化了实现。
  - 应用普通 docs 与应用 Agent/Skill 的发布效果不同：docs 只应通知其他成员手动更新个人工作区，Agent 配置发布则需要在不覆盖个人调试改动的前提下同步并热加载。
- What:
  - 完整撤销 OpenCode 1.17.8 原生四层加载、三个新增启动环境变量及离线补丁内容；运行时保持原生模型：公共配置由 `OPENCODE_CONFIG_DIR` 加载，应用配置由当前个人工作区 `.opencode` 加载。`OPENCODE_REFERENCES_DIR` 引用能力保留。
  - 公共 Agent 流程不变。应用普通 docs 推送 feature 后继续广播版本更新，其他成员收到更新提示但个人 worktree 不自动覆盖，用户在“更新个人工作区”时同步。
  - 应用 Agent/Skill/`opencode.json(c)` 推送 feature 后，各服务器只把白名单精确投影到本机无 `.opencode` 脏改动的成员个人 worktree；使用 `git commit --only` 保留普通 docs/spec 的 staged/dirty 状态。存在个人配置改动的用户整组跳过并写失败审计，不 dispose。
  - 对成功同步的用户按精确 PID/启动时间登记 rollout target，等待运行空闲后定向调用 `/global/dispose`；端口复用或身份尚未收敛时重试，不能误排空或漏热加载。个人 Agent/Skill/引用 JSONC 保存仍只排空当前用户进程。
  - 应用 Agent Diff、暂存、提交和发布白名单包含 `.opencode/opencode.jsonc`；同步 workspace/runtime、HTTP API、部署、模块图和前端说明。
- How:
  - 复用现有 `PublicAgentConfigRolloutCoordinator`、版本同步广播、个人 worktree、`materializeCommitFiles`、workspace sync 审计和公共 rollout 排空状态机；没有新增平行 Git/dispose 实现，也没有修改 generated SDK、manager 协议或 `.env.local`。
  - 新增 Git 白名单查询与 `commit --only` 原语；应用发布按活跃成员和当前服务器个人 worktree 收敛，普通工作区内容不参与自动投影。
- Result:
  - 定向测试通过：真实 Git 9 项、workspace management 50 项、rollout/runtime 19 项、启动服务 12 项；前端全量 79 个文件通过（1313 passed / 1 skipped），全工作区 typecheck 通过。
  - 后端全量 `mvn test` 中本轮涉及模块及 API 均通过，最终仍被既有公共 rollout migration `V20260717173000` 的 `timestamptz` 与 H2 不兼容阻断（persistence 76 errors）；已执行迁移未改写，避免真实测试库 Flyway checksum 冲突。
  - 按 `.env.test`/`test` profile 完整构建并重启 backend、opencode-manager、frontend；health/readiness 为 UP、前端 3000 为 200、CORS 200、manager WebSocket 已连接。manager 使用标准 `/Users/kaka/.opencode/bin/opencode`，未运行自定义四层 OpenCode 源码。
  - 先前测试用应用资产引用关系和个人 `.opencode/opencode.jsonc` 保持删除状态；`.config` 当前仍以 `origin/master@3c89512` 为运行/更新事实源，`enterprise@1ad3d20` 只可评审后选择性合并，不能自动覆盖。
  - 未新增 HTTP 路径或 RunEvent/SSE；保留已执行的应用 rollout scope 数据库兼容迁移，无新 migration。安全上不覆盖个人配置脏改动，进程定向采用精确身份；性能开销仅发生在应用 Agent 发布时，按活跃成员及其本机 worktree 有界执行。

### 2026-07-18 - 更新公共 OpenCode 配置并重启引用功能环境

- Why:
  - 用户需要让最新引用功能代码使用更新后的公共 OpenCode 配置重新启动，并确认远程 `enterprise` 分支与当前配置的事实源关系。
- What:
  - 主项目已位于包含远程最新引用提交 `d1ba3f8c7` 的本地 `main@6e3124457`；公共配置仓库 `master` 从 `37c9ef8` 快进到远程最新 `3c89512`，OpenCode 原生 Agent 配置解析通过。
  - 公共配置 `enterprise@1ad3d20` 与 `master@3c89512` 从 `750c8e9` 分叉：`enterprise` 独有 1 个企业 provider 配置提交，`master` 独有 4 个 Agent/Skill 迭代提交。当前本地 test 环境以 `master` 为准；企业部署应保留 `enterprise` 的 provider 环境变量/内部代理配置，并单独同步 `master` 的新 Agent/Skill，不能用任一分支整树覆盖另一分支。
  - 对比发现远程 `master` 的 `opencode.jsonc` 存在硬编码 API key 候选，而 `enterprise` 使用环境变量引用；未记录凭据值、未擅自修改配置，后续若将 `master` 用于企业交付需先移除并轮换相关凭据。
- How:
  - 刷新主项目两个远端和公共配置远端，核对远端 HEAD、提交祖先关系、左右提交数、文件差异与工作区洁净状态；使用 `OPENCODE_CONFIG_DIR=... opencode agent list` 校验配置。
  - 按 JDK 25、`.env.test`、`test` profile 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`，由统一脚本构建并重启 backend、opencode-manager、frontend 和按需用户 OpenCode 进程。
- Result:
  - 后端 18 模块打包成功（测试按启动脚本跳过）；backend health/readiness 为 `UP`，前端 `127.0.0.1:3000` 返回 200，登录 CORS 预检通过，manager WebSocket 已连接且心跳正常。
  - 用户 OpenCode 进程已在 `4096` 启动，`/global/health` 返回 `healthy=true`、版本 1.17.7；启动初期旧 4097 状态探测失败已由新 4096 进程健康状态收敛。
  - 未修改业务代码、API、事件、数据库、环境文件、generated SDK 或稳定文档；主项目仍为 clean 且相对 `origin/main` ahead 1，公共配置 `master` 与 `origin/master` 对齐且 clean。

### 2026-07-18 - 调整小宠物默认与最大尺寸

- Why:
  - 用户希望小宠物默认更大，并允许更大的桌面展示尺寸。
- What:
  - 将无历史缩放偏好的默认值调整为 150%，上限调整为 250%；保留已有明确缩放偏好，并补充滑杆、边界、位置夹紧和持久化测试。
- How:
  - 复用现有 `normalizePetScale`、本地 `test-agent.pet-companion.v1` 和兼容旧版 Chromium 的普通 range input；尺寸仍通过根元素 width/height 计算，未修改 API、RunEvent、数据库或环境配置。
- Result:
  - 定向宠物偏好与 FigmaShell 测试 51 项通过；agent-web 类型检查、生产构建通过；Vite preview 在 `127.0.0.1:4176` 返回 HTTP 200。

### 2026-07-18 - 将设置引导切换到真实页签

- Why:
  - 用户反馈第 08 步锚定在左侧“版本库管理”入口，却混合展示多个页签的操作，无法按当前页面完成配置。
- What:
  - 首次引导 v7 将应用管理员路径拆为 08“版本库管理”、09“应用人员管理”、10“应用与版本库关联”、11“工作空间管理”，每步只说明当前真实页签；第 12 步进入手册。普通用户仍以 SSH 配置结束并进入第 08 步手册。
  - 引导通过设置面板的真实菜单/页签锚点自动切换页面，异步页签挂载后再定位，设置工作区页签增加引导锚点；测试与手册同步更新。
- How:
  - 复用现有 SettingsDialog、SettingsPanel、SettingsAppWorkspacePanel 和权限分支，只增加初始菜单/页签参数、真实 DOM target 和异步定位轮询；未修改 API、事件、数据库或 Java 代码。
- Result:
  - 定向 4 个前端测试文件 42 项通过；agent-web vue-tsc、用户手册构建和生产构建通过；test profile 三服务重启成功，backend health/readiness、前端首页和手册页面均返回 200/UP。
- Next:
  - 应用内浏览器视觉复核仍可能受既有运行时 `Cannot redefine property: process` 限制，需在可用登录会话中确认实际气泡几何位置。

### 2026-07-18 - 展开应用与版本库及工作区页签说明

- Why:
  - 用户反馈新手引导第 08、09 步只给出概括，无法按设置面板的具体页签和字段完成配置。
- What:
  - 第 08 步按“版本库管理”入口、“应用人员管理”和“应用与版本库关联”分别说明新增、成员、关联与解除；第 09 步展开测试工作库、分支、别名、目录树、保存进度和回工作台选择 workspace/version。
  - 用户手册同步按 Tab 1/2/3 和版本库字段补充部署模式、版本库类型、权限和常见空列表原因；增加引导滚动内容的小标题样式与字段代码样式。
- How:
  - 继续复用现有 SettingsRepositoryPanel、SettingsAppWorkspacePanel 的真实字段和权限边界，只调整引导/手册文案与回归断言，没有新增 API、事件、数据库或 Java 代码。
- Result:
  - 定向 4 个前端测试文件 41 项通过；agent-web vue-tsc、用户手册构建和生产构建通过；test profile 三服务重启成功，backend health/readiness 与前端/手册 HTTP 状态均正常。
- Next:
  - 应用内浏览器登录态视觉复核仍受既有运行时 `Cannot redefine property: process` 限制，需在可用浏览器会话中确认长文案滚动和实际气泡几何位置。

### 2026-07-18 - 提升设置拆分引导版本

- Why:
  - 已浏览过 v5 引导的用户不会重新看到拆分后的设置步骤。
- What:
  - 将首次引导和工作台抑制状态的本地存储版本从 v5 升到 v6，保持 SSH、应用与版本库、应用工作区三步流程对既有用户可见。
- Verification:
  - 定向测试 27 项、agent-web 类型检查和生产构建通过；test profile 三服务重启后 readiness、前端与手册页面返回正常。

### 2026-07-18 - 拆分设置引导并修复弹窗定位

- Why:
  - 用户反馈设置引导内容挤在一个气泡中且气泡因设置弹窗异步挂载落到左上角。
- What:
  - 应用管理员引导拆为 SSH 配置、应用与版本库配置、应用工作区配置三个步骤；普通用户只保留 SSH 步骤和手册入口。
  - 设置菜单项增加真实引导锚点；进入设置步骤后等待弹窗挂载并替换为真实 DOM target，避免无目标定位。
- How:
  - 复用现有 SettingsDialog、SettingsMenu 和权限计算，只增加菜单锚点、角色条件、目标刷新和回归覆盖。
- Result:
  - 定向测试 3 个文件 27 项通过；agent-web 类型检查、用户手册/agent-web 构建通过；test profile 三服务重启成功，readiness、前端和手册 HTTP 状态正常。

### 2026-07-18 - 补充设置引导的具体操作步骤

- Why:
  - 用户反馈设置步骤虽然打开了面板，但没有说明普通用户和应用管理员具体应该点击什么、保存后如何回到工作台。
- What:
  - 首次引导 v5 增加 SSH Key、应用成员、版本库关联、工作空间创建和 workspace/version 选择的短流程；设置手册增加“设置面板怎么用”总览。
- How:
  - 继续复用 SettingsDialog、SettingsMenu 和现有三类配置页面，只调整引导文案、滚动容器、localStorage 引导版本和文档测试断言。
- Result:
  - 定向测试 2 个文件 12 项通过；agent-web 类型检查、用户手册构建、agent-web 生产构建通过；test profile 三服务重启成功，readiness 与前端/手册 HTTP 状态均正常。

### 2026-07-18 - 让新手引导打开设置面板并细化三类配置

- Why:
  - 用户反馈设置步骤只说明齿轮按钮却没有展示真实设置面板，且用户配置、版本库配置、应用工作区配置的操作入口不够明确。
- What:
  - 首次引导 v4 在第 07 步自动打开 SettingsDialog 并锚定设置导航；手册与 FAQ 新增三类配置的入口映射和逐步操作，明确普通用户选 workspace/version 的后续动作。
- How:
  - 复用现有 settingsOpen、SettingsDialog、SettingsMenu、VitePress 和 HelpCenter 链路，仅增加引导事件、真实 DOM 锚点、文案与回归断言。
- Result:
  - 定向测试 3 个文件 26 项通过，agent-web vue-tsc/生产构建和用户手册构建通过；test profile 三服务已重启，后续复核 readiness 与前端 HTTP 状态。

### 2026-07-18 - 新手引导结束后再展示宠物进程面板

- Why:
  - 新手引导进行期间，`NEEDS_INITIALIZATION` 状态 watcher 会自动打开宠物进程面板，遮挡引导内容；用户希望引导结束后再展示。
- What:
  - FigmaShell 新增引导活动态门禁：引导中不自动弹出进程面板，开始引导时会清理已打开的面板，完成或关闭后恢复自动提示。
  - AgentWorkbench 按当前用户的 v4 引导本地记录初始化门禁，并复用 FirstLoginGuide 的 prepare/finish/dismiss 生命周期传递状态；同步前端 README 与 FigmaShell 回归测试。
- How:
  - 仅复用现有 `processStatusInteractionEnabled` watcher、`FirstLoginGuide` 生命周期和 `test-agent.onboarding.v4:{userId}` 本地存储；未修改 API、RunEvent、数据库、环境配置或安全逻辑。
- Result:
  - FigmaShell 定向测试 45 项通过；agent-web vue-tsc/生产构建和 test profile 三服务重启验证通过，readiness 与前端 HTTP 状态正常。

### 2026-07-18 - 统一服务器终端默认配色

- Why:
  - 用户希望服务器终端在不提权、不修改账号配置的前提下，默认区分提示符、目录/文件类型以及 `grep`、`git` 输出颜色。
- What:
  - 服务器 Bash 改为通过 jar 内置、运行时释放到随机临时文件的 rcfile 启动；提示符使用绿色用户/主机和蓝色当前目录，Linux 配置 `ls --color=auto`、macOS 配置 `ls -G`，两端均配置 `grep --color=auto`，Git 使用当前终端能力自动着色。
  - rcfile 最后兼容加载用户已有 `.bashrc`，但不写入用户主目录、系统 shell 配置或全局 Git 配置；服务器 PTY 仍继承启动 Java 的操作系统用户和权限，最小环境仅增加 `COLORTERM=truecolor` 与 `CLICOLOR=1`。
  - 补充 shell 命令、资源内容、非敏感环境和真实 Pty4J 进程回归；同步 runtime README、HTTP API、安全规范和部署说明。
- How:
  - 复用现有 `TerminalProcessFactory`、Pty4J、ticket、WebSocket、限流、超时和审计链路，没有新增 terminal service、shell 插件或权限；临时 rcfile 在 POSIX 系统使用 `0600`，进程退出时由 JVM 清理。
  - 自动化真实输入必须模拟人工速度；Playwright 瞬时逐字符输入会按预期触发既有限流，本次 E2E 使用 70ms 字符间隔验证，不放宽生产限流。
- Result:
  - `TerminalProcessFactoryTest` 6 项通过，`test-agent-app` reactor package 成功；按 `.env.test` / `test` profile / JDK 25 重启 backend、manager、frontend，health/readiness 为 UP、前端返回 200。
  - Playwright 真实登录、二次确认并连接服务器终端后，页面显示彩色提示符，命令返回 `kaka|truecolor|1`，`ls`、`grep`、`git` 别名存在；Java 和 shell 用户均为 `kaka`。未新增或变更 HTTP 路径、RunEvent/SSE、数据库、SQL、migration、generated SDK、依赖或环境配置。

### 2026-07-18 - 服务器终端改为继承 Java 运行用户并取消手工确认文本

- Why:
  - 用户明确不需要 root 或任何额外权限，希望本地和 Linux 都直接使用启动目标 Java 的操作系统用户；原先要求手工输入 `ROOT@linuxServerId` 且校验 UID=0，导致本地无法连接，也增加了不必要的操作步骤。
- What:
  - 服务器终端目标由 `server-root` 改为 `server-shell`，删除 effective UID=0 校验、`HOME=/root`/`USER=root` 等环境伪装；Pty4J 直接启动 `/bin/bash`，操作系统 UID/GID 天然继承 Java 进程，最小环境只写入 Java 用户对应的 `HOME/USER/LOGNAME` 和非敏感基础变量。
  - 前端改为“点击连接服务器终端 → 二次确认目标服务器 → 确认连接”，取消手工输入框；目标绑定值改为 `SERVER@linuxServerId`，取消确认以 `AbortError` 回到 idle，不显示伪失败。所有 root 文案和 API client 方法名同步改为服务器终端语义。
  - 正式环境仍默认关闭并强制 WSS 定向网关；本地 `test` profile 显式启用服务器终端、使用 Java `user.dir` 作为工作目录并允许直连签票 Java 的 `ws://`，未修改 `.env.test` 或 `.env.local`。
  - 同步 HTTP API、安全、部署、多后台、模块与前端包文档；保留 `SUPER_ADMIN`、目标 Java 精确路由、一次性 ticket、Origin、限流、active 租约、超时、清理和无命令正文审计。
- How:
  - 后端 terminal/API 定向测试 22 项通过；前端弹窗、terminal、backend-api 定向测试 77 项通过，13 个前端项目 typecheck、agent-web 生产 build、后端 app reactor package 通过。
  - 按 `.env.test` / `test` profile / JDK 25 重启 backend、opencode-manager 和 frontend，health/readiness 为 UP、前端 3000 返回 200、登录 CORS 和 manager WebSocket 正常。
- Result:
  - Playwright 真实登录后完成“选择服务器工作空间 → 服务器终端 → 二次确认 → WebSocket → 命令输入”，终端执行 `id -un` 写出的用户为 `kaka`，与实际 Java 进程用户一致；终端保持固定高度且可输入。验收截图保存在本机 `.tmp/server-terminal-java-user.png`。
  - 未新增 HTTP 路径、RunEvent/SSE、数据库字段、migration、SQL、generated SDK 或额外权限；`confirmationText` 字段形状保持不变，但确认值从旧 `ROOT@...` 改为 `SERVER@...`，旧前端需与后端同步升级。生产 Linux 仍需按目标 systemd 用户和真实 WSS 网关验收。

### 2026-07-18 - 迁移服务器终端入口并修复 xterm 高度反馈循环

- Why:
  - 用户要求把超级管理员服务器终端放进“选择服务器工作空间”弹窗，并反馈现有 xterm 会持续拉长且无法正常输入。
- What:
  - `ServerWorkspacePickerDialog` 新增绑定左侧当前服务器的“服务器终端”视图，保留 `ROOT@linuxServerId` 逐次确认；切服、返回目录或关闭弹窗都会卸载旧终端并清空确认。运行管理页删除重复入口。
  - `TerminalPanel` 改为有界 viewport + 绝对定位宿主；ResizeObserver 对相同尺寸去重并按动画帧合并 fit，WebSocket open 后同步 cols/rows 并聚焦 xterm。
- How:
  - 继续复用既有 root ticket API、terminal client、xterm/FitAddon 和后端 WSS/PTY 链路，没有新增 API、服务、ticket 类型或安全例外。
  - 前端全量 Vitest 79 files、1273 passed/1 skipped，terminal/agent-web typecheck 与 agent-web 生产 build 通过；`.env.test`/`test` 三服务重启后 health/readiness UP、前端 200、manager health 正常。
- Result:
  - 入口、确认、切服重置、重复 resize 去重、连接后聚焦与键盘 envelope 均有回归覆盖。应用内浏览器运行时因 `Cannot redefine property: process` 未完成登录态视觉点验；macOS 本机默认关闭 root 终端，真实 Linux root + WSS 命令执行仍需目标环境验收。

### 2026-07-18 - 补充设置权限内的新手路径与手册章节

- Why:
  - 用户希望把设置弹窗中普通用户和应用管理员相关的操作纳入新手引导与内置手册，同时不展开超级管理员专属用户管理。
- What:
  - 新增“设置与权限内操作”手册章节并注册到 VitePress、HelpCenter 和宠物问答；引导第 07 步改为说明个人 SSH Key、应用管理、版本库管理和工作空间入口。
  - 同步 FAQ、首次准备、快速开始、手册首页、前端 README/PACKAGE、模块地图，并修正设置角色可见性说明。
- How:
  - 复用现有 SettingsMenu/SettingsAppWorkspacePanel/SettingsRepositoryPanel 的真实权限和操作文案，仅增加手册注册、引导文案与回归断言，没有新增 API、事件或数据状态。
- Result:
  - 定向设置/引导/帮助中心测试 5 个文件 44 项通过；当前 app 的 vue-tsc、用户手册构建、agent-web 生产构建通过；test profile 三服务重启成功，readiness UP、前端 200。构建仍有既有大 chunk 提示。

### 2026-07-18 - 优化普通用户工作区与对话新手路径

- Why:
  - 用户反馈普通用户不知道应用入口、应用选中后工作区仍为空、对话如何建立，以及工作区小地球如何引入需求子条目。
- What:
  - 首次引导 v3 改为锚定真实应用下拉、workspace/version 切换、小地球、新建对话、宠物、设置和手册按钮；明确普通用户不能新建应用、必须选中 workspace/version、首条消息自动建对话。
  - 快速开始、工作区、对话、首次准备、FAQ 和手册首页补充四个入口、空白工作区排查、管理员边界、小地球引入和 `#` 子条目上下文流程；同步前端 README、PACKAGE 和模块地图。
- How:
  - 复用已有 UI、工作区切换、iframe、对话和手册链路，只增加 `data-onboarding` 锚点与文案，没有新增 API、事件或数据状态。
- Result:
  - 5 个前端相关测试文件 185 passed/1 skipped，agent-web typecheck、用户手册构建、agent-web 生产构建通过；test profile 三服务重启完成，backend readiness UP、frontend 200。真实登录态浏览器交互未代填账号，未做登录后视觉验收。

### 2026-07-18 - 发布公共 Mermaid 规约到远端 master

- Why:
  - 用户确认将公共测试设计 Agent 的 Mermaid 11.16.0 规约提交发布到远端主分支，使公共仓库包含该修复。
- What:
  - 将公共配置提交 `3c89512 统一 Mermaid 11.16.0 语法规约` 从现有 `public-usr_test_dev` 分支推送到 `origin/master`。
- How:
  - 推送前执行 `git fetch origin`，确认本地相对 `origin/master` 为 `1 ahead / 0 behind`，随后使用非强制 `git push origin HEAD:master`；最后通过 `git ls-remote` 和远端跟踪引用双重核对。
- Result:
  - 远端 `refs/heads/master` 已从 `37c9ef8` 快进到 `3c89512bae0c6fa681157e61fc4c62e4d8430ed8`，本地公共 worktree clean；未验证平台各节点的公共配置 rollout 状态。

### 2026-07-18 - 公共测试设计 Agent 统一 Mermaid 11.16.0 语法规约

- Why:
  - 公共测试设计 Agent 生成的场景图在节点 label 中直接写入 ASCII 双引号，导致项目使用的 Mermaid 11.16.0 无法解析，图表展示和可视化编辑同时失败。
- What:
  - 在公共 Agent 个人 worktree `public-usr_test_dev` 新增 `test-design/rules/mermaid.md`，集中约束 Mermaid 11.16.0 最低兼容基线、动态 label 转义、ASCII 节点 ID、subgraph 可视化编辑限制和写入/冻结前校验记录。
  - `test-design`、路径法、场景法 skills 以及 generation/review Agents 强制按需读取公共规约；质量门禁和 Phase A manifest 增加 `syntaxBaseline/staticCheck/parserCheck`，模板改为安全的带引号 label 写法，并新增包含 `用户点击"发起取证"` 的回归 eval。
  - 同步公共配置 `README.md` 和 `opencode/AGENTS.md`；公共配置提交为 `3c89512 统一 Mermaid 11.16.0 语法规约`。
- How:
  - 复用现有 test-design rules 加载链路，没有新增平行 Agent、Skill 或运行时代码；parser 不可用时只能记录 `UNAVAILABLE`，不得伪造通过。
  - 使用项目实际 Mermaid 11.16.0 官方 parser 校验公共规约示例、路径模板和场景模板；同时校验 19 个 Agent/Skill frontmatter、规则引用、eval JSON、冲突标记和 `git diff --check`。
- Result:
  - 三个 Mermaid 代码块均通过 11.16.0 解析，公共 Agent/Skill 配置结构校验通过；未修改 API、事件、数据库、前后端代码、环境配置或 generated SDK。
  - 公共配置提交保留在本地 `public-usr_test_dev` 分支，未推送或合并到远端 `master`。

### 2026-07-18 - 超级管理员服务器 root 终端

- Why:
  - 超级管理员需要在运行管理页直接进入当前部署 Linux 服务器，不希望维护额外 SSH 用户名、密码、独立 terminal service、分布式租约或另一套消息协议。
- What:
  - 复用现有 terminal service/ticket/store/WebSocket/限流/超时/清理/审计链路，新增 `server-root` 目标；HTTP POST 通过公共 `BackendJavaRouteResolver`/`BackendHttpForwarder` 路由到目标 Java，WebSocket 由 Nginx 按 `linuxServerId` 精确代理。
  - 进程适配改用 Pty4J，支持真实 resize；服务器终端固定 `/bin/bash`、`/data/testagent` 和最小环境，不继承 Java 密钥。功能默认关闭，仅 `SUPER_ADMIN`、严格确认 `ROOT@linuxServerId`、目标匹配且 Java effective UID 为 0 时签票，公开地址强制 `wss://`。
  - 运行管理服务器行新增 root 终端弹窗，复用改造后的 xterm.js `TerminalPanel`；同步 HTTP API、安全、部署、模块和前端包文档，以及企业 backend/nginx env、TLS 和定向路由渲染脚本。
- How:
  - 没有新增平行 service、Redis lease、数据库或 SSE；workspace 与 server-root ticket 只在目标 JVM 内存中短期保存。Nginx 使用 `TEST_AGENT_NGINX_TERMINAL_ROUTES=linuxServerId=host:port` 生成 exact location，TLS 由证书路径参数显式开启。
  - 后端 terminal/API 定向测试分别 25/16 passed；前端全量 Vitest 77 files、1271 passed/1 skipped，terminal/agent-web typecheck 与生产 build 通过；Nginx 单/多后端、TLS、定向 route 和单机配置脚本通过。
- Result:
  - `.env.test`/`test` profile 三服务真实重启成功，backend health/readiness UP、frontend 3000 返回 200、未认证 root ticket 返回统一 401；fat jar 已确认包含 Pty4J 和 Linux x86-64/aarch64 原生库。
  - macOS 本机不是 Linux root + HTTPS/WSS 企业环境，因此未实际执行 root 命令；生产启用前仍需在目标 Linux 以 root Java、真实 TLS 证书和 WSS 网关完成验收。未改数据库、事件、generated SDK 或 `.env.local`。

### 2026-07-18 - 公共 Agent Diff 面板持续感知磁盘变化

- Why:
  - 既有实现只在当前工作台保存成功后传递一次 revision；该信号被错过或变更来自其他本地 Git/磁盘操作时，已经打开的 Diff 面板会保留旧快照，仍需点击刷新按钮。
- What:
  - 进入“变更”面板时立即复用 `GitChangesPanel.refreshChanges()` 核验三个作用域；停留期间每 5 秒继续调用同一方法，切回文件树/搜索或组件卸载时立即清理定时器。
  - 保留 Agent 文件保存后的 revision 即时刷新，形成“保存立即刷新 + 可见期间兜底核验”两层机制，没有新增 API、事件或第二套 Diff 状态。
  - 同步 frontend、agent-web README/PACKAGE、前端规范和模块图。
- How:
  - TDD 先新增“进入立即刷新、5 秒后再次刷新、离开后停止”组件用例并确认旧实现失败，再扩展 `FigmaFileExplorer`；Git Changes 定向 41 项与 agent-web typecheck 通过。
  - Playwright 真实登录验证公共 Diff 请求在进入面板后新增并持续出现；切回文件树后超过 5 秒，请求计数保持 `5 -> 5`。
- Result:
  - JDK 25 下后端 18 模块打包成功，按 `.env.test`/`test` profile 重启 backend、opencode-manager、frontend；health/readiness UP、前端 3000 返回 200、CORS 正常，manager 无 decode/reconnect 错误。
  - 轮询只在变更面板可见期间执行，不在后台长期扫描 Git；不涉及 HTTP API、RunEvent、数据库、generated SDK、环境配置或安全凭据。

# Session Log

## Entries

### 2026-06-25 - Fix AgentWorkbench Curly Quote Parse Error

- Why: 本地 Vite 启动后报 `[vue/compiler-sfc] Unexpected character '“'`，`AgentWorkbench.vue` 的 `handleSend` 分支里混入了中文弯引号，导致 SFC 无法解析。
- What: 将 `feedback.value` 对象里的非法弯引号恢复为 TypeScript 字符串所需的 ASCII 引号，保留用户可见文案里的中文引号。
- How: 只修改 `AgentWorkbench.vue` 报错段落，并用前端 build 与 dev server 页面访问验证。
- Result: `corepack pnpm --filter @test-agent/agent-web build` 通过；`http://127.0.0.1:3000/` 已无 Vite overlay，页面进入登录态。
- Pitfalls: 无。
- Verification: 前端 build；Playwright 访问本地 dev server 检查无 `[plugin:vite:*]` overlay 和控制台错误。

### 2026-06-25 - Close opencode process deployment operations batch

- Why: opencode 用户进程管理已经完成数据模型、调度契约、manager 控制面、runtime 接入和超管页面，还缺少多 Linux 服务器真实部署、扩容、回滚和验收说明。
- What: Added `tools/verify-opencode-process-deployment.sh`, wired it into `tools/verify-dev-scripts.sh`, fixed a `restart-dev-services.sh` unset-variable edge case, and updated deployment/database/security/backend/frontend/opencode-manager docs plus the batch ledger.
- How: Kept runtime behavior unchanged; the new script only performs read-only health, manager discovery and SUPER_ADMIN overview smoke checks. Deployment docs now cover direct backend listen URLs, manager all-backend WebSocket connections, non-overlapping host port pools, mounted session/config/state directories, heartbeat/timeout knobs, scale-out, failure handling and rollback.
- Result: Batch 7 has an executable smoke check and a stable operations handoff for multi-server validation; `requirements/todo/deployment.md` remains unrelated and unstaged.
- Pitfalls: Full first-login/process-rebuild validation still requires a real multi-server environment and real tokens; local verification only proves scripts/docs and existing tests.
- Verification: `git diff --check -- . ':(exclude)requirements/todo/deployment.md'`, `bash -n tools/verify-opencode-process-deployment.sh && tools/verify-dev-scripts.sh`, and `tools/verify-ai-docs.sh` passed locally.
- Next: Stage only batch 7 files and commit with a Chinese message.

### 2026-06-25 - Fix Chat New Conversation And External Model Sync

- Why: 手工测试发现「新建对话」按钮点击后无效果，且选择部分百炼模型发送后没有收到响应。
- What: 前端把新建对话从空发送改为显式重置当前 Session / Run / 日志 / diff / token 状态；发送和命令执行时统一传 `provider/model`；后端同步外网百炼 `/models` 返回的完整模型列表到 opencode provider 配置，避免选择未注册模型导致 `ProviderModelNotFound`。
- How: 复用 `AgentWorkbench.vue` 现有状态、`dispatchChat(reset)` 和 `ModelCatalogApplicationService` 的 provider patch 入口，只扩展现有同步逻辑，不新增并行 API。
- Result: 本地重启后 `http://127.0.0.1:3000` 和 `http://127.0.0.1:8080` 已启动；opencode 当前 provider 配置已包含百炼返回的 10 个模型 key；`guo` profile 数据库和 Redis 地址按用户最新要求切到 `192.168.100.115`。
- Pitfalls: UI 级完整人工验收按用户要求改为由用户手工执行；本次只保留接口、构建、单测和启动级验证。
- Verification: `corepack pnpm --filter @test-agent/agent-web build`；`JAVA_HOME=$(/usr/libexec/java_home -v 25) ... mvn -pl test-agent-opencode-runtime,test-agent-app -am test`；`./restart-dev-services.sh --profile guo --env-file .env.local --skip-frontend-build`。

### 2026-06-24 - Prepare WR Local Guo Startup Data

- Why: 本地验收需要使用 `guo` 配置连接共享 PostgreSQL/Redis，并为用户 `wr` 准备自己的应用 `F-WRAPP` 和项目本地工作区。
- What: 拉取远程最新 `main`，保留 `guo` PostgreSQL `sslmode=disable` 修正，允许根目录重启脚本使用 `--profile guo`，并同步启动文档；在共享库中按 `lipc` 的数字应用 ID 习惯创建 `app_id=113023` 的 `F-WRAPP` 数据。
- How: 通过 JDBC 事务清理上一轮不合规的 `f-wrapp` 临时记录，再写入用户、应用成员、代码库、应用工作区模板、应用版本、运行态 Workspace、recent workspace 和分支偏好，工作区路径指向 `/Users/kaka/Desktop/intelligent-test-agent`。
- Result: Flyway 已校验到 V12，`wr` 用户和 `113023/F-WRAPP` 的本地工作区数据已落库；后续重启仍需受 Redis `192.168.100.135:6379` 网络连通性约束。
- Pitfalls: `192.168.100.135:6379` 当前从本机返回 `No route to host`，不能通过关闭校验掩盖；若网络未恢复，Actuator health 会保持 DOWN。
- Verification: 已用 JDBC 查询核对 Flyway、用户、应用、仓库、工作区模板、版本和偏好记录；待执行脚本校验和本地重启。

### 2026-06-24 - Require Session Log In Project Rules

- Why: The session log needed to be treated as a first-class tracked artifact, not an ad hoc local note, so remote commits carry the handoff context too.
- What: Updated `AGENTS.md`, `docs/guides/ai-workflow.md`, and `docs/guides/self-checklist.md` to require `.agents/session-log.md` updates and to describe how it is included in commits.
- How: Kept the change in the project entry docs instead of business code, and reused the existing Why/What/How/Result log shape so future sessions stay consistent.
- Result: Future code-change batches should leave behind a committed session log that explains the change for other developers and agents, including remote-push ready workflows.
- Pitfalls: None.
- Verification: `git diff --check` not run yet.
- Next: Run a light diff sanity check, then commit the doc updates together with this log entry.

### 2026-06-24 - Add Code Update Handoff Skill

- Why: Code-change batches in this repo needed a shared handoff rule so future agents can see the real status and avoid re-deriving context.
- What: Added `.opencode/skills/code-update-handoff/SKILL.md`, fixed `agents/openai.yaml`, and created this session log file.
- How: Started from the skill-creator template, then replaced placeholders with a repo-specific workflow that always emits `Not done yet` and appends a compact log entry.
- Result: Future handoffs can stay candid, and other developers or agents can quickly understand the reason, scope, approach, and expected effect of a change.
- Pitfalls: `quick_validate.py` needed `PyYAML`; resolved by running it inside `.tmp/skill-validate-venv`.
- Verification: `./.tmp/skill-validate-venv/bin/python3 /Users/kaka/.codex/skills/.system/skill-creator/scripts/quick_validate.py .` in `.opencode/skills/code-update-handoff`.
- Next: Use this skill whenever a batch edits repository files so the handoff and session log stay consistent.

### 2026-06-24 - Simplify workspace selector + add +新增版本 + seed F-COSS workspaces

- Why: 用户希望「应用工作空间」两级菜单只展示工作空间名（一级），hover 展开版本子菜单（二级），版本列表底部加 `+新增版本` 行，弹 yyyy年M月 时间组件，并在 F-COSS 应用下多造几个工作空间模板。
- What:
  - 后端 `ManagedWorkspaceApplicationService` 新增 `yyyy年M月` 版本格式校验，`sanitizeVersionForBranchAndPath` 把 `2024年1月` 转为 `2024-01` 用于派生分支名 / 物理路径，`normalizeVersion` / `resolveBranch` / `appRepoRoot` / `personalRepoRoot` 全部接入。
  - 新增 Flyway `V13__seed_fcoss_more_workspaces.sql`，在 V10 的 F-COSS 数据基础上追加 3 个工作空间模板（移动端 / 数据同步 / 报表）和对应初始版本。
  - `WorkbenchFooter.vue` 简化一级菜单只显示 `workspaceName`、去掉 `directoryPath · branch` 副标题；子菜单底部加「+新增版本」行；新增 el-dialog + `ElDatePicker` (`type=month` / `format=yyyy年M月` / `value-format=yyyy年M月`) 提交 `create-version` 事件。
  - `FigmaFileExplorer.vue` 透传 `creatingVersion` prop 与 `createVersion` emit。
  - `AgentWorkbench.vue` 接入 `handleCreateVersion`：调 `api.createWorkspaceVersion`，成功后失效 `versionsByTemplateId` 缓存并把新版本切到工作区；`@create-version` 监听接好。
  - `workbench.spec.ts` mock 新增 `POST .../versions` 路径拦截，捕获用户原值 `version` 字段。
  - 文档：更新 `docs/api/http-api.md`（POST 规则 / 两级菜单说明）、`docs/deployment/database.md`（V13 节）、`backend/test-agent-workspace-management/README.md`（测试覆盖说明）、`frontend/apps/agent-web/README.md`（两级菜单简化 / 「+新增版本」说明）。
- How: 后端先扩 `Pattern` + `sanitize`，新加一个 `Path.endsWith` 风格的 Java 单元测试绕开 Windows 路径分隔符；前端用 Element Plus 的 `el-dialog` + `el-date-picker` 直接覆盖时间选择场景；V13 用 `where exists / where not exists` 幂等保护。
- Result: 工作空间选择器符合「只显示名 + hover 出版本 + 底部新增版本」三段式；后端同时兼容 `yyyyMMdd` 和 `yyyy年M月`，新增版本入参为 `2024年1月` 原值；F-COSS 应用从 1 个模板扩展为 4 个模板。
- Pitfalls: 仓库里两个旧测试（`createsStandardApplicationVersionWorkspaceAndRecordsRecentUsage` / `createsPersonalWorkspaceFromApplicationVersionWorktree`）在 Windows 上因路径分隔符断言失败，与本次改动无关（已用 `git stash` 验证过改动前的状态同样失败）；本次新测试改用 `Path.endsWith` 规避。
- Verification: `pnpm typecheck` 通过；`mvn -pl test-agent-workspace-management -am test` 我新加的 2 个测试通过（8 / 10），其余 2 个失败是上面提到的预存在 Windows 路径问题。
- Next: 等用户审过 PR 提单；如需进一步简化可考虑把 FigmaFileExplorer 的 `creatingVersion` 与工作区切换的反馈合并。

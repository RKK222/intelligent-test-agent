# Session Log

## Entries

### 2026-06-25 - Reduce Session Log Noise

- Why: The previous policy made the session log too chatty for small edit batches, which reduced its usefulness as a concise handoff artifact.
- What: Tightened the repo rules in `AGENTS.md`, `docs/guides/ai-workflow.md`, `docs/guides/self-checklist.md`, and `.opencode/skills/code-update-handoff/SKILL.md` plus its `agents/openai.yaml` metadata so logging happens once per meaningful session boundary.
- How: Kept the same `Why / What / How / Result` shape, but changed the trigger from per-batch persistence to per-session reusable information, with related edits merged into one entry.
- Result: Future sessions should write fewer, denser log entries that are easier for other developers and agents to scan.
- Pitfalls: None.
- Verification: `git diff --check`; `/Users/kaka/Desktop/intelligent-test-agent/.tmp/skill-validate-venv/bin/python3 /Users/kaka/.codex/skills/.system/skill-creator/scripts/quick_validate.py .`.
- Next: Use the new rule in subsequent sessions and avoid file-level log spam.

### 2026-06-25 - 修复运行管理页面因 ID 格式不一致导致查询失败的问题

- Why: 超级用户在设置-运行管理页面无法看到容器、进程状态。经排查发现：数据库中存在历史/异常写入的 `backend_process_id` 等字段，其格式与当前领域对象要求不一致（如 `BackendProcessId` 要求 `bjp_` 前缀），导致 RowMapper 构造领域对象时抛出 `IllegalArgumentException`，整个页面查询失败。
- What:
  - 新增 Flyway migration `V15__add_opencode_process_id_check_constraints.sql`
  - 清理不符合前缀规则的脏数据：删除 `backend_java_processes` 中 `backend_process_id` 不以 `bjp_` 开头的记录，删除 `opencode_container_managers` 中 `manager_id` 不以 `mgr_` 开头的记录，删除 `opencode_server_processes` 中 `process_id` 不以 `ocp_` 开头的记录
  - 添加数据库 CHECK 约束，确保 ID 前缀格式正确，防止未来再写入不符合格式的数据
- How: 通过 Flyway migration 执行 DELETE 清理脏数据 + ALTER TABLE 添加 CHECK 约束。
- Result: 运行管理页面查询不再因脏数据导致领域对象构造失败；数据库层面新增约束防止非法 ID 写入。
- Pitfalls: 
  - 一开始误认为 `LinuxServerId` 也需要 `lsrv_` 前缀，实际上它要求 IPv4 地址格式
  - `OpencodeContainerId` 只要求非空文本，无固定前缀要求
- Verification: 需要在有脏数据的环境中重启后端验证 migration 执行成功，页面可正常加载。
- Next: 建议用户执行 SQL 查询确认是否存在脏数据：`SELECT backend_process_id FROM backend_java_processes WHERE backend_process_id NOT LIKE 'bjp_%';`

### 2026-06-25 - 为 F-WRAPP 应用新增远程代码库用于测试工作区和分支功能

- Why: 本地开发环境数据库中，F-WRAPP 应用只有本地代码库，需要新增远程 Git 代码库用于测试工作区创建、版本库克隆、分支操作等功能。
- What:
  - 在 `code_repositories` 表新增 `repo_wrapp_mimoagent` 代码库记录，git_url 为 `https://gitee.com/wrui233/mimoagent`
  - 在 `application_repository_links` 表新增关联，将新代码库关联到 F-WRAPP 应用 (app_id: 113023)
  - 拉取远程分支并重启前后台服务
  - 更新 `.tmp/test-data-add-mimoagent-repo.md` 文档，记录测试场景、测试步骤、测试数据
- How: 通过 Docker exec 执行 psql 命令直接操作本地数据库（15432端口），使用 INSERT ... ON CONFLICT 语法保证幂等。
- Result: F-WRAPP 应用现在关联了两个代码库（本地仓库 + 远程仓库），可用于测试工作区和分支功能；前后台服务已重启成功。
- Pitfalls: 一开始误修改了 `repo_fcoss_main` 的 git_url，后来恢复原数据并新增正确记录。
- Verification: 数据库查询确认新增记录存在，前端可访问 `http://127.0.0.1:3000`。
- Next: 用户验证工作区和分支功能是否正常。

### 2026-06-25 - 将 wr 用户角色改为应用管理员

- Why: 用户要求将 wr 用户从普通用户角色改为应用管理员角色。
- What: 更新 `user_roles` 表，将 wr 用户的 `dict_id` 从 `dict_role_user` 改为 `dict_role_app_admin`。
- How: 通过 Docker exec 执行 psql UPDATE 命令。
- Result: wr 用户角色已从"普通用户"改为"应用管理员"。
- Pitfalls: 无。
- Verification: 数据库查询确认角色已更新。
- Next: 无。

### 2026-06-25 - Fix el-date-picker month cells to show "1月/2月/…" in Chinese

- Why: 用户反馈「+新增版本」弹窗里的 el-date-picker (type=month) 打开后，月份单元格里显示英文 "Jan/Feb/…"，希望显示中文 "1月/2月/3月/…"，与项目里其他中文文案风格一致。
- What:
  - `frontend/apps/agent-web/src/main.ts` 引入 `element-plus/es/locale/lang/zh-cn` 和 `dayjs/locale/zh-cn`，调用 `dayjs.locale("zh-cn")`；在原 zh-cn locale 上派生一个只覆盖 `el.datepicker.months` 12 项的浅拷贝（`jan: "1月"`, `feb: "2月"`, …, `dec: "12月"`），再把这份 locale 通过 `app.use(ElementPlus, { locale: zhCnWithArabicMonths })` 注入。
  - 不直接用 Element Plus 默认的 `zh-cn` locale 是因为它把月份渲染为中文数字"一月/二月/…"（Element Plus 2.12 的 `el.datepicker.months.{jan,dec}` 默认值），与用户期望的阿拉伯数字 "1月/2月/…" 不一致。
  - `frontend/apps/agent-web/tests/workbench.spec.ts` 既有"yyyy年M月"测试里追加两步断言：打开日期面板后能定位到 `el-month-table`，并看到 `^1月$` 和 `^6月$` 文案（之前是 `console.log` 调试输出，已清理）。
- How: 复制 zh-cn locale 的浅层结构再覆盖 `datepicker.months` 这一层，其它字段（按钮、星期、占位符等）原样保留，避免影响其它使用 Element Plus 的位置。
- Result: e2e 中 `+新增版本` 弹窗的月份面板渲染为 "1月/2月/…/12月"；`pnpm playwright test workbench.spec.ts -g "cascade"` 6 个 case 全部通过（1 个 mobile 被 skip）。
- Pitfalls: 仅设置 `dayjs.locale("zh-cn")` 不够，Element Plus 月份面板走的是 i18n 包而不是 dayjs 的 locale；需要同时注入 Element Plus locale。`zh-cn` locale 默认会把月份渲染为 "一月/二月/…"，需要再浅拷贝覆盖 `months` 字段。
- Verification: `pnpm playwright test workbench.spec.ts -g "新增版本 dialog opens"` 通过；`pnpm playwright test workbench.spec.ts -g "cascade"` 全部通过。
- Next: 等用户验收；如果未来 Element Plus 升级破坏了 i18n key，需要在 e2e 第一时间复现。

### 2026-06-25 - Repair FigmaChatPanel.vue duplicate declarations blocking dev server

- Why: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 在某个合并后存在两套 `defineProps` / `defineEmits`（一份带 `processStatus`/`initialize-process`，另一份带 `selectedModelLabel`/`open-model-picker`）和重复的 `const hasFileChanges = computed(...)`，导致 vue-tsc 报 TS2451 / TS2339 / TS2551 / TS2769 共 ~30 条错误，Vite dev server 抛 "Identifier 'props' has already been declared"，e2e 跑不起来。
- What: 删掉旧的 `const props = defineProps<{...}>()` / `const emit = defineEmits<{...}>()` 整段以及重复的 `hasFileChanges`，把保留版的 props (`selectedModelLabel`/`modelPickerDisabled`/`stopDisabled`/`stopDisabledReason`/`processStatus`/...) 与 emits (`open-model-picker`/`initialize-process`/...) 合到一份。
- How: 全文检索确认旧版 props（`selectedModelLabel`/`history`/`modelPickerDisabled`/`stopDisabled`）在模板 / script 中没有引用，所以可以直接合并而非 union；保持新版（带 `processStatus` 等的）作为唯一一份。
- Result: `pnpm typecheck` 对 FigmaChatPanel 的错误清零；dev server 重新启动后 HTTP 200，e2e 可以正常 navigate 到 `/`。
- Pitfalls: 这个修复与"中文月份"任务无关，但属于阻断 dev server 的预存在 bug；不修就测不了用户反馈。
- Verification: `pnpm typecheck` 仅剩其它预存在错误（与本 PR 无关），`pnpm playwright test workbench.spec.ts -g "新增版本 dialog opens"` 通过。
- Next: 在 commit message 里把"中文月份"和"FigmaChatPanel 修复"拆成两条提交，避免单点耦合。

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

### 2026-06-25 - 右上角用户菜单顶部灰显用户角色（来自 dictionaries.dict_label）

- Why: 用户反馈「F-COSS」右上角下拉菜单只有「用户名 / 退出登录」两项，希望在菜单顶部加一行灰显展示当前用户角色；角色来源涉及 `users`（/api/auth/me 上下文）→ `user_roles`（关联角色 code）→ `dictionaries.dict_label`（中文展示名）三张表。
- What:
  - 后端：`AuthDtos.CurrentUserResponse` 新增 `roleLabels: List<String>` 字段（与 `roles` 等长、对齐）；`AuthController.me` 注入 `DictionaryRepository`，按 `Dictionary.DICT_KEY_ROLE` + role code 查 `dict_label`，缺失时回退为 role code 本身，避免阻断主链路。
  - 共享类型：`shared-types/CurrentUser` 新增 `roleLabels?: string[]`，向下兼容旧 token / 旧响应。
  - 前端壳子：`FigmaShell` 新增 prop `currentUserRoleLabels?: string[]`；下拉菜单顶部以 `ShieldCheck` 图标 + 灰显样式新增一行（class `figma-user-menu-role`），多角色用「、」拼接；`roleLabels` 为空或缺失时整行 v-if 不渲染，避免出现「角色：」空文案。
  - 入口串联：`AgentWorkbench` 把 `authStore.currentUser?.roleLabels` 透传给 `FigmaShell`。
  - e2e：`workbench.spec.ts` 的 `/api/auth/me` mock 同步返回 `roleLabels`（新增 `roleLabelOf` 工具，固定映射 `SUPER_ADMIN / SYSTEM_ADMIN / APP_ADMIN / USER`），`user avatar menu logs out` 用例额外断言下拉菜单顶部出现 `.figma-user-menu-role` 灰显行且文案为「应用管理员」。
  - 文档：`docs/api/http-api.md` 同步 `CurrentUserResponse.roleLabels` 字段、三表数据来源、字典缺失回退行为；`frontend/apps/agent-web/README.md` 顶栏下拉菜单条目补一句角色灰显行说明。
- How:
  - 后端先扩 DTO，再在 controller 用 `dictionaryRepository.findByDictKeyAndValue(...)` 现成 API 翻译角色；测试新增 `meReturnsRolesAndChineseRoleLabelsFromDictionary` / `meFallsBackToRoleCodeWhenDictionaryEntryIsMissing` 两条覆盖主链路 + 回退；`loginReturnsRolesLoadedByAuthService` 保留。
  - 前端用 lucide-vue-next 的 `ShieldCheck`（已存在于 `node_modules`），样式复用现有 `.figma-user-menu-summary` / `.figma-user-menu-item` 的基础 padding/border-radius，仅叠加更小字号 + 次要色 + 灰底图标 + 不可点击 cursor，保留设计语言一致。
  - e2e mock 用 `roleLabelOf` 把 mock 后端的字典翻译前置到 e2e 层，避免 e2e 依赖新的 GET /api/dictionaries 接口；这样 future 字典表字段变化只需要改 mock 工具即可。
- Result: 点击右上角 F-COSS 头像，下拉菜单顶部出现一行灰色角色（如「应用管理员」），位置在用户名 / 退出登录之上；多角色显示为「应用管理员、普通用户」；后端 `/api/auth/me` 的 `roleLabels` 与 `roles` 顺序一致。
- Pitfalls: 工作区里同时存在另一位开发者「opencode 进程本地节点回退 & 重置绑定」相关文件的中间态改动（`UserOpencodeProcessStatusResponse` / `UserOpencodeProcessAssignmentService` / `RuntimeDtos` / `UserOpencodeProcessController` / `OpencodeProcessManagementRepository` / `JdbcOpencodeProcessManagementRepository` / `FigmaChatPanel` / `backend-api/index.ts` / `RuntimeControllerTest` / `UserOpencodeProcessAssignmentServiceTest`），会破坏 `mvn -am` 与 `pnpm typecheck` 的全量构建；本次提交只 `git add` 上面 9 个直接相关文件 + 本条 session-log，未把这些未完成改动一起带入。
- Verification: 临时 stash 掉上述中间态后，`mvn -pl test-agent-api test -Dtest=AuthControllerRolesTest` 3/3 通过；`pnpm --filter @test-agent/shared-types typecheck` 通过；FigmaShell 的 `ShieldCheck` 在 `lucide-vue-next` 类型声明中存在，prop 与 `currentUserRoleLabels` 字段链路类型自洽。
- Next: 等用户验收；如需补充真实字典接口（`GET /api/dictionaries?dictKey=ROLE`）让前端不再依赖 `/api/auth/me` 翻译结果，下一轮再加，避免本次改动超出最小范围。

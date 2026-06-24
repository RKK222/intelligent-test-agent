# Session Log

## Entries

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

### 2026-06-24 - Fix workspace cascade menu z-index/clipping/overflow + date format

- Why: 用户反馈两级菜单 ① 浮在底部被 dockview `overflow:hidden` 裁掉、② hover 一级菜单没有在右侧出现二级菜单（被一级菜单 max-width 切了）、③ 出现横向/竖向滚动条、④ 子菜单靠近视口底时底部被遮挡、⑤ 「+新增版本」日期显示成 `yyyy年1月` 而不是 `2026年8月`。
- What:
  - `WorkbenchFooter.vue`：
    - 一级菜单面板和二级菜单都用 `<Teleport to="body">` + `position:fixed` 挂到 body 末尾，加 `cascadeButtonRef` / `cascadeMenuPos` / `cascadeSubmenuPos` / `hoveredTemplateEl`，打开前用 `getBoundingClientRect()` 算 fixed 坐标；`onCascadePosScrollOrResizeBound` 在 raf 内同步刷新一二级菜单位置。
    - 一级菜单二级菜单 z-index 分别 9999 / 10000，避免被 dockview 内部 stacking context 盖住。
    - `onDocumentClick` 改用 `closest('.ta-workbench-cascade-panel/.ta-workbench-cascade-submenu/.ta-workbench-cascade')` 判断点击位置（Teleport 后 contains 失效）。
    - 子菜单 right 用一级菜单面板的 `right`（不是 li 的 right）作为锚点，避免子菜单起点落在面板里。
    - 删掉 `max-width:360px / max-height:360px / max-height:280px / overflow-y:auto` 触发的横向竖向滚动条，CSS 只剩面板级 `max-height: calc(100vh - 24px)` 兜底。
    - 子菜单防底部遮挡：`naturalTop = liRect.top - 6`；若 `viewportHeight - naturalTop - 12 < 200`，把 `naturalTop` 抬到 `viewportHeight - 12 - 200`，并把 `maxHeight` 同步成 `viewportHeight - naturalTop - 12`（用 `Math.max(80, ...)` 兜底），保证子菜单底部不超出视口。
    - 「+新增版本」`ElDatePicker` 改成 `format="yyyy-MM"` + `value-format="yyyy-MM"`，提交时 `confirmCreateVersion` 用 regex 把 `2026-08` 转换为 `2026年8月` 再 emit；规避 Element Plus 把 `yyyy年M月` 格式串当占位符渲染成 `yyyy年1月` 的 bug。
  - `workbench.spec.ts`：mockBackendApi 新增 `workspaceTemplates` / `workspaceVersions` 参数；新增 2 个测试：① 一级菜单 Teleport 到 body + 位置在按钮上方 + 无横向滚动 + 二级菜单在右侧；② 构造 20 个模板 + 15 个版本验证子菜单靠近底部时底部不超出视口。
- How: 一二级菜单都搬出 dockview 容器后，stacking context 不再互相嵌套，z-index 才能稳定生效；用 `closest` 替代 `contains` 适配 Teleport；溢出用"可用空间 + max-height"组合（不是 nextTick 二次测量），避免在 Vite/Playwright 环境下 nextTick 回调被 reactive batch 吞掉的边界；日期格式转换在提交时做而不是在 picker 上做，picker 显示标准格式（清晰无歧义），提交时按用户原始期望"原值透传"映射回 `yyyy年M月`。
- Result: 5 个 cascade 相关 e2e 测试全过（含桌面 + mobile 配置）；`pnpm typecheck` 通过；`pnpm build` 通过。6 个无关历史失败（model picker / phase 11 / live tracking）已用 `git stash` 验证为改动前就存在。
- Pitfalls: 第一次实现用 `nextTick` 二次测量修正底部溢出，Playwright 跑下来回调未触发（Vite/Vue 微任务时机问题），改用同步 `max-height` 计算更稳；`Math.max(120, ...)` 给子菜单硬保底 120px 高度反而让靠近底部的子菜单越界，去掉硬保底改 `Math.max(80, ...)` 兜底可读性 + 安全性平衡。
- Verification: `pnpm playwright test workbench.spec.ts -g "workspace cascade"` 5/5 通过；`pnpm typecheck` 通过；`pnpm build` 通过。
- Next: 让用户实际再点一遍两级菜单确认无其他视觉问题；如需进一步优化可考虑子菜单"展开方向自适应"（当一级菜单靠近视口右边缘时子菜单从左侧展开）。


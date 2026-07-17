# Session Log - huangzhenren

> 按提交者 `git config user.name` 分文件维护，新增条目置于 `## Entries` 顶部。
> 提交前需回顾所有 `.agents/session-log*.md`（含已冻结旧档 `.agents/session-log.md`）的近期条目。

## Entries

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

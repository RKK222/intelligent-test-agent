# macOS opencode Local Initialization Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 macOS 本地 `.env.test` 环境的公共配置、用户 opencode 初始化和通用参数平台筛选。

**Architecture:** 使用新的 Flyway 兼容迁移把 macOS 系统参数改为基于 `$TEST_AGENT_ROOT` 的项目内 `temp/` 路径，并由根启动脚本注入项目根目录。前端复用现有 Vue Query 查询，通过筛选值直接进入 query key 实现选择即刷新，不新增 API 或状态层。

**Tech Stack:** Java 21、Spring Boot、Flyway、Vue 3、Element Plus、TanStack Vue Query、Vitest、Bash、Go opencode-manager

---

## Chunk 1: 回归测试与本地参数

### Task 1: macOS 后端筛选契约

**Files:**
- Modify: `backend/test-agent-configuration-management/src/test/java/com/icbc/testagent/configuration/management/CommonParameterManagementApplicationServiceTest.java`
- Modify: `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform/CommonParameterManagementControllerTest.java`

- [ ] 把现有 `macos` 非法值用例改为未知值，新增 `macos` 可解析和可查询断言。
- [ ] 运行定向测试，确认旧断言先失败且新契约随后通过。

### Task 2: 通用参数平台选择即刷新

**Files:**
- Create: `frontend/apps/agent-web/tests/general-param-management-panel.test.ts`
- Modify: `frontend/apps/agent-web/src/components/system/GeneralParamManagementPanel.vue`

- [ ] 编写组件测试，断言下拉包含 `macos`，选择后立即使用 `platform=macos` 查询且页码重置。
- [ ] 运行测试并确认在生产代码修改前按预期失败。
- [ ] 删除 `draftPlatform/applyFilter`，将下拉绑定到 `activePlatform`，监听平台变化重置页码，移除筛选按钮。
- [ ] 运行定向测试并确认通过。

### Task 3: 可移植的 macOS 项目内路径

**Files:**
- Create: `backend/test-agent-persistence/src/main/resources/db/migration/V20260629095000__fix_macos_opencode_local_paths.sql`
- Modify: `backend/test-agent-persistence/src/test/java/com/icbc/testagent/persistence/MyBatisCommonParameterRepositoryIntegrationTest.java`
- Modify: `restart-dev-services.sh`
- Modify: `tools/verify-dev-scripts.sh`

- [ ] 先扩展持久化/脚本验证，断言 macOS 原始参数使用 `$TEST_AGENT_ROOT/temp` 且启动脚本提供默认根目录。
- [ ] 运行测试并确认新增断言失败。
- [ ] 新增数据迁移，修正六个 macOS 参数，并在公共 Git 地址仍为 `UNCONFIGURED` 时写入确认的 Gitee 地址。
- [ ] 在加载 `.env.test` 后设置可覆盖的 `TEST_AGENT_ROOT` 默认值。
- [ ] 运行持久化测试和 `tools/verify-dev-scripts.sh`。

## Chunk 2: 文档、真实启动与交付

### Task 4: 同步稳定文档

**Files:**
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `backend/README.md`
- Modify: `docs/api/http-api.md`
- Modify: `docs/deployment/backend.md`
- Modify: `docs/deployment/database.md`

- [ ] 记录 `macos` 过滤值与选择即刷新行为。
- [ ] 记录 `$TEST_AGENT_ROOT/temp` 路径、公共配置目录层级和启动脚本默认值。
- [ ] 明确本次没有 API、事件或结构变更。

### Task 5: 回归、目录清理与运行验收

**Files:**
- Modify if durable findings exist: `.agents/session-log.md`

- [ ] 运行后端定向测试、相关模块测试和打包。
- [ ] 运行前端定向测试、typecheck、构建，确认不改动用户已有 `frontend/pnpm-lock.yaml`。
- [ ] 停止服务后删除 `/tmp/test-agent` 和存在时的 `$HOME/tmp/test-agent`，确认没有扩大清理范围。
- [ ] 使用 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 启动三服务。
- [ ] 验证 backend health/readiness、frontend、CORS 和 manager 日志。
- [ ] 在真实页面选择 `macos` 验证自动刷新，初始化公共配置仓库，再初始化当前用户 opencode 进程。
- [ ] 按 `docs/guides/self-checklist.md` 自检；有新增持久结论时合并更新一次 session log。
- [ ] 只暂存本任务文件，自动创建中文 commit。

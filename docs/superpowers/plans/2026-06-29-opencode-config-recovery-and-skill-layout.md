# OpenCode Config Recovery and Skill Layout Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让脏公共配置仓库仍可浏览并可显式从 Git 恢复，同时整理公共/工作空间技能包目录并恢复 OpenCode 进程初始化。

**Architecture:** 复用现有 `AgentConfigApplicationService`、`GitWorkspaceService`、文件 WebSocket RPC 和 `AgentConfigPanel`，只扩展公共更新请求的可选恢复语义。公共配置使用 OpenCode 原生 `agents/` 与 `skills/`，工作空间普通文件树在展示层过滤 `.opencode`。

**Tech Stack:** Java 21、Spring Boot、Vue 3、TypeScript、Vitest、Maven、Git、OpenCode 1.17

---

## Chunk 1: 公共配置恢复

### Task 1: 脏仓库仍可浏览

**Files:**
- Modify: `backend/test-agent-workspace-management/src/test/java/com/enterprise/testagent/workspace/AgentConfigApplicationServiceTest.java`
- Modify: `backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/AgentConfigApplicationService.java`

- [ ] 增加失败测试，断言已初始化但脏的仓库返回 `initialized=true/status=CONFLICT`。
- [ ] 运行测试确认按预期失败。
- [ ] 分离“已初始化”和“工作树干净”语义。
- [ ] 运行测试确认通过。

### Task 2: 显式放弃修改并同步

**Files:**
- Modify: `backend/test-agent-common/src/main/java/com/enterprise/testagent/common/git/GitWorkspaceService.java`
- Modify: `backend/test-agent-common/src/test/java/com/enterprise/testagent/common/git/GitWorkspaceServiceTest.java`
- Modify: `backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/AgentConfigApplicationService.java`
- Modify: `backend/test-agent-workspace-management/src/test/java/com/enterprise/testagent/workspace/AgentConfigApplicationServiceTest.java`
- Modify: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/AgentConfigDtos.java`
- Modify: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/AgentConfigController.java`
- Modify: `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/platform/AgentConfigControllerTest.java`
- Modify: `frontend/packages/backend-api/src/index.ts`
- Modify: `frontend/packages/backend-api/tests/backend-api.test.ts`

- [ ] 增加失败测试，覆盖默认拒绝脏仓库和显式恢复已跟踪修改。
- [ ] 运行测试确认失败。
- [ ] 扩展现有更新请求和 Git 服务，未跟踪文件不删除。
- [ ] 运行后端和 backend-api 测试确认通过。

## Chunk 2: 目录与界面

### Task 3: 公共技能包扁平化

**Files:**
- Modify: `temp/opencode-config/opencode/skills/**`
- Delete: `temp/opencode-config/opencode/mimoagent-agents/**`
- Modify: `temp/opencode-config/README.md`

- [ ] 恢复误删的 `opencode/agents/*.md`。
- [ ] 把 18 个实际技能包移动到 `opencode/skills/<skill-name>/`。
- [ ] 删除符号链接和 `mimoagent-agents` 包装层。
- [ ] 验证每个技能包含 `SKILL.md`，并运行 OpenCode 加载检查。

### Task 4: 工作空间技能初始化和树去重

**Files:**
- Modify: `frontend/apps/agent-web/tests/agent-config-panel.test.ts`
- Modify: `frontend/apps/agent-web/src/components/AgentConfigPanel.vue`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Modify: `frontend/apps/agent-web/tests/workbench-utils.test.ts` or nearest existing workbench component test

- [ ] 增加失败测试，断言只写三个技能文件且普通根目录不展示 `.opencode`。
- [ ] 运行测试确认失败。
- [ ] 调整初始化模板和根目录展示过滤。
- [ ] 运行定向测试和 typecheck。

### Task 5: 公共恢复交互

**Files:**
- Modify: `frontend/apps/agent-web/tests/agent-config-panel.test.ts`
- Modify: `frontend/apps/agent-web/src/components/AgentConfigPanel.vue`

- [ ] 增加失败测试，断言脏仓库更新时需要显式确认放弃修改。
- [ ] 运行测试确认失败。
- [ ] 在现有更新弹窗中加入恢复警告和复选框，调用扩展请求。
- [ ] 运行测试确认通过。

## Chunk 3: 进程初始化与交付

### Task 6: manager 断连诊断和修复

**Files:**
- Modify: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/ManagerControlWebSocketHandler.java`
- Modify: `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/platform/ManagerControlWebSocketHandlerTest.java`
- Modify actual root-cause owner identified by the new log

- [ ] 增加覆盖入站异常可观测性的失败测试。
- [ ] 记录连接结束的异常和 manager/container 标识。
- [ ] 使用 `.env.test` 重启并定位实际异常。
- [ ] 在实际归属模块补回归测试和最小修复。

### Task 7: 文档、回归和提交

**Files:**
- Modify: `docs/api/http-api.md`
- Modify: `docs/deployment/backend.md`
- Modify: `backend/test-agent-workspace-management/README.md`
- Modify: `frontend/README.md`
- Modify: `.agents/session-log.md`

- [ ] 同步可选 API 字段、目录与恢复行为文档。
- [ ] 运行相关 Maven、Vitest、typecheck、OpenCode smoke 和 `git diff --check`。
- [ ] 使用 `.env.test` 重启三服务并在浏览器验证。
- [ ] 回顾 session log 和两个仓库状态。
- [ ] 分别创建中文提交，并推送公共配置仓库。

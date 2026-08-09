# 分享会话用户消息权威同步 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 任一分享会话参与方发送消息后，其他已打开页面立即显示完整正文和真实发送人，不再出现空白或错误归属的用户气泡。

**Architecture:** legacy RunEvent SSE 从平台 `session_messages` 恢复本轮权威 USER 输入并与 OpenCode 尾流合并，Redis 模式继续使用现有 input snapshot。前端仅隐藏尚无可见内容的临时 user envelope，正文到达后按稳定 message id 原地显示。

**Tech Stack:** Java 21、Spring WebFlux、MyBatis XML、JUnit 5/AssertJ、Vue 3、TypeScript、Vitest、Playwright。

## Global Constraints

- 不修改 OpenCode 源码或 generated SDK。
- 不新增 HTTP API、RunEvent wire type、数据库字段或 Flyway migration。
- 关系型查询只能通过 MyBatis XML mapper 实现。
- 分享请求继续保留真实 `AuthPrincipal`，OpenCode 执行身份仍为会话所属人。
- 人工维护的复杂逻辑必须有中文注释。
- 只改与本问题直接相关的最小范围，完成后更新稳定文档、session log 并创建中文 Git 提交。

---

### Task 1: legacy SSE 恢复平台权威 USER 输入

**Files:**
- Modify: `backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/session/SessionMessageRepository.java`
- Modify: `backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/mybatis/SessionMessageMapper.java`
- Modify: `backend/test-agent-persistence/src/main/resources/mybatis/SessionMessageMapper.xml`
- Modify: `backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/mybatis/MyBatisSessionMessageRepository.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/run/RunMessageRecoveryService.java`
- Test: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/run/RunMessageRecoveryServiceTest.java`
- Test: `backend/test-agent-persistence/src/test/java/com/enterprise/testagent/persistence/MyBatisSessionMessageRepositoryIntegrationTest.java`

**Interfaces:**
- Produces: `Optional<SessionMessage> findUserBySessionIdAndRunId(SessionId sessionId, RunId runId)`。
- Produces: legacy `RunMessageRecoveryService.recover(...)` 首帧平台 USER `message.updated`。

- [ ] **Step 1: 写失败的恢复测试**

构造 OpenCode 查询失败、但 `SessionMessageRepository.findUserBySessionIdAndRunId` 返回带正文和分享归因的 USER 消息；断言 `recover` 仍输出一条 `message.updated`，其中 `message.text`、`senderUserId`、`senderUnifiedAuthId`、`sentBySharedUser` 为手工给定值。

- [ ] **Step 2: 运行测试确认按预期失败**

Run: `./mvnw -pl backend/test-agent-opencode-runtime -am -Dtest=RunMessageRecoveryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: 新用例因 legacy `recover` 在 OpenCode 失败时返回空流而失败。

- [ ] **Step 3: 写失败的 MyBatis 精确查询测试**

保存同 Session 下不同 Run 的 USER 消息及同 Run 的 ASSISTANT 消息，断言新查询只返回目标 Run 的 USER。

- [ ] **Step 4: 实现最小查询与平台输入投影**

在 MyBatis XML 中按 `session_id = #{sessionId} and run_id = #{runId} and role = 'USER'` 查询，按 `created_at desc, id desc limit 1` 收敛。恢复服务把 `remoteMessageId`（缺失时用平台 message id）作为运行时 id，输出正文、平台 message id、创建时间和发送人归因；使用 `Flux.concat(platformInput, remoteAssistantSnapshot)` 保证慢远端查询不阻塞平台输入。

- [ ] **Step 5: 运行后端定向测试确认通过**

Run: `./mvnw -pl backend/test-agent-opencode-runtime,backend/test-agent-persistence -am -Dtest=RunMessageRecoveryServiceTest,MyBatisSessionMessageRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 2: 前端不渲染空 user envelope

**Files:**
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/rows/UserMessageRow.vue`
- Test: `frontend/packages/agent-chat/tests/opencode-timeline.test.ts`

**Interfaces:**
- Consumes: 既有 `AgentMessage` 的 `text`、`parts` 和 sender attribution。
- Produces: 仅当正文或可见附件存在时渲染 `.oc-user-message`。

- [ ] **Step 1: 写失败的组件测试**

先传入带 message id、sender 但空正文/无附件的 user envelope，断言不存在 `data-testid="oc-user-message"`；再更新为相同 message id 且正文为“多人实时同步”，断言只出现一条带正文和发送人姓名的用户消息。

- [ ] **Step 2: 运行测试确认按预期失败**

Run: `corepack pnpm@10.25.0 --dir frontend --filter @test-agent/agent-chat test -- opencode-timeline.test.ts`

Expected: 空 envelope 仍渲染气泡，断言失败。

- [ ] **Step 3: 实现最小展示守卫**

在 `UserMessageRow.vue` 增加 `hasVisibleContent` computed，只有 `displayText.trim()` 非空或 `workspaceContexts.length > 0` 时渲染根节点；不删除 reducer 中的 envelope。

- [ ] **Step 4: 运行组件测试确认通过**

Run: `corepack pnpm@10.25.0 --dir frontend --filter @test-agent/agent-chat test -- opencode-timeline.test.ts`

Expected: PASS。

### Task 3: 分享工作台端到端回归与文档交付

**Files:**
- Modify: `frontend/apps/agent-web/tests/workbench.spec.ts`
- Modify: `backend/test-agent-opencode-runtime/README.md`
- Modify: `backend/test-agent-persistence/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/packages/agent-chat/README.md`
- Modify: `docs/api/event-stream.md`
- Modify: `docs/testing/conversation-scenes.md`
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: 平台 USER `message.updated` 与现有分享 RunEvent SSE。
- Produces: A→B 的正文、姓名、颜色归属和无空白气泡回归证据。

- [ ] **Step 1: 写失败的 Playwright 场景**

模拟 B 已进入分享会话，runtime-state 通知 A 的新 Run；SSE 先给空 user envelope，再给平台权威 USER 事件。断言空 envelope 阶段没有空白 user bubble，权威事件到达后立即显示 A 的正文与姓名且只有一条消息。

- [ ] **Step 2: 运行场景并确认旧实现失败**

Run: `corepack pnpm@10.25.0 --dir frontend exec playwright test --config=playwright.session-share.config.ts --project=chromium --retries=0 -g "shared participant immediately sees the owner's authoritative user message"`

Expected: 旧实现出现空白 user bubble或无法显示权威正文。

- [ ] **Step 3: 同步稳定文档**

记录 legacy/Redis 的权威输入恢复、空 envelope 展示规则、断线重连与多人实时验收；不改 HTTP API 文档，因为 URL 和 DTO 不变。

- [ ] **Step 4: 执行关联与全量验证**

Run: 后端定向测试、agent-chat/agent-web 类型检查、前端 Vitest、三浏览器分享定向 E2E、agent-web production build、`git diff --check`。

- [ ] **Step 5: 更新 session log 并提交**

回顾全部 `.agents/session-log*.md`，只暂存本任务文件，提交信息：`fix: 修复分享会话用户消息实时同步`。

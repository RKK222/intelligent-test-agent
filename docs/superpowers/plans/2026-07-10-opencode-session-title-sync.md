# OpenCode 会话标题同步 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 OpenCode 内置自动生成的 root session 标题同步到平台 Session，并实时显示在 Web 工作台。

**Architecture:** 复用 `RunApplicationService` 已接收的 `session.updated` RunEvent 和既有 `SessionRepository`；前端复用 `event-stream-client` 的 EventSource 订阅与 `AgentWorkbench` 的 Session 状态，只补齐标题事件消费。

**Tech Stack:** Java 21、Spring Boot、Vue 3、TypeScript、Vitest、Maven、OpenCode 1.17。

---

## Chunk 1: 标题同步与展示

### Task 1: 后端同步 root OpenCode 标题

**Files:**
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/run/RunApplicationServiceTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/run/RunApplicationService.java`

- [ ] 增加失败测试：经 `RunSessionScopeRouter` 路由后的 root `session.updated`（`isChildSession=false`，且 `sessionId/rootSessionId` 对应）从 `info.title` 或 `rawPayload.properties.info.title` 更新对应平台 Session；child、未知归属和空标题不覆盖主标题。
- [ ] 运行 `mvn -pl test-agent-opencode-runtime -am -Dtest=RunApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认测试因尚未同步标题失败。
- [ ] 在既有流式事件处理路径中，仅对已确认 root scope 的有效标题复用 `Session.updateTitleAndPinned` 与 `SessionRepository.save` 完成最小同步。
- [ ] 重新运行同一测试，确认通过。

### Task 2: 前端消费并即时展示标题事件

**Files:**
- Modify: `frontend/packages/event-stream-client/tests/event-stream-client.test.ts`
- Modify: `frontend/packages/event-stream-client/src/index.ts`
- Modify: `frontend/apps/agent-web/tests/workbench-utils.test.ts`
- Modify: `frontend/apps/agent-web/src/components/workbench-utils.ts`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`

- [ ] 增加失败测试：EventSource 订阅 `session.updated`；标准和 raw 包装 payload 可提取非空标题。
- [ ] 运行相关 Vitest 命令，确认测试因缺少事件订阅/标题提取失败。
- [ ] 将 `session.updated` 加入已知事件；添加纯标题提取 helper；工作台只在当前 root Session 收到有效标题时更新当前标题和已加载历史列表，并失效 sessions 查询。
- [ ] 运行相关 Vitest、event-stream-client typecheck 和 agent-web typecheck，确认通过。

### Task 3: 文档、运行验证和提交

**Files:**
- Modify: `docs/api/event-stream.md`
- Modify: `backend/test-agent-opencode-runtime/README.md`
- Modify: `frontend/packages/event-stream-client/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `.agents/session-log.md`（仅在结论需要交接时）

- [ ] 在事件流与 runtime 模块文档记录：只同步 root `session.updated` 标题，child 与无有效标题事件保持原平台标题；旧客户端继续可忽略该事件。
- [ ] 运行后端定向测试、前端定向测试、`git diff --check`。
- [ ] 使用 `.env.test` 启动三服务并检查 health/readiness、前端入口、manager 日志；在浏览器首轮对话验证标题更新。
- [ ] 回顾 `.agents/session-log.md`，只暂存本任务文件，使用中文信息提交。

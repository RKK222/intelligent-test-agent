# 历史消息加载可靠性优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除 manager WebSocket 并发控制命令静默丢失，并让历史消息正文不再被实时 permission/question 校准阻塞。

**Architecture:** 后端在单条 manager WebSocket 连接边界串行化 sink emission，并把非成功 `EmitResult` 转换为即时平台异常；前端把历史正文视觉 loading 与完整历史切换发送锁拆成两个状态，正文数据库快照先渲染，实时交互和树快照随后增强。

**Tech Stack:** Java 21、Spring WebFlux、Reactor Sinks、JUnit 5、Vue 3、TypeScript、Playwright。

## Global Constraints

- 只修改 manager WebSocket 发送可靠性和历史会话加载编排的最小范围。
- 不新增或变更 HTTP API、RunEvent SSE、数据库、generated SDK 或环境配置。
- 后端复杂并发逻辑必须有中文注释。
- 前端不得直连 OpenCode，现有 backend-api 调用路径保持不变。
- 不新建 git 分支；完成后使用中文提交信息自动提交。

---

### Task 1: manager WebSocket 并发发送可靠性

**Files:**
- Modify: `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform/ManagerControlWebSocketHandlerTest.java`
- Modify: `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/ManagerControlWebSocketHandler.java`

**Interfaces:**
- Consumes: `ManagerConnectionRegistry.register(..., ManagerCommandSender)` 与 `Sinks.Many<ManagerControlMessage>`。
- Produces: 同一 manager 连接上并发调用安全、失败可见的 `ManagerCommandSender`。

- [x] **Step 1: 编写并发发送失败测试**

在测试中注入可访问的 `ManagerConnectionRegistry`，保持 manager WebSocket 入站流打开；注册完成后用固定线程池和 `CountDownLatch` 同时调用 `registry.send(...)`，收集所有 commandId，并断言 WebSocket outbound 收到每一条命令。

- [x] **Step 2: 运行测试确认 RED**

Run:

```bash
cd backend && mvn -pl test-agent-api -am -Dtest=ManagerControlWebSocketHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 并发发送用例因实际收到的控制消息少于发送数量而失败。

- [x] **Step 3: 实现最小并发安全 emission**

在 `ManagerControlWebSocketHandler` 内集中发送：

```java
private void emitOutbound(Sinks.Many<ManagerControlMessage> outbound, ManagerControlMessage message) {
    synchronized (outbound) {
        Sinks.EmitResult result = outbound.tryEmitNext(message);
        if (result != Sinks.EmitResult.OK) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程控制消息发送失败");
        }
    }
}
```

注册到 `ManagerConnectionRegistry` 的 sender、协议错误、registered/config update 等出站消息统一调用该方法；连接结束的 complete emission 使用同一同步边界，避免与命令发送竞争。

- [x] **Step 4: 运行测试确认 GREEN**

Run Task 1 Step 2 同一命令，Expected: `ManagerControlWebSocketHandlerTest` 全部通过。

---

### Task 2: 历史正文首屏与实时交互校准解耦

**Files:**
- Modify: `frontend/apps/agent-web/tests/workbench.spec.ts`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`

**Interfaces:**
- Consumes: `listSessionMessages(refresh=false)`、`listSessionPermissions`、`listSessionQuestions`、`getSessionTreeMessages` 和既有 history switch generation guard。
- Produces: `historyLoadingSessionId` 仅控制正文视觉 loading；`historySwitchingSessionId` 独立控制提交锁。

- [x] **Step 1: 编写交互校准延迟回归测试**

给 mock backend 增加 `sessionInteractionsGate`；permission/question route 在返回前等待 gate。测试先保持交互 gate pending，释放 `sessionMessagesGate` 后断言历史正文可见且 loading 消失，同时发送按钮仍禁用；释放交互 gate 和剩余投影后断言发送按钮恢复。

- [x] **Step 2: 运行测试确认 RED**

Run:

```bash
cd frontend && corepack pnpm --filter @test-agent/agent-web exec playwright test tests/workbench.spec.ts --grep "history loading"
```

Expected: 释放消息请求后 loading 仍存在，断言失败。

- [x] **Step 3: 拆分视觉 loading 与发送锁**

在 `AgentWorkbench.vue` 增加：

```ts
const historyLoadingSessionId = ref<string | null>(null);
const historySwitchingSessionId = ref<string | null>(null);
```

历史切换开始时同时持有两者；消息 page 返回并渲染后清除 loading，完整交互、树、Todo、Run/Diff 投影完成后在 owner guard 下清除 switching。`invalidateConversationInteraction` 同时清理两者，`handleSend` 检查 switching。

在 `FigmaChatPanel.vue` 增加可选 `historySubmitBlocked` prop，并让 `sendSubmitBlocked` 同时考虑 `historyLoading` 和 `historySubmitBlocked`。父组件分别传入两个状态。

- [x] **Step 4: 运行测试确认 GREEN**

Run Task 2 Step 2 同一命令，Expected: 目标历史加载测试通过。

---

### Task 3: 文档、回归验证与提交

**Files:**
- Modify: `backend/test-agent-api/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `docs/api/http-api.md`
- Modify: `.agents/session-log.md`
- Include: `docs/superpowers/plans/2026-07-13-history-message-loading-reliability.md`

**Interfaces:**
- Consumes: Task 1、Task 2 已验证行为。
- Produces: 稳定工程说明、API 行为说明、会话记录和最终提交。

- [x] **Step 1: 同步稳定文档**

记录 manager WebSocket 出站串行化与失败即时上抛；记录历史正文首屏不等待 permission/question，完整投影前仍禁止发送。明确不变更接口字段和路径。

- [x] **Step 2: 执行相关回归与构建**

```bash
cd backend && mvn -pl test-agent-api -am -Dtest=ManagerControlWebSocketHandlerTest,SocketOpencodeProcessManagerGatewayTest -Dsurefire.failIfNoSpecifiedTests=false test
cd frontend && corepack pnpm --filter @test-agent/agent-web exec playwright test tests/workbench.spec.ts --grep "history loading|history.*send|switching history"
cd frontend && corepack pnpm --filter @test-agent/agent-web typecheck
cd backend && mvn -pl test-agent-app -am -DskipTests package
```

Expected: 所有命令退出码为 0。

- [x] **Step 3: 自检并提交**

检查冲突标记、`git diff --check`、改动范围和 `.agents/session-log.md` 近期记录；确认没有 `.env.local`、generated SDK、数据库或无关文件变化。

```bash
git add \
  backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/ManagerControlWebSocketHandler.java \
  backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform/ManagerControlWebSocketHandlerTest.java \
  backend/test-agent-api/README.md \
  frontend/apps/agent-web/src/components/AgentWorkbench.vue \
  frontend/apps/agent-web/src/components/FigmaChatPanel.vue \
  frontend/apps/agent-web/tests/workbench.spec.ts \
  frontend/apps/agent-web/README.md \
  docs/api/http-api.md \
  docs/superpowers/plans/2026-07-13-history-message-loading-reliability.md \
  .agents/session-log.md
git commit -m "修复历史消息加载偶发等待"
```

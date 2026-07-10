# OpenCode 原生标题事件同步 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 仅凭 OpenCode 原生 `session.updated` 异步更新首轮会话标题，并在 3000 页面主动显示更新。

**Architecture:** 首轮 Run 的既有远端事件流在 root 成功后转入 `TITLE_WAIT`，仅保留对应 root session 的标题更新；会话级 token 防止下一轮或手动改名后的旧事件覆盖。标题写入采用既有 MyBatis CAS，成功或取消均以既有 `session.updated` RunEvent 通知前端；前端在 pending 时保留 Run SSE。

**Tech Stack:** Java 21/Spring WebFlux/Reactor、MyBatis、OpenCode HTTP SSE、Vue 3/Vitest。

---

## Chunk 1: 后端原生标题监听

### Task 1: 令牌状态与标题 CAS 测试

**Files:**
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunSessionTitleWatchRegistry.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunSessionTitleWatchService.java`
- Create: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/run/RunSessionTitleWatchRegistryTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/run/RunApplicationServiceTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/session/SessionApplicationService.java`
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/session/SessionApplicationServiceTest.java`

- [ ] **Step 1: 写出失败测试**
  - 覆盖首轮 token 注册、仅 `TITLE_WAIT` 可由下一轮注销、过期 token 不接受标题、原生标题到达后关闭 token。
  - 覆盖 root `session.updated` 写入只在 CAS 成功时携带标题确认字段。
  - 覆盖取消信号真实 dispose 远端 Flux；手动改名、归档、删除和下一轮关闭等待并向原 Run 发布关闭标记。

- [ ] **Step 2: 运行失败测试**

  Run: `cd backend && mvn -pl test-agent-opencode-runtime -am -Dtest=RunSessionTitleWatchRegistryTest,RunApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

  Expected: FAIL，缺少 token 状态机/终态后标题处理。

- [ ] **Step 3: 最小实现**
  - 增加局部 `ACTIVE → TITLE_WAIT → CLOSED` token registry 和 watch service，不创建跨 Java 共享桥；token 保存本次实际 `AgentRuntime`、`ExecutionNode`、directory、workspace、远端 session ID 与 `Sinks.One` 取消信号。
  - `RunApplicationService` 改为在 scope router 前按 `TITLE_WAIT` 过滤，只接受 root `session.updated` 和 `sessionID` 匹配、`info.role=assistant`、`info.agent=title`、`info.time.completed` 非空的 raw `message.updated`；`takeUntilOther` 消费 token 取消信号并释放远端流。
  - 原生标题同步复用 `SessionTitleUpdateRepository.updateTitleIfCurrent`，不再直接 `SessionRepository.save` 覆盖标题。
  - `SessionApplicationService.updateSession/archiveSession` 调用 watch service 取消等待；token 结束时追加既有 `session.updated`，带 `platformSessionTitlePending=false` 与 `platformSessionTitleWatchClosed=true`；成功时同时带既有同步字段。

- [ ] **Step 4: 运行通过测试**

  Run: 与步骤 2 相同。

  Expected: PASS。

### Task 2: 断线后的远端标题读取

**Files:**
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationService.java`
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationServiceTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunApplicationService.java`
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/run/RunApplicationServiceTest.java`

- [ ] **Step 1: 写出失败测试**
  - `findRemoteSessionTitle(titleWatchToken, traceId)` 从 OpenCode `GET /session/{remoteSessionId}` 的 top-level `title` 或 `info.title` 提取有效文本；直接用 token 内实际 runtime/node/directory/workspace/sessionId 构造调用，禁止 `workspaceLocation`、`sessionLocation`、`withAgent` 和任何可能重建 binding 的 resolver。
  - 404、超时、默认标题和空标题不修改平台标题。
  - `TITLE_WAIT` 流断线重连后读取远端 title，并以同一 token + CAS 发出确认事件；title agent 完成消息即使未收到 `session.updated` 也必须读取最终 title 并关闭等待。

- [ ] **Step 2: 运行失败测试**

  Run: `cd backend && mvn -pl test-agent-opencode-runtime -am -Dtest=OpencodeRuntimeApplicationServiceTest,RunApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

  Expected: FAIL，缺少最小标题投影和断线补偿。

- [ ] **Step 3: 最小实现**
  - 在 runtime application service 增加内部只读标题方法；直接复用 token 的不可变路由与通用 `get`，不改 generated SDK 或外部 HTTP API。
  - `TITLE_WAIT` 异常不改变已成功 Run；token 有效时单飞重连并进行一次标题状态读取补偿，取消后不得重连。title 完成后的最终读取无有效标题时关闭等待，避免永久连接。

- [ ] **Step 4: 运行通过测试**

  Run: 与步骤 2 相同。

## Chunk 2: 前端主动通知与收尾

### Task 3: 保持标题待定 Run SSE

**Files:**
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Modify: `frontend/apps/agent-web/tests/workbench.spec.ts`
- Modify: `frontend/apps/agent-web/src/components/workbench-utils.ts`（仅在现有标题事件解析不能表达 pending/closed 时）

- [ ] **Step 1: 写出失败测试**
  - Run 成功携带 `platformSessionTitlePending=true` 时 SSE 不关闭。
  - 标题确认或 `platformSessionTitleWatchClosed=true` 时关闭，并清理待定状态。
  - 会话切换和新 Run 仍关闭旧订阅。

- [ ] **Step 2: 运行失败测试**

  Run: `cd frontend && corepack pnpm test --run tests/workbench.spec.ts`

  Expected: FAIL，终态总会关闭 SSE。

- [ ] **Step 3: 最小实现**
  - 为当前 Run 维护标题待定状态；只在该状态下让既有 Run SSE 穿过终态继续存在。
  - 继续只消费后端已持久化的 `platformSessionTitleSynchronized` 标题；关闭标记只影响订阅生命周期。

- [ ] **Step 4: 运行通过测试与静态校验**

  Run: `cd frontend && corepack pnpm test --run tests/workbench.spec.ts && corepack pnpm typecheck`

  Expected: PASS。

### Task 4: 删除超时兜底、同步文档并验证运行态

**Files:**
- Delete: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunSessionTitleFallbackService.java`
- Delete: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/OpencodeSessionTitleProperties.java`
- Delete: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/run/RunSessionTitleFallbackServiceTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunApplicationService.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationService.java`（删除 `generateNativeSessionTitle` 及其测试）
- Modify: `backend/test-agent-app/src/main/resources/application.yml`
- Modify: `backend/README.md`
- Modify: `backend/test-agent-opencode-runtime/README.md`
- Modify: `backend/test-agent-domain/README.md`
- Modify: `backend/test-agent-persistence/README.md`
- Modify: `docs/api/event-stream.md`
- Modify: `.agents/session-log.md`

- [ ] **Step 1: 删除被替代的临时 session/title agent 轮询路径**
  - 删除配置开关、等待超时和轮询间隔，以及临时远端 session/第二次 title agent 调用；确认无遗留引用。

- [ ] **Step 2: 更新稳定文档**
  - 记录 `platformSessionTitlePending`、`platformSessionTitleWatchClosed` 的既有 `session.updated` 扩展语义、兼容性和无超时原则。

- [ ] **Step 3: 运行后端回归与打包**

  Run:
  `cd backend && mvn -pl test-agent-opencode-runtime -am -Dtest=RunApplicationServiceTest,RunSessionTitleWatchRegistryTest,OpencodeRuntimeApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test && mvn -pl test-agent-app -am -DskipTests package`

  Expected: PASS。

- [ ] **Step 4: 启动并真实验证**

  Run:
  `export JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home; export PATH="$JAVA_HOME/bin:/Users/kaka/Desktop/intelligent-test-agent/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"; ./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`

  Verify: 8080 health/readiness、3000 可访问；在 3000 新建首轮对话，等待原生 `session.updated` 后标题变更，不依赖固定生成等待时间。

- [ ] **Step 5: 提交**
  - 仅暂存本任务文件与 `.agents/session-log.md`。
  - 提交信息：`改为事件驱动同步OpenCode会话标题`。

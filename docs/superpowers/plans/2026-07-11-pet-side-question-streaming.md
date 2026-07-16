# Pet Side-Question Streaming Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the pet side-question dialog open while waiting and stream real context/tool/answer progress through the existing RunEvent SSE without writing to the main conversation.

**Architecture:** A new streaming start API creates an archived internal Session and a normal `SIDE_QUESTION` Run, then performs the existing temporary OpenCode fork asynchronously. The backend projects only the temporary session’s safe progress and assistant text into durable/transient RunEvents; the frontend uses the existing RunEvent client, while the old synchronous side-question API remains compatible.

**Tech Stack:** Java 21+, Spring WebFlux, Reactor, MyBatis XML, Flyway, Vue 3, TypeScript, RunEvent SSE, Vitest, Vue Test Utils, Playwright.

**Specification:** `docs/superpowers/specs/2026-07-11-pet-side-question-streaming-design.md`

---

## Chunk 1: RunEvent 与 OpenCode 流式底座

### Task 1: 定义旁路 Run 来源和事件契约

**Files:**
- Modify: `backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/session/ConversationSourceType.java`
- Modify: `backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/event/RunEventType.java`
- Modify: `backend/test-agent-domain/src/test/java/com/enterprise/testagent/domain/event/RunEventTypeTest.java`
- Modify: `backend/test-agent-domain/src/test/java/com/enterprise/testagent/domain/run/RunTest.java`
- Modify: `frontend/packages/shared-types/src/index.ts`
- Modify: `frontend/packages/event-stream-client/src/index.ts`
- Modify: `frontend/packages/event-stream-client/tests/event-stream-client.test.ts`

- [ ] **Step 1: 写失败测试**

后端断言 `ConversationSourceType.SIDE_QUESTION` 可用于 Run，且以下 wire name 可双向解析：

```java
assertThat(RunEventType.fromWireName("side_question.started"))
        .contains(RunEventType.SIDE_QUESTION_STARTED);
assertThat(RunEventType.fromWireName("side_question.progress"))
        .contains(RunEventType.SIDE_QUESTION_PROGRESS);
assertThat(RunEventType.fromWireName("side_question.delta"))
        .contains(RunEventType.SIDE_QUESTION_DELTA);
```

前端 EventSource fake 依次 emit 三种事件，断言 `subscribeRunEvents` 全部交给 `onEvent`，`side_question.delta` 仍按唯一 eventId 去重。

- [ ] **Step 2: 运行 RED**

```bash
cd backend && mvn -pl test-agent-domain -am -Dtest=RunEventTypeTest,RunTest -Dsurefire.failIfNoSpecifiedTests=false test
cd frontend && corepack pnpm test --run packages/event-stream-client/tests/event-stream-client.test.ts
```

Expected: FAIL，枚举和前端已知事件尚不存在。

- [ ] **Step 3: 最小实现**

新增后端枚举值：

```java
SIDE_QUESTION_STARTED("side_question.started"),
SIDE_QUESTION_PROGRESS("side_question.progress"),
SIDE_QUESTION_DELTA("side_question.delta")
```

新增 `ConversationSourceType.SIDE_QUESTION`。同步 TypeScript `RunEventType` 和 `KNOWN_RUN_EVENT_TYPES`；不改变 RunEvent envelope。

- [ ] **Step 4: 运行 GREEN**

重复 Step 2，Expected: PASS。

### Task 2: 为 prompt_async 增加后端受控 system 字段

**Files:**
- Modify: `backend/test-agent-agent-runtime/src/main/java/com/enterprise/testagent/agent/runtime/AgentStartRunCommand.java`
- Modify: `backend/test-agent-agent-runtime/src/main/java/com/enterprise/testagent/agent/runtime/OpencodeAgentRuntime.java`
- Modify: `backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/OpencodeStartRunCommand.java`
- Modify: `backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/OpencodeSdkGateway.java`
- Modify: `backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/GeneratedOpencodeSdkGateway.java`
- Modify: `backend/test-agent-opencode-client/src/main/java/com/enterprise/testagent/opencode/client/DefaultOpencodeClientFacade.java`
- Modify: `backend/test-agent-opencode-client/src/test/java/com/enterprise/testagent/opencode/client/GeneratedOpencodeSdkGatewayTest.java`
- Modify: `backend/test-agent-agent-runtime/src/test/java/com/enterprise/testagent/agent/runtime/OpencodeAgentRuntimeTest.java`

- [ ] **Step 1: 写失败测试**

构造 `OpencodeStartRunCommand(..., system="只读旁路策略", ...)`，通过 mock HTTP server 捕获 `/prompt_async` body，断言：

```json
{
  "agent": "plan",
  "system": "只读旁路策略",
  "parts": [{"type":"text","text":"问题"}]
}
```

再断言普通主 Run 的 `system=null` 时请求体不包含 `system`，保持兼容。

- [ ] **Step 2: 运行 RED**

```bash
cd backend && mvn -pl test-agent-opencode-client,test-agent-agent-runtime -am -Dtest=GeneratedOpencodeSdkGatewayTest,OpencodeAgentRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，命令和 gateway 尚无 system 字段。

- [ ] **Step 3: 最小实现**

在三个稳定 command record 中增加可选 `system`，只在 gateway 的 `promptAsyncRequest` 中显式传入非空值。不得修改 generated SDK；继续使用当前稳定 JSON Map 调用。

- [ ] **Step 4: 运行 GREEN**

重复 Step 2，Expected: PASS。

### Task 3: 提取共享答案过滤器和旁路事件投影器

**Files:**
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionAnswerExtractor.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionEventProjector.java`
- Create: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionAnswerExtractorTest.java`
- Create: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionEventProjectorTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationService.java`
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationServiceTest.java`

- [ ] **Step 1: 写失败测试**

覆盖：

- 同步响应只有 `<tool_calls>` 时返回空；协议块后有自然语言时只保留自然语言；
- projected messages 只选择最后一条 `role=assistant` 的 text part；
- 事件 payload 的实际 `sessionID` 不等于临时 session 时全部丢弃；没有 session ID 的全局事件也丢弃；
- assistant message/part 建立关联后，text delta 映射为 `SIDE_QUESTION_DELTA`；user/reasoning/tool 参数不进入 delta；
- tool.started 只映射 `{stage:"tool", toolName}`，不携带 command/path/input/output/rawPayload；
- 主 session 与 fork session 事件交错时投影器只输出 fork 事件。

- [ ] **Step 2: 运行 RED**

```bash
cd backend && mvn -pl test-agent-opencode-runtime -am -Dtest=SideQuestionAnswerExtractorTest,SideQuestionEventProjectorTest,OpencodeRuntimeApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，新类不存在。

- [ ] **Step 3: 最小实现**

把现有 `extractAnswer/sanitizeSideQuestionText/extractPartsText` 移入共享提取器；同步入口改为复用它。投影器保持每次执行独立实例状态，递归读取 payload 内 `sessionID/sessionId`，严格精确匹配临时 fork。

在投影器测试注释中固定升级回归依据：`opencode-source/opencode-1.17.8/packages/opencode/src/session/session.ts` 的 `Session.fork` 使用 `createNext` 但不设置 `parentID`。未来升级必须重跑并发隔离测试。

- [ ] **Step 4: 运行 GREEN**

重复 Step 2，Expected: PASS。

## Chunk 2: 后端旁路 Run 编排与 API

### Task 4: 以归档内部 Session 启动异步旁路 Run

**Files:**
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionRunStartResult.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionStreamingApplicationService.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionTerminalService.java`
- Create: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionStreamingApplicationServiceTest.java`
- Create: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionTerminalServiceTest.java`
- Create: `backend/test-agent-app/src/test/java/com/enterprise/testagent/app/SideQuestionTerminalTransactionIntegrationTest.java`
- Modify: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/run/RunEventPersistencePolicy.java`
- Modify: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/run/RunEventPersistencePolicyTest.java`

- [ ] **Step 1: 写失败测试**

用 in-memory repositories 和 fake AgentRuntime 覆盖：

1. `start` 立即返回 `runId`；内部 Session 与主 Session 使用同一 Workspace、标题固定为“宠物旁路问答（内部）”、创建者为当前用户且从创建起即为 `ARCHIVED`。保存 `SIDE_QUESTION/PENDING` Run，`sourceRefId=主 Session ID`，同时保存目标节点 routing decision 和 `run.created`；不向主 Session 保存消息。
2. 后台先 CAS 到 `RUNNING`，事件严格为 `run.created → run.started → side_question.started`，之后才允许 progress；`side_question.started.payload.sessionId` 必须等于主平台 Session ID。
3. 上下文超过 40 条或约 48,000 字符时只 compact 临时 fork。
4. 先订阅 temp session 事件再调用 prompt_async；命令固定 `agent=plan` 和集中 system prompt。
5. delta transient 发布到 `RunEventLiveBus`，started/progress durable。
6. 成功时读取最后 assistant text，保存 SUCCEEDED 并追加单一 `run.succeeded`，payload 含 `sideQuestion:true/answer/compacted`；答案超过 64 KiB 时按 UTF-8 码点安全截断且设置 `truncated:true`，不得截断出无效 surrogate/UTF-8 序列。
7. fork、compact、prompt、stream、final answer 任一失败都保存 FAILED 和单一 `run.failed` 安全 payload。
8. fork 返回后必须先把临时远端 session ID 与原执行节点通过内部 Session mapping 持久化，测试用顺序探针断言 compact/prompt 不会早于该 save。模拟 mapping save 失败时仍 best-effort delete 并写安全失败终态；实现追加结构化 `event=side_question_fork_created_pending_mapping` 与映射成功日志，明确远端创建到本地 save 之间的极窄恢复窗口。
9. 成功和失败都 delete 临时 fork；delete 重复调用幂等。
10. 同一主 Session 的正常 Run 可同时 active，旁路 Run 使用不同的归档 Session，不改变 `findLatestActiveBySessionId(main)`。
11. `SideQuestionTerminalService` 在同一 Spring transaction 内先用 `RunRepository.saveIfStatus` 做持久化 CAS，只有 CAS 胜者追加唯一 `run.succeeded|run.failed`。runtime 单测验证调用规则；`test-agent-app` 的 H2 + Flyway + MyBatis 真实事务集成测试使用两个线程竞争 success-vs-cleanup、failure-vs-cleanup，并让 RunEvent append 人为抛错，断言数据库 CAS 后只有一个终态事件且异常时 Run 状态与事件同时回滚。不得用 in-memory repository 代替这项证据。

- [ ] **Step 2: 运行 RED**

```bash
cd backend && mvn -pl test-agent-app -am -Dtest=SideQuestionStreamingApplicationServiceTest,SideQuestionTerminalServiceTest,SideQuestionTerminalTransactionIntegrationTest,RunEventPersistencePolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，流式服务和事件策略尚不存在。

- [ ] **Step 3: 最小实现**

复用 `AgentRuntimeTargetResolver`、`AgentRuntime.runtime/sessionMessages/streamRunEvents/startRun`、`RunRepository`、`RoutingDecisionRepository`、`RunEventAppender` 与 `RunEventLiveBus`。使用 bounded-elastic 后台执行；保存 temp session mapping 后才继续 compact/prompt。终态使用独立 `@Transactional` 服务和 `saveIfStatus` CAS，不用进程内布尔值决定持久化事实；远端 delete 仍用本次执行内幂等 guard 避免重复请求。`SIDE_QUESTION_DELTA` 加入 transient-only policy。

- [ ] **Step 4: 运行 GREEN**

重复 Step 2，Expected: PASS。

### Task 5: 新增兼容的流式启动 HTTP 入口

**Files:**
- Modify: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/common/SideQuestionDtos.java`
- Modify: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/PlatformOpencodeRuntimeController.java`
- Modify: `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/agent/AgentOpencodeRuntimeController.java`
- Modify: `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/platform/RuntimeControllerTest.java`
- Modify: `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/platform/UserOpencodeBackendRoutingWebFilterTest.java`
- Modify: `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/platform/RunEventSseBackendRoutingWebFilterTest.java`
- Modify: `frontend/packages/shared-types/src/index.ts`
- Modify: `frontend/packages/backend-api/src/index.ts`
- Modify: `frontend/packages/backend-api/tests/backend-api.test.ts`

- [ ] **Step 1: 写失败测试**

Controller 测试两条 POST 路由返回 `{runId}`，流式请求只接受 `question/messageId/model`，并在调用服务时固定 plan。backend-api 测试断言：

```ts
await api.startSideQuestionRun("ses_1", {
  question: "现在做到哪里了？",
  messageId: "msg_1",
  model: "provider/model"
});
```

请求 `/sessions/ses_1/side-question/runs`，body 不含 `agent`。旧 `askSideQuestion` 测试保持通过。

路由测试必须覆盖：入口 Java 命中远端 ACTIVE binding 时通过既有 `BackendHttpForwarder` 转发该 POST，防循环 header 已存在时不再转发，入口侧 service/repository 零调用；目标 Java 创建 Run 后，保存的 routing decision 使随后 `/runs/{runId}/events` 由 `RunEventSseBackendRoutingWebFilter` 选择同一目标 Java。

- [ ] **Step 2: 运行 RED**

```bash
cd backend && mvn -pl test-agent-api -am -Dtest=RuntimeControllerTest,UserOpencodeBackendRoutingWebFilterTest,RunEventSseBackendRoutingWebFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
cd frontend && corepack pnpm test --run packages/backend-api/tests/backend-api.test.ts
```

Expected: FAIL，新路由和 client 方法不存在。

- [ ] **Step 3: 最小实现**

新增 `SideQuestionDtos.StreamRequest` 和 `RunResponse`，Controller 委托 `SideQuestionStreamingApplicationService`。保留旧同步 DTO/路由。平台与 agent 路由均依赖现有 `UserOpencodeBackendRoutingWebFilter`，不得新增自定义后端转发器。

- [ ] **Step 4: 运行 GREEN**

重复 Step 2，Expected: PASS。

## Chunk 3: 宠物浮层流式交互

### Task 6: 在 AgentWorkbench 订阅旁路 RunEvent

**Files:**
- Create: `frontend/apps/agent-web/src/components/useSideQuestionRun.ts`
- Create: `frontend/apps/agent-web/tests/useSideQuestionRun.test.ts`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Modify: `frontend/apps/agent-web/src/components/FigmaShell.vue`
- Modify: `frontend/apps/agent-web/tests/FigmaShell.test.ts`

- [ ] **Step 1: 写失败测试**

覆盖：

- 提交后 `startSideQuestionRun` 返回 runId 并调用现有 `subscribeRunEvents`；同一浮层运行中再次提交不创建第二个 Run；
- `side_question.progress` 更新真实阶段文本；`side_question.delta` 追加答案；
- `run.succeeded` 的最终 `answer` 覆盖丢帧后的增量并关闭订阅；`run.failed` 显示错误并关闭订阅；
- 显式 ×、收起宠物、组件卸载关闭订阅但不调用主 session abort；
- `sideQuestionLoading=true` 时点击 `.figma-app` 仍保留浮层；非加载态保持既有外部点击关闭；
- 右上角 × 在加载态仍可关闭。
- `run.failed` 后浮层保留错误，输入框可修改并重新提交；`run.succeeded`、`run.failed`、显式关闭三条终止路径都会释放单飞锁，下一次提交能创建新 Run。

- [ ] **Step 2: 运行 RED**

```bash
cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaShell.test.ts apps/agent-web/tests/useSideQuestionRun.test.ts
```

Expected: FAIL，仍调用同步接口且根点击无条件关闭。

- [ ] **Step 3: 最小实现**

新增聚焦的 `useSideQuestionRun` composable，复用 `startSideQuestionRun` 和 `subscribeRunEvents`，只负责旁路 subscription/runId/progress/answer/error 生命周期；AgentWorkbench 负责提供当前 session/message/model 并把状态传给 FigmaShell。该独立边界用于可靠单测订阅关闭和事件归并，不能扩展成第二套聊天 runtime。阶段文案只由真实事件驱动：

```text
preparing_context -> 正在读取当前上下文
forking           -> 正在准备临时上下文
compacting        -> 正在压缩较长上下文
reading/tool      -> 正在执行只读检查
composing         -> 正在整理答案
```

FigmaShell 增加 progress 展示和等待态状态区域；`closeHeaderMenus` 在 `sideQuestionLoading` 时不调用 `closeRobotQuestion`，显式关闭路径不受影响。保留简单克制的现有卡片样式，不新增大动画或伪进度百分比。

- [ ] **Step 4: 运行 GREEN**

重复 Step 2，Expected: PASS。

### Task 7: 增加浏览器端 Playwright E2E

**Files:**
- Modify: `frontend/apps/agent-web/tests/workbench.spec.ts`

- [ ] **Step 1: 写失败 E2E**

扩展 mock API 识别 `POST .../side-question/runs` 并返回 `run_side_question_1`；EventSource fake 对该 run 依次发出 `run.started`、`side_question.started`、`side_question.progress`、两条 delta 和 `run.succeeded`。

新增测试：唤起宠物、打开浮层、提交问题、看到真实阶段；点击编辑器/工作台空白处后浮层仍存在；看到增量；最终只显示 `run.succeeded.payload.answer` 一次。再模拟 Last-Event-ID 重连重投 progress/终态，答案不重复。追加失败场景：run.failed 后浮层仍在，修改问题重新提交成功创建第二个旁路 Run。

- [ ] **Step 2: 运行 RED**

```bash
cd frontend && corepack pnpm e2e -- --project=chromium --grep "pet side-question"
```

Expected: FAIL，页面尚未使用流式入口。

- [ ] **Step 3: 完善最小 mock 与断言**

只扩展现有 `mockBackendApi` 和 EventSource fake，不新建第二套测试服务器。

- [ ] **Step 4: 运行 GREEN**

重复 Step 2，Expected: PASS。

## Chunk 4: 孤儿回收、文档与真实 E2E

### Task 8: 回收重启后遗留的旁路 fork

**Files:**
- Modify: `backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/run/RunRepository.java`
- Modify: `backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/mybatis/RunMapper.java`
- Modify: `backend/test-agent-persistence/src/main/java/com/enterprise/testagent/persistence/mybatis/MyBatisRunRepository.java`
- Modify: `backend/test-agent-persistence/src/main/resources/mybatis/RunMapper.xml`
- Modify: `backend/test-agent-persistence/src/test/java/com/enterprise/testagent/persistence/MyBatisRunRepositoryIntegrationTest.java`
- Modify: `backend/test-agent-persistence/src/test/java/com/enterprise/testagent/persistence/MyBatisSessionHistoryRepositoryIntegrationTest.java`
- Modify: `backend/test-agent-persistence/src/test/java/com/enterprise/testagent/persistence/MyBatisSessionRuntimeStateRepositoryIntegrationTest.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionOrphanCleanupService.java`
- Create: `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionOrphanCleanupTaskHandler.java`
- Create: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionOrphanCleanupServiceTest.java`
- Create: `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime/runtime/SideQuestionOrphanCleanupTaskHandlerTest.java`
- Create: `backend/test-agent-persistence/src/main/resources/db/migration/V20260711120000__document_side_question_run_source.sql`

- [ ] **Step 1: 写失败测试**

MyBatis Run 集成测试断言只返回 `updated_at < cutoff AND source_type='SIDE_QUESTION' AND active` 的 Run。SessionHistory 与 SessionRuntimeState 两个真实 mapper 集成测试插入 `ARCHIVED/SIDE_QUESTION` 内部 Session 和 active Run，断言用户会话历史、运行中计数和 active session 列表均不出现它。清理服务测试持久化内部 Session 的 temp mapping，明确断言 delete 命令使用该 mapping 保存的原执行节点而不是当前用户 binding；断言 CAS FAILED、单一 `run.failed` 和第二次执行不重复事件。并发测试让正常 success/failure 与 cleanup 同时竞争，数据库仍只有一个终态事件。handler 测试断言 5 分钟 cron、scheduler lock 和 stopRequested。

- [ ] **Step 2: 运行 RED**

```bash
cd backend && mvn -pl test-agent-persistence,test-agent-opencode-runtime -am -Dtest=MyBatisRunRepositoryIntegrationTest,MyBatisSessionHistoryRepositoryIntegrationTest,MyBatisSessionRuntimeStateRepositoryIntegrationTest,SideQuestionOrphanCleanupServiceTest,SideQuestionOrphanCleanupTaskHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，查询与清理服务不存在。

- [ ] **Step 3: 最小实现**

新增 Repository 端口方法和 MyBatis XML SQL；不得向 `JdbcRunRepository` 添加新 SQL。任务每 5 分钟扫描 10 分钟前的 active SIDE_QUESTION Run，复用内部 Session node/session mapping、AgentRuntime runtime delete 和同一个 `SideQuestionTerminalService` 事务 CAS。Flyway 只更新 `sessions/runs/session_messages.source_type` 注释中的允许值，不写测试数据。

- [ ] **Step 4: 运行 GREEN**

重复 Step 2，Expected: PASS。

### Task 9: 同步稳定文档和 session log

**Files:**
- Modify: `docs/api/http-api.md`
- Modify: `docs/api/event-stream.md`
- Modify: `docs/deployment/database.md`
- Modify: `docs/architecture/module-map.md`
- Modify: `backend/test-agent-domain/README.md`
- Modify: `backend/test-agent-opencode-client/README.md`
- Modify: `backend/test-agent-opencode-runtime/README.md`
- Modify: `backend/test-agent-api/README.md`
- Modify: `backend/test-agent-persistence/README.md`
- Modify: `frontend/packages/shared-types/README.md`
- Modify: `frontend/packages/backend-api/README.md`
- Modify: `frontend/packages/event-stream-client/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `.agents/session-log.md`

- [ ] **Step 1: 更新文档**

记录新增 start API、三种 side_question event、run.succeeded/failure payload、durable/transient/Last-Event-ID 规则、SIDE_QUESTION 枚举兼容、归档内部 Session、跨后端路由、10 分钟孤儿清理和前端点击保持。

- [ ] **Step 2: 文档一致性检查**

```bash
rg -n "side_question|side-question/runs|SIDE_QUESTION" docs backend/test-agent-*/README.md frontend/packages/*/README.md frontend/apps/agent-web/README.md
git diff --check
```

Expected: 无旧“流式入口禁用工具并同步等待最终答案”的冲突描述，无空白错误。

### Task 10: 全量验证、真实 E2E 与提交

**Files:**
- Modify: `frontend/apps/agent-web/tests/workbench.real-spec.ts`
- Modify: `.agents/session-log.md`

- [ ] **Step 1: 增加真实服务 E2E**

真实测试创建 Workspace/主 Session，记录主消息数；启动一个仍在回答的主 Run，再启动旁路 Run并订阅现有 `/runs/{runId}/events`。最长 120 秒按事件条件等待：

- durable 顺序为 `run.created → run.started → side_question.started → ... → run.succeeded`；
- 至少一个 `side_question.progress`；
- `run.succeeded.payload.answer` 非空且不含 `<tool_calls`；
- 旁路结束后主 Session 消息数只包含主 Run自身变化，不包含旁路问题；主 Run SSE 的 message/tool/session.scope payload 不得出现临时 fork session ID；旁路 RunEvent SSE 只允许 `run.*` 与 `side_question.*`，不得出现原始 message/tool/session.scope，也不得泄漏主 remote session ID。旁路投影器的 fake AgentRuntime 集成测试负责用交错原始事件证明它不消费主 remote session，真实 E2E 不假装观察后端内部原始订阅。
- 通过 `/processes/me` 的 serviceAddress 在 Node 测试进程比较 OpenCode session 列表，新增 fork 最终 GET 为 404/不存在。

- [ ] **Step 2: 运行后端和前端检查**

```bash
export JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd backend && mvn test
cd ../frontend && corepack pnpm test
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm build
corepack pnpm e2e -- --project=chromium
```

Expected: 全部 exit 0；记录测试数和任何非失败警告。

- [ ] **Step 3: 重启真实服务**

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build
```

验证：`GET http://127.0.0.1:8080/actuator/health/readiness` 为 UP，`http://127.0.0.1:3000/` 为 200。

- [ ] **Step 4: 运行真实 E2E**

```bash
cd frontend
TEST_AGENT_RUN_REAL_E2E=1 \
TEST_AGENT_BASE_URL=http://127.0.0.1:8080 \
TEST_AGENT_FRONTEND_URL=http://127.0.0.1:3000 \
corepack pnpm exec playwright test -c playwright.real.config.ts --grep "pet side-question"
```

Expected: PASS。若测试环境需要 token，使用当前测试登录流程获取，不把 token 写入日志或仓库。

- [ ] **Step 5: 真实 UI 验收**

在浏览器登录，建立主对话上下文，唤起宠物并提问。用 DOM 条件而非固定等待验证：阶段提示出现；等待时点击工作区其他区域浮层仍在；答案开始增量显示；最终答案非空且不含工具协议；显式 × 可关闭。

- [ ] **Step 6: 最终审查与提交**

按 `docs/guides/self-checklist.md` 逐项自检，回顾 `.agents/session-log.md` 最近条目，独立代码审查确认无 Critical/Important 问题。只暂存本任务文件并提交中文 commit；不新建分支，不修改 `.env.local/.env.test`。

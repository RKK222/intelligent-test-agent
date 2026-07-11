# OpenCode 1.17.7 全部 Part 真实 E2E Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以真实模型优先、OpenCode 原生测试会话构造兜底的方式，完成 OpenCode 1.17.7 全部 12 种官方 Part 的真实服务 E2E，并把 Question/Permission 作为额外回归门禁。

**Architecture:** 扩展现有 Playwright real-service 套件，不新增生产 API。测试通过平台创建真实 Workspace/Session/Run，采集 OpenCode 原始 HTTP、平台 messages/tree、RunEvent SSE 和浏览器当前/历史视图；自然触发未命中时，只在 manager 隔离端口数据库的测试 Session 中构造原生 message/part，再从 OpenCode HTTP 开始验证 recovery/history 链路。

**Tech Stack:** Playwright 1.61、Vitest 4、TypeScript 6、Node `child_process`、sqlite3 CLI、OpenCode 1.17.7 HTTP/SQLite、Spring Boot test profile、现有 backend-api/RunEvent SSE。

---

## Chunk 1: 先验证所有外部假设

### Task 1: 记录真实接口、manager state 与 OpenCode schema

**Files:**
- Create: `frontend/apps/agent-web/tests/opencode-parts-real-e2e.preflight.md`
- Read: `frontend/apps/agent-web/tests/workbench.real-spec.ts`
- Read: `frontend/playwright.real.config.ts`
- Read: `frontend/packages/backend-api/src/index.ts`
- Read: `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/platform/PlatformOpencodeRuntimeController.java`
- Read: `backend/test-agent-api/src/main/java/com/icbc/testagent/api/web/agent/AgentOpencodeRuntimeController.java`

- [ ] **Step 1: 启动 test 环境并确认版本**

```bash
export JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home
export PATH="$JAVA_HOME/bin:$PWD/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"
./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build
opencode --version
```

Expected: backend readiness UP、frontend 200、OpenCode `1.17.7`。

- [ ] **Step 2: 用唯一测试 Session 勘察真实端点**

通过已有平台 API 创建临时 Workspace/Session/Run，捕获 `run.started` 的 `sessionID`。实际调用并把脱敏后的响应 schema 记录到 preflight：

```text
GET  {opencodeBaseUrl}/session/{remoteSessionId}/message?directory={workspaceRoot}
GET  /api/internal/platform/opencode-runtime/sessions/{sessionId}/messages?refresh=true
GET  /api/internal/agent/opencode/sessions/{sessionId}/session-tree/messages
GET  /api/internal/agent/opencode/processes/me
DELETE /api/internal/platform/opencode-runtime/sessions/{sessionId}
```

不得凭计划猜测 endpoint；若实际 route 不同，以 Controller/backend-api 代码和实测响应为准更新后续实现。

- [ ] **Step 3: 勘察 manager state 与 SQLite schema**

读取 `.tmp/dev-services/opencode-manager-state/processes/*.json`，确认当前用户 processId/port/PID 与 `/processes/me` 一致；只读执行：

```bash
sqlite3 .tmp/dev-services/opencode-manager-session/<port>/opencode/opencode.db '.schema session'
sqlite3 .tmp/dev-services/opencode-manager-session/<port>/opencode/opencode.db '.schema message'
sqlite3 .tmp/dev-services/opencode-manager-session/<port>/opencode/opencode.db '.schema part'
```

把实际 JSON 字段和 schema 记录到 preflight。

- [ ] **Step 4: finally 清理勘察资源并验证**

关闭 SSE、取消仍活跃 Run、删除平台测试 Session/Workspace；确认历史列表不再包含唯一标题。若平台删除不级联远端 Session，使用实测的 OpenCode delete endpoint 删除远端 Session。

- [ ] **Step 5: 提交 preflight**

```bash
git add frontend/apps/agent-web/tests/opencode-parts-real-e2e.preflight.md
git commit -m "记录OpenCode Part真实E2E接口基线"
```

## Chunk 2: 可保留的测试工具与字段契约

### Task 2: 提取真实服务 API 客户端

**Files:**
- Create: `frontend/apps/agent-web/tests/real-e2e-api.ts`
- Create: `frontend/apps/agent-web/tests/real-e2e-api.test.ts`
- Modify: `frontend/apps/agent-web/tests/workbench.real-spec.ts`

- [ ] **Step 1: 写持久行为 RED 测试**

覆盖 GET/POST/DELETE、`X-Trace-Id`、Bearer header、HTTP 非 2xx、业务 `success=false`、错误文本不得包含 token：

```ts
it("sends DELETE with trace and never includes bearer token in errors", async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: false, error: { code: "X", message: "bad" } }), { status: 500 }));
  await expect(apiDelete("/x", { fetcher, token: "secret-token" })).rejects.not.toThrow(/secret-token/);
  expect(fetcher.mock.calls[0][1]).toMatchObject({ method: "DELETE" });
});
```

Run: `cd frontend && corepack pnpm exec vitest run apps/agent-web/tests/real-e2e-api.test.ts`

Expected: RED，模块不存在。

- [ ] **Step 2: 实现最小客户端并重构既有 real spec**

导出 `apiGet/apiPost/apiDelete/requestEnvelope/authHeaders`，允许测试注入 fetcher/token；默认读取 `TEST_AGENT_BASE_URL/TEST_AGENT_API_TOKEN`。把 `workbench.real-spec.ts` 的重复 helper 改为导入，不改变 PTY 流程。

- [ ] **Step 3: GREEN、typecheck、既有真实 E2E**

```bash
cd frontend
corepack pnpm exec vitest run apps/agent-web/tests/real-e2e-api.test.ts
corepack pnpm --filter @test-agent/agent-web typecheck
TEST_AGENT_RUN_REAL_E2E=1 TEST_AGENT_API_TOKEN="$TEST_AGENT_API_TOKEN" corepack pnpm exec playwright test --config=playwright.real.config.ts workbench.real-spec.ts
```

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/agent-web/tests/real-e2e-api.ts frontend/apps/agent-web/tests/real-e2e-api.test.ts frontend/apps/agent-web/tests/workbench.real-spec.ts
git commit -m "复用真实E2E平台客户端"
```

### Task 3: 定义 12 种 Part 的字段与 UI 契约

**Files:**
- Create: `frontend/apps/agent-web/tests/opencode-parts-real-e2e.ts`
- Create: `frontend/apps/agent-web/tests/opencode-parts-real-e2e.test.ts`

- [ ] **Step 1: 写 12 种类型完整性 RED 测试**

断言官方顺序、数量、每项关键字段和 locator 均非空：

```ts
expect(PART_KINDS).toEqual(["text", "subtask", "reasoning", "file", "tool", "step-start", "step-finish", "snapshot", "patch", "agent", "retry", "compaction"]);
expect(PART_SPECS).toHaveLength(12);
```

- [ ] **Step 2: 定义逐层字段定位**

根据 preflight 的实际 schema 实现：

```ts
findRawPart(rawMessages, partId)
findPlatformMessagePart(platformMessages, partId)
findTreeMessagePart(tree, remoteMessageId, partId)
selectPartFields(kind, part)
assertPartProjection(kind, raw, platformMessages, platformTree)
```

`assertPartProjection` 必须分别比较 raw→platform messages、raw→platform tree，字段采用设计规范矩阵；缺字段即失败，不能以 UI marker 代替。

- [ ] **Step 3: 定义 12 类 UI 语义**

| Part | 当前/历史 locator 与语义 | 适用交互 |
| --- | --- | --- |
| text | `.oc-text-part` 包含唯一文本 | `button[aria-label=复制]` |
| reasoning | `.oc-reasoning-part` 和“思考状态” | 点击展开后正文标记可见 |
| file | 原生仅作为输入附件/引用，assistant timeline 不单独渲染 | N/A |
| tool | `[data-testid=oc-tool-group]` 或具体 tool view 显示 read/标记 | 点击展开输出 |
| subtask | 原生 assistant timeline 无 Part renderer；task tool 卡片不能冒充 SubtaskPart | N/A |
| step-start | 原生事件同步层跳过，不渲染 | N/A |
| step-finish | 原生事件同步层跳过，不渲染 | N/A |
| snapshot | 原生 assistant timeline 无 Part renderer | N/A |
| patch | 原生事件同步层跳过；diff 由消息 summary 单独呈现 | N/A |
| agent | 原生仅作为输入 Agent 引用，assistant timeline 不单独渲染 | N/A |
| retry | 原生 assistant timeline 无 Part renderer | N/A |
| compaction | 原生 `compaction-part` 分隔线可见 | N/A |

对照 OpenCode 1.17.8 原生 Web UI（与本项目运行时 1.17.7 Part schema 一致），assistant timeline
只直接映射 `text/reasoning/tool/compaction`。其余类型仍必须在 raw/messages/tree 无损，但 UI 应验证
不产生额外可见卡片；隐藏 fallback 不算“可见通过”，也不应为了类型齐全新增视觉噪音。

- [ ] **Step 4: 深层脱敏与证据路径测试**

`sanitizeEvidence` 递归移除 Authorization/Cookie/token/key/secret；`evidencePath(runId, kind, name)` 必须生成 `.tmp/e2e/opencode-parts/<runId>/<kind>/<name>`，拒绝 `..` 和覆盖其他 kind。

- [ ] **Step 5: GREEN 并提交**

```bash
cd frontend && corepack pnpm exec vitest run apps/agent-web/tests/opencode-parts-real-e2e.test.ts
git add frontend/apps/agent-web/tests/opencode-parts-real-e2e.ts frontend/apps/agent-web/tests/opencode-parts-real-e2e.test.ts
git commit -m "定义OpenCode全部Part真实E2E契约"
```

## Chunk 3: 12 类自然触发，每类只尝试一次

### Task 4: 实现自然触发状态机和真实 Run 资源生命周期

**Files:**
- Modify: `frontend/apps/agent-web/tests/opencode-parts-real-e2e.ts`
- Create: `frontend/apps/agent-web/tests/opencode-parts.real-spec.ts`
- Modify: `frontend/playwright.real.config.ts`
- Test: `frontend/apps/agent-web/tests/opencode-parts-real-e2e.test.ts`

- [ ] **Step 1: 写一次尝试/45 秒 fallback RED 测试**

fake callback 只测试状态机：`runOnce` 必须恰好一次；45 秒或测试注入的短 timeout 后返回 `native-fixture` 并保留 `reason/rawSnapshot`。

- [ ] **Step 2: 实现每类独立 try/finally 资源容器**

每个 kind 创建唯一 Workspace root/file、平台 Session、Run 和 SSE AbortController；finally 中关闭 SSE、取消非终态 Run、删除远端 Session、平台 Session/Workspace/临时文件，并验证平台历史无唯一标题。失败和超时同样执行 finally。

- [ ] **Step 3: 配置逐类 trace 与证据目录**

Playwright config 对本 spec 使用 `trace: "on"` 或测试内 `context.tracing.start/stop({ path: <runId>/<kind>/trace.zip })`。每类写独立 `opencode-raw.json/platform-messages.json/platform-tree.json/run-events.ndjson/current-ui.png/history-ui.png`；测试断言非空且 matrix 中路径真实存在。

- [ ] **Step 4: 实现 12 类具体自然调用**

每类均使用一次 `startRun` 或对应既有 runtime API，45 秒内轮询 OpenCode raw：

1. `text`：固定文本 prompt。
2. `reasoning`：固定数字分析，命中非空 reasoning。
3. `file`：Run `parts` 传 text/plain data URL 文件。
4. `tool`：read 临时 README。
5. `subtask`：明确要求 task/subagent 只读检查；只认 raw `type=subtask`。
6. `step-start`：复用 read Run，但独立在本 kind 资源中执行一次。
7. `step-finish`：独立 read Run，等待完成并检查 tokens/cost。
8. `snapshot`：要求 edit 修改临时文件一次。
9. `patch`：独立 edit Run，检查 hash/files。
10. `agent`：`startRun` payload 选择真实 build Agent并在 prompt parts 中带 agent（以 preflight 确认的格式为准）；只认 raw `type=agent`。
11. `retry`：先检查测试专属 Provider 是否能安全配置一次 429/503；不能在不改用户配置下完成则记录 `unsafe-provider-injection` 并直接 fallback，不发送模型请求。
12. `compaction`：最多准备 50 条/48k 字符测试消息，调用 preflight 确认的 summarize/compact API；只认 raw compaction。

- [ ] **Step 5: 自然命中后复用完整链路验证，再清理**

每个 `natural-pass` 在 per-kind 外层 `try/finally` 内继续执行同一 `verifyFullChain`：OpenCode raw → platform `messages?refresh=true` → platform tree → raw/messages/tree 字段比较 → 当前 UI locator/交互 → 新建对话 → 历史 UI locator/交互。只有上述步骤全部完成后才执行 finally 清理。自然模式额外要求 SSE 中出现目标 Part；构造模式复用相同函数但 SSE 写 `not-claimed`。

- [ ] **Step 6: 先运行自然阶段并保存命中/未命中证据**

```bash
cd frontend
TEST_AGENT_RUN_PART_E2E=1 TEST_AGENT_PART_PHASE=natural TEST_AGENT_API_TOKEN="$TEST_AGENT_API_TOKEN" \
corepack pnpm exec playwright test --config=playwright.real.config.ts opencode-parts.real-spec.ts
```

Expected: 12 行均为 `natural-pass` 或 `native-fixture-required`，无重复模型尝试，资源清理通过。

- [ ] **Step 7: 提交**

```bash
git add frontend/apps/agent-web/tests/opencode-parts-real-e2e.ts frontend/apps/agent-web/tests/opencode-parts-real-e2e.test.ts frontend/apps/agent-web/tests/opencode-parts.real-spec.ts frontend/playwright.real.config.ts
git commit -m "增加全部Part自然触发真实E2E"
```

## Chunk 4: OpenCode 原生构造与 recovery 链路

### Task 5: 实现 realpath 保护、SQLite transaction 和清理

**Files:**
- Modify: `frontend/apps/agent-web/tests/opencode-parts-real-e2e.ts`
- Test: `frontend/apps/agent-web/tests/opencode-parts-real-e2e.test.ts`

- [ ] **Step 1: 写路径/符号链接/归属/rollback RED 测试**

覆盖：数据库和所有父目录 `realpath` 必须位于仓库 manager-session root；拒绝 symlink 逃逸和 `~/.local/share/opencode`；Session ID/title 必须匹配本轮 manifest；SQL 使用 `.timeout 5000`、`PRAGMA foreign_keys=ON`、`BEGIN IMMEDIATE/COMMIT`，错误时 sqlite3 自动 rollback；probe 也用唯一 `e2e_part_` ID。

- [ ] **Step 2: 实现 manager state 与 DB 交叉校验**

从真实 `/processes/me` 获取 port/processId/baseUrl，从 manager state 获取 port/PID，并校验 DB `session` 表存在本轮 `remoteSessionId`。使用 `execFile("sqlite3", [dbPath], { input: sql })` 或无 shell 等价方式，严禁字符串 shell 执行。

- [ ] **Step 3: 实现官方字段完整 payload**

每个 fallback kind 在单一 assistant/user message 下插入唯一 part；message data 使用 OpenCode 实际 info schema。12 种 builder 均由单元测试逐字段比对官方关键字段；retry error、tool state、file source、step-finish tokens 必须完整。

- [ ] **Step 4: 实现 HTTP probe 与延迟 cleanup callback**

写入 probe 后 5 秒轮询真实 OpenCode HTTP。fixture helper 只返回构造结果和幂等 `cleanup()` callback，不在 helper 内提前删除 Session。资源所有权属于 per-kind 外层 `try/finally`；只有 Task 6 完成 raw/messages/tree/current/history 后才调用 cleanup。cleanup 删除远端/平台测试 Session并验证 OpenCode HTTP 404/空、SQLite message/part count=0、平台 Session 404、历史无唯一标题。任何一项失败，matrix cleanup=failed。

- [ ] **Step 5: GREEN 并提交**

```bash
cd frontend && corepack pnpm exec vitest run apps/agent-web/tests/opencode-parts-real-e2e.test.ts
git add frontend/apps/agent-web/tests/opencode-parts-real-e2e.ts frontend/apps/agent-web/tests/opencode-parts-real-e2e.test.ts
git commit -m "增加OpenCode原生Part隔离构造"
```

### Task 6: 明确构造后的平台 recovery 与两次 UI 恢复

**Files:**
- Modify: `frontend/apps/agent-web/tests/opencode-parts.real-spec.ts`

- [ ] **Step 1: 建立 per-kind 外层 try/finally**

统一执行顺序为：`构造 → raw → messages/tree → 当前 UI → 历史 UI → 适用交互 → finally cleanup()`。中间任一步失败仍进入 cleanup，cleanup 自身失败追加到原始错误而不覆盖首个失败原因。

- [ ] **Step 2: 调用真实 recovery 入口**

SQLite 构造不会产生 SSE。调用实测确认的 `GET .../messages?refresh=true`，再调用 agent-scoped session-tree messages；分别保存响应并运行 raw→messages、raw→tree 字段比较。

- [ ] **Step 3: 第一次 UI recovery（current-ui）**

真实浏览器登录后打开工作台消息列表，选择测试 Session。该动作走 AgentWorkbench 的 messages/tree recovery，不注入前端状态；按 Task 3 的 locator 验证并保存 `<kind>/current-ui.png`。

- [ ] **Step 4: 第二次历史恢复（history-ui）**

点击“新建对话”，重新打开消息列表并再次选择同一 Session，重复 locator/交互断言并保存 `<kind>/history-ui.png`。构造模式 matrix 的 SSE 固定为 `not-claimed`。

- [ ] **Step 5: 最后执行 cleanup 并运行完整 fallback 阶段**

```bash
cd frontend
TEST_AGENT_RUN_PART_E2E=1 TEST_AGENT_PART_PHASE=fallback TEST_AGENT_API_TOKEN="$TEST_AGENT_API_TOKEN" \
corepack pnpm exec playwright test --config=playwright.real.config.ts opencode-parts.real-spec.ts
```

Expected: 所有自然未命中项 raw/messages/tree/current/history/cleanup PASS；证据路径独立、非空、可解析。

## Chunk 5: 按真实失败逐层修复

### Task 7: 对每个失败 Part 执行严格 RED/GREEN

**Conditional Files:**
- `backend/test-agent-opencode-client/src/main/java/com/icbc/testagent/opencode/client/OpencodeRunEventMapper.java`
- `backend/test-agent-opencode-client/src/test/java/com/icbc/testagent/opencode/client/OpencodeRunEventMapperTest.java`
- `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/run/RunMessageRecoveryService.java`
- `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/run/RunMessageRecoveryServiceTest.java`
- `frontend/packages/agent-chat/src/runtime-reducer.ts`
- `frontend/packages/agent-chat/tests/runtime-reducer.test.ts`
- `frontend/packages/agent-chat/src/opencode-like/**`
- `frontend/packages/agent-chat/tests/opencode-timeline.test.ts`

对每个失败类型单独循环：

- [ ] **Step 1:** 用证据确定首个丢失层：raw、Java/platform messages、tree、reducer、Timeline。
- [ ] **Step 2:** 在现有所有者旁写最小失败测试并运行确认 RED；隐藏 `.oc-unknown-part` 不算可见通过。
- [ ] **Step 3:** 做最小生产修复，保留未知类型 fallback 和原生 Timeline结构。
- [ ] **Step 4:** 运行 GREEN、相邻回归和该 kind 真实 E2E。
- [ ] **Step 5:** 中文单缺陷提交。

不得修改 generated SDK、生产 API、事件 wire name、Huangzhenren 的 ask/reasoning/tool/subagent/终态逻辑或无关视觉。

## Chunk 6: Question/Permission 门禁与最终交付

### Task 8: 实现可执行的交互门禁

**Files:**
- Modify: `frontend/apps/agent-web/tests/opencode-parts.real-spec.ts`

- [ ] **Step 1: Question 提交与忽略**

会话 A：prompt 明确只调用 question，等待 `question.asked` 和弹框；记录 requestId；新建对话→历史恢复，选择 A 并提交；断言 requestId 仍是 OpenCode pending 当前值、最终文本 A、Run SUCCEEDED。会话 B：同样触发后点击“忽略”，断言 `question.rejected`、弹框消失、Run 进入明确终态。finally 删除两会话。

- [ ] **Step 2: Permission once 与 reject**

会话 C：要求 read 工作区外 `/etc/hosts`，等待 `permission.asked(external_directory)`；新建对话→历史恢复，确认 requestId 刷新后点击“一次”，断言读取结果和 Run SUCCEEDED。会话 D：同样触发后历史恢复并点击“拒绝”，断言 permission 清除、工具错误/拒绝结果和 Run 明确终态。`always` 只保留组件编码回归，明确标为非真实 E2E，不替代 once/reject。finally 删除两会话且不写用户永久权限。

- [ ] **Step 3: 保存交互证据**

每会话独立 raw/platform/SSE/current/history 截图和 trace，清理状态写入 matrix 的 `question-submit/question-ignore/permission-once/permission-reject` 行。

### Task 9: 全量回归、重启、最终矩阵与文档

**Files:**
- Modify: `frontend/apps/agent-web/src/PACKAGE.md`
- Conditional Modify: `backend/test-agent-opencode-runtime/README.md`
- Modify: `.agents/session-log.md`

- [ ] **Step 1: 自动化回归**

```bash
cd backend
export JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl test-agent-opencode-runtime -am test

cd ../frontend
corepack pnpm exec vitest run apps/agent-web/tests/FigmaChatPanel.test.ts packages/agent-chat/tests apps/agent-web/tests/real-e2e-api.test.ts apps/agent-web/tests/opencode-parts-real-e2e.test.ts
corepack pnpm -r typecheck
corepack pnpm -r lint
corepack pnpm --filter @test-agent/agent-web build
```

- [ ] **Step 2: 重启最新代码**

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
export JAVA_HOME=/Users/kaka/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home
export PATH="$JAVA_HOME/bin:$PWD/.tmp/dev-bin:/opt/homebrew/opt/libpq/bin:$PATH"
./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build
```

- [ ] **Step 3: 最终复跑全部真实 E2E**

运行 natural + fallback + Question/Permission；断言 matrix 12 个 Part 和 4 个交互门禁全部通过，每条证据路径存在非空，所有 cleanup=pass。构造类型 SSE 必须显示 `not-claimed`。

- [ ] **Step 4: 完成项目审计**

```bash
git diff --check
rg -n '^(<<<<<<<|=======|>>>>>>>)' backend frontend docs .agents || true
git diff --name-only -- backend/test-agent-opencode-sdk-generated
```

逐项人工审查 `frontend/packages/agent-chat/src/opencode-like` 相对基线的差异；只允许本轮经 RED/GREEN 证明的 Part 可见性修复，不能要求目录完全等于旧基线而覆盖合法修复。

- [ ] **Step 5: 文档、session log 与提交**

更新真实 E2E 运行方式、12 行字段证据、自然/构造方式、SSE 声明、Question/Permission 结果、已知无关失败和清理状态。回顾 `.agents/session-log.md` 后提交：

```bash
git add <本任务文件>
git commit -m "完成OpenCode全部Part真实E2E"
```

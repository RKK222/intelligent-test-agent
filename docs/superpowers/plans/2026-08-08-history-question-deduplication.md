# History Question Deduplication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打开仍有待答问题的历史会话时，同一 OpenCode `requestId` 只展示一张可交互问题卡，同时保留不同请求 ID 的子 Agent 交互。

**Architecture:** 保留平台 sessionId 与 OpenCode 远端 sessionId 的双身份设计，只在历史交互快照合并边界按 `requestId` 统一身份。OpenCode 当前 pending 快照为根会话权威数据；Session Tree 只为不同 requestId 的子会话交互和接口失败降级提供补充。

**Tech Stack:** Vue 3、TypeScript 6、Vitest 4、pnpm workspace。

## Global Constraints

- 不修改 HTTP API、RunEvent wire name、共享 DTO、后端、数据库/Flyway、环境配置、generated SDK 或 OpenCode 源码。
- 只修改与历史 interaction 合并、回归测试和稳定文档直接相关的最小范围。
- 保留工作区现有批量定时功能的未提交改动；暂存和提交时只纳入本任务新增的 hunks。
- 人工维护的复杂逻辑使用中文注释；完成后更新 `.agents/session-log.huangzhenren.md` 并使用中文 commit。

---

### Task 1: 在历史交互合并边界消除 requestId 别名重复

**Files:**
- Modify: `frontend/apps/agent-web/tests/workbench-utils.test.ts`
- Modify: `frontend/apps/agent-web/src/components/workbench-utils.ts`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: `replaceRootSessionInteractions<T>(restored, liveRoot, rootSessionId)` 及 `reduceAgentChatRuntime(state, action)`。
- Produces: `replaceRootSessionInteractions<T extends { requestId: string; sessionId: string }>(restored: T[], liveRoot: T[] | null, rootSessionId: string): T[]`；实时根请求按 `requestId` 覆盖历史别名，实时 `null` 保持降级，实时空数组清空平台根作用域请求。

- [ ] **Step 1: 写入可复现完整恢复顺序的失败测试**

在 `replaceRootSessionInteractions` 测试组中直接导入该函数，构造远端根问题、平台实时问题和不同 ID 的 child 问题：

```ts
const restored = [
  { requestId: "que_1", sessionId: "ses_remote_root" },
  { requestId: "que_child", sessionId: "ses_remote_child" }
];
const liveRoot = [{ requestId: "que_1", sessionId: "ses_platform_root" }];

expect(replaceRootSessionInteractions(restored, liveRoot, "ses_platform_root")).toEqual([
  { requestId: "que_child", sessionId: "ses_remote_child" },
  { requestId: "que_1", sessionId: "ses_platform_root" }
]);
```

同组补充两个边界断言：`liveRoot=[]` 删除已投影到平台根作用域的请求但保留 child；`liveRoot=null` 原样返回历史降级数据。测试名称明确说明防止“远端根别名在 SSE 投影后变成第二张卡片”。

- [ ] **Step 2: 运行聚焦测试并确认 RED**

Run:

```bash
cd frontend && corepack pnpm vitest run apps/agent-web/tests/workbench-utils.test.ts
```

Expected: 新增主场景 FAIL，实际结果仍包含 `{ requestId: "que_1", sessionId: "ses_remote_root" }`；既有测试继续执行。

- [ ] **Step 3: 实现最小 requestId 权威合并**

把 helper 泛型约束扩展为同时要求 `requestId`，实时列表非空或空数组时先建立权威 ID 集合，再过滤历史根和同 ID 别名：

```ts
export function replaceRootSessionInteractions<T extends { requestId: string; sessionId: string }>(
  restored: T[],
  liveRoot: T[] | null,
  rootSessionId: string
): T[] {
  if (liveRoot === null) return restored;
  const liveRequestIds = new Set(liveRoot.map((item) => item.requestId));
  return [
    ...restored.filter((item) => item.sessionId !== rootSessionId && !liveRequestIds.has(item.requestId)),
    ...liveRoot.filter((item) => item.sessionId === rootSessionId)
  ];
}
```

保留中文注释，说明 `requestId` 是跨平台/远端 session 别名的稳定交互身份；不在 reducer 或 FigmaChatPanel 增加第二层 UI 去重。

- [ ] **Step 4: 运行聚焦测试并确认 GREEN**

Run:

```bash
cd frontend && corepack pnpm vitest run apps/agent-web/tests/workbench-utils.test.ts
```

Expected: `workbench-utils.test.ts` 全部 PASS，无新增错误或 warning。

- [ ] **Step 5: 同步稳定文档与会话记录**

在 agent-web README 的历史会话恢复说明中补充：Session Tree 与当前 pending 快照按 `requestId` 对齐，实时根请求覆盖远端根别名，子 Agent 不同请求保留。向本机 session log 追加一条合并的 `Why / What / How / Result` 记录，说明根因、TDD 证据、影响边界和验证结果；不修改冻结的 `.agents/session-log.md`。

- [ ] **Step 6: 执行前端验证与差异自检**

Run:

```bash
cd frontend && corepack pnpm --filter @test-agent/agent-web typecheck
git diff --check
git status --short
```

Expected: typecheck exit 0；`git diff --check` 无输出；status 中本任务只有 helper、单测、README、个人 session log，其他批量定时文件仍保持原有未提交状态。

- [ ] **Step 7: 只暂存本任务 hunks 并提交**

先回顾全部 `.agents/session-log*.md` 的近期条目，再核对 cached diff 不包含批量定时改动；对已有用户修改的 README 使用 hunk 级暂存。

```bash
git diff --cached --check
git diff --cached --stat
git commit -m "修复历史会话待答问题重复显示"
```

Expected: 中文 commit 成功；提交内容只包含本任务代码、测试、文档和 `.agents/session-log.huangzhenren.md`。

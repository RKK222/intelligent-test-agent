# 分享会话用户消息两色 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将用户消息气泡收敛为“自己绿色、其他人紫色、全部无边框”的固定两色规则。

**Architecture:** 保留 `resolveUserMessageAppearance` 作为普通工作台和分享工作台唯一的消息外观解析入口，只替换其颜色与边框输出，不改变调用组件、消息归因或姓名解析。缺少发送人归因的历史消息在解析层显式回退为自己的绿色样式。

**Tech Stack:** Vue 3、TypeScript、Vitest、pnpm workspace

## Global Constraints

- 自己发送的消息背景色必须始终为 `#B2EDDF`。
- 当前用户看到的所有其他人消息背景色必须统一为 `#9A8EDE`。
- 两种消息气泡都必须使用 `border: none`。
- 自己的消息不显示姓名，其他人的消息继续显示姓名。
- 缺少发送人归因的历史消息按自己发送处理。
- 不修改后端归因字段、HTTP API、事件、数据库、安全策略、OpenCode 源码或 generated SDK。

---

### Task 1: 固定用户消息两色外观

**Files:**
- Modify: `frontend/packages/agent-chat/tests/user-message-appearance.test.ts`
- Modify: `frontend/packages/agent-chat/src/user-message-appearance.ts`
- Modify: `frontend/packages/agent-chat/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/apps/agent-web/tests/workbench.spec.ts`
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: `resolveUserMessageAppearance(attribution: UserMessageAttribution, currentUserId?: string | null): UserMessageAppearance`
- Produces: 同一函数签名；`style` 对自己返回 `{ backgroundColor: "#B2EDDF", border: "none" }`，对其他人返回 `{ backgroundColor: "#9A8EDE", border: "none" }`。

- [x] **Step 1: 写入失败测试**

将现有测试改为分别断言自己、其他人和历史消息：

```ts
expect(mine).toEqual({
  own: true,
  style: { backgroundColor: "#B2EDDF", border: "none" }
});
expect(otherFirst).toEqual({
  own: false,
  displayName: "协作者",
  style: { backgroundColor: "#9A8EDE", border: "none" }
});
expect(otherFirst.style).toEqual(otherSecond.style);
expect(resolveUserMessageAppearance({}, "usr_me")).toEqual({
  own: true,
  style: { backgroundColor: "#B2EDDF", border: "none" }
});
```

- [x] **Step 2: 运行定向测试并确认 RED**

Run: `cd frontend && corepack pnpm exec vitest run packages/agent-chat/tests/user-message-appearance.test.ts`

Expected: FAIL；旧实现返回 HSL 哈希颜色、边框颜色或空历史样式，与固定两色断言不一致。

- [x] **Step 3: 写入最小实现**

删除 `stableHue`，在 `resolveUserMessageAppearance` 中保留现有 own 与姓名判断，只返回固定样式：

```ts
const ownStyle = { backgroundColor: "#B2EDDF", border: "none" };
const otherStyle = { backgroundColor: "#9A8EDE", border: "none" };

if (!senderUserId) {
  return { own: true, style: ownStyle };
}

return {
  own,
  ...(own ? {} : { displayName }),
  style: own ? ownStyle : otherStyle
};
```

同步更新中文注释，说明颜色以当前查看者为判断基准。

- [x] **Step 4: 运行定向测试并确认 GREEN**

Run: `cd frontend && corepack pnpm exec vitest run packages/agent-chat/tests/user-message-appearance.test.ts`

Expected: PASS，2 个测试全部通过。

- [x] **Step 5: 同步稳定文档**

将两个 README 中“按发送人生成稳定颜色”的描述改为：自己的消息固定 `#B2EDDF`、其他人的消息统一 `#9A8EDE`、两者无边框；姓名规则不变。

- [x] **Step 6: 执行前端回归验证**

Run:

```bash
cd frontend
corepack pnpm test
corepack pnpm typecheck
corepack pnpm build
corepack pnpm e2e:session-share
```

Expected: Vitest、全部 workspace 类型检查、生产构建及 Chromium/Firefox/WebKit 分享回归均退出码 0；浏览器计算样式精确匹配两个色值且边框为 `none`，允许保留仓库既有的大 chunk 警告。

- [x] **Step 7: 更新会话日志并提交**

在 `.agents/session-log.huangzhenren.md` 追加一条 `Why / What / How / Result` 记录，回顾全部 `.agents/session-log*.md` 后执行：

```bash
git diff --check
git add frontend docs/superpowers/plans/2026-08-09-shared-message-two-color-plan.md .agents/session-log.huangzhenren.md
git commit -m "fix: 统一分享消息气泡两色样式"
```

Expected: 提交成功且 `git status --short` 无输出。

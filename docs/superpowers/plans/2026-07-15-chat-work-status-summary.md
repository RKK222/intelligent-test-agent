# 合并对话工作状态展示实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将主智能体 reasoning 与普通工具事件聚合为每轮一个两行工作状态块，并始终显示在该轮最新输出之后。

**Architecture:** 在 `agent-chat` 时间线投影层生成根会话专用 `work-status` 行，组件层负责状态摘要、事件图标和全宽悬浮详情；子智能体投影、问答卡片、消息 reducer 和网络契约保持不变。通用 `ShimmerDivider` 只增加向后兼容的纵向与静态展示能力。

**Tech Stack:** Vue 3、TypeScript 6、Vitest、Testing Library Vue、Tailwind CSS 4、lucide-vue-next、pnpm workspace。

## Global Constraints

- 只修改本任务相关前端代码、测试和稳定文档。
- `task`、`question` 和子智能体视图保持原展示方式。
- 不修改 API、RunEvent、数据库、后端或环境配置。
- 人工维护的复杂逻辑补充中文注释。
- 先写失败测试并确认 RED，再实现最小代码并确认 GREEN。

---

### Task 1: 扩展 ShimmerDivider

- [x] 为纵向布局、静态模式和默认横向兼容补充失败测试。
- [x] 运行 `packages/ui-kit/tests/ShimmerDivider.test.ts` 确认 RED。
- [x] 增加 `orientation?: "horizontal" | "vertical"` 与 `animated?: boolean`，切换渐变、mask 和动画方向。
- [x] 重跑组件测试和 `@test-agent/ui-kit` typecheck 确认 GREEN。

### Task 2: 聚合工作状态投影

- [x] 为无 assistant part、事件分类/计数、末尾排序、多轮保留和 task/question/子智能体不变补充失败测试。
- [x] 运行 `packages/agent-chat/tests/opencode-like-state.test.ts` 确认 RED。
- [x] 新增 `work-status` 类型与事件分类 helper，在根时间线聚合过程 part，并把最新状态块延后到 runtime 行之后。
- [x] 重跑状态测试确认 GREEN。

### Task 3: 实现两行状态块与全宽气泡

- [x] 为两行边框、动态图标、计数、互斥气泡、外部点击/Esc、新轮收起和流光状态补充失败测试。
- [x] 运行 `packages/agent-chat/tests/opencode-timeline.test.ts` 确认 RED。
- [x] 新增工作状态组件与样式，复用 reasoning 和工具详情组件；在时间线根控制全局唯一打开气泡。
- [x] 重跑时间线测试、`FigmaChatPanel` 回归测试和 `@test-agent/agent-chat` typecheck 确认 GREEN。

### Task 4: 文档、全量验证与提交

- [x] 同步 `agent-chat`、`agent-web`、`ui-kit` README/PACKAGE 和 `.agents/session-log.md`。
- [x] 运行 `corepack pnpm typecheck`、`corepack pnpm test`、`corepack pnpm build` 与 `git diff --check`。
- [x] 在桌面与窄屏真实页面验证流式排序、单行图标、气泡定位和历史收起。
- [x] 回顾 `.agents/session-log.md`，检查暂存范围并使用中文 commit 提交。

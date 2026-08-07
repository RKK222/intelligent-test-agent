# 顶部上下文选择器现代化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将工作台顶部“应用、工作空间、版本”三个独立输入框式按钮改造成连续的现代上下文轨道，并统一三个下拉菜单的视觉状态，不改变现有交互和数据逻辑。

**Architecture:** 改动收敛在现有 `FigmaShell.vue`：在三个现有菜单 wrapper 外增加纯视觉分组容器，通过已有 `appMenuOpen`、`workspaceMenuOpen`、`versionMenuOpen` 绑定整体展开态，三个 button、下拉内容和事件处理保持原节点与接口。测试继续使用 Vue Test Utils 验证 DOM/事件，并用现有源码断言锁定 shell 关键视觉契约；稳定文档同步描述新的上下文轨道。

**Tech Stack:** Vue 3、TypeScript 6、Scoped CSS、Vue Test Utils、Vitest 4、Vite 8、Corepack + pnpm 10.25.0

## Global Constraints

- 保持现有 36px 顶栏和“36px 首行 + 8px 面板间隔”的 44px 视觉带，不移动 Logo、手册、运行态摘要和用户菜单。
- 颜色优先复用现有 `--ta-shell-*` token；品牌红为 `#C8161D`，顶栏画布为 `#F0F4FA`，表面为 `#FFFFFF`。
- 上下文轨道高度固定为 34px、外层圆角 11px、分段圆角 7px、左侧信号条为 3px × 15px。
- 保留应用、工作空间、版本的内容、顺序、可访问名称、菜单项、默认选择、禁用语义和全部事件回调。
- 继续支持现有 900px 响应式断点和企业 Chromium 108；禁止使用 `:has()`、容器查询或新版 CSS `scale`。
- 仅修改任务直接相关的前端、测试、稳定文档和本机会话日志；不修改工作区现有后端并行变更，不创建新分支。
- 不新增运行时依赖、图片资产、HTTP API、RunEvent、DTO、数据库、Flyway、安全或环境配置变更。

---

## File Map

- `frontend/apps/agent-web/src/components/FigmaShell.vue`：保留三个菜单的现有行为，增加统一上下文轨道结构并重写顶部选择器和下拉菜单局部样式。
- `frontend/apps/agent-web/tests/FigmaShell.test.ts`：锁定统一轨道、三段顺序、展开态、禁用态和关键视觉规则，继续覆盖原切换回调。
- `frontend/README.md`：更新整个前端工作台顶栏的稳定视觉说明。
- `frontend/apps/agent-web/README.md`：更新 agent-web 顶栏职责与状态说明。
- `frontend/apps/agent-web/src/PACKAGE.md`：更新 `FigmaShell.vue` 包级说明。
- `.agents/session-log.huangzhenren.md`：按 `Why / What / How / Result` 记录本次可复用的设计与验证结论。

### Task 1: 用测试驱动实现统一上下文轨道与现代下拉菜单

**Files:**
- Modify: `frontend/apps/agent-web/tests/FigmaShell.test.ts:41-90, 900-940`
- Modify: `frontend/apps/agent-web/src/components/FigmaShell.vue:2086-2325, 3074-3082, 3910-4174, 4924-4948`

**Interfaces:**
- Consumes: 现有 `appMenuOpen: Ref<boolean>`、`workspaceMenuOpen: Ref<boolean>`、`versionMenuOpen: Ref<boolean>` 与三个现有 button/menu wrapper。
- Produces: `data-testid="header-context-rail"`；CSS 类 `.figma-context-rail`、`.figma-context-rail.has-open-menu`；现有 `header-workspace-selector`、`header-version-selector` 和 `select-app` / `load-versions` / `select-version` 事件保持不变。

- [ ] **Step 1: 新增统一轨道结构和视觉契约的失败测试**

在 `FigmaShell.test.ts` 的 shell 视觉测试中加入以下源码断言：

```ts
expect(figmaShellSource).toContain('data-testid="header-context-rail"');
expect(figmaShellSource).toMatch(/\.figma-context-rail\s*\{[^}]*height: 34px;[^}]*border: 1px solid var\(--ta-shell-border, #e5e7eb\);[^}]*border-radius: 11px;[^}]*background: var\(--ta-shell-surface, #fff\)/s);
expect(figmaShellSource).toMatch(/\.figma-context-rail::before\s*\{[^}]*width: 3px;[^}]*height: 15px;[^}]*background: var\(--ta-shell-accent, #c8161d\)/s);
expect(figmaShellSource).toMatch(/\.figma-context-rail\.has-open-menu\s*\{[^}]*border-color: var\(--ta-shell-accent, #c8161d\)/s);
expect(figmaShellSource).toMatch(/\.figma-context-rail > :not\(:first-child\)::before\s*\{[^}]*background: var\(--ta-shell-border, #e5e7eb\)/s);
expect(figmaShellSource).toMatch(/\.figma-app-menu-trigger,[\s\S]*?\.figma-context-menu-trigger\s*\{[^}]*height: 26px;[^}]*border: 0;[^}]*background: transparent/s);
expect(figmaShellSource).toMatch(/\.figma-app-menu-dropdown\s*\{[^}]*border-radius: 12px;[^}]*box-shadow:\s*0 12px 32px rgba\(15, 23, 42, 0\.10\),\s*0 2px 8px rgba\(15, 23, 42, 0\.06\)/s);
expect(figmaShellSource).toMatch(/\.figma-app-menu-item\s*\{[^}]*border-radius: 8px/s);
```

在“keeps application context centered…”用例中加入 DOM 顺序与整体展开态断言：

```ts
const contextRail = wrapper.get('[data-testid="header-context-rail"]');
const railSegments = contextRail.findAll(
  ':scope > .figma-app-menu-wrapper, :scope > .figma-workspace-menu-wrapper, :scope > .figma-version-menu-wrapper'
);
expect(railSegments.map((segment) => segment.classes()[0])).toEqual([
  'figma-app-menu-wrapper',
  'figma-workspace-menu-wrapper',
  'figma-version-menu-wrapper'
]);
expect(contextRail.classes()).not.toContain('has-open-menu');

await appSwitch.get('button').trigger('click');
expect(contextRail.classes()).toContain('has-open-menu');
expect(appSwitch.get('button').attributes('aria-expanded')).toBe('true');
```

- [ ] **Step 2: 运行定向测试并确认先失败**

Run:

```bash
cd frontend && corepack pnpm test -- apps/agent-web/tests/FigmaShell.test.ts
```

Expected: FAIL；至少报告找不到 `data-testid="header-context-rail"`，证明测试能捕获尚未实现的统一轨道。

- [ ] **Step 3: 增加最小模板结构，不改变三个菜单节点**

将 `FigmaShell.vue` 的三个 wrapper 包在新容器内；在现有 `.figma-header-center` 开始标签后插入以下开始标签：

```vue
<div
  :class="[
    'figma-context-rail',
    (appMenuOpen || workspaceMenuOpen || versionMenuOpen) && 'has-open-menu'
  ]"
  data-testid="header-context-rail"
>
```

在现有 `.figma-version-menu-wrapper` 结束标签之后、`.figma-header-center` 结束标签之前插入一个 `</div>`，只关闭新轨道容器。三个 wrapper 内部的 button、`ul`、事件和条件渲染不移动、不改名。

- [ ] **Step 4: 实现上下文轨道、状态、菜单和响应式样式**

在 `FigmaShell.vue` 的 scoped CSS 中实现以下规则，并删除原三个按钮的独立边框、白底和互相间的 6px gap：

```css
.figma-header-center {
  display: flex;
  align-items: center;
  justify-self: center;
  transform: translateY(calc(var(--ta-shell-gap, 8px) / 2));
  z-index: 2;
}

.figma-context-rail {
  position: relative;
  display: flex;
  align-items: center;
  height: 34px;
  box-sizing: border-box;
  padding: 3px 3px 3px 8px;
  border: 1px solid var(--ta-shell-border, #e5e7eb);
  border-radius: 11px;
  background: var(--ta-shell-surface, #fff);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  transition: border-color 0.14s ease, box-shadow 0.14s ease;
}

.figma-context-rail::before {
  content: "";
  position: absolute;
  left: 3px;
  top: 50%;
  width: 3px;
  height: 15px;
  border-radius: 999px;
  background: var(--ta-shell-accent, #c8161d);
  transform: translateY(-50%);
}

.figma-context-rail.has-open-menu {
  border-color: var(--ta-shell-accent, #c8161d);
  box-shadow: 0 0 0 2px rgba(200, 22, 29, 0.08), 0 4px 12px rgba(15, 23, 42, 0.08);
}

.figma-context-rail > :not(:first-child)::before {
  content: "";
  position: absolute;
  left: 0;
  top: 7px;
  bottom: 7px;
  width: 1px;
  background: var(--ta-shell-border, #e5e7eb);
}

.figma-app-menu-trigger,
.figma-context-menu-trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  min-width: 0;
  box-sizing: border-box;
  padding: 0 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--ta-shell-header-text, #000000);
  cursor: pointer;
  font-family: var(--font-sans, "PingFang SC", "Microsoft YaHei", sans-serif);
  transition: background-color 0.14s ease, color 0.14s ease, box-shadow 0.14s ease;
}

.figma-app-menu-trigger:hover,
.figma-context-menu-trigger:hover {
  background: var(--ta-shell-hover, #f3f4f6);
}

.figma-app-menu-trigger:focus-visible,
.figma-context-menu-trigger:focus-visible {
  outline: none;
  background: var(--ta-shell-accent-soft, #fdf2f2);
  box-shadow: inset 0 0 0 1px var(--ta-shell-accent, #c8161d);
}

.figma-app-menu-trigger.is-open,
.figma-app-menu-trigger:active,
.figma-context-menu-trigger.is-open,
.figma-context-menu-trigger:active {
  background: var(--ta-shell-accent-soft, #fdf2f2);
}

.figma-context-menu-key {
  color: var(--ta-shell-muted, #6b7280);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.figma-context-menu-value {
  color: var(--ta-shell-header-text, #000000);
  font-size: 11px;
  font-weight: 600;
}

.figma-app-menu-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 240px;
  margin: 0;
  padding: 6px;
  border: 1px solid var(--ta-shell-border, #e5e7eb);
  border-radius: 12px;
  background: var(--ta-shell-surface, #fff);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.10), 0 2px 8px rgba(15, 23, 42, 0.06);
  list-style: none;
  z-index: 40;
}

.figma-app-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  outline: none;
  transition: background-color 0.12s ease, color 0.12s ease;
}
```

同时完成以下适配：

```css
.figma-version-menu-wrapper .figma-context-menu-trigger:disabled {
  background: transparent;
  color: var(--ta-shell-muted, #6b7280);
  cursor: default;
  opacity: 0.72;
}

@media (max-width: 900px) {
  .figma-context-rail {
    padding-right: 2px;
    padding-left: 7px;
  }

  .figma-app-menu-trigger,
  .figma-context-menu-trigger {
    gap: 3px;
    padding-inline: 6px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .figma-context-rail,
  .figma-app-menu-trigger,
  .figma-context-menu-trigger,
  .figma-app-menu-chevron,
  .figma-app-menu-item {
    transition: none;
  }
}
```

- [ ] **Step 5: 运行定向测试并确认通过**

Run:

```bash
cd frontend && corepack pnpm test -- apps/agent-web/tests/FigmaShell.test.ts
```

Expected: `FigmaShell.test.ts` 全部 PASS；已有应用、工作空间、版本选择回调用例继续通过。

- [ ] **Step 6: 检查本任务 diff 并提交组件实现**

Run:

```bash
git diff --check -- frontend/apps/agent-web/src/components/FigmaShell.vue frontend/apps/agent-web/tests/FigmaShell.test.ts
git diff -- frontend/apps/agent-web/src/components/FigmaShell.vue frontend/apps/agent-web/tests/FigmaShell.test.ts
git add frontend/apps/agent-web/src/components/FigmaShell.vue frontend/apps/agent-web/tests/FigmaShell.test.ts
git commit -m "优化顶部上下文选择器与菜单样式"
```

Expected: 只提交上述两个文件，不包含工作区并行后端变更。

### Task 2: 同步稳定文档并完成前端验证

**Files:**
- Modify: `frontend/README.md:5-14`
- Modify: `frontend/apps/agent-web/README.md:13-20`
- Modify: `frontend/apps/agent-web/src/PACKAGE.md:50-52`

**Interfaces:**
- Consumes: Task 1 已实现的 `.figma-context-rail` 视觉和现有三个菜单行为。
- Produces: 稳定文档中一致的“精密上下文舱”说明；无代码接口输出。

- [ ] **Step 1: 更新三份稳定文档中的顶栏说明**

三处文档使用以下一致事实，不改写其它功能段落：

```markdown
应用、工作空间、版本三个入口合并为 34px 高的连续“上下文舱”：共享纯白表面、11px 圆角、发丝边框与轻阴影，内部分隔线表达三层工作上下文，左侧 3px 工行红信号条提供品牌定位。各分段保留原宽度、菜单、加载和切换回调；悬停只提升当前分段，键盘焦点与展开态使用柔红背景和工行红反馈。三个下拉菜单统一为 12px 圆角、双层轻阴影、8px 项目圆角和柔红选中态，不改变菜单内容、分组、禁用语义或定位逻辑。
```

- [ ] **Step 2: 运行类型检查与生产构建**

Run:

```bash
cd frontend
corepack pnpm --filter @test-agent/agent-web typecheck
corepack pnpm --filter @test-agent/agent-web build
```

Expected: 两条命令退出码均为 0；构建不新增依赖或浏览器兼容错误。

- [ ] **Step 3: 运行完整前端单元测试**

Run:

```bash
cd frontend && corepack pnpm test
```

Expected: 全量 Vitest 通过；已有 skipped 用例可保留，但不得出现新增失败。

- [ ] **Step 4: 执行桌面和窄屏视觉验收**

使用当前本地工作台真实页面检查 2048px 桌面宽度和不大于 900px 的窄屏宽度，逐项确认：

```text
桌面：轨道位于 Logo 与右侧工具组之间，三个分段不重叠，长值省略，菜单不越出视口。
窄屏：现有 92px / 112px / 86px 宽度生效，Logo 和右侧工具组仍可见。
状态：应用、工作空间、版本分别打开时，只有当前分段柔红，整体轨道红色弱描边。
键盘：Tab 可依次聚焦三个 button，焦点环清晰；Enter/Space 继续打开菜单。
禁用：应用源码模式的“源码快照”不可点击且仍可辨识。
动效：系统 reduced-motion 开启后不再播放箭头和背景过渡。
```

若本地服务不可用，先记录阻塞和日志路径，不修改 `.env.local`；代码级验收仍以定向测试、typecheck 和 build 为最低完成门槛。

- [ ] **Step 5: 检查文档和任务范围**

Run:

```bash
git diff --check -- frontend/README.md frontend/apps/agent-web/README.md frontend/apps/agent-web/src/PACKAGE.md
git diff --stat
git status --short
```

Expected: 本任务只出现三份文档变更；工作区已有后端文件保持原状态且未被暂存。

### Task 3: 会话留痕、最终复核与提交

**Files:**
- Modify: `.agents/session-log.huangzhenren.md`
- Verify: `frontend/apps/agent-web/src/components/FigmaShell.vue`
- Verify: `frontend/apps/agent-web/tests/FigmaShell.test.ts`
- Verify: `frontend/README.md`
- Verify: `frontend/apps/agent-web/README.md`
- Verify: `frontend/apps/agent-web/src/PACKAGE.md`

**Interfaces:**
- Consumes: Task 1 的代码与测试、Task 2 的稳定文档和所有验证结果。
- Produces: 本机提交者会话日志、最终文档提交和可追溯验证摘要。

- [ ] **Step 1: 再次回顾全部会话日志近期条目**

Run:

```bash
rg --files .agents | rg 'session-log.*\.md$' | sort
tail -n 120 .agents/session-log.md
tail -n 120 .agents/session-log.guojq.md
tail -n 120 .agents/session-log.huangzhenren.md
tail -n 120 .agents/session-log.rkk222.md
```

Expected: 没有发现本任务会覆盖、丢弃或误合并的他人成果；若出现新冲突或合并标记，停止提交并先处理风险。

- [ ] **Step 2: 追加本机会话日志**

在 `.agents/session-log.huangzhenren.md` 末尾追加以下结构。`Result` 中直接复制本轮定向测试、完整测试、typecheck 和 build 输出的摘要行；不得预填或估算数量：

```markdown
### 2026-08-07 - 优化顶部应用、工作空间与版本上下文轨道

- Why:
  - 顶部三个独立输入框式选择器没有表达应用、工作空间和版本共同构成当前工作上下文，下拉菜单视觉层级也不统一。
- What:
  - 将三个入口合并为连续的精密上下文舱，增加内部发丝分隔、工行红信号条和统一的悬停、焦点、展开、禁用状态；同步优化三个下拉菜单的圆角、阴影、间距与选中态。
  - 保留原菜单内容、默认选择、响应式宽度、可访问名称和全部切换回调；同步前端稳定文档与测试。
- How:
  - 按 TDD 先增加统一轨道和关键视觉契约测试，再实现最小模板与 scoped CSS；执行 FigmaShell 定向测试、agent-web typecheck、生产构建、完整前端测试和桌面/窄屏视觉验收。
- Result:
  - 粘贴各验证命令的真实摘要，并记录桌面/窄屏验收是否通过；明确说明未修改 API、事件、数据库、性能、安全、环境配置、generated SDK 或 OpenCode 源码。
```

- [ ] **Step 3: 执行最终完整性检查**

Run:

```bash
git diff --check
git status --short
git diff --name-only HEAD
git diff --cached --name-only
```

Expected: 没有空白错误或合并标记；只暂存本任务文档与会话日志，不暂存并行后端变更。

- [ ] **Step 4: 提交稳定文档与会话日志**

Run:

```bash
git add frontend/README.md frontend/apps/agent-web/README.md frontend/apps/agent-web/src/PACKAGE.md .agents/session-log.huangzhenren.md
git diff --cached --check
git diff --cached --name-only
git commit -m "同步顶部上下文轨道文档与验证记录"
```

Expected: 提交仅包含三份前端文档和 `.agents/session-log.huangzhenren.md`；工作区其它并行文件不进入提交。

- [ ] **Step 5: 确认最终提交和剩余工作区状态**

Run:

```bash
git log -3 --oneline
git status --short
```

Expected: 最近提交包含设计规范、组件实现、稳定文档与验证记录；剩余状态只包含任务开始前或并行产生的后端变更，并在最终回复中明确说明未触碰。

# Mermaid 真菱形与多端口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将判断节点改为宽高不等的水平真菱形，并让每个节点按图方向提供 3 个入口和 3 个出口，连线按稳定顺序自动均匀分配。

**Architecture:** 新增一个无状态端口布局模块，集中定义 Handle ID、图方向到入口/出口边的映射以及端口偏移。节点组件只按布局结果渲染六个 Handle；Vue Flow 适配层只负责给每条投影边分配端口，领域模型和 Mermaid 文本继续只保存起点与终点。

**Tech Stack:** Vue 3、TypeScript、Vue Flow、Vitest、Testing Library、CSS `clip-path`

## Global Constraints

- 每个节点固定提供 3 个入口和 3 个出口，端口 ID 为 `target-0..2` 与 `source-0..2`。
- 三个端口沿所在边按 25%、50%、75% 均匀分布。
- TD/TB、BT、LR、RL 四种方向分别把端口放在入口侧和出口侧；未知方向回退到 TD。
- 第 N 条同源出边使用 `source-(N % 3)`，第 N 条同目标入边使用 `target-(N % 3)`。
- 端口信息不得写入 Mermaid 领域模型、parser、serializer 或 Markdown 文本。
- 不修改后端 API、事件、数据库、全局状态或 Sequence Diagram 编辑器。

---

### Task 1: 端口布局与边分配

**Files:**
- Create: `frontend/packages/editor/src/mermaid/visual-editor/node-ports.ts`
- Modify: `frontend/packages/editor/src/mermaid/visual-editor/vue-flow-adapter.ts`
- Test: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`

**Interfaces:**
- Produces: `MERMAID_NODE_PORT_COUNT = 3`
- Produces: `getMermaidNodePortLayout(direction): { target: Position; source: Position; offsets: readonly number[] }`
- Produces: `getMermaidNodePortId(type: "target" | "source", edgeIndex: number): string`
- Consumes: `MermaidGraph["direction"]`、Vue Flow `Position`

- [ ] **Step 1: 写多边端口分配的失败测试**

在 `MermaidVisualEditor.test.ts` 中构造同源、同目标的四条边，断言 `toVueFlowEdges()` 依次输出 `source-0..2, source-0` 和 `target-0..2, target-0`：

```ts
expect(toVueFlowEdges(multiEdgeGraph).map((edge) => edge.sourceHandle))
  .toEqual(["source-0", "source-1", "source-2", "source-0"]);
expect(toVueFlowEdges(multiEdgeGraph).map((edge) => edge.targetHandle))
  .toEqual(["target-0", "target-1", "target-2", "target-0"]);
```

方向和 25%、50%、75% 偏移由 Task 2 的真实节点渲染测试覆盖，避免因导入尚不存在的新模块而只得到加载错误。

- [ ] **Step 2: 运行测试并确认失败原因是端口模块与 Handle 字段尚不存在**

Run from `frontend`: `corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts --maxWorkers=1 --no-file-parallelism`

Expected: FAIL，实际值缺少 `sourceHandle` / `targetHandle`。

- [ ] **Step 3: 实现最小端口纯函数**

创建 `node-ports.ts`，用显式方向表返回入口边、出口边和固定偏移：

```ts
export const MERMAID_NODE_PORT_COUNT = 3;
const PORT_OFFSETS = [25, 50, 75] as const;

export function getMermaidNodePortId(type: "target" | "source", edgeIndex: number): string {
  return `${type}-${edgeIndex % MERMAID_NODE_PORT_COUNT}`;
}
```

`getMermaidNodePortLayout()` 对 LR、RL、BT 单独映射，其余值回退到顶部入口、底部出口。

- [ ] **Step 4: 在 Vue Flow 边投影中按节点分别计数**

在 `toVueFlowEdges()` 的 `map` 前创建 `sourceCounts`、`targetCounts`，每条边读取并递增对应节点计数，再写入：

```ts
sourceHandle: getMermaidNodePortId("source", sourceIndex),
targetHandle: getMermaidNodePortId("target", targetIndex),
```

不修改 `appendMermaidEdge()`，确保端口仍不进入领域模型。

- [ ] **Step 5: 运行定向测试并确认通过**

Run from `frontend`: `corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts --maxWorkers=1 --no-file-parallelism`

Expected: PASS。

### Task 2: 节点六端口渲染

**Files:**
- Modify: `frontend/packages/editor/src/mermaid/visual-editor/MermaidFlowNode.vue`
- Test: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`

**Interfaces:**
- Consumes: `getMermaidNodePortLayout()`、`getMermaidNodePortId()`、`MERMAID_NODE_PORT_COUNT`
- Produces: 每个 `MermaidFlowNode` 的 3 个 target Handle 和 3 个 source Handle

- [ ] **Step 1: 让 Handle mock 暴露类型、ID、位置和内联样式并写失败测试**

把测试中的 Handle mock 改为接收 `id/type/position/style` props，并输出 `data-handle-id`、`data-handle-type`、`data-position`。直接渲染 `MermaidFlowNode`，断言 6 个 Handle、稳定 ID，以及 LR 方向三个入口的 `top` 为 `25%/50%/75%`。

- [ ] **Step 2: 运行测试并确认当前单入口、单出口实现失败**

Run from `frontend`: `corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts --maxWorkers=1 --no-file-parallelism`

Expected: FAIL，Handle 数量为 2 而不是 6。

- [ ] **Step 3: 按纯函数布局渲染六个 Handle**

在 `MermaidFlowNode.vue` 中删除 `vertical/targetPosition/sourcePosition`，生成三个索引并按 Position 计算样式：顶部或底部使用 `left: "<offset>%"`，左侧或右侧使用 `top: "<offset>%"`。模板分别 `v-for` 渲染 target 和 source：

```vue
<Handle
  v-for="port in targetPorts"
  :id="port.id"
  :key="port.id"
  type="target"
  :position="port.position"
  :style="port.style"
/>
```

source 使用相同结构和 `source-*` ID。为端口样式添加中文注释，说明位置由图方向和均匀偏移共同决定。

- [ ] **Step 4: 运行定向测试和 editor 类型检查**

Run from `frontend`: `corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts --maxWorkers=1 --no-file-parallelism`

Expected: PASS。

Run: `corepack pnpm --filter @test-agent/editor typecheck`

Expected: exit 0。

### Task 3: 真菱形与图形库预览

**Files:**
- Modify: `frontend/packages/editor/src/mermaid/visual-editor/MermaidFlowNode.vue`
- Modify: `frontend/packages/editor/src/mermaid/visual-editor/MermaidVisualEditor.vue`
- Test: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`

**Interfaces:**
- Consumes: 现有 `.is-diamond` 节点类型 class
- Produces: 画布与图形库中一致的水平真菱形轮廓

- [ ] **Step 1: 写真菱形静态回归测试**

读取两个 Vue 文件源码，断言判断节点不再出现 `rotate(45deg)` / `rotate(-45deg)`，并包含水平菱形 `polygon(50% 0, 100% 50%, 50% 100%, 0 50%)` 与宽度大于高度的尺寸声明。

- [ ] **Step 2: 运行测试并确认旧旋转正方形导致失败**

Run from `frontend`: `corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts --maxWorkers=1 --no-file-parallelism`

Expected: FAIL，源码仍包含 `rotate(45deg)`。

- [ ] **Step 3: 改造画布判断节点样式**

保持节点根元素水平，使用 `::before` 和 `::after` 两层相同 `clip-path` 多边形分别绘制边框和内层背景；判断节点设置约 150px × 88px 的水平比例，内容提高 `z-index` 并限制在安全内区。选中态改变外层多边形颜色，不再旋转文字。

- [ ] **Step 4: 同步图形库判断预览**

将 `.ta-mermaid-palette__shape.is-diamond` 改为约 72px × 38px，使用两层多边形绘制边框和背景，删除旋转。保持卡片尺寸与其他节点类型布局稳定。

- [ ] **Step 5: 运行定向测试并确认通过**

Run from `frontend`: `corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts --maxWorkers=1 --no-file-parallelism`

Expected: PASS。

### Task 4: 文档、全量验证与浏览器验收

**Files:**
- Modify: `frontend/packages/editor/README.md`
- Modify: `frontend/packages/editor/src/PACKAGE.md`
- Modify if knowledge is worth retaining: `.agents/session-log.md`

**Interfaces:**
- Consumes: 已通过的真菱形与多端口实现
- Produces: 稳定工程说明、验证记录和最终提交

- [ ] **Step 1: 同步稳定说明文档**

在 editor README 和包说明中补充：判断节点为水平真菱形；每个节点按图方向显示 3 入 3 出；可视化层按边顺序分配端口且不改变 Mermaid Markdown。不得修改 agent-web 的并发 README/PACKAGE 改动。

- [ ] **Step 2: 运行完整校验**

Run: `corepack pnpm lint`

Expected: exit 0。

Run: `corepack pnpm typecheck`

Expected: exit 0。

Run: `corepack pnpm exec vitest run --maxWorkers=1 --no-file-parallelism`

Expected: 全部测试通过，允许仓库已标记的 skip。

Run: `corepack pnpm build`

Expected: exit 0；允许既有 Google Fonts CSS `@import` 顺序和大 chunk 警告。

Run: `git diff --check`

Expected: exit 0。

- [ ] **Step 3: 在本地页面完成视觉和交互验收**

打开 `http://127.0.0.1:3000/` 的 Mermaid 可视化编辑器，确认图形库和画布判断节点为水平菱形；节点显示 3 个入口和 3 个出口；切换 TD、BT、LR、RL 时端口跟随入口侧/出口侧；多条边视觉上分散到三个端口。记录浏览器控制能力无法覆盖的交互限制，但不得把未验证项描述为已通过。

- [ ] **Step 4: 更新会话记录并审查暂存范围**

若真菱形 CSS 或端口投影规则对后续开发有复用价值，在 `.agents/session-log.md` 合并写一条 `Why / What / How / Result`。提交前重新阅读近期日志，确认未暂存 `frontend/apps/agent-web/**` 等其他开发者改动。

- [ ] **Step 5: 提交本次实现**

只暂存 editor 实现、测试、editor 文档和本次会话记录，执行：

```bash
git commit -m "前端：支持 Mermaid 真菱形与多端口"
```

Expected: commit 成功，工作区仍只保留实施前已存在的无关改动。

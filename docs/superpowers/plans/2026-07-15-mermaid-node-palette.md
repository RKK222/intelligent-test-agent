# Mermaid 节点图形库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Flowchart/Graph Mermaid 可视化编辑器改为可拖放的节点图形库，并在图形库下方保留选中节点的属性与删除操作。

**Architecture:** 继续由 `MermaidVisualEditor.vue` 持有草稿交互状态；节点图形库只产生 `MermaidNodeType`，通过 Vue Flow 实例的 `screenToFlowCoordinate` 把拖放或画布中心坐标转换为领域节点位置，再沿既有 `cloneMermaidGraph` 与 `update:modelValue` 链路更新。连线只保留 Vue Flow Handle 创建入口，parser、serializer 与领域模型不变。

**Tech Stack:** Vue 3.5、TypeScript 6、Vue Flow 1.48.2、Vitest、Testing Library Vue、scoped CSS。

## Global Constraints

- 只修改 `frontend/packages/editor` 的 Flowchart/Graph 编辑器、测试和稳定说明，不改 Sequence Diagram。
- 不新增依赖、后端 API、事件、数据库、全局 store 或 Mermaid 领域字段。
- 顶部删除“新增节点”“新增连线”，右栏删除连线列表；Handle 连线必须保持可用。
- 图形库固定展示矩形、圆角、胶囊、判断、圆形，并支持拖放与键盘/点击创建。
- 选中节点后在图形库下方显示 ID、名称、类型和删除操作。
- 不修改工作区中与本任务无关的 `FigmaChatPanel` 变更，不新建 git 分支。

---

### Task 1: 用组件契约锁定新布局与创建交互

**Files:**
- Modify: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`
- Test: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`

**Interfaces:**
- Consumes: `MermaidVisualEditor` 的 `modelValue: MermaidGraph` 和 `update:modelValue`。
- Produces: 图形库按钮可访问名称 `添加矩形节点`、`添加圆角节点`、`添加胶囊节点`、`添加判断节点`、`添加圆形节点`；Vue Flow 测试替身暴露 `screenToFlowCoordinate(position)`。

- [ ] **Step 1: 扩展 Vue Flow 测试替身并写布局失败测试**

在 mock 的 `setup(_, { expose })` 中暴露确定性坐标转换：

```ts
setup(_, { expose }) {
  expose({
    screenToFlowCoordinate: ({ x, y }: { x: number; y: number }) => ({ x: x - 100, y: y - 50 })
  });
}
```

新增测试，断言五个图形库按钮存在，`新增节点`、`新增连线` 和 `连线` 标题不存在；选中节点后属性控件才显示。

- [ ] **Step 2: 运行测试并确认按预期失败**

Run: `cd frontend && corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts`

Expected: FAIL，失败原因是图形库按钮尚不存在或旧按钮、连线区域仍存在，而不是测试环境错误。

- [ ] **Step 3: 写点击与拖放创建失败测试**

点击 `添加判断节点` 后断言最后一次更新包含：

```ts
expect(updates.at(-1)?.[0].nodes.at(-1)).toMatchObject({
  id: "N3",
  text: "新节点",
  type: "diamond"
});
```

向 `Mermaid 可视化画布` 触发带 `clientX: 420`、`clientY: 260` 和 `dataTransfer.getData()` 返回 `circle` 的 drop，断言新节点为圆形且位置为 `{ x: 320, y: 210 }`。

- [ ] **Step 4: 运行测试并确认新交互失败**

Run: `cd frontend && corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts`

Expected: FAIL，失败原因是点击/拖放尚未创建对应类型节点。

### Task 2: 实现节点图形库、拖放和上下文属性区

**Files:**
- Modify: `frontend/packages/editor/src/mermaid/visual-editor/MermaidVisualEditor.vue`
- Test: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`

**Interfaces:**
- Consumes: `MermaidNodeType`、Vue Flow `screenToFlowCoordinate({ x, y })`、既有 `updateGraph`。
- Produces: `createNode(type: MermaidNodeType, position?: MermaidPosition): void`、`onPaletteDragStart(event: DragEvent, type: MermaidNodeType): void`、`onCanvasDrop(event: DragEvent): void`。

- [ ] **Step 1: 增加节点类型元数据与统一创建函数**

定义只读图形库数据：

```ts
const nodeTypes: ReadonlyArray<{ type: MermaidNodeType; label: string }> = [
  { type: "rectangle", label: "矩形" },
  { type: "rounded", label: "圆角" },
  { type: "stadium", label: "胶囊" },
  { type: "diamond", label: "判断" },
  { type: "circle", label: "圆形" }
];
```

将旧 `addNode()` 改为 `createNode(type, position)`：分配唯一 `N<number>`，写入传入位置或画布可见中心位置，并把新 ID 写入 `selectedNodeId`。

- [ ] **Step 2: 接入原生拖放与 Vue Flow 坐标转换**

为 Vue Flow 和画布容器增加模板 ref。`dragstart` 把节点类型写入固定 MIME key；`dragover` 调用 `preventDefault()`；`drop` 读取类型、校验属于五种支持类型，再执行：

```ts
const position = vueFlowRef.value?.screenToFlowCoordinate({
  x: event.clientX,
  y: event.clientY
});
if (position) createNode(type, position);
```

非法或缺失拖放数据直接忽略，不产生草稿更新。

- [ ] **Step 3: 替换模板布局**

删除顶部两个新增按钮和整个连线 section。右栏第一段渲染五个 `draggable="true"` 的图形按钮；第二段仅负责当前节点属性。按钮 `aria-label` 使用 `添加${label}节点`，节点预览元素通过 `is-${type}` class 绘制真实轮廓。

- [ ] **Step 4: 实现紧凑视觉与状态反馈**

图形库使用两列网格；卡片保持白底、细边框与 `--ta-*` token，拖拽/hover/focus 使用现有主色。判断节点预览旋转 45 度而文字反向旋转，圆形保持等宽高。画布拖放目标状态用主色内描边提示，窄屏继续沿用右栏下置滚动布局。

- [ ] **Step 5: 运行定向测试并确认通过**

Run: `cd frontend && corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts`

Expected: PASS，现有拖拽坐标回写、Handle 连线、属性编辑和删除关联边测试也全部通过。

- [ ] **Step 6: 重构测试夹具以验证选中新节点属性区**

使用持有 `ref(graph())` 的轻量宿主通过 `v-model` 渲染组件，点击图形按钮后断言 `节点 ID` 为 `N3`，再修改名称和类型，确认草稿同步。只抽取测试内重复装配，不拆分生产组件。

- [ ] **Step 7: 再次运行定向测试**

Run: `cd frontend && corepack pnpm exec vitest run packages/editor/tests/MermaidVisualEditor.test.ts`

Expected: PASS，且无 Vue warning 或未处理异常。

### Task 3: 同步稳定文档并完成风险相称的验证

**Files:**
- Modify: `frontend/packages/editor/README.md`
- Modify: `frontend/packages/editor/src/PACKAGE.md`
- Modify conditionally: `.agents/session-log.md`（仅在本次产生可复用的新结论时）

**Interfaces:**
- Consumes: 已通过的 Mermaid 编辑器交互契约。
- Produces: 与实际入口一致的稳定说明和可复验命令记录。

- [ ] **Step 1: 更新 editor 稳定说明**

把 Flowchart 能力描述改为：节点类型通过右侧图形库拖放或点击创建；选中节点后在图形库下方编辑属性；连线只通过 Handle 创建。明确 parser、serializer 与保存链路未变化。

- [ ] **Step 2: 运行静态检查和前端测试**

Run:

```bash
cd frontend
corepack pnpm --filter @test-agent/editor typecheck
corepack pnpm exec vitest run --maxWorkers=1 --no-file-parallelism
corepack pnpm build
```

Expected: exit 0；若出现与本任务无关的既有失败，保留完整证据并再次运行 editor 定向测试。

- [ ] **Step 3: 运行差异校验**

Run: `git diff --check && git status --short && git diff -- frontend/packages/editor docs/superpowers/plans/2026-07-15-mermaid-node-palette.md`

Expected: 无空白错误；差异只包含本任务文件，既有 `FigmaChatPanel` 修改保持未暂存且内容未变化。

- [ ] **Step 4: 在本地浏览器验收**

打开 `http://127.0.0.1:3000/` 的 Mermaid Flowchart 可视化编辑器，验证五种轮廓、拖放落点、点击创建、选中属性、Handle 连线、桌面与窄屏布局；检查控制台无新增错误。若当前 dev server 未加载最新代码，按项目 `restart-services` 技能重启后再验收。

- [ ] **Step 5: 按需更新会话日志**

若拖放坐标、测试限制或浏览器验收产生值得后续保留的新结论，在 `.agents/session-log.md` 增加一条合并记录，使用 `Why / What / How / Result`；否则明确说明无需更新。

- [ ] **Step 6: 回顾会话日志并提交本任务文件**

再次完整阅读 `.agents/session-log.md`，确认没有覆盖他人成果；只暂存 editor 代码、测试、稳定文档、实施计划和按需更新的 session log，执行中文提交：

```bash
git commit -m "前端：改造 Mermaid 可拖拽节点图形库"
```

Expected: commit 成功，工作区中用户已有的 `FigmaChatPanel` 修改仍保持未提交。

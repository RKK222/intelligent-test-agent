# Mermaid 可视化编辑器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Markdown 预览中依次完成 flowchart/graph 和 sequenceDiagram 可视化编辑，并把稳定、可验证的 Mermaid DSL 回写到现有保存链路。

**Architecture:** `editor/src/mermaid` 提供无 Vue 依赖的模型、parser、serializer、metadata、Markdown fence 替换和自动布局；懒加载的 Vue 对话框用 `@vue-flow/core` 映射并编辑模型。`MarkdownPreview` 负责当前 block 定位与流程编排，`CodeEditor` 继续只向上游发送完整 Markdown change。

**Tech Stack:** Vue 3、TypeScript strict、Vitest、Testing Library、Mermaid 11、`@vue-flow/core` 1.48.2。

## Global Constraints

- Markdown 是唯一事实源，不新增后端 API、数据库、文件格式或 Pinia store。
- 支持 `flowchart`、`graph` 与 `sequenceDiagram`，未知 Mermaid 语句必须原样保留。
- 对话框和 Mermaid 官方 parser 必须按点击入口懒加载。
- Vue Flow 仅负责画布交互，第三方 nodes/edges 类型不得进入 Mermaid parser/serializer。
- 不改动现有 Monaco、dirty、Ctrl/Cmd+S、Git Diff 和外部刷新链路。
- 所有新增复杂逻辑使用中文注释，不新增 `any`。

---

### Task 1: Mermaid 领域模型与双向转换

**Files:**
- Create: `frontend/packages/editor/src/mermaid/model.ts`
- Create: `frontend/packages/editor/src/mermaid/metadata.ts`
- Create: `frontend/packages/editor/src/mermaid/parser.ts`
- Create: `frontend/packages/editor/src/mermaid/serializer.ts`
- Create: `frontend/packages/editor/src/mermaid/markdown-blocks.ts`
- Create: `frontend/packages/editor/src/mermaid/layout.ts`
- Modify: `frontend/packages/editor/package.json`
- Modify: `frontend/pnpm-lock.yaml`
- Test: `frontend/packages/editor/tests/mermaid-domain.test.ts`

**Interfaces:**
- Produces: `parseMermaidFlowchart(source): MermaidGraph`、`serializeMermaidGraph(graph): string`、`findMermaidBlocks(markdown): MermaidBlock[]`、`replaceMermaidBlock(markdown, index, source, expectedSource?): string`、`autoLayoutMermaidGraph(graph): MermaidGraph`。

- [ ] 写 parser/serializer/metadata/round-trip/fence 替换/布局失败测试，断言缺少实现。
- [ ] 运行 `corepack pnpm exec vitest run packages/editor/tests/mermaid-domain.test.ts`，确认因模块不存在而失败。
- [ ] 实现最小强类型领域层：稳定节点/边输出、未知行保留、metadata JSON 解析与写回、当前 fence 精确替换。
- [ ] 重跑定向测试并确认全部通过。

### Task 2: 可视化画布与编辑对话框

**Files:**
- Create: `frontend/packages/editor/src/mermaid/visual-editor/vue-flow-adapter.ts`
- Create: `frontend/packages/editor/src/mermaid/visual-editor/MermaidFlowNode.vue`
- Create: `frontend/packages/editor/src/mermaid/visual-editor/MermaidVisualEditor.vue`
- Create: `frontend/packages/editor/src/mermaid/visual-editor/MermaidEditorDialog.vue`
- Test: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`

**Interfaces:**
- Consumes: `MermaidGraph`、`autoLayoutMermaidGraph`。
- Produces: `toVueFlowNodes/Edges` 与 `applyVueFlowPositions` 适配接口；`MermaidEditorDialog` 的 `apply(graph)` 与 `cancel()`；`MermaidVisualEditor` 的 `update:modelValue(graph)`。

- [ ] 写 Vue Flow 映射、节点拖拽位置同步、连接创建、名称/形状修改、节点增删、边删除/交换方向/标签修改和自动布局组件测试。
- [ ] 运行组件定向测试，确认因组件不存在而失败。
- [ ] 安装 `@vue-flow/core@1.48.2`，实现受控 nodes/edges、Handle 连接、拖拽同步、工具栏和右侧属性面板，并导入官方基础样式。
- [ ] 重跑组件测试，保持领域测试同时通过。

### Task 3: Markdown 预览与现有保存链路接入

**Files:**
- Modify: `frontend/packages/editor/src/MarkdownPreview.vue`
- Modify: `frontend/packages/editor/src/CodeEditor.vue`
- Modify: `frontend/packages/editor/tests/MarkdownPreview.test.ts`
- Modify: `frontend/packages/editor/tests/CodeEditor.preview.test.ts`

**Interfaces:**
- `MarkdownPreview` 新增 `change(content: string)` 事件；`CodeEditor` 原样转发到既有 `change` 事件。
- Mermaid header 新增 `data-mermaid-mode="visual"`，点击时动态加载对话框并只解析当前 block。

- [ ] 写打开可视化编辑、语法错误、应用回写、外部刷新冲突与 CodeEditor change 转发测试。
- [ ] 运行两个定向测试文件，确认新行为测试失败。
- [ ] 接入官方 `mermaid.parse` 前后校验、异步对话框、目标 block 替换和冲突保护。
- [ ] 重跑 editor 全部测试并确认原有脚本/图表切换不回归。

### Task 4: Sequence diagram 领域层与 Vue Flow 编辑器

**Files:**
- Create: `frontend/packages/editor/src/mermaid/diagram.ts`
- Create: `frontend/packages/editor/src/mermaid/sequence/model.ts`
- Create: `frontend/packages/editor/src/mermaid/sequence/parser.ts`
- Create: `frontend/packages/editor/src/mermaid/sequence/serializer.ts`
- Create: `frontend/packages/editor/src/mermaid/sequence/layout.ts`
- Create: `frontend/packages/editor/src/mermaid/sequence/visual-editor/vue-flow-adapter.ts`
- Create: `frontend/packages/editor/src/mermaid/sequence/visual-editor/SequenceParticipantNode.vue`
- Create: `frontend/packages/editor/src/mermaid/sequence/visual-editor/SequenceMessageEdge.vue`
- Create: `frontend/packages/editor/src/mermaid/sequence/visual-editor/SequenceVisualEditor.vue`
- Modify: `frontend/packages/editor/src/mermaid/visual-editor/MermaidEditorDialog.vue`
- Modify: `frontend/packages/editor/src/MarkdownPreview.vue`
- Test: `frontend/packages/editor/tests/mermaid-sequence-domain.test.ts`
- Test: `frontend/packages/editor/tests/SequenceVisualEditor.test.ts`
- Modify: `frontend/packages/editor/tests/MarkdownPreview.test.ts`

**Interfaces:**
- Produces: `MermaidSequenceDiagram`、`parseMermaidSequence`、`serializeMermaidSequence`、`parseMermaidDiagram`、`serializeMermaidDiagram`；对话框按 `kind` 分发到两类编辑器。

- [ ] 写 participant/actor、隐式参与者、四种常见消息箭头、有序 round trip、unknown line 和 layout metadata 红灯测试。
- [ ] 实现 Sequence 领域层并用 Mermaid 官方 parser 验证序列化输出。
- [ ] 写参与者拖拽、Handle 新增消息、参与者编辑、消息上移/下移/方向/标签/箭头类型红灯测试。
- [ ] 实现 Vue Flow participant/lifeline 与有序自定义 message edge，并接入通用对话框。
- [ ] 增加 Markdown Sequence 打开、编辑、应用回写测试，并重跑 Flowchart 全部回归测试。

### Task 5: 文档、自检与提交

**Files:**
- Modify: `frontend/packages/editor/README.md`
- Modify: `frontend/packages/editor/src/PACKAGE.md`
- Modify: `.agents/session-log.md`

**Interfaces:**
- 文档记录领域边界、懒加载、保存链路、metadata 与当前语法范围。

- [ ] 更新 editor README/PACKAGE 和会话级 Why/What/How/Result 记录。
- [ ] 执行 `corepack pnpm test`、`corepack pnpm typecheck`、`corepack pnpm lint`、`corepack pnpm build` 和 `git diff --check`。
- [ ] 对照设计逐项自检 API、事件、数据库、性能、安全、兼容性影响。
- [ ] 回顾 session log 与工作区既有改动，只提交本任务文件，中文 commit message 使用 `feat: 增加 Mermaid 可视化编辑器`。

# Mermaid draw.io 式连线交互 Implementation Plan

> **For agentic workers:** 使用 superpowers:executing-plans 与 superpowers:test-driven-development，逐项执行并在每个行为实现前先观察对应测试失败。

**Goal:** 实现六点悬浮、近点吸附和固定端口保存的 draw.io 式 Mermaid 新建连线交互。

**Architecture:** 领域层保存可选固定端口，并用独立注释完成兼容序列化；适配层优先投影固定端口；纯几何模块负责屏幕距离判定；Vue composable 管理窗口级指针生命周期和临时路径；节点组件只负责端口测量入口与视觉状态。

**Tech Stack:** Vue 3、TypeScript、Vue Flow、Vitest、Testing Library、SVG

## 约束

- 连接点直径 14px；起线命中 18px；目标外框激活 24px；端口吸附 28px。
- 六点均可作为起点和终点；Vue Flow Loose 模式保留，原生 connect 关闭。
- 拒绝重复有向边和同节点同端口；允许同节点不同端口自环。
- 不实现自动平移、点击两次连接、端口编辑、连线改接和浮动连接。
- 不修改后端 API、事件、数据库或标准 Mermaid DSL。

### Task 1: 固定端口领域模型与元数据

**Files:**
- Modify: `frontend/packages/editor/src/mermaid/model.ts`
- Create: `frontend/packages/editor/src/mermaid/edge-port-metadata.ts`
- Modify: `frontend/packages/editor/src/mermaid/parser.ts`
- Modify: `frontend/packages/editor/src/mermaid/serializer.ts`
- Test: `frontend/packages/editor/tests/mermaid-domain.test.ts`

- [ ] 先写端口元数据往返、旧 Markdown、损坏注释保留、陈旧条目清理和删除边清理的失败测试。
- [ ] 运行领域定向测试并确认因字段和 metadata 尚不存在而失败。
- [ ] 给 `MermaidEdge` 增加两个可选 Handle 字段，实现独立注释提取与序列化。
- [ ] parser 在边解析完成后按唯一 `source + target` 合并元数据；serializer 只输出仍存在的完整合法固定端口。
- [ ] 重跑领域测试至通过。

### Task 2: 固定端口投影与连接约束

**Files:**
- Modify: `frontend/packages/editor/src/mermaid/visual-editor/vue-flow-adapter.ts`
- Test: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`

- [ ] 先写固定端口优先、旧边自动分配、六点通用连接、重复边和自环规则的失败测试。
- [ ] 运行定向测试并确认预期失败。
- [ ] `toVueFlowEdges()` 优先读取领域边端口；`appendMermaidEdge()` 保存完整端口并复用可测试的合法性判断。
- [ ] 重跑适配测试至通过。

### Task 3: 屏幕几何与拖线生命周期

**Files:**
- Create: `frontend/packages/editor/src/mermaid/visual-editor/mermaid-connection-geometry.ts`
- Create: `frontend/packages/editor/src/mermaid/visual-editor/use-mermaid-connection-drag.ts`
- Create: `frontend/packages/editor/tests/MermaidConnectionController.test.ts`

- [ ] 先写候选节点、重叠最上层、最近外框、最近端口、18/24/28px 边界和稳定平局的失败测试。
- [ ] 实现无 DOM 依赖的纯几何函数并让测试通过。
- [ ] 先写成功松开、空白松开、Escape、pointercancel、失焦、rAF 与监听器清理的失败测试。
- [ ] 实现控制器：窗口级监听、rAF 合并、DOM 测量、SmoothStep 路径和完整清理。
- [ ] 重跑控制器测试至通过。

### Task 4: 节点和画布交互集成

**Files:**
- Modify: `frontend/packages/editor/src/mermaid/visual-editor/MermaidFlowNode.vue`
- Modify: `frontend/packages/editor/src/mermaid/visual-editor/MermaidVisualEditor.vue`
- Test: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`

- [ ] 先写默认隐藏、悬浮六点、六点均可起线、仅当前目标显示、有效/无效状态、临时 SVG 和关闭原生 connect 的失败测试。
- [ ] 节点根元素测量 18px 起线命中并发出起线事件；六个 Handle 均以通用端口运行且关闭原生 connect。
- [ ] 画布接入控制器、状态 props 和 SVG overlay，删除原生 `@connect` 处理。
- [ ] 定向运行组件与 controller 测试并修正类型问题。

### Task 5: 文档、真实浏览器与全量验证

**Files:**
- Modify: `frontend/packages/editor/README.md`
- Modify: `frontend/packages/editor/src/PACKAGE.md`
- Modify: `.agents/session-log.md`（按需）

- [ ] 同步 editor README、包说明和稳定设计说明；记录固定端口 metadata 与交互边界。
- [ ] 运行 editor 定向测试和类型检查。
- [ ] 在 `http://127.0.0.1:3000/` 验收不同缩放下的起线、候选显示、吸附、成功、取消和重新打开端口保持。
- [ ] 运行前端全量 lint、typecheck、Vitest、生产构建及 `git diff --check`。
- [ ] 回顾 `.agents/session-log.md` 近期记录，更新本次结论，审查暂存范围并使用中文提交信息自动提交。

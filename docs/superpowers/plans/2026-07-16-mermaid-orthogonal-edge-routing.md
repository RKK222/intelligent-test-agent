# Mermaid 正交避障与紧凑元数据 Implementation Plan

**Goal:** 由 ELK 完整计算并保存 Flowchart 正交避障路由，同时把 Flow/Sequence 私有编辑信息压缩为单行 `%%@<base64url>`。

**Architecture:** `layout.ts` 消费 ELK node positions 与 edge sections，稳定映射现有 Handle；领域边持有可失效的派生 route，自定义边负责圆角 path 和 SmoothStep 回退。`compact-metadata.ts` 独立实现 Base64URL、LEB128、ZigZag、FNV-1a、拓扑校验和解码上限；Flow/Sequence parser 保留旧格式读取，serializer 只写新格式。

**Constraints:** 只修改 editor、对应文档和会话日志；不新增运行时依赖、API、事件、数据库、全局状态或环境配置；复杂逻辑使用中文注释；按 TDD 先观察失败再实现。

## Task 1：紧凑 codec 外部行为

**Files:**

- Create: `frontend/packages/editor/src/mermaid/compact-metadata.ts`
- Create: `frontend/packages/editor/tests/mermaid-compact-metadata.test.ts`
- Modify: Flow/Sequence parser 与 serializer

- [x] 先添加 Flow/Sequence 单行 marker、0.1px round trip、旧布局/端口迁移、无数据不输出、代表图小于展开 JSON 10% 的失败测试。
- [x] 手写无 padding Base64URL、unsigned LEB128、ZigZag 和 FNV-1a，不引入依赖。
- [x] 实现 `0xA1`、flags、entity/edge counts、坐标增量、端口 nibble、正交路由轴向增量及 little-endian checksum。
- [x] 使用规范化 Flow/Sequence 拓扑签名校验陈旧数据；边标签不进入签名。
- [x] Flow/Sequence serializer 只写 `%%@...`；没有非零坐标、端口或路由时不写 marker。

## Task 2：失败安全与旧格式兼容

**Files:**

- Modify: `frontend/packages/editor/src/mermaid/parser.ts`
- Modify: `frontend/packages/editor/src/mermaid/sequence/parser.ts`
- Retain read-only compatibility: `metadata.ts`、`edge-port-metadata.ts`

- [x] 唯一有效新 marker 优先，成功后消费有效旧数据；有效旧数据在无新 marker 时应用并在保存时迁移。
- [x] 损坏或重复新 marker 原样保留并回退旧格式；同时保留有效旧注释并禁止写第二个新 marker。
- [x] 损坏旧注释无论新格式是否有效都原样保留。
- [x] 解码限制 1 MiB、单边 4096 点、单个 LEB128 5 bytes、绝对坐标 10,000,000px；拒绝非法 Base64URL、非最短 LEB128、类型/数量/端口/hash/EOF/路由异常。
- [x] 固定 Flow 与 Sequence golden vector，并覆盖截断、hash、拓扑变化、重复 marker、非法类型/端口、超限和尾随数据。
- [x] 删除尚未发布的展开式 `edge-route-metadata.ts` 实现，不把它定义为兼容协议。

## Task 3：ELK 完整正交布局

**Files:**

- Modify: `frontend/packages/editor/src/mermaid/model.ts`
- Modify: `frontend/packages/editor/src/mermaid/layout.ts`
- Create: `frontend/packages/editor/tests/mermaid-edge-route.test.ts`

- [x] 为 `MermaidEdge` 增加深拷贝的 `route?: { points }`，提供统一清理函数。
- [x] ELK 使用普通 120–190×52、判断 150×88、圆形 92×92 的包围盒，并配置 `ORTHOGONAL`、layer sweep、直线偏好及 96/64/28/16 间距。
- [x] 消费 `sections/startPoint/bendPoints/endPoint`，节点与边统一加画布偏移并保留 0.1px。
- [x] 按端点所在边和沿边顺序稳定匹配 8/12 个 Handle；端口不足时按比例复用，端口与 section 之间插入正交接驳段。
- [x] 新一轮异步/同步布局先清旧 route；ELK 失败或缺 section 时安全回退普通边。
- [x] 测试四种方向、有限正交段、真实判断节点间距、避开非端点节点、代表图 proper crossing 为 0、输入模型不变和失败回退。

## Task 4：圆角渲染与失效规则

**Files:**

- Create: `frontend/packages/editor/src/mermaid/visual-editor/edge-path.ts`
- Modify: `vue-flow-adapter.ts`、`MermaidFlowEdge.vue`、`MermaidVisualEditor.vue`
- Modify: `frontend/packages/editor/tests/MermaidVisualEditor.test.ts`

- [x] adapter 深拷贝 route points 到边 data；合法正交点生成 6px `L/Q` path，标签按折线路程中点定位。
- [x] 实际 DOM 端点与 ELK 估算点有偏差时插入正交适配段；非法或缺失 route 调用 `getSmoothStepPath()`。
- [x] 节点拖动、改名/改形、方向切换、节点/边增删和重连清除 route；只修改边标签保留。
- [x] 覆盖 route data、圆角 path、DOM 偏差、路径中点、SmoothStep、模型不变及全部失效边界。

## Task 5：文档、验证和提交

**Files:**

- Modify: `frontend/packages/editor/README.md`
- Modify: `frontend/packages/editor/src/PACKAGE.md`
- Modify: 当前设计/实施计划
- Modify if worth retaining: `.agents/session-log.md`

- [x] README/PACKAGE 说明 ELK 正交避障、圆角渲染、紧凑协议、旧格式迁移、异常保留和路由失效。
- [x] 运行 editor/full Vitest、editor/repo typecheck、生产 build 和 `git diff --check`。
- [x] 按 `docs/guides/self-checklist.md` 复核范围、兼容、性能、安全和文档。
- [x] 回顾并按需更新 `.agents/session-log.md`，确认没有覆盖其他开发者成果。
- [x] 只暂存本任务文件，以中文 commit 自动提交。

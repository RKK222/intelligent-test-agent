# Mermaid State Diagram 可视化编辑能力实施计划

> **执行要求：** 使用测试驱动开发逐项推进；每个行为先补红灯测试，再实现最小代码并复跑相关回归。

**目标：** 为 Markdown Mermaid 编辑器增加支持常用 State Diagram 语义、递归层级、并发区域和直接画布交互的结构化编辑能力。

**架构：** State 子系统采用递归 Scope/Region 领域、纯 parser/serializer/validator、层级 ELK 布局和专用 Vue Flow 场景；外层 Mermaid 对话框、官方 parser 校验、Markdown fence 替换和保存链保持不变。

**技术栈：** Vue 3、TypeScript 6、Vue Flow 1.48.2、ELK 0.9.3、Mermaid 11.16.0、Vitest、Testing Library、Playwright。

## 全局约束

- Markdown 是唯一事实源，不新增后端 API、数据库、全局 store、依赖或文件格式。
- 不依赖 Mermaid 私有 StateDB；未知结构语法不能安全转换时整图降级源码模式。
- 人工维护的复杂逻辑补充中文注释。
- 不修改 Flowchart、Sequence Diagram 行为、公共 editor 导出或现有保存链。
- 保持 Chrome 108 兼容，不依赖新浏览器 API。

## Task 1：领域模型、双向转换与校验

- 新建 State 树形领域，覆盖普通/复合状态、开始/结束、Choice、Fork/Join、转换、Note、嵌套 Scope 与并发 Region。
- 以 tokenizer/容器栈解析两种头部、状态说明、转换标签、方向、样式和原样指令；危险未知语法安全降级。
- serializer 输出规范化源码，保留原头部与非结构指令，并通过官方 Mermaid parser round-trip。
- 校验全局 ID、区域引用、伪状态基数、自循环、Note 目标和并发区域；测试全部支持语法和错误原因。

## Task 2：紧凑元数据与层级布局

- 增加 State 专用元数据版本，保存局部坐标、转换端口和层级拓扑指纹；损坏或冲突时保留源码并自动布局。
- 递归布局子 Scope，分别布局并发 Region 后按当前方向打包，计算复合摘要与伪状态尺寸。
- 测试四种方向、深层嵌套、并发区域、元数据 round-trip、拓扑失配和损坏标记。

## Task 3：概览/聚焦可视化编辑器

- 实现 State 专用节点、转换、Note 与 Region 场景；复用对话框、视口、行内编辑、颜色控件和领域化连接拖拽内核。
- 实现分组元素面板、点击/拖放创建、节点拖动、并行转换、自循环、端点重连、状态说明、Note、方向与限定样式。
- 实现复合状态双击聚焦、面包屑返回和全部并发 Region 同屏展示；非法连接立即拒绝，应用时完整校验。
- 测试键盘/焦点、选择联动、窄屏、层级导航、连接规则、样式范围和错误保留。

## Task 4：集成、文档与交付

- 扩展图表分发、Mermaid 对话框、MarkdownPreview 集成测试和 Workbench Mermaid mock E2E，验证单围栏替换、保存链及 Flowchart/Sequence 回归。
- 同步 frontend/editor README、PACKAGE、模块地图和本机 session log；不修改 API、事件、数据库或部署文档。
- 顺序执行定向测试、前端全量 test/lint/typecheck/build、目标 mock E2E 与 `git diff --check`。
- 回顾全部 `.agents/session-log*.md`，只暂存本任务文件并用中文提交信息 `功能：新增 Mermaid 状态图可视化编辑能力` 提交。

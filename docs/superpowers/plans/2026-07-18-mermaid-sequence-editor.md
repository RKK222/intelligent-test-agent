# Mermaid SequenceDiagram 结构化编辑能力实施计划

> **执行要求：** 使用测试驱动开发逐项推进；每个行为先补红灯测试，再实现最小代码并复跑相关回归。

**目标：** 将 Sequence 可视化编辑器升级为支持完整常用 Mermaid 时序语义、递归嵌套、直接画布交互和最小源码差异回写的结构化编辑器。

**架构：** Sequence 子系统采用递归 AST、纯命令层、纯布局层和单个 Vue Flow 场景节点；外层 Mermaid 对话框、官方 parser 校验、Markdown fence 替换和保存链保持不变。

**技术栈：** Vue 3、TypeScript 6、Vue Flow 1.48.2、Mermaid 11.16.0、Vitest、Testing Library、Playwright。

## 全局约束

- Markdown 是唯一事实源，不新增后端 API、数据库、全局 store、依赖或文件格式。
- 未修改源码尽量字节级保留；未知高级语法局部锁定并原样写回。
- 人工维护的复杂逻辑补充中文注释。
- 不修改 Flowchart 行为、公共 editor 导出或现有保存链。
- 保持 Chromium 108 兼容，不依赖新浏览器 API。

## Task 1：递归领域模型与双向转换

- 扩展参与者、box、autonumber、标准箭头、Note、激活、生命周期、注释和递归组合片段类型。
- 以 tokenizer/容器栈解析常用语法，把高级语法转为局部锁定节点。
- serializer 对未修改项复用原源码，对修改项按推断缩进生成，并继续读写旧紧凑坐标 metadata。
- 测试全部语法、嵌套、CRLF/缩进、最小 Diff、锁定语法和 Mermaid 官方 parser round-trip。

## Task 2：命令、校验与布局

- 增加不可变命令接口，覆盖增删改、参与者重命名、端点重绑、同/跨分支移动和确认后的级联删除。
- 校验唯一 ID、引用、锁定边界、激活栈、create/destroy 邻接和生命周期先后关系。
- 计算参与者、生命线、消息、自调用、激活、Note、box、rect、分支和嵌套片段的确定性场景几何。
- 测试非法操作拒绝原因、空分支保留、深层嵌套与大型图布局。

## Task 3：结构化画布与三标签侧栏

- Vue Flow 只承载一个 Sequence 场景节点；专用场景渲染时序元素并发出选择、编辑、拖动和端点重绑事件。
- 实现“元素 / 结构 / 属性”三标签、元素点击/拖放创建、结构树排序、属性编辑和选中联动。
- 复用现有对话框 token、视口、就地编辑和错误展示，不复用 Flowchart 的图方向、端口、缩放、ELK 路由与唯一边约束。
- 测试键盘、焦点、窄屏、确认弹窗、局部锁定和双向选择。

## Task 4：集成、文档与交付

- 扩展 MarkdownPreview 集成测试和工作台 Mermaid mock E2E，覆盖复杂 Sequence 编辑、应用、保存及 Flowchart 回归。
- 同步 frontend/editor README、包说明和本机 session log。
- 顺序执行定向测试、editor typecheck、前端全量 test/lint/typecheck/build、目标 mock E2E 与 `git diff --check`。
- 回顾全部 `.agents/session-log*.md`，只暂存本任务文件并用中文提交信息 `功能：增强 Mermaid 时序图可视化编辑能力` 提交。

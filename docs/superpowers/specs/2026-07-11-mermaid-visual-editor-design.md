# Mermaid 可视化编辑器设计

## 目标与边界

在现有 Markdown 预览中先为 `flowchart`/`graph`、再为 `sequenceDiagram` Mermaid 代码块增加可视化编辑入口。Markdown 仍是唯一事实源；可视化编辑完成后仅替换当前 Mermaid fence 内容，并继续复用 `CodeEditor change -> Workbench.updateTabContent -> workspace.write` 保存链路。不新增后端 API、数据库、全局 store 或独立文件格式。

Flowchart 范围支持节点拖拽、名称与形状修改、节点增删、边增删、边标签与方向修改、自动布局，以及节点坐标 metadata 回写。Sequence 范围支持参与者拖拽、名称与 participant/actor 类型修改、参与者增删，以及消息增删、顺序、方向、标签和常见箭头类型修改。其他 Mermaid 图类型不能进入可视化编辑；`classDef`、`style`、`click`、`subgraph`、`linkStyle`、复杂时序控制块等未参与编辑的语句按原文本保留。

## 架构

`frontend/packages/editor/src/mermaid` 新增独立领域层：Graph Model 描述节点、边、布局方向和保留语句；parser 只解析当前 Mermaid block；serializer 生成稳定 DSL；metadata 负责 `%% editor-layout:` 注释；Markdown block 工具负责按 fence 索引精确替换。领域层不依赖 Vue 或 DOM，可单元测试。

画布使用 `@vue-flow/core` 1.48.2：Vue Flow 负责节点拖拽、缩放/平移、选择、连接创建和 viewport。Flowchart 与 Sequence 各自拥有领域模型、parser、serializer 和 Vue Flow adapter，通过判别联合类型接入同一对话框；第三方类型不进入领域 parser/serializer。Sequence 将参与者映射为带 lifeline 的节点，将有序消息映射为带序号的自定义边，并在属性面板中提供消息上移/下移。`MarkdownPreview` 在 Mermaid header 中增加“可视化编辑”，点击后才动态加载 Mermaid 官方 parser 和编辑对话框。官方 parser 先验证语法，领域 parser 再生成模型。应用时先序列化并再次用官方 parser 校验，再替换当前 block 并向 `CodeEditor` 发送完整 Markdown `change`。若打开期间 block 被外部刷新，应用被拒绝，避免覆盖 Agent 的新内容。

## 交互与视觉

对话框沿用现有 `--ta-surface`、`--ta-control`、`--ta-border`、`--ta-ink` 和主色 token，不引入新的全局主题。Vue Flow 画布使用轻量网格表达“源码结构图”，节点 ID 使用等宽字体；其余 chrome 保持紧凑克制。顶部提供自动布局、新增节点、适应视图、应用和取消；右侧编辑选中节点及全部边，边可通过画布 Handle 新建，并支持交换起终点作为方向修改。

画布支持键盘焦点、清晰 focus ring 和 reduced-motion。删除节点时同步删除关联边；新增边必须选择有效起终点。解析或序列化失败时保留当前 Markdown 和对话框草稿，只展示可操作的中文错误提示。

## 数据与兼容性

节点坐标写入 Mermaid 可忽略的注释：

```mermaid
flowchart TD
%% editor-layout:
%% {
%%   "A": { "x": 120, "y": 80 }
%% }
A["开始"]
```

parser 缺少坐标时使用确定性自动布局；serializer 对节点和边使用稳定顺序，避免无意义 Git diff。未知语句原样追加回 DSL。只替换目标 fence 内部内容，Markdown 其他文本、fence 标记和保存行为保持不变。

## 验证

领域测试分别覆盖 flowchart/graph 与 sequenceDiagram 的节点/参与者、边/有序消息、箭头类型、metadata、未知语句保留、round trip、精确 block 替换和自动布局。组件测试覆盖两类图的 Vue Flow 映射、拖拽位置同步、连接创建、打开入口、解析错误、属性编辑、应用回写和外部内容冲突。最终执行 editor 定向 Vitest、前端全量 test/typecheck/lint/build 与 `git diff --check`。

# @test-agent/editor

## 工程定位

Monaco 文件编辑器包。

## 主要职责

- 按文件路径推断语言。
- 展示当前文件内容、脏状态、只读状态和保存按钮。
- 编辑器 tab、文件工具栏和 Monaco 容器使用 Figma Web IDE 风格的浅灰/白底紧凑 chrome，代码区默认 14px 字号、20px 行高；无文件空态使用细边框图标，不使用投影，并通过 `empty-actions` slot 允许 app 层注入主页操作，但 editor 包不感知具体业务。
- 从空状态首次打开文件时按需初始化 Monaco，并在后续文件切换时复用 editor 实例切换 model。
- 上报 Monaco 当前文本选区给 app 层，用于构造 Prompt file context。
- Monaco 右键菜单提供“添加选中内容到对话” action，只 emit 当前选区添加事件；具体上下文类型、大小校验和提示由 app 层处理。
- 保存动作通过回调交给 app 层调用 `backend-api`。
- 选区上下文只上报文件路径、语言、选区范围和文本片段；是否转换为 `PromptPart` 由 app 层负责。
- 编辑器容器需要保持稳定高度和最小宽度，避免保存反馈、只读状态或长文件名挤压 Monaco 区域。
- Markdown 预览：`.md` 文件的「预览」开关由调用方受控传入，文件打开时默认不预览；开启后采用 Monaco + markdown-it 模式，现有的 Monaco 组件负责源码编辑，markdown-it 负责渲染，编辑器主体上下分屏（上为 Monaco 源码、下为渲染预览），中间为可拖拽 sash（20%~80%）。源码宿主在分屏和容器尺寸变化时会按实际宽高显式触发 Monaco `layout()`，并丢弃异步加载期间已过期的文件模型，避免原文区域空白。预览由 `MarkdownPreview.vue` 懒加载 `markdown-it`（转 HTML）+ `highlight.js`（代码高亮）+ `dompurify`（安全消毒）渲染，`github-markdown-css` 提供基础排版样式；这套库不进入首屏同步 bundle，解析失败时保留原文纯文本回退；切换文件时预览自动重置为关闭。
- 源码↔预览对齐：`markdown-it` 顶级块带 `data-source-line`（源码行号），在预览左侧 gutter 显示行号；编辑器与预览双向按源码行滚动联动（编辑器顶部行 → 预览对应块顶端；预览顶部块 → 编辑器对应行），用 `scrollLock` 防回环。
- Mermaid 可视化编辑：预览中的 Mermaid block 提供“可视化编辑”入口，点击后才懒加载 `@vue-flow/core`、Mermaid 官方 parser 和编辑对话框，不影响 Markdown 首屏。支持 `flowchart`/`graph` 的节点/边增删、拖拽、名称、形状、标签、方向、箭头类型与自动布局；支持 `sequenceDiagram` 的 participant/actor 增删拖拽、名称/类型，以及有序消息的增删、标签、方向、箭头类型和上移/下移。
- Mermaid 双向转换：`src/mermaid` 使用独立 Flowchart/Sequence 领域模型，禁止反向操作渲染 SVG。应用前后都通过 Mermaid 官方 parser 校验；未知 `classDef`、`style`、`Note`、`activate`、`loop` 等暂不参与可视化的语句原样保留，解析或序列化失败不会覆盖 Markdown。
- 布局与并发保护：节点/参与者位置写入 Mermaid 可忽略的 `%% editor-layout:` JSON 注释；应用时只替换当前 fence。若打开期间 Agent 或其他入口刷新了该 block，则拒绝覆盖并提示重新打开。
- 保存集成：`MarkdownPreview` 把完整新 Markdown 通过既有 `CodeEditor change` 上报，不新增保存 API、文件格式或全局 store；dirty、Ctrl/Cmd+S、workspace.write 和 Git Diff 行为保持原链路。

## 禁止事项

- 不启动 Run。
- 不直接调用后端。
- 不处理 Diff 接受拒绝。
- 不订阅 RunEvent，不直连 opencode server。
- 不把 Mermaid 编辑状态写入数据库、独立文件或 Pinia。

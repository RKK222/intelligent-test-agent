# @test-agent/editor

## 工程定位

Monaco 文件编辑器包。

## 主要职责

- 按文件路径推断语言。
- 展示当前文件内容、脏状态、只读状态和保存按钮。
- 编辑器 tab、文件工具栏和 Monaco 容器使用 Figma Web IDE 风格的浅灰/白底紧凑 chrome，代码区默认 14px 字号、20px 行高并按可视宽度自动换行；无文件空态使用细边框图标，不使用投影，并通过 `empty-actions` slot 允许 app 层注入主页操作，但 editor 包不感知具体业务。
- 从空状态首次打开文件时按需初始化 Monaco，并在后续文件切换时复用 editor 实例切换 model。
- 外部 `path/content` 同 tick 变化时，内容同步必须先校验当前 Monaco model URI 与目标路径一致；异步模型切换完成前不得把新文件内容写入旧模型。
- 上报 Monaco 当前文本选区给 app 层，用于构造 Prompt file context。
- Monaco 右键菜单提供“添加选中内容到对话” action，只 emit 当前选区添加事件；具体上下文类型、大小校验和提示由 app 层处理。
- 保存动作通过回调交给 app 层调用 `backend-api`。
- 选区上下文只上报文件路径、语言、选区范围和文本片段；是否转换为 `PromptPart` 由 app 层负责。
- 编辑器容器需要保持稳定高度和最小宽度，避免保存反馈、只读状态或长文件名挤压 Monaco 区域。
- Markdown 预览：`.md` 文件的「预览」开关由调用方受控传入，文件打开时默认不预览；开启后采用 Monaco + markdown-it 模式，现有的 Monaco 组件负责源码编辑，markdown-it 负责渲染，编辑器主体上下分屏（上为 Monaco 源码、下为渲染预览），中间为可拖拽 sash（20%~80%）。源码宿主在分屏和容器尺寸变化时会按实际宽高显式触发 Monaco `layout()`，并丢弃异步加载期间已过期的文件模型，避免原文区域空白。预览由 `MarkdownPreview.vue` 懒加载 `markdown-it`（转 HTML）+ `highlight.js`（代码高亮）+ `dompurify`（安全消毒）渲染，`github-markdown-css` 提供基础排版样式；这套库不进入首屏同步 bundle，解析失败时保留原文纯文本回退；切换文件时预览自动重置为关闭。
- 源码↔预览对齐：`markdown-it` 顶级块带 `data-source-line`（源码行号），在预览左侧 gutter 显示行号；编辑器与预览双向按源码行滚动联动（编辑器顶部行 → 预览对应块顶端；预览顶部块 → 编辑器对应行），用 `scrollLock` 防回环。
- Mermaid 可视化编辑：预览中的 Mermaid block 提供“可视化编辑”入口，点击后才懒加载 `@vue-flow/core`、Mermaid 官方 parser 和编辑对话框，不影响 Markdown 首屏。`flowchart`/`graph` 支持开始/结束、普通处理步骤、圆角处理节点、子程序、数据库、连接点、条件判断、准备步骤、输入或输出、人工处理、终止节点、文本块、文档和多文档共 14 类节点；右侧图形库按“流程图 / 文档与显示”分组，支持拖放或点击创建，四向快捷建连使用双列“图标＋名称”的屏幕空间浮层，在画布缩放时保持可读并在视口边缘自动翻转。画布、图形库和快捷菜单复用同一 SVG 轮廓；各轮廓按目录配置 8/12 个连接点并保证四个方向均可快捷建连。切换节点类型时，关联边按旧端口相对位置迁移到新轮廓最近端口，自环保留两个不同端口并清除陈旧路由。编辑器支持四种方向与 ELK Layered 自动布局，画布与 ELK 共用节点尺寸计算；自定义边从节点外安全轨道重接 Vue Flow 实际 Handle，校验源端向外、目标端向内后渲染 6px 小圆角轨道并按路径中点放标签。没有安全内部轨道、路由非法或只有两个端点时回退 SmoothStep。浏览器构建必须使用 `elkjs/lib/elk.bundled.js`，不能引用默认 Node 入口。`sequenceDiagram` 支持 participant/actor 增删拖拽、名称/类型，以及有序消息的增删、标签、方向、箭头类型和上移/下移。
- Mermaid 双向转换：`src/mermaid` 使用独立 Flowchart/Sequence 领域模型，禁止反向操作渲染 SVG。Flowchart 兼容 11 类旧式节点语法和 14 类现代节点语法；用户应用可视化结果时统一保存为 `ID@{ shape: <短名>, label: "<文本>" }`，单纯打开预览不会改写 Markdown。应用前后都通过 Mermaid 官方 parser 校验；未知形状、现代节点额外属性、损坏语句及 `classDef`、`style`、`Note`、`activate`、`loop` 等暂不参与可视化的内容原样保留，解析或序列化失败不会覆盖 Markdown。
- 布局、端口、路由与并发保护：Flowchart 节点坐标、固定端口、正交路由及 Sequence 参与者坐标统一写成一个无空格、无 padding 的紧凑逻辑 envelope。Base64URL payload 不超过 240 字符时使用 `%%@<chunk>` 单行，较长时紧邻追加 `%%@+<chunk>` 续行，每段最多 240 字符；内部版本化二进制仍使用 0.1px 坐标、增量 ZigZag/LEB128、端口 nibble、正交分段和拓扑绑定的 FNV-1a 校验，不增加运行时依赖。没有非零坐标、端口或路由时不写注释。旧 `%% editor-layout:` / `%% editor-edge-ports:` 及历史超长单行 `%%@` 仍可读；损坏、重复、孤立或中断的新注释及损坏旧注释保留并回退，且不会写入第二个冲突 marker。旧版 reader 遇到多行 envelope 只会保留数据并回退，不能应用其中布局。节点拖动、改名/改形、改方向、增删或重连会清除派生路由，只有修改边标签保留路由。应用时只替换当前 fence；若打开期间 Agent 或其他入口刷新了该 block，则拒绝覆盖并提示重新打开。
- 保存集成：`MarkdownPreview` 把完整新 Markdown 通过既有 `CodeEditor change` 上报，不新增保存 API、文件格式或全局 store；dirty、Ctrl/Cmd+S、workspace.write 和 Git Diff 行为保持原链路。

## 禁止事项

- 不启动 Run。
- 不直接调用后端。
- 不处理 Diff 接受拒绝。
- 不订阅 RunEvent，不直连 opencode server。
- 不把 Mermaid 编辑状态写入数据库、独立文件或 Pinia。
